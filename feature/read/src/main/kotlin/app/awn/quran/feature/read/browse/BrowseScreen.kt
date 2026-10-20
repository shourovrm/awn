@file:Suppress("TooManyFunctions") // One small composable per list keeps each readable.

package app.awn.quran.feature.read.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.ToolbarAction
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.page.JuzTable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXPORT_FILE_NAME = "awn-bookmarks.json"
private const val JSON_MIME_TYPE = "application/json"
private const val FIELD_BLOCK_ALPHA = 0.1f

private enum class BrowseTab(val labelRes: Int) {
    Surahs(R.string.tab_surahs),
    Juz(R.string.tab_juz),
    Page(R.string.tab_page),
    Bookmarks(R.string.tab_bookmarks),
    Saved(R.string.tab_saved),
    Recent(R.string.tab_recent),
}

/**
 * The lists that lead into the text: surahs, juz, go to page, bookmarks, saved ayahs and recent
 * pages. [onOpenPage] receives the page to read; [onClose] returns to the reading view.
 */
@Composable
fun BrowseScreen(
    dependencies: ReadDependencies,
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel =
        viewModel(factory = viewModelFactory { initializer { BrowseViewModel(dependencies) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(BrowseTab.Surahs.ordinal) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(horizontal = AwnSpacing.screenMargin)) {
        ToolbarAction(label = stringResource(R.string.action_close), onClick = onClose)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            BrowseTab.entries.forEach { tab ->
                TabLabel(
                    label = stringResource(tab.labelRes),
                    isSelected = tab.ordinal == selectedTab,
                    onClick = { selectedTab = tab.ordinal },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (BrowseTab.entries[selectedTab]) {
                BrowseTab.Surahs -> SurahList(state.surahs, onOpenPage)
                BrowseTab.Juz -> JuzList(onOpenPage)
                BrowseTab.Page -> GoToPage(viewModel, onOpenPage)
                BrowseTab.Bookmarks -> BookmarkList(state, viewModel, onOpenPage)
                BrowseTab.Saved ->
                    SavedList(state, viewModel) { ayah ->
                        scope.launch { onOpenPage(viewModel.pageOf(ayah)) }
                    }
                BrowseTab.Recent -> RecentList(state, onOpenPage)
            }
        }
    }
}

@Composable
private fun TabLabel(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = if (isSelected) AwnTypography.bodyStrong else AwnTypography.body,
        color = if (isSelected) Awn.field.buttonFill else Awn.field.content,
        maxLines = 1,
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
    )
}

/** A 48 dp row: [primary] on the leading side, [secondary] on the trailing side in the accent. */
@Composable
private fun ListRow(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = primary,
            style = AwnTypography.body,
            modifier = Modifier.weight(1f).padding(end = AwnSpacing.small),
        )
        Text(text = secondary, style = AwnTypography.body, color = Awn.field.buttonFill)
    }
}

@Composable
private fun SurahList(rows: List<SurahRow>, onOpenPage: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.chapter.number }) { row ->
            ListRow(
                primary = "${row.chapter.number}  ${row.chapter.localName()}",
                secondary =
                    stringResource(R.string.surah_row_detail, row.chapter.versesCount, row.page),
                onClick = { onOpenPage(row.page) },
            )
        }
    }
}

@Composable
private fun JuzList(onOpenPage: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items((1..JuzTable.JUZ_COUNT).toList()) { juz ->
            val page = JuzTable.startPageOf(juz)
            ListRow(
                primary = stringResource(R.string.juz_row, juz),
                secondary = stringResource(R.string.page_row, page),
                onClick = { onOpenPage(page) },
            )
        }
    }
}

@Composable
private fun GoToPage(viewModel: BrowseViewModel, onOpenPage: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val pageCount = viewModel.state.collectAsStateWithLifecycle().value.pageCount
    val page = viewModel.parsePage(text)
    Column(Modifier.padding(top = AwnSpacing.medium)) {
        Text(
            text = stringResource(R.string.go_to_page_range, 1, pageCount),
            style = AwnTypography.body,
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = AwnTypography.lead.copy(color = Awn.field.content),
            cursorBrush = SolidColor(Awn.field.content),
            modifier =
                Modifier.padding(vertical = AwnSpacing.medium)
                    .fillMaxWidth()
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clip(AwnShapes.control)
                    .background(Awn.field.content.copy(alpha = FIELD_BLOCK_ALPHA))
                    .padding(AwnSpacing.small),
        )
        AwnButton(
            label = stringResource(R.string.go_to_page_action),
            onClick = { page?.let(onOpenPage) },
            enabled = page != null,
        )
    }
}

