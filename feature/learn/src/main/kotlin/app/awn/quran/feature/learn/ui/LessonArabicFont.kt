package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import app.awn.quran.feature.learn.R

/** The Arabic font of the new lesson parts: quiz tiles, letter cards and reveal grids. */
internal val LessonArabicFamily = FontFamily(Font(R.font.scheherazade_new))

/** True for lesson markup that is one `[[...]]` fragment, such as `[[ب]]`. */
internal fun isFragment(markup: String): Boolean = markup.startsWith("[[") && markup.endsWith("]]")

/** The Arabic inside one `[[...]]` fragment: `[[ب]]` gives `ب`. */
internal fun fragmentLetters(markup: String): String = markup.removePrefix("[[").removeSuffix("]]")

/** Arabic letters in the lesson font, centred. */
@Composable
internal fun ArabicText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = LessonArabicFamily,
        fontSize = size,
        color = color,
        textAlign = TextAlign.Center,
    )
}
