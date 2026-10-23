package app.awn.quran.feature.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.fsrs.FsrsRating
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.LessonLanguageChoice
import app.awn.quran.feature.learn.course.LearningArabic
import app.awn.quran.feature.learn.progress.ProgressSnapshot
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import app.awn.quran.feature.learn.sound.MediaClipPlayer
import app.awn.quran.feature.learn.sound.PackUnitSounds
import app.awn.quran.feature.learn.sound.WordSound
import app.awn.quran.feature.learn.ui.CourseDependencies
import app.awn.quran.feature.learn.ui.CourseRoutes
import app.awn.quran.feature.learn.ui.CourseRuntime
import app.awn.quran.feature.learn.ui.LearnEnvironment
import app.awn.quran.feature.learn.ui.LearnRoute
import app.awn.quran.feature.learn.ui.LearnRouteSaver
import app.awn.quran.feature.learn.ui.LocalLearnEnvironment
import app.awn.quran.feature.learn.ui.LocalOpenAyah
import app.awn.quran.feature.learn.ui.learnBackFrom
import app.awn.quran.feature.learn.ui.learnStartRoute
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val EMPTY_SNAPSHOT =
    ProgressSnapshot(
        streakDays = 0,
        pagesToday = 0,
        pagesThisWeek = 0,
        memorisedByAyah = emptySet(),
        memorisedBySurah = emptyMap(),
        memorisationThreshold = 0,
        dueToday = emptyList(),
        nextDue = null,
    )

/**
 * The Learn tab: owns the database and moves between the word course, Revision, the progress pages
 * and the lessons. [quranText], [translations], [packs] and [downloads] supply the Arabic, the
 * meanings and the word-meaning download of the course.
 *
 * When [onReviseAt] is set, "Revise" calls it with the first due ayah in reading order, and :app
 * opens the Quran screen there in recite mode with words hidden; the Revision screen of this tab is
 * then not used. Without it, revision runs inside this tab. [barActions] are icon buttons at the
 * end of the bar on the hub (:app puts Settings there). [wordSound] plays a Quran word of a
 * practice item with the word reciter the user chose; the recorded lesson sounds come from [packs].
 * [onOpenAyah] opens an ayah in the Quran, from the surah name under a lesson word.
 */
