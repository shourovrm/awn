package app.awn.quran.index

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnChoiceDialog
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.AwnTopBar
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import java.text.NumberFormat

/** Recent pages, then the user's bookmarks, under a top bar with Back. */
@Composable
fun BookmarksScreen(
    state: IndexState,
    onSortChange: (BookmarkSort) -> Unit,
    onBack: () -> Unit,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Column(modifier.fillMaxSize()) {
            AwnTopBar(
                title = stringResource(R.string.index_tab_bookmarks),
                onNavigateBack = onBack,
                backDescription = stringResource(R.string.navigate_back),
            )
            BookmarksList(state, onSortChange, onOpen)
        }
    }
}

@Composable
private fun BookmarksList(
    state: IndexState,
    onSortChange: (BookmarkSort) -> Unit,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    val content = state.content ?: return
    val chapterNames = content.chapters.associate { it.number to it.nameSimple }
    var isSortDialogOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        recentPagesSection(state.recentPages, chapterNames, onOpen)
        item(key = "bookmarks-title") {
            SectionTitle(stringResource(R.string.index_tab_bookmarks)) {
                AwnIconButton(
                    icon = R.drawable.ic_app_sort,
                    contentDescription = stringResource(R.string.index_sort),
                    onClick = { isSortDialogOpen = true },
                )
            }
        }
        if (state.bookmarks.isEmpty()) {
            item(key = "bookmarks-empty") {
                Text(
                    text = stringResource(R.string.index_bookmarks_empty),
                    style = AwnTypography.body,
                    color = Awn.field.content,
                    modifier = Modifier.padding(AwnSpacing.screenMargin),
                )
            }
        }
        bookmarkRows(state.bookmarks, chapterNames, onOpen)
    }
    if (isSortDialogOpen) {
        AwnChoiceDialog(
            title = stringResource(R.string.index_sort),
            options = BookmarkSort.entries,
            selected = state.sort,
            optionLabel = {
                stringResource(
                    if (it == BookmarkSort.Date) {
                        R.string.index_sort_date
                    } else {
                        R.string.index_sort_position
                    },
                )
            },
            onSelect = {
                onSortChange(it)
                isSortDialogOpen = false
            },
            onDismiss = { isSortDialogOpen = false },
        )
    }
}

private fun LazyListScope.recentPagesSection(
    recentPages: List<RecentRow>,
    chapterNames: Map<Int, String>,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    if (recentPages.isEmpty()) return
    item(key = "recent-title") { SectionTitle(stringResource(R.string.index_recent_pages)) }
    items(recentPages.size, key = { "recent-$it" }) { index ->
        val recent = recentPages[index]
        BookmarkRow(
            icon = R.drawable.ic_app_menu_book,
            title = pageLabel(recent.page),
            detail = chapterNames[recent.surah].orEmpty(),
            onClick = { onOpen(recent.page, null) },
        )
    }
}

private fun LazyListScope.bookmarkRows(
    bookmarks: List<BookmarkEntry>,
    chapterNames: Map<Int, String>,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    items(bookmarks.size, key = { "bookmark-$it" }) { index ->
        when (val entry = bookmarks[index]) {
            is BookmarkEntry.PageMark ->
                BookmarkRow(
                    icon = R.drawable.ic_app_bookmark,
                    title = chapterNames[entry.surah].orEmpty(),
                    detail = pageLabel(entry.page),
                    onClick = { onOpen(entry.page, null) },
                )
            is BookmarkEntry.AyahMark ->
                BookmarkRow(
                    icon = R.drawable.ic_app_star,
                    title =
                        stringResource(
                            R.string.index_ayah_reference,
                            chapterNames[entry.ayah.surah].orEmpty(),
                            entry.ayah.surah,
                            entry.ayah.ayah,
                        ),
                    detail = pageLabel(entry.page),
                    onClick = { onOpen(entry.page, entry.ayah) },
                )
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .padding(start = AwnSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AwnTypography.lead,
            color = Awn.field.buttonFill,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun BookmarkRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(
            text = title,
            style = AwnTypography.body,
            color = field.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = AwnSpacing.medium),
        )
        Text(text = detail, style = AwnTypography.small, color = field.content)
    }
}

@Composable
internal fun pageLabel(page: Int): String =
    stringResource(R.string.index_page, NumberFormat.getIntegerInstance().format(page))
