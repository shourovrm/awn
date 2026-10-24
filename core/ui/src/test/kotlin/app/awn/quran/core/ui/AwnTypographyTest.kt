package app.awn.quran.core.ui

import androidx.compose.ui.unit.TextUnit
import app.awn.quran.core.ui.theme.AwnTypography
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A letter spacing on a heading made "কণ্ঠ" and "পাঁচটি এলাকা" wrap inside a column that had room
 * for them (2026-10-24): Android measures spaced Bangla and Arabic text narrower than it draws it.
 */
class AwnTypographyTest {

    @Test
    fun noInterfaceStyleSetsALetterSpacing() {
        val styles =
            mapOf(
                "caption" to AwnTypography.caption,
                "small" to AwnTypography.small,
                "body" to AwnTypography.body,
                "bodyStrong" to AwnTypography.bodyStrong,
                "lead" to AwnTypography.lead,
                "title" to AwnTypography.title,
                "poster" to AwnTypography.poster,
                "searchResultTranslation" to AwnTypography.searchResultTranslation,
                "searchResultReference" to AwnTypography.searchResultReference,
            )

        for ((name, style) in styles) {
            assertEquals(name, TextUnit.Unspecified, style.letterSpacing)
        }
    }
}
