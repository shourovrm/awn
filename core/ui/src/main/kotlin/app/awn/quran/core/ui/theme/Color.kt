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

/**
 * Soft fills drawn behind a word while it is recited. The hues are green, gold and red, which the
 * tajweed print does not use for its ink, and the alpha keeps black ink (day family: Awn and Light)
 * or light ink (night family: Dark and Black) above 4.5:1 contrast. Values are tokens only; the
 * Mushaf page picks the family from the field, not from the mode name.
 */
object AwnReciteFills {
    val dayConfirmed = Color(0x4D2E9E5B)
    val dayTentative = Color(0x59E0B000)
    val dayWrong = Color(0x4DE5484D)

    val nightConfirmed = Color(0x4D4ADE80)
    val nightTentative = Color(0x4DFACC15)
    val nightWrong = Color(0x4DF87171)
}

/**
 * Inks for the letters a lesson colours in a Quran word: one per tajweed rule, close to the hues of
 * the colour-coded tajweed Mushaf, plus the grammar ending. Day inks keep at least 3:1 on paper and
 * white at the large size lesson words are drawn; night inks do the same on #121212 and black.
 */
object AwnLessonMarks {
    val dayGhunnah = Color(0xFFB45300)
    val dayQalqalah = Color(0xFF1A6E9E)
    val dayMadd = Color(0xFFB02A1E)
    val dayIkhfa = Color(0xFF8A3A9A)
    val dayIdgham = Color(0xFF2F7D3A)
    val dayIqlab = Color(0xFF2F55B8)
    val dayHeavy = Color(0xFF233C8C)
    val daySilent = Color(0xFF8A857A)
    val dayEnding = AwnColors.green

    val nightGhunnah = Color(0xFFFFA24C)
    val nightQalqalah = Color(0xFF6CC3F0)
    val nightMadd = Color(0xFFFF8A7A)
    val nightIkhfa = Color(0xFFD49BE0)
    val nightIdgham = Color(0xFF7CD98A)
    val nightIqlab = Color(0xFF8FA8FF)
    val nightHeavy = Color(0xFFA9B9F5)
    val nightSilent = Color(0xFF8F8A80)
    val nightEnding = AwnColors.ochre
}
