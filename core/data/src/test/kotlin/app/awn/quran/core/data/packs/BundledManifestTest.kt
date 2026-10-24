package app.awn.quran.core.data.packs

import app.awn.quran.core.data.realBundledPacksDirectory
import app.awn.quran.core.data.realManifestCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app copies a bundled pack out of the APK only when the copy on the phone differs in size from
 * the manifest entry. A rebuilt bundled file whose manifest entry was not updated is therefore
 * never copied over an older one: the 2026-10-24 surah-name build crashed on update ("no such
 * column: name_bangla_meaning") because scripts.json still listed the old quran.sqlite.
 */
@RunWith(RobolectricTestRunner::class)
class BundledManifestTest {

    @Test
    fun everyBundledFileMatchesItsManifestEntry() {
        val bundled =
            realManifestCatalog().allPacks.filter { record ->
                File(realBundledPacksDirectory, record.fileName).isFile
            }
        assertTrue("no bundled pack was found", bundled.isNotEmpty())
        for (record in bundled) {
            val file = File(realBundledPacksDirectory, record.fileName)
            assertEquals("bytes of ${record.fileName}", record.bytes, file.length())
            assertEquals(
                "sha256 of ${record.fileName}",
                record.entry.sha256,
                PackDownloader.sha256Of(file),
            )
        }
    }
}
