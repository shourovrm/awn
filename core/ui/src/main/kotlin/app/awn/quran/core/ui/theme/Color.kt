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

    /**
     * Fills drawn behind Quran text on the page. Selection and playback use different hues so both
     * can show at once; see docs/redesign.md, "Highlights".
     */
    val selectedAyahLight = Color(0x331F7A8C)
    val playingAyahLight = Color(0x2E0E5A3C)
    val playingWordLight = Color(0x66D9A33A)
    val bookmarkedAyahLight = Color(0x1FD9A33A)
    val selectedAyahNight = Color(0x4D4FB3C4)
    val playingAyahNight = Color(0x407CE29A)
    val playingWordNight = Color(0x66F2C94C)
    val bookmarkedAyahNight = Color(0x26F2C94C)
}
