package app.awn.quran

import android.content.res.Resources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.ColourSettings
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.feature.duas.ui.DuasScreen
import app.awn.quran.feature.learn.LearnScreen
import app.awn.quran.feature.listen.AudioBar
import app.awn.quran.feature.listen.PlayRangePanel
import app.awn.quran.feature.read.ReadField
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.recite.MicrophoneAccess
import app.awn.quran.feature.recite.rememberMicrophonePermission
import app.awn.quran.feature.recite.rememberReciteCurrentAyah
import app.awn.quran.feature.recite.rememberReciteCurrentWord
import app.awn.quran.feature.recite.rememberReciteFocusedWord
import app.awn.quran.feature.recite.rememberReciteHintLetters
import app.awn.quran.feature.recite.rememberReciteWordProblems
import app.awn.quran.feature.recite.rememberReciteWordStates
import app.awn.quran.index.BookmarksScreen
import app.awn.quran.index.IndexViewModel
import app.awn.quran.index.QuranTabActions
import app.awn.quran.index.QuranTabScreen
import app.awn.quran.language.AppCompatLanguageApplier
import app.awn.quran.language.AppLanguage
import app.awn.quran.language.AppLanguageApplier
import app.awn.quran.language.FirstLaunchStep
import app.awn.quran.language.firstLaunchStep
import app.awn.quran.notice.LearnNoticeGate
import app.awn.quran.onboarding.LanguageScreen
import app.awn.quran.search.SearchScreen
import app.awn.quran.search.SearchViewModel
import app.awn.quran.search.SpeechModelOffer
import app.awn.quran.search.VoiceSearchActions
import app.awn.quran.search.VoiceSearchSlot
import app.awn.quran.search.VoiceSearchViewModel
import app.awn.quran.services.AwnServices
import app.awn.quran.tabs.AwnTab
import app.awn.quran.tabs.TabActions
import app.awn.quran.tabs.TabSlots
import app.awn.quran.tabs.TabsHost
import app.awn.quran.tabs.TodayNavigation
import app.awn.quran.tabs.defaultTabSlots
import app.awn.quran.tabs.rememberTodayItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun AwnApp(
    services: AwnServices,
    languageApplier: AppLanguageApplier = AppCompatLanguageApplier,
    tabSlots: TabSlots? = null,
) {
    val preferences = services.preferences
    val colourSettings by
        preferences.colourSettings.collectAsStateWithLifecycle(initialValue = ColourSettings())
    val colourMode = colourSettings.resolve(deviceIsDark = isSystemInDarkTheme())
    // null until DataStore has answered, so no first-launch screen flashes before the stored
    // answer.
    val noticeAccepted by
        preferences.noticeAccepted.collectAsStateWithLifecycle(initialValue = null)
    val languageChosen by
        preferences.languageChosen.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalColourMode provides colourMode) {
        val accepted = noticeAccepted
        val chosen = languageChosen
        if (accepted == null || chosen == null) {
            AwnTheme {}
            return@CompositionLocalProvider
        }
        when (firstLaunchStep(noticeAccepted = accepted, languageChosen = chosen)) {
            FirstLaunchStep.ChooseLanguage ->
                LanguageRoute(
                    onContinue = { language ->
                        coroutineScope.launch {
                            // The flag is written first: applying the language recreates the
                            // activity, and the screen must not come back afterwards.
                            preferences.markLanguageChosen(language.languageTag)
                            languageApplier.apply(language)
                        }
                    },
                )
            FirstLaunchStep.Done -> {
                AwnNavigation(
                    services,
                    colourSettings,
                    tabSlots ?: remember(services) { defaultTabSlots(services) },
                )
            }
        }
    }
}

/** Holds the highlighted language, which starts as the phone's language. */
@Composable
private fun LanguageRoute(onContinue: (AppLanguage) -> Unit) {
    val phoneLocale = Resources.getSystem().configuration.locales[0]
    var selectedTag by rememberSaveable {
        mutableStateOf(AppLanguage.preselectedFor(phoneLocale).languageTag)
    }
    val selected = AppLanguage.entries.first { it.languageTag == selectedTag }
    LanguageScreen(
        selected = selected,
        onSelect = { selectedTag = it.languageTag },
        onContinue = { onContinue(selected) },
    )
}

