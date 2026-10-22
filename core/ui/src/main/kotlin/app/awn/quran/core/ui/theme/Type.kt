package app.awn.quran.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Six sizes only: 12, 14, 16, 22, 32 and 56 sp. Roundo is not bundled yet, so the platform
 * sans-serif stands in until the font file is added.
 */
object AwnTypography {
    private val interfaceFont = FontFamily.SansSerif

    /** Word-by-word meanings under each Quran word. */
    val caption =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 1.5.em,
        )

    val small =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 1.5.em,
        )

    val body =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 1.5.em,
        )

    /** Body size at weight 600, for button labels and row values. */
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)

    val lead =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 1.2.em,
        )

    val title =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 1.05.em,
            letterSpacing = (-0.025).em,
        )

    val poster =
        TextStyle(
            fontFamily = interfaceFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 56.sp,
            lineHeight = 1.05.em,
            letterSpacing = (-0.025).em,
        )

    /**
     * Quran text in the bundled Uthmanic Hafs font; the user's zoom may scale it, nothing else may.
     * A getter, because the font family exists only after the theme installs it.
     */
    val quran: TextStyle
        get() =
            TextStyle(
                fontFamily = HafsFont.family,
                fontWeight = FontWeight.Normal,
                fontSize = 26.sp,
                lineHeight = 2.2.em,
            )

    /**
     * A line of Quran text on the Quran tab (the continue card and the day's snippet): the lead
     * size, with the tall line height that Arabic marks above and below the letters need.
     */
    val quranLead: TextStyle
        get() =
            TextStyle(
                fontFamily = HafsFont.family,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                lineHeight = 2.0.em,
            )

    fun toMaterialTypography(): Typography =
        Typography(
            displayLarge = poster,
            headlineLarge = title,
            titleLarge = lead,
            bodyLarge = body,
            bodyMedium = body,
            bodySmall = small,
            labelLarge = bodyStrong,
            labelMedium = small,
            labelSmall = small,
        )
}
