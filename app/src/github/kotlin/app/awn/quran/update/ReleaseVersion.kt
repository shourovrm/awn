package app.awn.quran.update

private const val VERSION_PART_COUNT = 3

/** The three numbers of a release tag such as v0.1.3. */
internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(
            this,
            other,
            ReleaseVersion::major,
            ReleaseVersion::minor,
            ReleaseVersion::patch,
        )
}

/**
 * Reads "v0.1.3", "0.1.3" or a build name like "0.1.2-36". The text after the first dash is the
 * number of commits since the tag, which says nothing about which release is newer, so it is cut.
 * Returns null when the text does not hold exactly three numbers.
 */
internal fun parseReleaseVersion(text: String): ReleaseVersion? {
    val parts = text.removePrefix("v").substringBefore('-').split('.')
    val numbers = parts.mapNotNull { part -> part.toIntOrNull() }
    if (parts.size != VERSION_PART_COUNT || numbers.size != VERSION_PART_COUNT) return null
    return ReleaseVersion(numbers[0], numbers[1], numbers[2])
}

/** True only when both texts are versions and the tag is strictly newer than the install. */
internal fun isNewerRelease(releaseTag: String, installedVersionName: String): Boolean {
    val release = parseReleaseVersion(releaseTag)
    val installed = parseReleaseVersion(installedVersionName)
    return release != null && installed != null && release > installed
}

/** The tag without its leading v, as shown to the user. */
internal fun releaseVersionName(tagName: String): String = tagName.removePrefix("v")
