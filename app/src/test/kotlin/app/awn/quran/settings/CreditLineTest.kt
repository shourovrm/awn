package app.awn.quran.settings

import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditLineTest {

    private val checksum = "a".repeat(64)

    private fun entry(id: String, kind: PackKind, sourceName: String, sourceUrl: String) =
        PackEntry(
            id,
            kind,
            1,
            checksum,
            "https://example.org/$id",
            Credit(sourceName, sourceUrl, ""),
        )

    @Test
    fun creditsComeFromAllThreeManifestsWithRepeatedSourcesListedOnce() = runBlocking {
        val packs =
            FakePackRepository(
                manifests =
                    mutableMapOf(
                        "scripts" to listOf(entry("v1", PackKind.Script, "King Fahd", "https://k")),
                        "translations" to
                            listOf(
                                entry("a", PackKind.Translation, "Quran.com", "https://q"),
                                entry("b", PackKind.Translation, "Quran.com", "https://q"),
                            ),
                        "reciters" to
                            listOf(entry("alafasy", PackKind.Timing, "EveryAyah", "https://e")),
                    ),
            )

        val credits = loadCredits(packs)

        assertEquals(
            listOf(
                CreditLine("King Fahd", "https://k"),
                CreditLine("Quran.com", "https://q"),
                CreditLine("EveryAyah", "https://e"),
            ),
            credits,
        )
    }

    @Test
    fun namesAreShownWithoutEntitiesAndOneRowPerName() = runBlocking {
        val packs =
            FakePackRepository(
                manifests =
                    mutableMapOf(
                        "scripts" to
                            listOf(
                                entry(
                                    "a",
                                    PackKind.Script,
                                    "Quran.com &amp; Foundation",
                                    "https://github.com/quran/x/tree/1",
                                ),
                                entry(
                                    "b",
                                    PackKind.Script,
                                    "Quran.com &amp; Foundation",
                                    "https://github.com/quran/y",
                                ),
                            ),
                    ),
            )

        val credits = loadCredits(packs)

        assertEquals(
            listOf(CreditLine("Quran.com & Foundation", "https://github.com/quran/x/tree/1")),
            credits,
        )
        assertEquals("github.com", credits.single().linkLabel)
    }
}
