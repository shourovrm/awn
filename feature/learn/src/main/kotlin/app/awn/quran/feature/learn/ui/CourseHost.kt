@file:Suppress("TooManyFunctions") // One small composable per course screen.

package app.awn.quran.feature.learn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.feature.learn.CourseAssets
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.LearnTracks
import app.awn.quran.feature.learn.course.LearningArabic
import app.awn.quran.feature.learn.course.RepositoryWordInfoSource
import app.awn.quran.feature.learn.course.RepositoryWordMeanings
import app.awn.quran.feature.learn.progress.LearnSettings
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.session.QuestionFactory
import app.awn.quran.feature.learn.session.SessionController
import app.awn.quran.feature.learn.session.SessionDependencies
import app.awn.quran.feature.learn.words.CourseSnapshot
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the course screens read from the rest of the app. */
internal class CourseDependencies(
    val services: LearnServices,
    val translations: TranslationRepository,
    val packs: PackRepository,
    val downloads: PackDownloads,
    val arabic: LearningArabic,
    /** "en" or "bn". */
    val languageCode: String,
)

/** The course data once it is loaded, with the lookups the screens and sessions share. */
internal class CourseRuntime(
    val assets: CourseAssets,
    private val dependencies: CourseDependencies,
) {
    private val infoSource =
        RepositoryWordInfoSource(
            assets.forms,
            dependencies.arabic,
            RepositoryWordMeanings(dependencies.translations, dependencies.languageCode),
        )

    val lookups = CourseLookups(assets.content, assets.forms, infoSource, dependencies.languageCode)

    fun sessionDependencies(choices: CourseChoices) =
        SessionDependencies(
            repository = dependencies.services.courseRepository,
            factory = QuestionFactory(assets.forms, infoSource),
            content = assets.content,
            frequency = assets.forms,
            infoSource = infoSource,
            arabic = dependencies.arabic,
            planner = assets.planner,
            choices = choices,
            languageCode = dependencies.languageCode,
            celebratedLevels = { dependencies.services.settings.celebratedLevels.first() },
        )
}

/** The steps of the session on screen: a day's plan, or a quiz the user started. */
internal class SessionRequest(
    val steps: List<PlanStep>,
    val plannedSeconds: Int,
    val isOwnQuiz: Boolean,
)

/** The word-by-word pack that gives the course its meanings: the app language, else English. */
internal fun wordMeaningsPackId(languageCode: String): String =
    if (languageCode == "bn") "wbw_bn" else "wbw_en"

private const val ENGLISH_MEANINGS_PACK = "wbw_en"

/** What every course screen shares: the data, the state of the course and how to move. */
@Suppress("LongParameterList") // Holds what every course screen reads.
internal class CourseScreenContext(
    val dependencies: CourseDependencies,
    val runtime: CourseRuntime,
    val snapshot: CourseSnapshot,
    val choices: CourseChoices,
    val scope: CoroutineScope,
    val onRouteChange: (LearnRoute) -> Unit,
    val startSession: (SessionRequest) -> Unit,
) {
    val services: LearnServices
        get() = dependencies.services

    fun goHub() = onRouteChange(LearnRoute.Hub)
}

/**
 * Draws the course screen for [route]; the routes that belong to the older lessons are drawn
 * elsewhere.
 */
@Composable
internal fun CourseRoutes(
    route: LearnRoute,
    onRouteChange: (LearnRoute) -> Unit,
    dependencies: CourseDependencies,
    runtime: CourseRuntime?,
    dueAyahs: List<AyahRef>,
    onReviseAt: ((AyahRef) -> Unit)?,
    barActions: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    if (runtime == null) return
    val services = dependencies.services
    val snapshot by services.courseRepository.observeSnapshot().collectAsState(CourseSnapshot.Empty)
    val sessionMinutes by
        services.settings.sessionMinutes.collectAsState(LearnSettings.DEFAULT_SESSION_MINUTES)
    val includeLetters by services.settings.includeLetters.collectAsState(false)
    var request by remember { mutableStateOf<SessionRequest?>(null) }
    val context =
        CourseScreenContext(
            dependencies = dependencies,
            runtime = runtime,
            snapshot = snapshot,
            choices = CourseChoices(sessionMinutes, includeLetters),
            scope = rememberCoroutineScope(),
            onRouteChange = onRouteChange,
            startSession = { newRequest ->
                request = newRequest
                onRouteChange(LearnRoute.Session(newRequest.isOwnQuiz))
            },
        )
    val actions = hubActions(context, dueAyahs, onReviseAt)
    when (route) {
        LearnRoute.FirstOpen -> FirstOpenRoute(context, modifier)
        LearnRoute.CourseChoices -> CourseChoicesRoute(context, modifier)
        LearnRoute.Hub ->
            HubRoute(context, dueAyahs) { CourseHub(it, actions, modifier, barActions) }
        is LearnRoute.Track,
        is LearnRoute.SetPage,
        is LearnRoute.PageWords,
        is LearnRoute.CourseUnit -> TrackRoutes(route, context, actions, dueAyahs, modifier)
        is LearnRoute.Session -> SessionRouteOrHub(request, context, modifier)
        LearnRoute.QuizStart -> QuizStartRoute(context, modifier)
        LearnRoute.WordList -> WordListRoute(context, modifier)
        is LearnRoute.WordPage -> WordPageRoute(route, context, modifier)
        is LearnRoute.WordAyahs -> WordAyahsRoute(route, context, modifier)
        LearnRoute.Sources ->
            SourcesScreen(
                runtime.assets.content.references,
                onBack = context::goHub,
                modifier = modifier,
            )
        LearnRoute.Progress ->
            CourseProgressScreen(
                progress =
                    buildCourseProgress(
                        snapshot.wordsMet,
                        snapshot.knownRanks,
                        runtime.assets.forms,
                    ),
                onBack = context::goHub,
                modifier = modifier,
            )
        else -> error("$route is not a course screen.")
    }
}

