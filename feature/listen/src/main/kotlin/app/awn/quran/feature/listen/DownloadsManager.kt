// The manager is one screen made of small composables: the view choice, rows and dialogs.
@file:Suppress("TooManyFunctions")

package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnConfirmDialog
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.components.AwnPackRow
import app.awn.quran.core.ui.components.PackRowButton
import app.awn.quran.core.ui.components.PackTick
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.player.DownloadProblem
import app.awn.quran.player.JuzTable

/** The ways the manager groups the audio of one reciter. */
private enum class ManagerView(@StringRes val label: Int) {
    Surahs(R.string.manager_surahs),
    Juz(R.string.manager_juz),
    Range(R.string.manager_range),
    Everything(R.string.manager_all),
}

@Composable
internal fun ColumnScope.ManagerReciterList(
    feature: AudioFeature,
    onOpenReciter: (String) -> Unit,
) {
    val viewModel: DownloadsManagerViewModel = viewModel(factory = feature.viewModelFactory)
    val reciters by viewModel.reciterRows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRemoval by remember { mutableStateOf<ManagerReciter?>(null) }
    for (reciter in reciters) {
        SettingsLinkRow(
            label = reciter.name,
            value = Formatter.formatShortFileSize(context, reciter.usedBytes),
            onClick = { onOpenReciter(reciter.id) },
            trailing =
                if (reciter.usedBytes > 0L) {
                    {
                        AwnIconButton(
                            icon = R.drawable.ic_listen_delete,
                            contentDescription =
                                stringResource(R.string.manager_remove_reciter, reciter.name),
                            onClick = { pendingRemoval = reciter },
                            tint = Awn.field.buttonFill,
                        )
                    }
                } else {
                    null
                },
        )
    }
    pendingRemoval?.let { reciter ->
        AwnConfirmDialog(
            title = stringResource(R.string.manager_remove_title, reciter.name),
            message =
                stringResource(
                    R.string.manager_remove_frees,
                    Formatter.formatShortFileSize(context, reciter.usedBytes),
                ),
            confirmLabel = stringResource(R.string.manager_delete),
            dismissLabel = stringResource(R.string.audio_cancel),
            onConfirm = {
                pendingRemoval = null
                viewModel.delete(reciter.id, ItemScope.Everything)
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
internal fun ColumnScope.ManagerReciterDetail(feature: AudioFeature, reciterId: String) {
    val viewModel: DownloadsManagerViewModel = viewModel(factory = feature.viewModelFactory)
    var view by rememberSaveable { mutableStateOf(ManagerView.Surahs) }
    AwnChoiceRow(
        label = stringResource(R.string.manager_view),
        options = ManagerView.entries,
        selected = view,
        optionLabel = { stringResource(it.label) },
        onSelect = { view = it },
    )
    ManagerViewContent(viewModel, reciterId, view)
}

@Composable
private fun ColumnScope.ManagerViewContent(
    viewModel: DownloadsManagerViewModel,
    reciterId: String,
    view: ManagerView,
) {
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    when (view) {
        ManagerView.Surahs ->
            LazyColumn(Modifier.weight(1f)) {
                items(chapters, key = { it.number }) { chapter ->
                    ItemRow(
                        viewModel,
                        reciterId,
                        ItemScope.Surah(chapter.number),
                        "${chapter.number}  ${chapter.nameSimple}",
                    )
                }
            }
        ManagerView.Juz ->
            LazyColumn(Modifier.weight(1f)) {
                items((1..JuzTable.COUNT).toList(), key = { it }) { juz ->
                    ItemRow(
                        viewModel,
                        reciterId,
                        ItemScope.Juz(juz),
                        stringResource(R.string.manager_juz_item, juz),
                    )
                }
            }
        ManagerView.Range ->
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RangeTab(viewModel, reciterId, chapters, range)
            }
        ManagerView.Everything ->
            ItemRow(
                viewModel,
                reciterId,
                ItemScope.Everything,
                stringResource(R.string.manager_everything),
            )
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
    val status = item.status
    val progress = item.progress
    val failure = failureText(item.failure, item.problem)
    AwnPackRow(
        title = title,
        detail = failure ?: itemDetail(item),
        tick = PackTick.Absent,
        onTickChange = {},
        isDetailError = failure != null,
        progress = progress?.let { progressFraction(it.doneBytes, it.totalBytes) },
        download =
            if (progress == null && (status == null || !status.complete)) {
                PackRowButton(stringResource(R.string.manager_download)) {
                    viewModel.download(reciterId, scope)
                }
            } else {
                null
            },
        cancel =
            if (progress != null) {
                PackRowButton(stringResource(R.string.audio_cancel)) {
                    viewModel.cancel(reciterId, scope)
                }
            } else {
                null
            },
        remove =
            if (progress == null && status != null && status.presentAyahs > 0) {
                PackRowButton(stringResource(R.string.manager_delete)) { confirmingDelete = true }
            } else {
                null
            },
    )
    if (confirmingDelete) {
        AwnConfirmDialog(
            title = stringResource(R.string.manager_remove_title, title),
            message =
                stringResource(
                    R.string.manager_remove_frees,
                    Formatter.formatShortFileSize(LocalContext.current, status?.presentBytes ?: 0L),
                ),
            confirmLabel = stringResource(R.string.manager_delete),
            dismissLabel = stringResource(R.string.audio_cancel),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(reciterId, scope)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Download progress as 0 to 1; 0 while the total size is not known yet. */
private fun progressFraction(doneBytes: Long, totalBytes: Long?): Float =
    if (totalBytes != null && totalBytes > 0L) doneBytes.toFloat() / totalBytes else 0f

/** The line under an item: running progress, what is on the phone, or the size to download. */
@Composable
private fun itemDetail(item: ItemState): String {
    val context = LocalContext.current
    val status = item.status
    val progress = item.progress
    val total = progress?.totalBytes
    val estimate = item.estimateBytes
    return when {
        progress != null && total != null -> {
            val (done, all) = progressSizes(context, progress.doneBytes, total)
            stringResource(R.string.downloads_size_progress, done, all)
        }
        status != null && status.complete ->
            stringResource(
                R.string.manager_size_downloaded,
                Formatter.formatShortFileSize(context, status.presentBytes),
            )
        status != null && status.presentAyahs > 0 ->
            stringResource(R.string.manager_part_downloaded, status.presentAyahs, status.totalAyahs)
        estimate != null ->
            stringResource(
                R.string.manager_size_estimate,
                Formatter.formatShortFileSize(context, estimate),
            )
        else -> ""
    }
}

@Composable
private fun failureText(failure: ItemFailure, problem: DownloadProblem?): String? =
    when {
        failure == ItemFailure.None -> null
        problem != null -> problemText(problem)
        failure == ItemFailure.WifiRequired -> stringResource(R.string.downloads_wifi_needed)
        else -> stringResource(R.string.manager_failed)
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
