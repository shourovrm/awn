package app.awn.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

private enum class IndexTab {
    Surahs,
    Juz,
    Bookmarks,
}

private val ARABIC_ROW_STYLE =
    AwnTypography.body.copy(fontFamily = FontFamily.Serif, textDirection = TextDirection.Rtl)

/** The index: surahs grouped by juz, juz quarters, and bookmarks with recent pages. */
@Composable
fun IndexScreen(
    state: IndexState,
    onSortChange: (BookmarkSort) -> Unit,
    actions: IndexActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(IndexTab.Surahs.ordinal) }
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            IndexTopBar(lastPage = state.lastPage, actions = actions)
            IndexTabs(selected = selectedTab, onSelect = { selectedTab = it })
            val content = state.content
            if (content != null) {
                when (IndexTab.entries[selectedTab]) {
                    IndexTab.Surahs -> SurahsTab(state, content, actions.onOpen)
                    IndexTab.Juz -> JuzTab(content, actions.onOpen)
                    IndexTab.Bookmarks -> BookmarksTab(state, onSortChange, actions.onOpen)
                }
            }
        }
    }
}

@Composable
private fun IndexTopBar(lastPage: Int?, actions: IndexActions) {
    AwnTopBar(title = stringResource(R.string.app_name)) {
        val onBar = Awn.field.onBar
        AwnIconButton(
            icon = UiR.drawable.ic_ui_search,
            contentDescription = stringResource(R.string.index_search),
            onClick = actions.onSearch,
            tint = onBar,
        )
        if (lastPage != null) {
            AwnIconButton(
                icon = R.drawable.ic_app_menu_book,
                contentDescription = stringResource(R.string.index_last_page),
                onClick = { actions.onOpen(lastPage, null) },
                tint = onBar,
            )
        }
        AwnIconButton(
            icon = R.drawable.ic_app_school,
            contentDescription = stringResource(R.string.index_learn),
            onClick = actions.onLearn,
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
private fun IndexTabs(selected: Int, onSelect: (Int) -> Unit) {
    val field = Awn.field
    Row(Modifier.fillMaxWidth().background(field.barBackground)) {
        IndexTab.entries.forEach { tab ->
            val isSelected = tab.ordinal == selected
            Column(
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = AwnSpacing.touchTarget)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(tab.ordinal) },
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(tab.labelResource()),
                        style = if (isSelected) AwnTypography.bodyStrong else AwnTypography.body,
                        color = field.onBar,
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(AwnSpacing.tabIndicatorHeight)
                        .background(if (isSelected) field.onBar else field.barBackground),
                )
            }
        }
    }
}

private fun IndexTab.labelResource(): Int =
    when (this) {
        IndexTab.Surahs -> R.string.index_tab_surahs
        IndexTab.Juz -> R.string.index_tab_juz
        IndexTab.Bookmarks -> R.string.index_tab_bookmarks
    }

@Composable
private fun JuzTab(content: IndexContent, onOpen: (page: Int, ayah: AyahRef?) -> Unit) {
    val fractions = stringArrayResource(R.array.hizb_fractions)
    LazyColumn(Modifier.fillMaxSize()) {
        content.juzSections.forEach { section ->
            item(key = "juz-${section.juz}") {
                GroupHeader(
                    title = stringResource(R.string.index_juz_header, section.juz),
                    page = section.startPage,
                )
            }
            items(section.quarters.size, key = { "q-${section.juz}-$it" }) { index ->
                val quarter = section.quarters[index]
                QuarterRow(
                    label =
                        stringResource(
                                R.string.index_hizb_fraction,
                                fractions[quarter.fraction],
                                quarter.hizb,
                            )
                            .trim(),
                    quarter = quarter,
                    onClick = { onOpen(quarter.page, null) },
                )
            }
        }
    }
}

@Composable
private fun QuarterRow(label: String, quarter: QuarterRow, onClick: () -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Text(
            text = label,
            style = AwnTypography.small,
            color = field.buttonFill,
            modifier = Modifier.widthIn(min = AwnSpacing.giant),
        )
        Text(
            text = quarter.firstWords,
            style = ARABIC_ROW_STYLE,
            color = field.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = pageLabel(quarter.page), style = AwnTypography.small, color = field.content)
    }
}
