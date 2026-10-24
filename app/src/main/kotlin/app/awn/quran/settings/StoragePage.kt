package app.awn.quran.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.R
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.data.models.SpeechModelStore
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsGroupHeading
import app.awn.quran.core.ui.components.SettingsInfoRow
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.searchTarget
import app.awn.quran.feature.read.settings.CatalogueViewModel
import app.awn.quran.feature.read.settings.PackListKind
import app.awn.quran.feature.read.settings.SpeechModelRow

/**
 * What the phone holds: the space all downloads use, then one row per kind of download. The Mushaf
 * row opens the Mushaf chooser ([onOpenMushaf]), the list rows open their list ([onOpenList]),
 * Reciters opens the audio manager directly ([onOpenReciters]), and the last row is the download
 * the recitation checker needs. [audioBytes] is the space the audio of all reciters uses;
 * [onSpeechModelRemoved] runs after the user removes [speechModel].
 */
@Composable
fun StoragePage(
    viewModel: CatalogueViewModel,
    audioBytes: Long,
    onOpenMushaf: () -> Unit,
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
        title = stringResource(R.string.settings_storage),
        onBack = onBack,
        targetRow = targetRow,
    ) {
        SettingsInfoRow(
            label = stringResource(R.string.storage_space_used),
            value = Formatter.formatShortFileSize(context, catalogue.spaceUsedBytes + audioBytes),
            rowKey = SettingRowKeys.STORAGE_SPACE_USED,
        )
        SettingsGroupHeading(stringResource(R.string.storage_group_on_phone))
        StorageLinkRow(
            R.string.storage_mushaf,
            CatalogueGroup.PageType,
            SettingRowKeys.STORAGE_MUSHAF,
            catalogue,
            onOpenMushaf,
        )
        PackListLinkRows(catalogue, onOpenList)
        SettingsLinkRow(
            label = stringResource(R.string.storage_reciters),
            value = Formatter.formatShortFileSize(context, audioBytes),
            onClick = onOpenReciters,
            rowKey = SettingRowKeys.STORAGE_RECITERS,
        )
        SpeechModelRow(
            store = speechModel,
            onRemoved = onSpeechModelRemoved,
            modifier = Modifier.searchTarget(SettingRowKeys.STORAGE_RECITATION_CHECKER),
        )
    }
}

/** A row that opens the page for [group], saying how many of its entries are on the phone. */
@Composable
private fun StorageLinkRow(
    label: Int,
    group: CatalogueGroup,
    rowKey: String,
    catalogue: CatalogueState,
    onClick: () -> Unit,
) {
    val rows = catalogue.rowsIn(group)
    SettingsLinkRow(
        label = stringResource(label),
        value =
            stringResource(
                R.string.storage_on_phone_count,
                rows.count { it.item.isInstalled },
                rows.size,
            ),
        onClick = onClick,
        rowKey = rowKey,
    )
}

/** One row for each kind of download that is a list of entries. */
@Composable
private fun PackListLinkRows(catalogue: CatalogueState, onOpenList: (PackListKind) -> Unit) {
    val listRows =
        listOf(
            Triple(
                R.string.storage_translations,
                PackListKind.Translations,
                SettingRowKeys.STORAGE_TRANSLATIONS,
            ),
            Triple(
                R.string.storage_transliteration,
                PackListKind.Transliteration,
                SettingRowKeys.STORAGE_TRANSLITERATION,
            ),
            Triple(
                R.string.storage_word_by_word,
                PackListKind.WordByWord,
                SettingRowKeys.STORAGE_WORD_BY_WORD,
            ),
            Triple(
                R.string.storage_word_audio,
                PackListKind.WordAudio,
                SettingRowKeys.STORAGE_WORD_AUDIO,
            ),
            Triple(
                R.string.storage_lesson_sounds,
                PackListKind.LessonSounds,
                SettingRowKeys.STORAGE_LESSON_SOUNDS,
            ),
        )
    listRows.forEach { (label, kind, rowKey) ->
        StorageLinkRow(label, kind.group, rowKey, catalogue) { onOpenList(kind) }
    }
}
