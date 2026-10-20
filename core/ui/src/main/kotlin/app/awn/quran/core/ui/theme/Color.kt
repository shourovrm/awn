package app.awn.quran.core.ui.theme

import androidx.compose.ui.graphics.Color

/** The Madani Mushaf's own colours; see docs/design-system.md. */
object AwnColors {
    val paper = Color(0xFFF4ECDA)
    val ink = Color(0xFF17160F)
    val green = Color(0xFF0E5A3C)
    val ochre = Color(0xFFD9A33A)
    val red = Color(0xFFB5341F)

    /** Word states on the night field, where the paper-field inks would be too dark to read. */
    val nightConfirmed = Color(0xFF7CE29A)
    val nightTentative = Color(0xFFF2C94C)
    val nightWrong = Color(0xFFFF7A66)

    // Light mode: a white page, neutral grey bars and the Awn green as the accent.
    val lightPage = Color(0xFFFFFFFF)
    val lightInk = Color(0xFF1B1B1B)
    val lightBar = Color(0xFFECEEEF)
    val lightHiddenWord = Color(0xFFDADDDF)
    val lightTentative = Color(0xFF8A5F00)

    // Dark mode: #121212 page with light text.
    val darkPage = Color(0xFF121212)
    val darkBar = Color(0xFF1F1F1F)
    val darkHiddenWord = Color(0xFF353535)

    // Black mode: #000000 page for OLED screens.
    val blackPage = Color(0xFF000000)
    val blackBar = Color(0xFF121212)
    val blackHiddenWord = Color(0xFF2A2A2A)

    /** Text on the dark and black pages: warm off-white, easier on the eye than pure white. */
    val nightText = Color(0xFFE8E2D2)
}
