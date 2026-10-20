// The manager is one screen made of small composables: tabs, rows and a delete dialog.
@file:Suppress("TooManyFunctions")

package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.JuzTable

private const val TAB_SURAH = 0
private const val TAB_JUZ = 1
private const val TAB_RANGE = 2
private const val TAB_ALL = 3

@Composable
internal fun ManagerReciterList(feature: AudioFeature, onOpenReciter: (String) -> Unit) {
    val viewModel: DownloadsManagerViewModel = viewModel(factory = feature.viewModelFactory)
    val reciters by viewModel.reciterRows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
        for (reciter in reciters) {
            SettingRow(
                label = reciter.name,
                value = Formatter.formatShortFileSize(context, reciter.usedBytes),
                onClick = { onOpenReciter(reciter.id) },
            )
        }
    }
}

@Composable
internal fun ManagerReciterDetail(feature: AudioFeature, reciterId: String) {
    val viewModel: DownloadsManagerViewModel = viewModel(factory = feature.viewModelFactory)
    var tab by rememberSaveable { mutableIntStateOf(TAB_SURAH) }
    Column(Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
        ManagerTabs(selected = tab, onSelect = { tab = it })
        ManagerTabContent(viewModel, reciterId, tab)
    }
}

@Composable
private fun ManagerTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs =
        listOf(
            TAB_SURAH to R.string.manager_surahs,
            TAB_JUZ to R.string.manager_juz,
            TAB_RANGE to R.string.manager_range,
            TAB_ALL to R.string.manager_all,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        for ((tabId, labelRes) in tabs) {
            Chip(
                label = stringResource(labelRes),
                isSelected = selected == tabId,
                onClick = { onSelect(tabId) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ManagerTabContent(viewModel: DownloadsManagerViewModel, reciterId: String, tab: Int) {
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    Column {
        when (tab) {
            TAB_SURAH ->
                LazyColumn {
                    items(chapters, key = { it.number }) { chapter ->
                        ItemRow(
                            viewModel,
                            reciterId,
                            ItemScope.Surah(chapter.number),
                            "${chapter.number}  ${chapter.nameSimple}",
                        )
                    }
                }
            TAB_JUZ ->
                LazyColumn {
                    items((1..JuzTable.COUNT).toList(), key = { it }) { juz ->
                        ItemRow(
                            viewModel,
                            reciterId,
                            ItemScope.Juz(juz),
                            stringResource(R.string.manager_juz_item, juz),
                        )
                    }
                }
            TAB_RANGE -> RangeTab(viewModel, reciterId, chapters, range)
            else ->
                ItemRow(
                    viewModel,
                    reciterId,
                    ItemScope.Everything,
                    stringResource(R.string.manager_everything),
                )
        }
    }
}

/** One downloadable item: title, size before downloading, and download, cancel or delete. */
@Composable
private fun ItemRow(
    viewModel: DownloadsManagerViewModel,
    reciterId: String,
    scope: ItemScope,
    title: String,
) {
    LaunchedEffect(reciterId, scope) { viewModel.load(reciterId, scope) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val item = items[DownloadsManagerViewModel.itemKey(reciterId, scope)] ?: ItemState()
    var confirmingDelete by rememberSaveable(scope.toString()) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemTexts(title, item, Modifier.weight(1f))
            ItemButtons(
                item = item,
                onDownload = { viewModel.download(reciterId, scope) },
                onCancel = { viewModel.cancel(reciterId, scope) },
                onDelete = { confirmingDelete = true },
            )
        }
        ItemProgress(item)
        failureText(item.failure)?.let { Text(text = it, style = AwnTypography.small) }
    }
    if (confirmingDelete) {
        DeleteConfirmation(
            title = title,
            bytes = item.status?.presentBytes ?: 0L,
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(reciterId, scope)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun ItemTexts(title: String, item: ItemState, modifier: Modifier) {
    val context = LocalContext.current
    val status = item.status
    val estimate = item.estimateBytes
    Column(modifier) {
        Text(text = title, style = AwnTypography.body)
        if (status != null && status.complete) {
            val size = Formatter.formatShortFileSize(context, status.presentBytes)
            Text(
                stringResource(R.string.manager_size_downloaded, size),
                style = AwnTypography.small,
            )
        } else if (estimate != null) {
            val size = Formatter.formatShortFileSize(context, estimate)
            Text(stringResource(R.string.manager_size_estimate, size), style = AwnTypography.small)
        }
        if (status != null && status.presentAyahs > 0 && !status.complete) {
            val part =
                stringResource(
                    R.string.manager_part_downloaded,
                    status.presentAyahs,
                    status.totalAyahs,
                )
            Text(text = part, style = AwnTypography.small)
        }
    }
}

@Composable
private fun ItemButtons(
    item: ItemState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = item.status
    if (item.progress != null) {
        IconAction(R.drawable.ic_listen_close, stringResource(R.string.audio_cancel), onCancel)
        return
    }
    if (status == null || !status.complete) {
        IconAction(
            R.drawable.ic_listen_download,
            stringResource(R.string.manager_download),
            onDownload,
        )
    }
    if (status != null && status.presentAyahs > 0) {
        IconAction(R.drawable.ic_listen_delete, stringResource(R.string.manager_delete), onDelete)
    }
}

@Composable
private fun ItemProgress(item: ItemState) {
    val progress = item.progress ?: return
    val total = progress.totalBytes
    ProgressTrack(
        fraction = total?.takeIf { it > 0L }?.let { progress.doneBytes.toFloat() / it },
        color = Awn.field.content,
    )
    if (total != null) {
        val (done, all) = progressSizes(LocalContext.current, progress.doneBytes, total)
        Text(
            text = stringResource(R.string.downloads_size_progress, done, all),
            style = AwnTypography.small,
        )
    }
}

@Composable
private fun DeleteConfirmation(
    title: String,
    bytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, bytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manager_delete_title)) },
        text = { Text(stringResource(R.string.manager_delete_message, title, size)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.manager_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audio_cancel)) }
        },
    )
}

@Composable
private fun failureText(failure: ItemFailure): String? =
    when (failure) {
        ItemFailure.None -> null
        ItemFailure.WifiRequired -> stringResource(R.string.downloads_wifi_needed)
        ItemFailure.Failed -> stringResource(R.string.manager_failed)
    }

@Composable
private fun RangeTab(
    viewModel: DownloadsManagerViewModel,
    reciterId: String,
    chapters: List<Chapter>,
    range: RangeSelection,
) {
    RangeEnd(
        stringResource(R.string.panel_from),
        range.from,
        chapters,
        viewModel::setRangeFromSurah,
        viewModel::setRangeFromAyah,
    )
    RangeEnd(
        stringResource(R.string.panel_to),
        range.to,
        chapters,
        viewModel::setRangeToSurah,
        viewModel::setRangeToAyah,
    )
    ItemRow(
        viewModel,
        reciterId,
        ItemScope.Range(range.from, range.to),
        stringResource(
            R.string.manager_range_item,
            range.from.toString(),
            range.to.toString(),
        ),
    )
}
