package app.awn.quran.core.data.packs

/**
 * Decides which listed packs this build can read. The app reads any pack whose `format_version` is
 * at or below the version it was built for, so old app versions keep working after new data is
 * published. Downloading and storage arrive with the first pack milestone.
 */
class Packs(private val supportedFormatVersion: Int) {

    fun usable(manifest: PackManifest): List<PackEntry> =
        manifest.packs.filter { it.formatVersion <= supportedFormatVersion }
}
