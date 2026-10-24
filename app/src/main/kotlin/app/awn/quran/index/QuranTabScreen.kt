package app.awn.quran.index

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.tabs.TodayItem
import kotlinx.coroutines.launch

/** What the buttons of the Quran tab open. */
class QuranTabActions(
    val onSearch: () -> Unit,
    val onBookmarks: () -> Unit,
    val onSettings: () -> Unit,
    /** Opens the Quran screen at [page]. */
    val onOpen: (page: Int, ayah: AyahRef?) -> Unit,
)

/** The two lists the Show choice offers. */
internal enum class IndexMode(val labelResource: Int) {
    Surahs(R.string.index_tab_surahs),
    Juz(R.string.index_tab_juz),
}

/**
 * The Quran tab: the continue card, today's rows, the day's snippet and the index list with the juz
 * rail. [todayItems] is already capped; with none the Today section is not drawn at all.
 */
@Composable
fun QuranTabScreen(
    state: IndexState,
    todayItems: List<TodayItem>,
    actions: QuranTabActions,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            QuranTabTopBar(actions)
            val content = state.content
            val continueReading = state.continueReading
            if (content != null && continueReading != null) {
                QuranTabBody(state, content, continueReading, todayItems, actions)
            }
        }
    }
}

@Composable
private fun QuranTabTopBar(actions: QuranTabActions) {
    val onBar = Awn.field.onBar
    AwnTopBar(
        title = stringResource(R.string.tab_quran),
        leading = {
            Image(
                painter = painterResource(R.drawable.awn_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(AwnSpacing.extraLarge).clip(AwnShapes.wordBlock),
            )
        },
    ) {
        AwnIconButton(
            icon = UiR.drawable.ic_ui_search,
            contentDescription = stringResource(R.string.index_search),
            onClick = actions.onSearch,
            tint = onBar,
        )
        AwnIconButton(
            icon = R.drawable.ic_app_bookmark,
            contentDescription = stringResource(R.string.index_bookmarks),
            onClick = actions.onBookmarks,
            tint = onBar,
        )
        AwnIconButton(
            icon = R.drawable.ic_app_settings,
            contentDescription = stringResource(R.string.index_settings),
            onClick = actions.onSettings,
            tint = onBar,
        )
    }
}

@Composable
private fun QuranTabBody(
    state: IndexState,
    content: IndexContent,
    continueReading: ContinueReading,
    todayItems: List<TodayItem>,
    actions: QuranTabActions,
) {
    var modeOrdinal by rememberSaveable { mutableIntStateOf(IndexMode.Surahs.ordinal) }
    // A saved ordinal from a build that had a third list falls back to Surahs.
    val mode = IndexMode.entries.getOrElse(modeOrdinal) { IndexMode.Surahs }
    var isGoToPageOpen by remember { mutableStateOf(false) }
    val rows =
        when (mode) {
            IndexMode.Surahs -> content.surahRows
            IndexMode.Juz -> content.juzRows
        }
    val language = LearnLanguage.of(LocalConfiguration.current.locales[0])
    val snippet = rememberDailySnippetCard(state.snippets, content.chapters, language)
    val meanings = rememberSurahMeanings()
    val listState = rememberLazyListState()
    // The rows follow the items above them, so a juz number maps to a list position through this.
    val itemsBeforeRows =
        1 + (if (todayItems.isNotEmpty()) 1 else 0) + (if (snippet != null) 1 else 0) + 1
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            topItems(continueReading, todayItems, snippet, actions)
            item(key = "list-header") {
                ListControls(
                    mode = mode,
                    onModeChange = { modeOrdinal = it.ordinal },
                    onGoToPage = { isGoToPageOpen = true },
                )
            }
            items(rows.size, key = { rowKey(rows[it]) }) { index ->
                IndexRowItem(
                    row = rows[index],
                    meanings = meanings,
                    showMeaning = state.showSurahNameMeaning,
                    lastSurah = if (mode == IndexMode.Surahs) state.lastSurah else null,
                    onOpen = { page -> actions.onOpen(page, null) },
                )
            }
        }
        IndexRailOverlay(rows, listState, itemsBeforeRows)
    }
    if (isGoToPageOpen) {
        GoToPageDialog(
            pageCount = content.pageCount,
            onGo = { page ->
                isGoToPageOpen = false
                actions.onOpen(page, null)
            },
            onDismiss = { isGoToPageOpen = false },
        )
    }
}

