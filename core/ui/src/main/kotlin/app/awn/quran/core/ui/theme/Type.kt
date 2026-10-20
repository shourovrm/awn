package app.awn.quran.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Five sizes only: 14, 16, 22, 32 and 56 sp. Roundo is not bundled yet, so the platform sans-serif
 * stands in until the font file is added.
 */
object AwnTypography {
    private val interfaceFont = FontFamily.SansSerif

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

    /** Quran text; the user's zoom may scale it, nothing else may. */
    val quran =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
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
