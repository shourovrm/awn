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
}