/**
 * Screens slide for [ScreenTransitions.SLIDE_MILLIS] ms. Navigation Compose's default is a 700 ms
 * cross-fade on every navigate and pop, which made Back feel slow; the four transitions below
 * replace it (NavTransitionsTest checks this file). The manifest opts out of the system's
 * predictive back, whose back-to-home animation shrank the whole app.
 */
@Composable
private fun AwnNavigation(
    services: AwnServices,
    colourSettings: ColourSettings,
    tabSlots: TabSlots,
) {
    val navController = rememberNavController()
    // Revision moves to the next due ayah through this; it needs the navigation host.
    DisposableEffect(navController) {
        services.openQuranAt = { ayah -> navController.openQuran(page = null, ayah = ayah) }
        onDispose { services.openQuranAt = {} }
    }
    ApplyAppOrientation(services.readDependencies.settingsStore, navController)
    val viewModelFactory = remember(services) { AwnViewModelFactory(services) }
    // A fresh start opens the Quran tab; the choice survives rotation but nothing else.
    var selectedTabName by rememberSaveable { mutableStateOf(AwnTab.Quran.name) }
    // A page whose words the Learn tab should open; cleared once the tab has shown it.
    var learnPageWordsRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    val tabNavigation =
        TabNavigation(
            navController = navController,
            selectTab = { selectedTabName = it.name },
            requestPageWords = { page -> learnPageWordsRequest = page },
        )
    NavHost(
        navController = navController,
        startDestination = Routes.TABS,
        enterTransition = ScreenTransitions.enter,
        exitTransition = ScreenTransitions.exit,
        popEnterTransition = ScreenTransitions.popEnter,
        popExitTransition = ScreenTransitions.popExit,
    ) {
        composable(
            route = Routes.QURAN,
            arguments =
                listOf(
                    optionalIntArgument(Routes.PAGE_ARGUMENT),
                    optionalIntArgument(Routes.SURAH_ARGUMENT),
                    optionalIntArgument(Routes.AYAH_ARGUMENT),
                ),
        ) { entry ->
            QuranRoute(services, entry, tabNavigation)
        }
        indexDestinations(navController, viewModelFactory, services)
        settingsDestinations(services, navController, viewModelFactory, colourSettings)
        composable(Routes.TABS) {
            TabsRoute(
                services = services,
                viewModelFactory = viewModelFactory,
                navigation = tabNavigation,
                selected = AwnTab.valueOf(selectedTabName),
                tabSlots = tabSlots,
                learnPageWordsRequest = learnPageWordsRequest,
                onLearnPageWordsShown = { learnPageWordsRequest = null },
            )
        }
    }
}

