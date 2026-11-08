package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R

/** How a quiz tile looks: open, taken up, right or wrong. */
internal enum class TileState {
    Open,
    Picked,
    Right,
    Wrong,
}

/** Tiles for answers hold a word or a letter; [LETTER_COLUMNS] letter tiles fit a row. */
internal const val CHOICE_COLUMNS = 2
internal const val LETTER_COLUMNS = 4

internal val CHOICE_TILE_HEIGHT: Dp = 140.dp
internal val LETTER_TILE_HEIGHT: Dp = 96.dp
internal val CHOICE_ARABIC_SIZE: TextUnit = 60.sp
internal val LETTER_ARABIC_SIZE: TextUnit = 40.sp

/** How long a wrong placement or pair stays in the error colour. */
internal const val FLASH_MILLIS = 600L
private val TILE_BORDER: Dp = 2.dp

/**
 * A quiz tile. A right tile takes the button colours and a tick; a wrong or taken-up tile gets a 2
 * dp border. [onClick] is null when the tile cannot be tapped. [content] gets the text colour.
 */
@Composable
internal fun QuizTile(
    state: TileState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val field = Awn.field
    val isRight = state == TileState.Right
    val border =
        when (state) {
            TileState.Wrong -> BorderStroke(TILE_BORDER, field.wrongWord)
            TileState.Picked -> BorderStroke(TILE_BORDER, field.buttonFill)
            TileState.Open,
            TileState.Right -> null
        }
    Column(
        modifier =
            modifier
                .clip(AwnShapes.control)
                .background(if (isRight) field.buttonFill else field.softFill)
                .then(if (border != null) Modifier.border(border, AwnShapes.control) else Modifier)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier,
                )
                .padding(AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content(if (isRight) field.onButton else field.content)
        if (isRight) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = stringResource(R.string.quiz_right),
                tint = field.onButton,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
        }
    }
}

/** The text of a tile: a letter in the lesson font, or any other text in the body style. */
@Composable
internal fun TileLabel(markup: String, color: Color, arabicSize: TextUnit) {
    if (isFragment(markup)) {
        ArabicText(fragmentLetters(markup), arabicSize, color)
    } else {
        Text(
            text = markup,
            style = AwnTypography.bodyStrong,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/** The tap for a tile that is still open, or null once the question is answered. */
internal fun tapIfOpen(open: Boolean, tap: () -> Unit): (() -> Unit)? = if (open) tap else null
