package app.awn.quran.settings

import app.awn.quran.core.data.PackRepository

/** One source named in a manifest: who made the data and where it comes from. */
data class CreditLine(val sourceName: String, val sourceUrl: String)

/** The manifests whose `credit` fields are listed on the Credits screen. */
private val CREDIT_MANIFESTS = listOf("scripts", "translations", "reciters")

/** Reads every entry of the three manifests and drops repeats of the same source. */
suspend fun loadCredits(packs: PackRepository): List<CreditLine> =
    CREDIT_MANIFESTS.flatMap { manifest -> packs.manifest(manifest) }
        .map { entry -> CreditLine(entry.credit.sourceName, entry.credit.sourceUrl) }
        .distinct()