@Composable
fun LearnScreen(
    quranText: QuranTextRepository,
    translations: TranslationRepository,
    packs: PackRepository,
    downloads: PackDownloads,
    modifier: Modifier = Modifier,
    onReviseAt: ((AyahRef) -> Unit)? = null,
    barActions: @Composable RowScope.() -> Unit = {},
    wordSound: WordSound = WordSound.Silent,
    onOpenAyah: ((AyahRef) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val services = remember { LearnServices.get(context) }
    val locale = LocalConfiguration.current.locales[0]
    val lessonLanguageChoice by
        services.settings.lessonLanguage.collectAsState(initial = LessonLanguageChoice.FollowApp)
    val language = LearnLanguage.resolve(lessonLanguageChoice, locale)
    val scope = rememberCoroutineScope()

    val routeState = rememberStartRoute(services)
    val route = routeState.value

    val snapshot by remember {
        services.progress.observeSnapshot()
    }
        .collectAsState(initial = EMPTY_SNAPSHOT)
    val chapters by produceState(emptyList<Chapter>(), quranText) { value = quranText.chapters() }
    val surahName = { surah: Int ->
        surahNameOf(chapters, surah, language) { resources.getString(R.string.surah_number, surah) }
    }

    val languageCode = if (language == LearnLanguage.Bangla) "bn" else "en"
    val arabic = remember(quranText, packs) { LearningArabic(quranText, packs) }
    val environment =
        rememberLearnEnvironment(arabic, quranText, translations, languageCode, chapters)
    val courseDependencies =
        rememberCourseDependencies(services, translations, packs, downloads, arabic, languageCode)
    val assets by produceState<CourseAssets?>(null) { value = services.courseAssets() }
    val runtime =
        remember(assets, courseDependencies) {
            assets?.let { CourseRuntime(it, courseDependencies) }
        }

    val unitSounds =
        remember(packs, wordSound) { PackUnitSounds(packs, MediaClipPlayer(), wordSound) }
    DisposableEffect(unitSounds) { onDispose { unitSounds.stop() } }

    val current = route ?: return
    CompositionLocalProvider(
        LocalLearnEnvironment provides environment,
        LocalUnitSounds provides unitSounds,
        LocalOpenAyah provides onOpenAyah,
    ) {
        LearnRoutes(
            route = current,
            onRouteChange = { routeState.value = it },
            content =
                LearnRouteContent(
                    snapshot = snapshot,
                    locale = locale,
                    quranText = quranText,
                    surahName = surahName,
                ),
            course = CourseRouteContext(courseDependencies, runtime),
            rate = { ayah, rating -> scope.launch { services.progress.gradeAyah(ayah, rating) } },
            onReviseAt = onReviseAt,
            barActions = barActions,
            modifier = modifier,
        )
    }
}

/** What the screens of the Learn tab read; loaded once by [LearnScreen]. */
internal class LearnRouteContent(
    val snapshot: ProgressSnapshot,
    val locale: Locale,
    val quranText: QuranTextRepository,
    val surahName: (Int) -> String,
)

/**
 * The route on screen, null until the setting that says whether the course started is read. Back
 * walks towards the hub and leaves the tab from the hub.
 */
@Composable
private fun rememberStartRoute(services: LearnServices): MutableState<LearnRoute?> {
    val courseStarted by
        produceState<Boolean?>(null) { value = services.settings.courseStarted.first() }
    // Saved so that turning the phone keeps the open unit instead of returning to the hub.
    val route = rememberSaveable(stateSaver = LearnRouteSaver) { mutableStateOf<LearnRoute?>(null) }
    LaunchedEffect(courseStarted) {
        val started = courseStarted
        if (route.value == null && started != null) route.value = learnStartRoute(started)
    }
    BackHandler(enabled = route.value?.let(::learnBackFrom) != null) {
        route.value = route.value?.let(::learnBackFrom)
    }
    return route
}

@Composable
private fun rememberLearnEnvironment(
    arabic: LearningArabic,
    quranText: QuranTextRepository,
    translations: TranslationRepository,
    languageCode: String,
    chapters: List<Chapter>,
): LearnEnvironment {
    val resources = LocalResources.current
    return remember(arabic, quranText, translations, languageCode, chapters) {
        LearnEnvironment(
            arabic = arabic,
            quranText = quranText,
            translations = translations,
            languageCode = languageCode,
            chapters = chapters,
            fallbackSurahName = { surah -> resources.getString(R.string.surah_number, surah) },
        )
    }
}

@Composable
private fun rememberCourseDependencies(
    services: LearnServices,
    translations: TranslationRepository,
    packs: PackRepository,
    downloads: PackDownloads,
    arabic: LearningArabic,
    languageCode: String,
): CourseDependencies =
    remember(services, translations, packs, downloads, arabic, languageCode) {
        CourseDependencies(services, translations, packs, downloads, arabic, languageCode)
    }

internal class CourseRouteContext(val dependencies: CourseDependencies, val runtime: CourseRuntime?)

@Composable
private fun LearnRoutes(
    route: LearnRoute,
    onRouteChange: (LearnRoute) -> Unit,
    content: LearnRouteContent,
    course: CourseRouteContext,
    rate: (AyahRef, FsrsRating) -> Unit,
    onReviseAt: ((AyahRef) -> Unit)?,
    barActions: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    val snapshot = content.snapshot
    val surahName = content.surahName
    val goHub = { onRouteChange(LearnRoute.Hub) }
    when (route) {
        LearnRoute.ReadingProgress ->
            ProgressScreen(
                state =
                    LearnUiMapper.progressState(
                        snapshot,
                        surahName,
                        ZoneId.systemDefault(),
                        content.locale,
                    ),
                onBackClick = goHub,
                modifier = modifier,
            )
        LearnRoute.Revision ->
            RevisionHost(
                dueToday = snapshot.dueToday,
                quranText = content.quranText,
                surahName = surahName,
                rate = rate,
                onBackClick = goHub,
                modifier = modifier,
            )
        else ->
            CoursePage {
                CourseRoutes(
                    route = route,
                    onRouteChange = onRouteChange,
                    dependencies = course.dependencies,
                    runtime = course.runtime,
                    dueAyahs = snapshot.dueToday,
                    onReviseAt = onReviseAt,
                    barActions = barActions,
                    modifier = modifier,
                )
            }
    }
}

/**
 * The theme for the course screens. The older Learn screens set their own theme; the course screens
 * had none, so nothing kept them clear of the status bar and their top bar drew under it.
 */
@Composable
private fun CoursePage(content: @Composable () -> Unit) {
    AwnTheme(field = AwnField.Paper, content = content)
}

/** Walks through the ayahs that were due when the user pressed Revise, one at a time. */
@Composable
private fun RevisionHost(
    dueToday: List<AyahRef>,
    quranText: QuranTextRepository,
    surahName: (Int) -> String,
    rate: (AyahRef, FsrsRating) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier,
) {
    // The queue is fixed on entry: rating a card removes it from the live due list, and a queue
    // that shrank under the user would skip the next ayah.
    val queue = remember { dueToday }
    var index by remember { mutableIntStateOf(0) }
    var isTextShown by remember { mutableStateOf(false) }
    val currentAyah = queue.getOrNull(index)
    val text by
        produceState<String?>(null, currentAyah, quranText) {
            value = currentAyah?.let { ayah ->
                quranText.wordsOfAyah(ayah).joinToString(" ") { it.textUthmani }.ifEmpty { null }
            }
        }
    val card = currentAyah?.let { RevisionCard(it, "${surahName(it.surah)} $it", text) }
    RevisionScreen(
        state = RevisionUiState(card, queue.size - index, queue.size, isTextShown),
        onShowTextClick = { isTextShown = true },
        onRate = { rating ->
            currentAyah?.let { rate(it, rating) }
            index += 1
            isTextShown = false
        },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

private fun surahNameOf(
    chapters: List<Chapter>,
    surah: Int,
    language: LearnLanguage,
    fallback: () -> String,
): String {
    val chapter = chapters.firstOrNull { it.number == surah } ?: return fallback()
    return if (language == LearnLanguage.Bangla) chapter.nameBangla ?: chapter.nameSimple
    else chapter.nameSimple
}
