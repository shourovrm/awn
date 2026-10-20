package app.awn.quran.settings

import app.awn.quran.core.data.PackRepository

/**
 * One source named in a manifest: who made the data, as readable text, and where it comes from.
 * [linkLabel] is the short form of [sourceUrl] shown to the user.
 */
data class CreditLine(val sourceName: String, val sourceUrl: String) {
    val linkLabel: String
        get() = CreditText.linkLabel(sourceUrl)
}

/** The manifests whose `credit` fields are listed on the Credits screen. */
private val CREDIT_MANIFESTS = listOf("scripts", "translations", "reciters")

/**
 * Reads every entry of the three manifests, decodes the markup in the names, and lists each source
 * once; entries that name the same source with different addresses keep the first address.
 */
suspend fun loadCredits(packs: PackRepository): List<CreditLine> =
    CREDIT_MANIFESTS.flatMap { manifest -> packs.manifest(manifest) }
        .map { entry ->
            CreditLine(CreditText.toPlainText(entry.credit.sourceName), entry.credit.sourceUrl)
        }
        .distinctBy { it.sourceName }
