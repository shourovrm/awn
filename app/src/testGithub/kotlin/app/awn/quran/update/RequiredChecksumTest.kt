package app.awn.quran.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RequiredChecksumTest {

    @Test
    fun aMissingChecksumFileIsRefused() {
        assertThrows(IOException::class.java) { requiredChecksum(null, "a.apk") }
    }

    @Test
    fun aChecksumFileWithoutTheApkIsRefused() {
        assertThrows(IOException::class.java) { requiredChecksum("aaaa  ./b.apk", "a.apk") }
    }

    @Test
    fun theListedChecksumIsReturned() {
        assertEquals("aaaa", requiredChecksum("AAAA  ./a.apk", "a.apk"))
    }
}
