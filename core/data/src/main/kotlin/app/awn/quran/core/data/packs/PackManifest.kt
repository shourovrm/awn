package app.awn.quran.core.data.packs

/** Where a pack's content comes from and under what conditions it may be shown. */
data class Credit(val sourceName: String, val sourceUrl: String, val licence: String)

enum class PackKind {
    Script,
    Timing,
    Translation,
    Model,
}

/** One downloadable file, as listed in a manifest such as `scripts.json`. */
data class PackEntry(
    val id: String,
    val kind: PackKind,
    val formatVersion: Int,
    val sha256: String,
    val downloadUrl: String,
    val credit: Credit,
) {
    init {
        require(id.isNotBlank()) { "A pack needs an id." }
        require(formatVersion >= 1) {
            "Pack '$id' has format_version $formatVersion; it starts at 1."
        }
        require(SHA256_PATTERN.matches(sha256)) {
            "Pack '$id' has a malformed sha256 '$sha256'; expected 64 lowercase hex digits."
        }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

data class PackManifest(val packs: List<PackEntry>)
