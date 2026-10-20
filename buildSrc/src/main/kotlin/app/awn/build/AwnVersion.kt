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
        const val ABI_OFFSET_STEP = 1_000_000

        val ABI_OFFSETS = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

        private val TAG_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

        /**
         * A commit that carries a tag `vMAJOR.MINOR.PATCH` gets `MAJOR*10000 + MINOR*100 + PATCH`.
         * Any other commit is `0.0.0` with the commit count, so every untagged build still
         * has a version code that grows with the history.
         */
        fun fromGit(exactTag: String?, commitCount: Int): AwnVersion {
            val match = exactTag?.let { TAG_PATTERN.matchEntire(it.trim()) }
            if (match == null) {
                return AwnVersion("0.0.0-$commitCount", commitCount)
            }
            val (major, minor, patch) = match.destructured.toList().map { it.toInt() }
            require(minor < 100 && patch < 100) {
                "Tag '$exactTag' has a minor or patch number of 100 or more, which would collide in versionCode."
            }
            val versionName = "$major.$minor.$patch"
            return AwnVersion(versionName, major * 10_000 + minor * 100 + patch)
        }
    }
}