@Composable
private fun FirstOpenRoute(context: CourseScreenContext, modifier: Modifier) {
    FirstOpenScreen(
        onContinue = { picked ->
            context.scope.launch {
                val settings = context.services.settings
                settings.setIncludeLetters(picked.includeLetters)
                settings.setSessionMinutes(picked.minutes)
                settings.setCourseStarted()
                context.goHub()
            }
        },
        modifier = modifier,
    )
}

/** The first screen again, with today's choices ticked; saving keeps the cards and progress. */
@Composable
private fun CourseChoicesRoute(context: CourseScreenContext, modifier: Modifier) {
    FirstOpenScreen(
        initial = FirstChoices(context.choices.includeLetters, context.choices.sessionMinutes),
        onContinue = { picked ->
            context.scope.launch {
                val settings = context.services.settings
                settings.setIncludeLetters(picked.includeLetters)
                settings.setSessionMinutes(picked.minutes)
                context.goHub()
            }
        },
        modifier = modifier,
    )
}

private fun hubActions(
    context: CourseScreenContext,
    dueAyahs: List<AyahRef>,
    onReviseAt: ((AyahRef) -> Unit)?,
): HubActions =
    HubActions(
        onStart = {
            context.scope.launch {
                val plan = context.services.planToday(context.snapshot, context.choices)
                context.startSession(SessionRequest(plan.steps, plan.plannedSeconds, false))
            }
        },
        onProgress = { context.onRouteChange(LearnRoute.Progress) },
        onWordList = { context.onRouteChange(LearnRoute.WordList) },
        onQuiz = { context.onRouteChange(LearnRoute.QuizStart) },
        onTrack = { context.onRouteChange(LearnRoute.Track(it)) },
        onReadingProgress = { context.onRouteChange(LearnRoute.ReadingProgress) },
        onReviseAyahs = {
            val firstDue = dueAyahs.minOrNull()
            if (onReviseAt != null && firstDue != null) onReviseAt(firstDue)
            else context.onRouteChange(LearnRoute.Revision)
        },
        onSessionMinutes = { minutes ->
            context.scope.launch { context.services.settings.setSessionMinutes(minutes) }
        },
        onSources = { context.onRouteChange(LearnRoute.Sources) },
        onDownloadMeanings = {
            context.dependencies.downloads.request(
                wordMeaningsPackId(context.dependencies.languageCode),
            )
        },
        // Only the Quran words track draws these links, so Back returns to it.
        onLesson = {
            val track = LearnTracks.trackOf(it, context.runtime.assets.content)
            context.onRouteChange(LearnRoute.CourseUnit(it, track, isOpenedFromWords = true))
        },
        onChangeChoices = { context.onRouteChange(LearnRoute.CourseChoices) },
    )

@Composable
private fun SessionRouteOrHub(
    request: SessionRequest?,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    if (request == null) {
        // The request is lost when the tab is rebuilt; there is nothing to continue.
        LaunchedEffect(Unit) { context.goHub() }
    } else {
        SessionRoute(
            request,
            context.runtime,
            context.choices,
            context.services.settings,
            onFinished = context::goHub,
            modifier,
        )
    }
}

@Composable
private fun QuizStartRoute(context: CourseScreenContext, modifier: Modifier) {
    QuizStartScreen(
        counts = quizCounts(context.snapshot, Instant.now().toEpochMilli()),
        onStart = { quizScope, length ->
            context.scope.launch {
                val pace = context.services.courseRepository.pace()
                val nowMillis = Instant.now().toEpochMilli()
                val quiz = buildOwnQuiz(quizScope, length, context.snapshot, nowMillis, pace)
                if (quiz.steps.isNotEmpty()) {
                    context.startSession(SessionRequest(quiz.steps, quiz.plannedSeconds, true))
                }
            }
        },
        onBack = context::goHub,
        modifier = modifier,
    )
}

