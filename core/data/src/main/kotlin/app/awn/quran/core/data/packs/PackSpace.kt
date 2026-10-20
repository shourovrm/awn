package app.awn.quran.core.data.packs

/**
 * How much free space an install needs, and whether the phone has it. Checking before the download
 * lets the app say "Needs 54 MB, 20 MB free" instead of failing at 90 %.
 */
object PackSpace {

    /**
     * Unpacked fonts are 1.5 to 2.4 times the size of their zip, and the zip stays on disk while it
     * is unpacked. Two extra copies of the download size cover the largest page font; the check
     * after unpacking still catches a rare overflow.
     */
    private const val UNPACKED_COPIES_OF_ARCHIVE = 2L

    /**
     * Returns the free bytes needed to install a pack that downloads [downloadBytes] when
     * [alreadyDownloadedBytes] of it are already on disk from an earlier attempt.
     */
    fun requiredFreeBytes(
        layout: PackFileLayout,
        downloadBytes: Long,
        alreadyDownloadedBytes: Long = 0L,
    ): Long {
        val remaining = (downloadBytes - alreadyDownloadedBytes).coerceAtLeast(0L)
        return when (layout) {
            PackFileLayout.Database,
            PackFileLayout.SingleFont -> remaining
            PackFileLayout.FontArchive -> remaining + downloadBytes * UNPACKED_COPIES_OF_ARCHIVE
        }
    }

    /** Returns how many bytes short the phone is, or 0 when [freeBytes] is enough. */
    fun shortfall(requiredBytes: Long, freeBytes: Long): Long =
        (requiredBytes - freeBytes).coerceAtLeast(0L)
}
