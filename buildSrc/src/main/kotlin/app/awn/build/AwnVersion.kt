package app.awn.build

/** Version information derived from git, never edited by hand. */
data class AwnVersion(val versionName: String, val baseVersionCode: Int) {

    /** Play and the package manager prefer the higher code, so 64-bit builds get the higher offset. */
    fun versionCodeForAbi(abi: String): Int {
        val offset =
            ABI_OFFSETS[abi]
                ?: throw IllegalArgumentException(
                    "No version code offset for ABI '$abi'. Known ABIs: ${ABI_OFFSETS.keys}"
                )
        return offset * ABI_OFFSET_STEP + baseVersionCode
    }

    companion object {
        /** Base codes stay below 100_000_000 (highest tag 0.99.99 plus 9_999 commits), so offsets never collide. */
        const val ABI_OFFSET_STEP = 100_000_000

        /** The last four digits of the base code count the commits since the tag. */
        const val COMMITS_SINCE_TAG_LIMIT = 10_000

        val ABI_OFFSETS = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

        private val DESCRIBE_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)-(\d+)-g[0-9a-f]+$""")

        /**
         * [describe] is the output of `git describe --tags --match 'v*' --long`, or null when the
         * history has no version tag. The base code is `(MINOR*100 + PATCH) * 10_000` plus the
         * commits since the tag, so it only grows along the history. Without a version tag the
         * build is `0.0.0` and the commit count is the code, which stays below the first tag's code
         * (1_000_000) while there are fewer than 10_000 commits.
         */
        fun fromGit(describe: String?, commitCount: Int): AwnVersion {
            val match = describe?.let { DESCRIBE_PATTERN.matchEntire(it.trim()) }
            if (match == null) {
                return AwnVersion("0.0.0-$commitCount", commitCount)
            }
            val (major, minor, patch, commitsSinceTag) = match.destructured.toList().map { it.toInt() }
            require(major == 0) {
                "Tag '$describe' has major version $major; the version code scheme only covers 0.x.y."
            }
            require(minor < 100 && patch < 100) {
                "Tag '$describe' has a minor or patch number of 100 or more, which would collide in versionCode."
            }
            require(commitsSinceTag < COMMITS_SINCE_TAG_LIMIT) {
                "$commitsSinceTag commits since the last tag; make a new tag before $COMMITS_SINCE_TAG_LIMIT."
            }
            val tagName = "$major.$minor.$patch"
            val versionName = if (commitsSinceTag == 0) tagName else "$tagName-$commitsSinceTag"
            return AwnVersion(versionName, (minor * 100 + patch) * 10_000 + commitsSinceTag)
        }
    }
}
