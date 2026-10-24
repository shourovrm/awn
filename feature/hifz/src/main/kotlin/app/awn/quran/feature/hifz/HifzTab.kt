package app.awn.quran.feature.hifz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.HideTabBarOnInnerPage
import app.awn.quran.feature.hifz.method.MethodDefaults
import app.awn.quran.feature.hifz.ui.HifzActions
import app.awn.quran.feature.hifz.ui.HifzMainContent
import app.awn.quran.feature.hifz.ui.JournalContent
import app.awn.quran.feature.hifz.ui.JournalScreen
import app.awn.quran.feature.hifz.ui.KnownUpToScreen
import app.awn.quran.feature.hifz.ui.MarkKnownScreen
import app.awn.quran.feature.hifz.ui.MethodChooser
import app.awn.quran.feature.hifz.ui.PageFacts
import app.awn.quran.feature.hifz.ui.PageSheet

private enum class HifzPage(val hasOwnBar: Boolean = false) {
    Main,
    Method,
    Mark(hasOwnBar = true),
    KnownUpTo(hasOwnBar = true),
    Journal(hasOwnBar = true),
}

/**
 * The Hifz tab. It draws its own top bar (title and [topBarActions], where :app puts the settings
 * button); pass [showTopBar] false when the tab scaffold already has one. [onStartPractice] runs
 * after the practice request is stored in [controller] and should open the Quran screen at
 * `request.firstAyah`; [onOpenAyah] opens the Quran screen at an ayah without practice.
 */
// The tab chooses between its pages in one place.
@Suppress("LongMethod")
@Composable
fun HifzTab(
    controller: HifzController,
    quranText: QuranTextRepository,
    onStartPractice: (PracticeRequest) -> Unit,
    onOpenAyah: (AyahRef) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val screenState by controller.screenState.collectAsStateWithLifecycle()
    val chapters by produceState(emptyList<Chapter>(), quranText) { value = quranText.chapters() }
    val isBangla = LocalConfiguration.current.locales[0].language == BANGLA_CODE
    val surahName: (Int) -> String = { number ->
        val chapter = chapters.firstOrNull { it.number == number }
        if (isBangla) chapter?.nameBangla ?: chapter?.nameSimple.orEmpty()
        else chapter?.nameSimple.orEmpty()
    }
    var page by rememberSaveable { mutableStateOf(HifzPage.Main) }
    HideTabBarOnInnerPage(page != HifzPage.Main)
    BackHandler(enabled = page != HifzPage.Main) { page = HifzPage.Main }

    Column(modifier.fillMaxSize().background(Awn.field.background)) {
        if (showTopBar && !page.hasOwnBar) {
            AwnTopBar(title = stringResource(R.string.hifz_title), actions = topBarActions)
        }
        when (val state = screenState) {
            HifzScreenState.Loading -> Box(Modifier.fillMaxSize())
            HifzScreenState.NoPlan ->
                ScrollingPage {
                    val language = LocalConfiguration.current.locales[0].language
                    val first = MethodDefaults.firstChoiceFor(language)
                    MethodChooser(
                        initialMethod = first,
                        initialSettings = MethodDefaults.settingsFor(first),
                        onUse = { method, settings -> controller.chooseMethod(method, settings) },
                    )
                }
            is HifzScreenState.Ready ->
                when (page) {
                    HifzPage.Main ->
                        MainPage(controller, state.state, surahName, onStartPractice, onOpenAyah) {
                            page = it
                        }
                    HifzPage.Method ->
                        ScrollingPage {
                            SettingsLinkRow(
                                label = stringResource(R.string.known_up_to_title),
                                onClick = { page = HifzPage.KnownUpTo },
                                modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
                            )
                            MethodChooser(
                                initialMethod = state.state.plan.method,
                                initialSettings = state.state.plan.settings,
                                onUse = { method, settings ->
                                    controller.chooseMethod(method, settings)
                                    page = HifzPage.Main
                                },
                            )
                        }
                    HifzPage.Mark ->
                        MarkKnownScreen(
                            table = state.state.table,
                            surahName = surahName,
                            onSave = controller::markPages,
                            onOpenKnownUpTo = { page = HifzPage.KnownUpTo },
                            onBack = { page = HifzPage.Main },
                        )
                    HifzPage.Journal ->
                        JournalPage(controller, state.state, surahName, onOpenAyah) {
                            page = HifzPage.Main
                        }
                    HifzPage.KnownUpTo ->
                        KnownUpToScreen(
                            table = state.state.table,
                            surahName = surahName,
                            onSave = controller::setKnownUpTo,
                            onBack = { page = HifzPage.Main },
                        )
                }
        }
    }
}

/** The main page and the sheet its map opens. */
@Composable
private fun MainPage(
    controller: HifzController,
    state: HifzState,
    surahName: (Int) -> String,
    onStartPractice: (PracticeRequest) -> Unit,
    onOpenAyah: (AyahRef) -> Unit,
    onOpen: (HifzPage) -> Unit,
) {
    var sheetPage by rememberSaveable { mutableStateOf<Int?>(null) }
    ScrollingPage {
        HifzMainContent(
            state = state,
            surahName = surahName,
            actions =
                HifzActions(
                    onOpenMethod = { onOpen(HifzPage.Method) },
                    onStartPart = { kind -> controller.startPractice(kind)?.let(onStartPractice) },
                    onOpenMark = { onOpen(HifzPage.Mark) },
                    onOpenWeakSpot = onOpenAyah,
                    onOpenJournal = { onOpen(HifzPage.Journal) },
                    onOpenPage = { sheetPage = it },
                ),
        )
    }
    sheetPage?.let { shown ->
        MapPageSheet(controller, state, shown, surahName, onOpenAyah) { sheetPage = null }
    }
}

@Composable
private fun MapPageSheet(
    controller: HifzController,
    state: HifzState,
    page: Int,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestAnswers by
        remember(controller) { controller.latestAnswers() }.collectAsStateWithLifecycle(emptyMap())
    PageSheet(
        facts = PageFacts.of(state, page, latestAnswers),
        surahName = surahName,
        onChangeState = { status -> controller.markPages(page..page, status) },
        onOpenPage = { ayah ->
            onDismiss()
            onOpenAyah(ayah)
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun JournalPage(
    controller: HifzController,
    state: HifzState,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
    onBack: () -> Unit,
) {
    val entries by
        remember(controller) { controller.journal() }.collectAsStateWithLifecycle(emptyList())
    val mistakes by
        remember(controller) { controller.journalMistakes() }
            .collectAsStateWithLifecycle(emptyList())
    JournalScreen(
        content = JournalContent(entries, mistakes, state.plan.method, state.today),
        surahName = surahName,
        onOpenAyah = onOpenAyah,
        onBack = onBack,
    )
}

@Composable
private fun ScrollingPage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AwnSpacing.extraLarge),
    ) {
        content()
    }
}

private const val BANGLA_CODE = "bn"
