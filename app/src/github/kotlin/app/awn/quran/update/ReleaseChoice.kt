package app.awn.quran.update

import org.json.JSONArray

/** A file attached to a GitHub release. */
internal data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)

/** A GitHub release of this repository. */
internal data class AppRelease(val tagName: String, val assets: List<ReleaseAsset>)

internal const val CHECKSUMS_ASSET_NAME = "SHA256SUMS.txt"

/** Releases of the app have tags like v0.1.3; data releases (data-v1, models-v1) do not parse. */
private const val APP_TAG_PREFIX = "v0."

/**
 * The newest app release by version. The list is not sorted by version and the repository's
 * "Latest" release is a data release, so the tag is read rather than trusting the order.
 */
internal fun newestAppRelease(releases: List<AppRelease>): AppRelease? =
    releases
        .filter { it.tagName.startsWith(APP_TAG_PREFIX) }
        .mapNotNull { release -> parseReleaseVersion(release.tagName)?.let { it to release } }
        .maxByOrNull { (version, _) -> version }
        ?.second

/** The APK built for the first of the phone's ABIs that the release has one for. */
internal fun pickApkAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? =
    supportedAbis.firstNotNullOfOrNull { abi ->
        assets.firstOrNull { it.name == "app-github-$abi-release.apk" }
    }

/**
 * The SHA-256 of [fileName] from the text of a `sha256sum` output, whose lines read `<hash>
 * ./<name>` (or `<hash> *<name>` for binary mode). Returned in lower case.
 */
internal fun expectedChecksum(checksumText: String, fileName: String): String? =
    checksumText.lineSequence().firstNotNullOfOrNull { line ->
        val hash = line.substringBefore(' ').trim()
        val name = line.substringAfter(' ').trim().removePrefix("*").removePrefix("./")
        if (name == fileName && hash.isNotEmpty()) hash.lowercase() else null
    }

/** Reads the body of the GitHub releases list. */
internal fun parseReleases(json: String): List<AppRelease> {
    val releases = JSONArray(json)
    return (0 until releases.length()).map { index ->
        val release = releases.getJSONObject(index)
        val assets = release.getJSONArray("assets")
        AppRelease(
            tagName = release.getString("tag_name"),
            assets =
                (0 until assets.length()).map { assetIndex ->
                    val asset = assets.getJSONObject(assetIndex)
                    ReleaseAsset(
                        name = asset.getString("name"),
                        downloadUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.getLong("size"),
                    )
                },
        )
    }
}
