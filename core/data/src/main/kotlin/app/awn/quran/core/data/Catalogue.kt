package app.awn.quran.core.data

import app.awn.quran.core.data.packs.PackEntry

/** The sections of the download catalogue, in the order the Downloads page lists them. */
enum class CatalogueGroup {
    /** A printed page type: one font per Mushaf page. */
    PageType,

    /** One translator's text of the whole Quran. */
    Translation,

    /** The meaning of each Arabic word, in one language. */
    WordByWord,

    /** The Arabic read in Latin letters. */
    Transliteration,

    /** One reciter's word timings, which let the word card play a single word. */
    WordAudio,

    /** The recorded letter and syllable sounds of the Learn units. */
    LearnAudio,
}

/**
 * One downloadable item of the catalogue, built from the manifests and what is on the phone. [name]
 * is the manifest name; [language] is a language code such as "en" or null when the item has none.
 * [requiredFreeBytes] is the free space an install needs, which exceeds [downloadBytes] for items
 * that are unpacked after the download. [isBundled] items ship inside the app: they count as
 * installed and cannot be removed. [hasUpdate] is true when the manifest lists another checksum
 * than the one the installed copy was installed with.
 */
data class CatalogueItem(
    val id: String,
    val name: String,
    val group: CatalogueGroup,
    val language: String?,
    val downloadBytes: Long,
    val requiredFreeBytes: Long,
    val isInstalled: Boolean,
    val installedBytes: Long?,
    val isBundled: Boolean,
    val hasUpdate: Boolean,
    val entry: PackEntry,
) {
    val canRemove: Boolean
        get() = isInstalled && !isBundled
}
