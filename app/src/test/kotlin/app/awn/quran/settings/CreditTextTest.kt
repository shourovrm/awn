package app.awn.quran.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditTextTest {

    @Test
    fun namedEntitiesAreDecoded() {
        assertEquals(
            "Quran.com & Quran Foundation",
            CreditText.toPlainText("Quran.com &amp; Quran Foundation"),
        )
        assertEquals("\"Hafs\" <text>", CreditText.toPlainText("&quot;Hafs&quot; &lt;text&gt;"))
    }

    @Test
    fun numericEntitiesAreDecodedInDecimalAndHexadecimal() {
        assertEquals("King Fahd's", CreditText.toPlainText("King Fahd&#39;s"))
        assertEquals("King Fahd's", CreditText.toPlainText("King Fahd&#x27;s"))
        assertEquals("ا", CreditText.toPlainText("&#x627;"))
    }

    @Test
    fun anUnknownEntityStaysAsWritten() {
        assertEquals("a &bogus; b", CreditText.toPlainText("a &bogus; b"))
        assertEquals("&#99999999999;", CreditText.toPlainText("&#99999999999;"))
    }

    @Test
    fun tagsAreRemovedAndWhiteSpaceIsCollapsed() {
        assertEquals(
            "Saheeh International",
            CreditText.toPlainText("<b>Saheeh</b>   International\n"),
        )
    }

    @Test
    fun anEntityIsDecodedOnlyOnce() {
        assertEquals("&lt;", CreditText.toPlainText("&amp;lt;"))
    }

    @Test
    fun aLongAddressBecomesItsHostName() {
        assertEquals(
            "github.com",
            CreditText.linkLabel(
                "https://github.com/quran/quran.com-frontend-next/tree/aff1a035/public/fonts",
            ),
        )
        assertEquals("everyayah.com", CreditText.linkLabel("https://www.everyayah.com/data/"))
    }

    @Test
    fun aValueThatIsNotAWebAddressIsReturnedAsWritten() {
        assertEquals("not a link", CreditText.linkLabel("not a link"))
        assertEquals("", CreditText.linkLabel(" "))
    }
}