@Composable
private fun TabsRoute(
    services: AwnServices,
    viewModelFactory: ViewModelProvider.Factory,
    navigation: TabNavigation,
    selected: AwnTab,
    tabSlots: TabSlots,
    learnPageWordsRequest: Int?,
    onLearnPageWordsShown: () -> Unit,
) {
    val tabActions = TabActions(navigation::openSettings, navigation::openQuran)
    val todayNavigation =
        TodayNavigation(
            openLearnTab = { navigation.selectTab(AwnTab.Learn) },
            openHifzTab = { navigation.selectTab(AwnTab.Hifz) },
            reviseAt = { ayah, limit ->
                navigation.openQuran(page = null, ayah = ayah)
                services.recite.reviseAt(ayah, limit = limit)
            },
        )
    TabsHost(
        selected = selected,
        onSelect = navigation.selectTab,
        quranTab = {
            val viewModel: IndexViewModel = viewModel(factory = viewModelFactory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            QuranTabScreen(
                state = state,
                todayItems = rememberTodayItems(tabSlots.todayProviders, todayNavigation),
                actions =
                    QuranTabActions(
                        onSearch = navigation::openSearch,
                        onBookmarks = navigation::openBookmarks,
                        onSettings = navigation::openSettings,
                        onDuas = navigation::openDuas,
                        onOpen = navigation::openQuran,
                    ),
                duaCount = rememberDuaCount(services),
            )
        },
        learnTab = {
            LearnNoticeGate(services.preferences) {
                LearnScreen(
                    quranText = services.coreData.quranText,
                    translations = services.coreData.translations,
                    packs = services.coreData.packs,
                    downloads = services.coreData.packDownloads,
                    wordSound = services.learnWordSound,
                    pageLinks = rememberLearnPageLinks(services, navigation),
                    pageWordsRequest = learnPageWordsRequest,
                    onPageWordsRequestHandled = onLearnPageWordsShown,
                    onReviseAt = { ayah ->
                        navigation.openQuran(page = null, ayah = ayah)
                        services.recite.reviseAt(ayah)
                    },
                    onOpenAyah = { ayah -> navigation.openQuran(page = null, ayah = ayah) },
                    barActions = {
                        AwnIconButton(
                            icon = R.drawable.ic_app_settings,
                            contentDescription = stringResource(R.string.index_settings),
                            onClick = navigation::openSettings,
                        )
                    },
                )
            }
        },
        hifzTab = { tabSlots.hifzTab(tabActions) },
    )
}

private fun optionalIntArgument(name: String) =
    navArgument(name) {
        type = NavType.IntType
        defaultValue = Routes.NOT_GIVEN
    }

/**
 * Opens the Quran screen directly above the tabs and drops the screens between, so Back always
 * returns to the tab.
 */
internal fun NavController.openQuran(page: Int?, ayah: AyahRef?) {
    navigate(Routes.quran(page, ayah)) {
        popUpTo(Routes.TABS) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.indexDestinations(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    services: AwnServices,
) {
    composable(Routes.BOOKMARKS) {
        val viewModel: IndexViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        BookmarksScreen(
            state = state,
            onSortChange = viewModel::setSort,
            onBack = { navController.popBackStack() },
            onOpen = navController::openQuran,
        )
    }
    composable(Routes.DUAS) {
        DuasScreen(
            dependencies = services.duasDependencies,
            onBack = { navController.popBackStack() },
            onOpenQuran = { page, ayah -> navController.openQuran(page, ayah) },
        )
    }
    composable(Routes.SEARCH) {
        val viewModel: SearchViewModel = viewModel(factory = viewModelFactory)
        val voiceViewModel: VoiceSearchViewModel = viewModel(factory = viewModelFactory)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()
        val speechModelState by services.speechModel.state.collectAsStateWithLifecycle()
        val typedQuery by viewModel.typedQuery.collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        SearchScreen(
            typedQuery = typedQuery,
            state = state,
            onQueryChange = viewModel::setQuery,
            onBack = { navController.popBackStack() },
            onOpen = navController::openQuran,
            onOpenSurah = { surah ->
                coroutineScope.launch {
                    navController.openQuran(page = viewModel.firstPageOf(surah), ayah = null)
                }
            },
            voice =
                VoiceSearchSlot(
                    state = voiceState,
                    speechModel =
                        SpeechModelOffer(speechModelState, services.speechModel.totalBytes),
                    actions =
                        VoiceSearchActions(
                            onStart = voiceViewModel::startListening,
                            onStop = voiceViewModel::stopListening,
                            onClose = voiceViewModel::close,
                            onDownloadModel = { services.speechModel.download() },
                            onCancelDownload = services.speechModel::cancel,
                        ),
                    onOpen = voiceViewModel::open,
                ),
        )
    }
}

/**
 * The Quran screen with every feature plugged into its slots: the audio bar and play panel from
 * :feature:listen, the recite bar from :feature:recite, day and night from the app preferences.
 * [Routes.SURAH_ARGUMENT] and [Routes.AYAH_ARGUMENT] select an ayah, else [Routes.PAGE_ARGUMENT]
 * opens a page, else the page read last opens.
 */
@Composable
private fun QuranRoute(
    services: AwnServices,
    entry: NavBackStackEntry,
    navigation: TabNavigation,
) {
    val target = quranTargetOf(entry)
    val nightToggle = rememberNightToggle(services.preferences)
    val isReciting by services.recite.isActive.collectAsStateWithLifecycle()
    val practice by services.hifz.controller.practice.collectAsStateWithLifecycle()
    val isAudioActive = rememberIsAudioActive(services)
    val busyState = rememberBusyState(services)
    val toggleBusy = rememberBusyToggle(services, busyState)
    // The audio bar maps a page to its ayahs in the print the reader shows.
    LaunchedEffect(services) {
        services.readDependencies.settingsStore.settings
            .map { it.script }
            .distinctUntilChanged()
            .collect(services.audio::useScript)
    }
    val slots =
        QuranScreenSlots(
            audioBar = { currentPage -> QuranAudioBar(services, currentPage) },
            playPanel = { selected, currentPage ->
                PlayRangePanel(services.audio, selected, currentPage)
            },
            onPlayFrom = services.audio.controller::playFrom,
            reciteBar = { onExit -> ReciteBarOrDownload(services, onExit) },
            onReciteFrom = services.recite::reciteFrom,
            reciteWordStates = { rememberReciteWordStates(services.recite) },
            reciteWordProblems = { rememberReciteWordProblems(services.recite) },
            reciteHintLetters = { rememberReciteHintLetters(services.recite) },
            isNight = nightToggle.isNight,
            onToggleNight = nightToggle.toggle,
            onOpenIndex = { navigation.showTab(AwnTab.Quran) },
            onBackWhenIdle = navigation::returnToTabs,
            onOpenSearch = navigation::openSearch,
            onOpenLearn = { navigation.showTab(AwnTab.Learn) },
            onOpenPageWords = navigation::showPageWords,
            onOpenSettings = navigation::openSettings,
            onPlayRange = { first, last -> services.audio.controller.playRange(first, last) },
            isReciteActive = isReciting,
            reciteCurrentAyah = { rememberReciteCurrentAyah(services.recite) },
            reciteCurrentWord = { rememberReciteCurrentWord(services.recite) },
            reciteFocusedWord = { rememberReciteFocusedWord(services.recite) },
            onReciteFocusWord = services.recite::focusWord,
            hifzBar =
                hifzBarSlot(
                    services,
                    isPracticing = practice != null,
                    onOpenPageWords = navigation::showPageWords,
                ),
            isAudioActive = isAudioActive,
            busyState = { busyState },
            onToggleBusy = toggleBusy,
        )
    AwnTheme(field = ReadField) {
        Box(Modifier.fillMaxSize()) {
            QuranScreen(dependencies = services.readDependencies, slots = slots, target = target)
            StarterOfferOverlay(services)
        }
    }
}

/** Where the Quran screen opens: an ayah, a page, or null for the last place read. */
private fun quranTargetOf(entry: NavBackStackEntry): QuranTarget? {
    val arguments = entry.arguments
    val page = arguments?.getInt(Routes.PAGE_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    val surah = arguments?.getInt(Routes.SURAH_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    val ayah = arguments?.getInt(Routes.AYAH_ARGUMENT)?.takeIf { it != Routes.NOT_GIVEN }
    return when {
        surah != null && ayah != null -> QuranTarget.Ayah(AyahRef(surah, ayah))
        page != null -> QuranTarget.Page(page)
        else -> null
    }
}

/**
 * The audio bar with its microphone: the recite bar takes the bar's place, and the Quran screen
 * enters recite mode by itself when the controller turns active.
 */
@Composable
private fun QuranAudioBar(services: AwnServices, currentPage: Int) {
    val microphone = rememberMicrophonePermission()
    AudioBar(
        services.audio,
        currentPage,
        onRecite = { ayah ->
            // Shown while reciting too: the microphone then goes back to the recitation where the
            // listening left off instead of loading it again.
            if (!services.recite.isActive.value) {
                services.recite.reciteFrom(ayah)
            } else {
                when (microphone.access) {
                    MicrophoneAccess.Granted -> services.recite.resumeReciting()
                    MicrophoneAccess.CanAsk -> microphone.requestAccess()
                    else -> microphone.openAppSettings()
                }
            }
        },
    )
}
