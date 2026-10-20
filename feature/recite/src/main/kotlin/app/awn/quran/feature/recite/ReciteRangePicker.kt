package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val SURAH_LABEL_GAP = "  "

/** Chooses the surah and the first and last ayah of the range to recite. */
@Composable
fun ReciteRangePicker(
    chapters: List<Chapter>,
    current: ReciteRange,
    onConfirm: (ReciteRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    val ayahCount = chapters.firstOrNull { it.number == draft.surah }?.versesCount ?: 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Awn.field.background)
                .padding(AwnSpacing.screenMargin),
        ) {
            Text(stringResource(R.string.recite_range_title), style = AwnTypography.title)
            Text(stringResource(R.string.recite_range_surah), style = AwnTypography.small)
            LazyColumn(Modifier.weight(1f)) {
                items(chapters, key = { it.number }) { chapter ->
                    val surahLabel =
                        chapter.number.toString() + SURAH_LABEL_GAP + chapter.nameSimple
                    ChoiceCell(
                        label = surahLabel,
                        selected = chapter.number == draft.surah,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { draft = ReciteRange(chapter.number, 1, 1) },
                    )
                }
            }
            AyahChooser(
                label = stringResource(R.string.recite_range_from),
                ayahCount = ayahCount,
                selected = draft.firstAyah,
                onSelect = { draft = draft.withFirstAyah(it) },
            )
            AyahChooser(
                label = stringResource(R.string.recite_range_to),
                ayahCount = ayahCount,
                selected = draft.lastAyah,
                onSelect = { draft = draft.withLastAyah(it) },
            )
            AwnButton(
                label = stringResource(R.string.recite_range_done),
                onClick = { onConfirm(draft) },
                modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
            )
        }
    }
}

@Composable
private fun AyahChooser(label: String, ayahCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = AwnTypography.small, modifier = Modifier.padding(top = AwnSpacing.small))
    LazyRow {
        items((1..ayahCount).toList()) { ayah ->
            ChoiceCell(
                label = ayah.toString(),
                selected = ayah == selected,
                modifier = Modifier.requiredSize(AwnSpacing.touchTarget),
                onClick = { onSelect(ayah) },
            )
        }
    }
}

@Composable
private fun ChoiceCell(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val field = Awn.field
    Box(
        modifier =
            modifier
                .clip(AwnShapes.control)
                .background(if (selected) field.buttonFill else field.background)
                .clickable(onClick = onClick)
                .padding(horizontal = AwnSpacing.small),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = AwnTypography.body,
            color = if (selected) field.onButton else field.content,
        )
    }
}
