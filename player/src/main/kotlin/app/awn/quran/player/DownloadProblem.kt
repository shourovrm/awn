package app.awn.quran.player

import java.io.IOException

/** Why an audio download did not happen, with the numbers the user needs to act on it. */
sealed interface DownloadProblem {

    /** The files need [neededBytes] and the phone has [freeBytes] free. */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : DownloadProblem

    /** The phone has no connection. */
    data object NoInternet : DownloadProblem

    /** The phone is online but the audio server did not answer or refused. */
    data object ServerNotReachable : DownloadProblem

    /** Downloads are limited to Wi-Fi and the phone is on mobile data. */
    data object WifiOnly : DownloadProblem

    /** A plain English line for logs and tests; screens use their own translated strings. */
    fun englishMessage(): String =
        when (this) {
            is NotEnoughSpace ->
                "Needs ${megabytes(neededBytes)} MB, ${megabytes(freeBytes)} MB free"
            NoInternet -> "No internet"
            ServerNotReachable -> "Server not reachable"
            WifiOnly -> "Connect to Wi-Fi to download"
        }

    /** Flat key-value form, so a work request can carry the problem to the code that waits. */
    fun toFields(): Map<String, Long> =
        when (this) {
            is NotEnoughSpace ->
                mapOf(
                    KIND_FIELD to KIND_SPACE,
                    NEEDED_FIELD to neededBytes,
                    FREE_FIELD to freeBytes,
                )
            NoInternet -> mapOf(KIND_FIELD to KIND_INTERNET)
            ServerNotReachable -> mapOf(KIND_FIELD to KIND_SERVER)
            WifiOnly -> mapOf(KIND_FIELD to KIND_WIFI)
        }

    companion object {
        const val KIND_FIELD = "problem_kind"
        const val NEEDED_FIELD = "problem_needed_bytes"
        const val FREE_FIELD = "problem_free_bytes"
        private const val KIND_SPACE = 1L
        private const val KIND_INTERNET = 2L
        private const val KIND_SERVER = 3L
        private const val KIND_WIFI = 4L
        private const val BYTES_PER_MEGABYTE = 1_000_000L

        /** Whole megabytes, rounded up so "needs" never understates. */
        fun megabytes(bytes: Long): Long = (bytes + BYTES_PER_MEGABYTE - 1) / BYTES_PER_MEGABYTE

        fun fromFields(fields: Map<String, Long>): DownloadProblem? =
            when (fields[KIND_FIELD]) {
                KIND_SPACE -> NotEnoughSpace(fields[NEEDED_FIELD] ?: 0L, fields[FREE_FIELD] ?: 0L)
                KIND_INTERNET -> NoInternet
                KIND_SERVER -> ServerNotReachable
                KIND_WIFI -> WifiOnly
                else -> null
            }

        /**
         * Names the cause of [error]: the typed causes first, then no connection when the phone is
         * offline, otherwise the server (or the route to it).
         */
        fun classify(error: Throwable, online: Boolean): DownloadProblem =
            when {
                error is DownloadFailedException -> error.problem
                error is InsufficientSpaceException ->
                    NotEnoughSpace(error.neededBytes, error.freeBytes)
                error is DownloadNotAllowedException -> WifiOnly
                !online -> NoInternet
                else -> ServerNotReachable
            }
    }
}

/** Thrown before a download starts when the phone cannot hold the files. */
class InsufficientSpaceException(val neededBytes: Long, val freeBytes: Long) :
    IOException("Needs $neededBytes bytes, $freeBytes free")

/** A download that failed for a named [problem], possibly in another process or work request. */
class DownloadFailedException(val problem: DownloadProblem) : IOException(problem.englishMessage())
