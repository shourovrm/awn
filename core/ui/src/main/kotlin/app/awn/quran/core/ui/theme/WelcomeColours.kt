package app.awn.quran.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colours of the first-launch language screen, the one screen that repaints the whole window:
 * binding green in the day modes (Awn and Light) and the page colour of the night mode in the Dark
 * and Black modes. [field] feeds the theme; the rest colours the two language blocks.
 */
data class WelcomeColours(
    val field: AwnFieldColors,
    val mutedText: Color,
    val blockFill: Color,
    val blockSubText: Color,
    val selectedBlockFill: Color,
    val selectedBlockText: Color,
    val selectedBlockSubText: Color,
    val markRing: Color,
    val selectedMarkFill: Color,
    val selectedMarkTick: Color,
)

private object WelcomeTokens {
    val greenField = Color(0xFF0E5A3C)
    val paper = Color(0xFFF4ECDA)
    val mutedPaper = Color(0xFFE3DCC8)
    val ink = Color(0xFF17160F)
    val softInk = Color(0xFF3A3A2C)
    val ochre = Color(0xFFD9A33A)
    val nightText = Color(0xFFE8E2D2)
    val nightMuted = Color(0xFFB9B2A0)
    val nightBlock = Color(0xFF1D1D1B)
    val nightSelectedBlock = Color(0xFF2C2B26)
    const val DAY_BLOCK_ALPHA = 0.14f
    const val MARK_RING_ALPHA = 0.55f
}

/** The colours of the language screen in [mode]. */
fun welcomeColours(mode: ColourMode): WelcomeColours {
    val baseField = awnFieldColors(AwnField.Green, ColourMode.Awn)
    return if (mode.isNight) {
        val nightPage = awnFieldColors(AwnField.Paper, mode).background
        WelcomeColours(
            field =
                baseField.copy(
                    background = nightPage,
                    content = WelcomeTokens.nightText,
                    buttonFill = WelcomeTokens.ochre,
                    onButton = WelcomeTokens.ink,
                ),
            mutedText = WelcomeTokens.nightMuted,
            blockFill = WelcomeTokens.nightBlock,
            blockSubText = WelcomeTokens.nightMuted,
            selectedBlockFill = WelcomeTokens.nightSelectedBlock,
            selectedBlockText = WelcomeTokens.nightText,
            selectedBlockSubText = WelcomeTokens.nightMuted,
            markRing = WelcomeTokens.nightText.copy(alpha = WelcomeTokens.MARK_RING_ALPHA),
            selectedMarkFill = WelcomeTokens.ochre,
            selectedMarkTick = WelcomeTokens.ink,
        )
    } else {
        WelcomeColours(
            field =
                baseField.copy(
                    background = WelcomeTokens.greenField,
                    content = WelcomeTokens.paper,
                    buttonFill = WelcomeTokens.ochre,
                    onButton = WelcomeTokens.ink,
                ),
            mutedText = WelcomeTokens.mutedPaper,
            blockFill = WelcomeTokens.paper.copy(alpha = WelcomeTokens.DAY_BLOCK_ALPHA),
            blockSubText = WelcomeTokens.paper,
            selectedBlockFill = WelcomeTokens.paper,
            selectedBlockText = WelcomeTokens.ink,
            selectedBlockSubText = WelcomeTokens.softInk,
            markRing = WelcomeTokens.paper.copy(alpha = WelcomeTokens.MARK_RING_ALPHA),
            selectedMarkFill = WelcomeTokens.greenField,
            selectedMarkTick = WelcomeTokens.paper,
        )
    }
}
