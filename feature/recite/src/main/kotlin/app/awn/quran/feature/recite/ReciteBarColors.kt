package app.awn.quran.feature.recite

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnFieldColors

/** The colours of the recite bar, taken from the active colour mode. */
internal data class ReciteBarColors(
    val background: Color,
    val content: Color,
    /** The fill of the microphone button, the grade that was suggested, and a switch that is on. */
    val primaryFill: Color,
    val onPrimary: Color,
) {
    companion object {
        /**
         * The colours of the bar while reciting: the ochre of the mushaf's rosettes in every colour
         * mode, so the bar looks different from the audio bar at the same place. Ink text and a
         * green main button and switch, both with at least the contrast the colour-mode bars have.
         */
        fun reciting(): ReciteBarColors =
            ReciteBarColors(
                background = AwnColors.ochre,
                content = AwnColors.ink,
                primaryFill = AwnColors.green,
                onPrimary = AwnColors.paper,
            )

        fun of(field: AwnFieldColors): ReciteBarColors {
            // In Awn mode the button colour is the bar's own green, so a button in it would vanish;
            // there the main button is the ochre of the mushaf's rosettes, as the design system
            // says.
            val buttonVanishes = field.buttonFill == field.barBackground
            return ReciteBarColors(
                background = field.barBackground,
                content = field.onBar,
                primaryFill = if (buttonVanishes) AwnColors.ochre else field.buttonFill,
                onPrimary = if (buttonVanishes) AwnColors.ink else field.onButton,
            )
        }
    }
}