@Composable
private fun WordListRoute(context: CourseScreenContext, modifier: Modifier) {
    val lookups = context.runtime.lookups
    val snapshot = context.snapshot
    val rows by
        produceState(emptyList<WordListRow>(), snapshot, lookups) {
            value = withContext(Dispatchers.Default) { buildWordListRows(lookups, snapshot) }
        }
    WordListScreen(
        rows = rows,
        onOpen = { context.onRouteChange(LearnRoute.WordPage(it)) },
        onBack = context::goHub,
        modifier = modifier,
    )
}

/** The word page: swipes between the words of the set it was opened from. */
@Composable
private fun WordPageRoute(
    route: LearnRoute.WordPage,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    val plan = rememberWordPagerPlan(route, context.runtime.lookups.frequency.formCount)
    WordPagerScreen(
        plan = plan,
        onBack = { context.onRouteChange(learnBackFrom(route) ?: LearnRoute.WordList) },
        modifier = modifier,
    ) { rank ->
        WordPageOfRank(rank, context) {
            context.onRouteChange(LearnRoute.WordAyahs(rank, LearnRoute.WordPage(rank, route.from)))
        }
    }
}

@Composable
private fun WordPageOfRank(rank: Int, context: CourseScreenContext, onOpenAyahs: () -> Unit) {
    val lookups = context.runtime.lookups
    val snapshot = context.snapshot
    val details by
        produceState<WordDetails?>(null, rank, snapshot, lookups) {
            val nowMillis = Instant.now().toEpochMilli()
            value =
                withContext(Dispatchers.Default) {
                    buildWordDetails(lookups, snapshot, rank, nowMillis)
                }
        }
    details?.let { shown ->
        WordPageBody(
            details = shown,
            onReviseNow = {
                context.scope.launch {
                    val quiz = singleWordQuiz(rank, context.services.courseRepository.pace())
                    context.startSession(SessionRequest(quiz.steps, quiz.plannedSeconds, true))
                }
            },
            onAdd = { context.scope.launch { context.services.courseRepository.queueWord(rank) } },
            onOpenAyahs = onOpenAyahs,
        )
    }
}

/** Builds the hub state off the main thread and draws [screen] with it: the hub or Words. */
@Composable
internal fun HubRoute(
    context: CourseScreenContext,
    dueAyahs: List<AyahRef>,
    screen: @Composable (HubState) -> Unit,
) {
    val meanings = rememberMeaningsState(context.dependencies)
    val snapshot = context.snapshot
    val choices = context.choices
    val lookups = context.runtime.lookups
    val state by
        produceState<HubState?>(null, snapshot, choices, meanings, dueAyahs, lookups) {
            value =
                withContext(Dispatchers.Default) {
                    val plan = context.services.planToday(snapshot, choices)
                    buildHubState(
                        lookups,
                        snapshot,
                        plan,
                        choices,
                        dueAyahs.size,
                        meanings,
                    )
                }
        }
    state?.let { screen(it) }
}

@Composable
private fun rememberMeaningsState(dependencies: CourseDependencies): MeaningsState {
    val installed by
        remember(dependencies.packs) { dependencies.packs.installed() }.collectAsState(emptyList())
    val transfers by dependencies.downloads.transfers.collectAsState(emptyMap())
    val wantedId = wordMeaningsPackId(dependencies.languageCode)
    val isInstalled = installed.any { it.id == wantedId || it.id == ENGLISH_MEANINGS_PACK }
    return when {
        isInstalled -> MeaningsState.Ready
        transfers[wantedId].let { it is PackTransfer.Waiting || it is PackTransfer.Running } ->
            MeaningsState.Downloading
        else -> MeaningsState.Missing
    }
}

@Composable
private fun SessionRoute(
    request: SessionRequest,
    runtime: CourseRuntime,
    choices: CourseChoices,
    settings: LearnSettings,
    onFinished: () -> Unit,
    modifier: Modifier,
) {
    val controller =
        remember(request) {
            SessionController(
                request.steps,
                runtime.sessionDependencies(choices),
                request.isOwnQuiz,
                request.plannedSeconds,
            )
        }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsState()
    val leave = {
        scope.launch {
            controller.close()
            onFinished()
        }
        Unit
    }
    BackHandler(onBack = leave)
    SessionScreen(
        state = state,
        actions =
            SessionActions(
                onClose = leave,
                onNext = { scope.launch { controller.next() } },
                onAnswer = { optionIndex -> scope.launch { controller.answer(optionIndex) } },
                onDone = onFinished,
                onLevelCelebrated = { level ->
                    scope.launch { settings.markLevelCelebrated(level) }
                },
            ),
        modifier = modifier,
    )
}