@Composable
private fun BookmarkList(
    state: BrowseUiState,
    viewModel: BrowseViewModel,
    onOpenPage: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TransferActions(state.transferStatus, viewModel)
        if (state.bookmarks.isEmpty()) {
            Text(text = stringResource(R.string.bookmarks_empty), style = AwnTypography.body)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.bookmarks, key = { it.page }) { bookmark ->
                RemovableRow(
                    primary = stringResource(R.string.page_row, bookmark.page),
                    onOpen = { onOpenPage(bookmark.page) },
                    onRemove = { viewModel.toggleBookmark(bookmark.page) },
                )
            }
        }
    }
}

@Composable
private fun SavedList(
    state: BrowseUiState,
    viewModel: BrowseViewModel,
    onOpenAyah: (AyahRef) -> Unit,
) {
    if (state.savedAyahs.isEmpty()) {
        Text(text = stringResource(R.string.saved_empty), style = AwnTypography.body)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.savedAyahs, key = { it.ayah.toString() }) { saved ->
            val surahName =
                state.chapters.firstOrNull { it.number == saved.ayah.surah }?.localName().orEmpty()
            val note = saved.note?.let { "  $it" }.orEmpty()
            RemovableRow(
                primary =
                    stringResource(R.string.ayah_reference, surahName, saved.ayah.ayah) + note,
                onOpen = { onOpenAyah(saved.ayah) },
                onRemove = { viewModel.removeSavedAyah(saved.ayah) },
            )
        }
    }
}

@Composable
private fun RecentList(state: BrowseUiState, onOpenPage: (Int) -> Unit) {
    if (state.recentPages.isEmpty()) {
        Text(text = stringResource(R.string.recent_empty), style = AwnTypography.body)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.recentPages, key = { it.page }) { recent ->
            ListRow(
                primary = stringResource(R.string.page_row, recent.page),
                secondary = "",
                onClick = { onOpenPage(recent.page) },
            )
        }
    }
}

@Composable
private fun RemovableRow(primary: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = primary,
            style = AwnTypography.body,
            modifier =
                Modifier.weight(1f)
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clickable(onClick = onOpen)
                    .padding(end = AwnSpacing.small),
        )
        Text(
            text = stringResource(R.string.action_remove),
            style = AwnTypography.bodyStrong,
            color = Awn.field.buttonFill,
            modifier =
                Modifier.heightIn(min = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .padding(AwnSpacing.small),
        )
    }
}

/** Export and import through the system file picker; the picker returns the file to use. */
@Composable
private fun TransferActions(status: TransferStatus?, viewModel: BrowseViewModel) {
    val resolver = LocalContext.current.contentResolver
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) {
            uri ->
            if (uri != null) {
                viewModel.export { json ->
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            ?: throw IOException("No output stream for $uri")
                    }
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.import {
                    withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use { String(it.readBytes()) }
                            ?: throw IOException("No input stream for $uri")
                    }
                }
            }
        }
    Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        ToolbarAction(
            label = stringResource(R.string.action_export),
            onClick = { exportLauncher.launch(EXPORT_FILE_NAME) },
        )
        ToolbarAction(
            label = stringResource(R.string.action_import),
            onClick = { importLauncher.launch(arrayOf(JSON_MIME_TYPE)) },
        )
    }
    status?.let {
        Text(
            text =
                stringResource(
                    when (it) {
                        TransferStatus.Exported -> R.string.transfer_exported
                        TransferStatus.Imported -> R.string.transfer_imported
                        TransferStatus.ExportFailed -> R.string.transfer_export_failed
                        TransferStatus.ImportFailed -> R.string.transfer_import_failed
                    },
                ),
            style = AwnTypography.small,
        )
    }
}
