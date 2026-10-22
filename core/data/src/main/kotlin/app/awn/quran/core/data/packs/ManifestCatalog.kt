package app.awn.quran.core.data.packs

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** How a pack's file is stored once installed. */
enum class PackFileLayout {
    /** One SQLite file. */
    Database,

    /** One font file. */
    SingleFont,

    /** A zip of per-page fonts, extracted into a folder. */
    FontArchive,
}

/**
 * A manifest entry with the extra fields the app needs to download and place the pack. [kindLabel]
 * is the label shown by `InstalledPack.kind`: script, font, translation or timing. [fileName] is
 * the basename of the manifest `file` path, which is also the release asset name. [memberCount] is
 * the number of files in a font archive, or null for other layouts. [timingGranularity] is "word"
 * or "ayah" for a timing pack and null for every other kind. [requires] lists the ids of the packs
 * that must be on the phone for this one to work; they are installed with it.
 */
data class PackRecord(
    val entry: PackEntry,
    val kindLabel: String,
    val fileName: String,
    val bytes: Long,
    val layout: PackFileLayout,
    val memberCount: Int?,
    val translationKind: String? = null,
    val language: String? = null,
    val displayName: String,
    val timingGranularity: String? = null,
    val requires: List<String> = emptyList(),
)

/** A reciter row of `reciters.json`; [timingPack] is null when the reciter has no timings. */
data class ReciterRecord(
    val id: String,
    val name: String,
    val surahUrlPattern: String,
    val ayahUrlPattern: String?,
    val wordByWord: Boolean,
    val timingPack: PackRecord?,
)

/**
 * The three bundled manifests, parsed. Entries newer than [supportedFormatVersion] are dropped so
 * an old app ignores packs it cannot read; a manifest file newer than that is rejected whole.
 */
