package app.awn.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.quranfont.QuranAyahText
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import java.text.NumberFormat

/**
 * The one primary action of the Quran tab: a slim row that opens the Quran screen at the page read
 * last (page 1 before anything was read). The surah and page are on the left; the first line of
 * that page in Arabic, cut between words, is on the right.
 */
@Composable
internal fun ContinueCard(
    reading: ContinueReading,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val title =
        stringResource(if (reading.isStart) R.string.continue_start else R.string.continue_reading)
    val detail =
        stringResource(
            R.string.continue_detail,
            reading.chapter.nameSimple,
            NumberFormat.getIntegerInstance().format(reading.page),
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.buttonFill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = AwnTypography.bodyStrong, color = field.onButton)
            Text(text = detail, style = AwnTypography.small, color = field.onButton)
        }
        reading.firstWord?.let { firstWord ->
            FirstLine(
                firstWord = firstWord,
                color = field.onButton,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The first line of the page in the user's print, from its first word to the end of that ayah. The
 * flow keeps whole words only, so the one line ends at a word.
 */
@Composable
private fun FirstLine(firstWord: WordKey, color: Color, modifier: Modifier = Modifier) {
    QuranAyahText(
        ayah = AyahRef(firstWord.surah, firstWord.ayah),
        modifier = modifier.fillMaxWidth(),
        style = AwnTypography.quranLead,
        color = color,
        wordRange = firstWord.wordPosition..Int.MAX_VALUE,
        showEndMarker = false,
        maxLines = 1,
    )
}
