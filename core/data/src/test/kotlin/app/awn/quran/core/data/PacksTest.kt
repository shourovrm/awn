package app.awn.quran.core.data

import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackKind
import app.awn.quran.core.data.packs.PackManifest
import app.awn.quran.core.data.packs.Packs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PacksTest {

    private val credit = Credit("QUL", "https://qul.tarteel.ai", "see TERMS.md")
    private val validHash = "a".repeat(64)

    private fun entry(id: String, formatVersion: Int) =
        PackEntry(id, PackKind.Script, formatVersion, validHash, "https://example.org/$id", credit)

    @Test
    fun readsPacksAtOrBelowTheSupportedFormatVersion() {
        val manifest =
            PackManifest(listOf(entry("old", 1), entry("current", 2), entry("future", 3)))

        val usable = Packs(supportedFormatVersion = 2).usable(manifest)

        assertEquals(listOf("old", "current"), usable.map { it.id })
    }

    @Test
    fun rejectsAMalformedChecksum() {
        assertThrows(IllegalArgumentException::class.java) {
            PackEntry("bad", PackKind.Model, 1, "not-a-hash", "https://example.org", credit)
        }
    }
}