class ManifestCatalog(
    private val assets: BundledAssets,
    private val releaseBaseUrl: String = DATA_RELEASE_URL,
    private val supportedFormatVersion: Int = SUPPORTED_FORMAT_VERSION,
    private val externalUrlPrefix: String = HTTPS_PREFIX,
) {

    val scriptPacks: List<PackRecord> by lazy {
        packsOf("scripts.json").map { json -> recordOf(json, scriptKindLabel(json)) }
    }

    val translationPacks: List<PackRecord> by lazy {
        packsOf("translations.json").map { json ->
            recordOf(json, "translation")
                .copy(
                    translationKind = json.getString("kind"),
                    language = json.stringOrNull("language"),
                )
        }
    }

    val reciters: List<ReciterRecord> by lazy {
        val rows = loadManifest("reciters.json").getJSONArray("reciters")
        objectsOf(rows).map(::reciterOf)
    }

    val timingPacks: List<PackRecord> by lazy { reciters.mapNotNull { it.timingPack } }

    val allPacks: List<PackRecord> by lazy { scriptPacks + translationPacks + timingPacks }

    fun findPack(id: String): PackRecord? = allPacks.firstOrNull { it.entry.id == id }

    private fun reciterOf(json: JSONObject): ReciterRecord {
        val timingPackId = json.stringOrNull("timing_pack")
        val timingPack =
            if (timingPackId != null && json.getInt("format_version") <= supportedFormatVersion) {
                recordOf(json, "timing", id = timingPackId)
                    .copy(timingGranularity = json.stringOrNull("timing_granularity"))
            } else {
                null
            }
        return ReciterRecord(
            id = json.getString("id"),
            name = json.getString("name"),
            surahUrlPattern = json.getString("surah_url_pattern"),
            ayahUrlPattern = json.stringOrNull("ayah_url_pattern"),
            wordByWord = json.getBoolean("word_by_word"),
            timingPack = timingPack,
        )
    }

    private fun scriptKindLabel(json: JSONObject): String =
        when (val kind = json.getString("kind")) {
            "text" -> "script"
            "font" -> "font"
            else -> throw IllegalArgumentException("scripts.json has an unknown kind '$kind'.")
        }

    private fun recordOf(
        json: JSONObject,
        kindLabel: String,
        id: String = json.getString("id"),
    ): PackRecord {
        val relativeFile = json.getString("file")
        val fileName = relativeFile.substringAfterLast('/')
        val layout =
            when {
                fileName.endsWith(".sqlite") -> PackFileLayout.Database
                fileName.endsWith(".zip") -> PackFileLayout.FontArchive
                else -> PackFileLayout.SingleFont
            }
        val entry =
            PackEntry(
                id = id,
                kind = packKindOf(kindLabel),
                formatVersion = json.getInt("format_version"),
                sha256 = json.getString("sha256"),
                downloadUrl = downloadUrlOf(json, id, fileName),
                credit =
                    Credit(
                        sourceName = json.getString("credit"),
                        sourceUrl = json.getString("source_url"),
                        licence = LICENCE_NOT_STATED,
                    ),
            )
        return PackRecord(
            entry = entry,
            kindLabel = kindLabel,
            fileName = fileName,
            bytes = json.getLong("bytes"),
            layout = layout,
            memberCount = if (json.has("member_count")) json.getInt("member_count") else null,
            displayName = json.optString("name", id),
            requires = json.stringsOrEmpty("requires"),
        )
    }

    /**
     * A pack comes from the data release unless the manifest gives its own `download_url` (the
     * IndoPak font, which the data release does not host). Either way the app checks the hash and
     * the size, and an address other than https is refused.
     */
    private fun downloadUrlOf(json: JSONObject, id: String, fileName: String): String {
        val externalUrl = json.stringOrNull("download_url")
        if (externalUrl == null) return "${releaseBaseUrl.trimEnd('/')}/$fileName"
        require(externalUrl.startsWith(externalUrlPrefix)) {
            "Pack '$id' has a download_url that does not start with $externalUrlPrefix: $externalUrl"
        }
        return externalUrl
    }

    private fun packKindOf(kindLabel: String): PackKind =
        when (kindLabel) {
            "script",
            "font" -> PackKind.Script
            "translation" -> PackKind.Translation
            "timing" -> PackKind.Timing
            else -> throw IllegalArgumentException("Unknown pack kind label '$kindLabel'.")
        }

    private fun packsOf(manifestName: String): List<JSONObject> =
        objectsOf(loadManifest(manifestName).getJSONArray("packs")).filter {
            it.getInt("format_version") <= supportedFormatVersion
        }

    private fun loadManifest(manifestName: String): JSONObject {
        val text = assets.open(manifestName).use { it.readBytes().toString(Charsets.UTF_8) }
        try {
            val manifest = JSONObject(text)
            val version = manifest.getInt("format_version")
            require(version <= supportedFormatVersion) {
                "$manifestName has format_version $version; this app reads up to " +
                    "$supportedFormatVersion."
            }
            return manifest
        } catch (error: JSONException) {
            throw IllegalArgumentException("$manifestName is malformed: ${error.message}", error)
        }
    }

    /**
     * `optString` returns the text "null" for a JSON null, which is never what a manifest means.
     */
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key).ifEmpty { null }

    private fun objectsOf(rows: JSONArray): List<JSONObject> =
        (0 until rows.length()).map { rows.getJSONObject(it) }

    companion object {
        const val SUPPORTED_FORMAT_VERSION = 1
        const val HTTPS_PREFIX = "https://"
        const val DATA_RELEASE_URL = "https://github.com/shourovrm/awn/releases/download/data-v1"

        /** The manifests carry no licence text; `docs/packs.md` says it must be confirmed. */
        const val LICENCE_NOT_STATED = "Not stated by the source"
    }
}

private fun JSONObject.stringsOrEmpty(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return (0 until values.length()).map { values.getString(it) }
}
