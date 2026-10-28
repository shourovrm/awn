package app.awn.quran.player

/**
 * The bytes [firstByte] to [lastByte] of a file, both counted from the start of that file and both
 * included, as in an HTTP `Range` header. [lastByte] is null when the range runs to the end of the
 * file.
 */
internal data class ByteRange(val firstByte: Long, val lastByte: Long?) {

    /** How many bytes the range holds, or null when it runs to the end of the file. */
    val length: Long?
        get() = lastByte?.let { it - firstByte + 1L }

    /**
     * The `Range` header that asks for the rest of the range after [alreadyHaveBytes] bytes of it
     * were stored by an earlier, interrupted try.
     */
    fun requestHeader(alreadyHaveBytes: Long): String =
        "bytes=${firstByte + alreadyHaveBytes}-${lastByte ?: ""}"

    /** The offset in the whole file at which a response must start after [alreadyHaveBytes]. */
    fun responseStart(alreadyHaveBytes: Long): Long = firstByte + alreadyHaveBytes
}

/**
 * Returns the `Range` header for a download that already holds [resumeFrom] bytes, or null when the
 * whole file is wanted from its start. [clip] is the part of the file to fetch, if not all.
 */
internal fun rangeHeaderFor(clip: ByteRange?, resumeFrom: Long): String? =
    when {
        clip != null -> clip.requestHeader(resumeFrom)
        resumeFrom > 0L -> "bytes=$resumeFrom-"
        else -> null
    }
