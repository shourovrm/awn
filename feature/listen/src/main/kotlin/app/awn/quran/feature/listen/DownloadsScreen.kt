package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.DownloadState

private const val TRACK_ALPHA = 0.3f
private const val BANGLA = "bn"

/** Every surah with its download progress for the selected reciter and a download action. */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, modifier: Modifier = Modifier) {
    val chapters by viewModel.chapters.collectAsState()
    val message by viewModel.message.collectAsState()
    AwnTheme(field = ListenField) {
        Column(modifier.fillMaxSize().padding(horizontal = AwnSpacing.screenMargin)) {
            Text(
                text = stringResource(R.string.downloads_title),
                style = AwnTypography.title,
                modifier = Modifier.padding(vertical = AwnSpacing.medium),
            )
            messageText(message)?.let { text ->
                TextAction(text, viewModel::dismissMessage)
            }
            LazyColumn {
                items(chapters, key = { it.number }) { chapter ->
                    val state by
                        viewModel.downloadState(chapter.number).collectAsState(initial = null)
                    SurahDownloadRow(chapter, state) { viewModel.download(chapter.number) }
                }
            }
        }
    }
}

@Composable
private fun messageText(message: DownloadMessage): String? =
    when (message) {
        DownloadMessage.None -> null
        DownloadMessage.WifiRequired -> stringResource(R.string.downloads_wifi_needed)
        DownloadMessage.Failed -> stringResource(R.string.downloads_failed)
    }

@Composable
private fun SurahDownloadRow(chapter: Chapter, state: DownloadState?, onDownload: () -> Unit) {
    val context = LocalContext.current
    val rowTitle = "${chapter.number}  ${chapterName(chapter)}"
    val isDownloading = state != null && !state.complete && state.downloadedBytes > 0L
    Column(Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rowTitle,
                style = AwnTypography.body,
                modifier = Modifier.weight(1f),
            )
            when {
                state?.complete == true ->
                    Text(
                        text = stringResource(R.string.downloads_downloaded),
                        style = AwnTypography.small,
                    )
                else -> TextAction(stringResource(R.string.downloads_download), onDownload)
            }
        }
        if (isDownloading && state != null) {
            ProgressTrack(state.downloadedBytes, state.totalBytes)
            Text(
                text =
                    stringResource(
                        R.string.downloads_size_progress,
                        Formatter.formatShortFileSize(context, state.downloadedBytes),
                        Formatter.formatShortFileSize(context, state.totalBytes ?: 0L),
                    ),
                style = AwnTypography.small,
            )
        }
    }
}

@Composable
private fun ProgressTrack(downloadedBytes: Long, totalBytes: Long?) {
    val fraction =
        if (totalBytes != null && totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
    Box(
        Modifier.padding(vertical = AwnSpacing.small)
            .fillMaxWidth()
            .height(AwnSpacing.sliderTrackHeight)
            .clip(AwnShapes.control)
            .background(Awn.field.content.copy(alpha = TRACK_ALPHA)),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Awn.field.buttonFill),
        )
    }
}

private fun chapterName(chapter: Chapter): String =
    if (Locale.current.language == BANGLA) chapter.nameBangla ?: chapter.nameSimple
    else chapter.nameSimple
