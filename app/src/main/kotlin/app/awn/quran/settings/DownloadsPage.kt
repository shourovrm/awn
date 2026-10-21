package app.awn.quran.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.R
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.data.models.SpeechModelStore
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsInfoRow
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.feature.read.settings.CatalogueViewModel
import app.awn.quran.feature.read.settings.PackListKind
import app.awn.quran.feature.read.settings.SpeechModelRow

/**
 * What the phone holds: the space all downloads use, then one row per kind of download that opens
 * its list, the reciter audio and the download that recite needs. [audioBytes] is the space the
 * audio of all reciters uses; [onSpeechModelRemoved] runs after the user removes [speechModel].
 */
@Composable
fun DownloadsPage(
    viewModel: CatalogueViewModel,
    audioBytes: Long,
    onOpenList: (PackListKind) -> Unit,
    onOpenReciters: () -> Unit,
    speechModel: SpeechModelStore,
    onSpeechModelRemoved: () -> Unit,
    onBack: () -> Unit,
    targetRow: String? = null,
) {
    val catalogue by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(
        title = stringResource(R.string.settings_downloads),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        SettingsInfoRow(
            label = stringResource(R.string.downloads_space_used),
            value = Formatter.formatShortFileSize(context, catalogue.spaceUsedBytes + audioBytes),
            rowKey = SettingRowKeys.DOWNLOADS_SPACE_USED,
        )
        SettingsGroupHeading(stringResource(R.string.downloads_group_on_phone))
        PackListLinkRow(
            R.string.downloads_page_types,
            PackListKind.PageTypes,
            SettingRowKeys.DOWNLOADS_PAGE_TYPES,
            catalogue,
            onOpenList,
        )
        PackListLinkRow(
            R.string.downloads_translations,
            PackListKind.Translations,
            SettingRowKeys.DOWNLOADS_TRANSLATIONS,
            catalogue,
            onOpenList,
        )
        PackListLinkRow(
            R.string.downloads_transliteration,
            PackListKind.Transliteration,
            SettingRowKeys.DOWNLOADS_TRANSLITERATION,
            catalogue,
            onOpenList,
        )
        PackListLinkRow(
            R.string.downloads_word_by_word,
            PackListKind.WordByWord,
            SettingRowKeys.DOWNLOADS_WORD_BY_WORD,
            catalogue,
            onOpenList,
        )
        PackListLinkRow(
            R.string.downloads_word_audio,
            PackListKind.WordAudio,
            SettingRowKeys.DOWNLOADS_WORD_AUDIO,
            catalogue,
            onOpenList,
        )
        SettingsLinkRow(
            label = stringResource(R.string.downloads_reciters),
            value = Formatter.formatShortFileSize(context, audioBytes),
            onClick = onOpenReciters,
            rowKey = SettingRowKeys.DOWNLOADS_RECITERS,
        )
        SpeechModelRow(store = speechModel, onRemoved = onSpeechModelRemoved)
    }
}

/** A row that opens the list of [kind], saying how many of its entries are on the phone. */
@Composable
private fun PackListLinkRow(
    label: Int,
    kind: PackListKind,
    rowKey: String,
    catalogue: CatalogueState,
    onOpenList: (PackListKind) -> Unit,
) {
    val rows = catalogue.rowsIn(kind.group)
    SettingsLinkRow(
        label = stringResource(label),
        value =
            stringResource(
                R.string.downloads_on_phone_count,
                rows.count { it.item.isInstalled },
                rows.size,
            ),
        onClick = { onOpenList(kind) },
        rowKey = rowKey,
    )
}
