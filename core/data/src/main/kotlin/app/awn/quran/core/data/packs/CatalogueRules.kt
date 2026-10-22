package app.awn.quran.core.data.packs

import app.awn.quran.core.data.CatalogueGroup

/** Decides whether an installed pack is out of date. */
object PackUpdate {

    /**
     * Returns true when the manifest lists a different checksum than the one the installed copy was
     * installed with. A copy with no recorded checksum (bundled in the app, or installed by an
     * older version) is never offered an update, because nothing says it is out of date.
     */
    fun isAvailable(installedSha256: String?, manifestSha256: String): Boolean =
        installedSha256 != null && installedSha256 != manifestSha256
}

/** Sorts manifest records into the sections of the catalogue without naming any pack. */
object CatalogueGrouping {

    private const val AYAH_KIND = "ayah"
    private const val WORD_KIND = "word"
    private const val TRANSLITERATION = "transliteration"
    private const val WORD_TIMING_GRANULARITY = "word"

    /**
     * Returns the section of [record], or null when the record is not a catalogue item: the Quran
     * text, fonts that ship inside the app, and timings that only mark ayahs. [isBundled] says the
     * app ships the file. A timing pack with word timings is the reciter's word audio.
     *
     * A translation is a transliteration when its manifest `kind` says so or its id does; the
     * manifests list the English transliteration with kind "ayah".
     */
    fun groupOf(record: PackRecord, isBundled: Boolean): CatalogueGroup? =
        when (record.kindLabel) {
            "font" -> if (isBundled) null else CatalogueGroup.PageType
            ManifestCatalog.LEARN_AUDIO_KIND -> CatalogueGroup.LearnAudio
            "translation" -> translationGroupOf(record)
            // An ayah-level timing pack (Basfar) cannot play a word, so it is not offered.
            "timing" ->
                if (record.timingGranularity == WORD_TIMING_GRANULARITY) {
                    CatalogueGroup.WordAudio
                } else {
                    null
                }
            else -> null
        }

    private fun translationGroupOf(record: PackRecord): CatalogueGroup? =
        when {
            record.translationKind == WORD_KIND -> CatalogueGroup.WordByWord
            record.translationKind == TRANSLITERATION ||
                record.entry.id.contains(TRANSLITERATION, ignoreCase = true) ->
                CatalogueGroup.Transliteration
            record.translationKind == AYAH_KIND -> CatalogueGroup.Translation
            else -> null
        }
}