/** The items above the list: the continue card, Today, and the day's snippet. */
private fun LazyListScope.topItems(
    continueReading: ContinueReading,
    todayItems: List<TodayItem>,
    snippet: DailySnippetCard?,
    actions: QuranTabActions,
) {
    item(key = "continue") {
        ContinueCard(
            reading = continueReading,
            onClick = { actions.onOpen(continueReading.page, null) },
            modifier =
                Modifier.padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.screenMargin,
                    top = AwnSpacing.medium,
                ),
        )
    }
    if (todayItems.isNotEmpty()) {
        item(key = "today") {
            TodaySection(todayItems, Modifier.padding(horizontal = AwnSpacing.screenMargin))
        }
    }
    if (snippet != null) {
        item(key = "snippet") {
            DailySnippet(snippet, Modifier.padding(horizontal = AwnSpacing.screenMargin))
        }
    }
}

/**
 * The juz rail over the list. While a finger is on it the rail keeps the top it had at touch down,
 * because the jump scrolls the controls row away and the rail would otherwise slide under the
 * finger.
 */
@Composable
private fun BoxScope.IndexRailOverlay(
    rows: List<IndexRow>,
    listState: LazyListState,
    itemsBeforeRows: Int,
) {
    val scope = rememberCoroutineScope()
    val currentJuz by
        remember(rows) {
            derivedStateOf {
                JuzRail.currentJuz(rows, listState.firstVisibleItemIndex - itemsBeforeRows) ?: 1
            }
        }
    var frozenTopPx by remember { mutableStateOf<Int?>(null) }
    val naturalTopPx by remember { derivedStateOf { railTop(listState, itemsBeforeRows - 1) } }
    val top = frozenTopPx ?: naturalTopPx ?: return
    JuzRailView(
        currentJuz = currentJuz,
        onDragStateChange = { isDragging -> frozenTopPx = if (isDragging) top else null },
        onJump = { juz ->
            scope.launch { listState.scrollToItem(itemsBeforeRows + rowIndexOfJuz(rows, juz)) }
        },
        modifier =
            Modifier.align(Alignment.TopEnd).layout { measurable, constraints ->
                val topPx = frozenTopPx ?: railTop(listState, itemsBeforeRows - 1) ?: 0
                val height = (constraints.maxHeight - topPx).coerceAtLeast(0)
                val placeable =
                    measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, constraints.maxHeight) {
                    placeable.place(IntOffset(0, topPx))
                }
            },
    )
}

/**
 * Where the rail starts, in pixels from the top of the list: under the controls row while it is on
 * screen, at the top once it has scrolled past, and null (no rail) while it is still below the
 * screen.
 */
private fun railTop(listState: LazyListState, controlsItemIndex: Int): Int? {
    val controls =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == controlsItemIndex }
    return when {
        controls != null -> (controls.offset + controls.size).coerceAtLeast(0)
        listState.firstVisibleItemIndex > controlsItemIndex -> 0
        else -> null
    }
}

private fun rowKey(row: IndexRow): String =
    when (row) {
        is JuzHeading -> "heading-${row.juz}"
        is SurahRow -> "surah-${row.chapter.number}"
        is JuzStartRow -> "juz-${row.juz}"
    }

@Composable
private fun ListControls(
    mode: IndexMode,
    onModeChange: (IndexMode) -> Unit,
    onGoToPage: () -> Unit,
) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.screenMargin,
                    top = AwnSpacing.medium,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier.heightIn(min = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = onGoToPage),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_go_to_page),
                contentDescription = null,
                tint = field.buttonFill,
                modifier = Modifier.size(AwnSpacing.smallIconSize),
            )
            Text(
                text = stringResource(R.string.go_to_page),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
            )
        }
        AwnChoiceRow(
            label = stringResource(R.string.index_show),
            options = IndexMode.entries,
            selected = mode,
            optionLabel = { stringResource(it.labelResource) },
            onSelect = onModeChange,
            isCompact = true,
        )
    }
}
