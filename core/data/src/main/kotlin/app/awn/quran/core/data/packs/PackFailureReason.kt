package app.awn.quran.core.data.packs

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why an install failed, in the terms the user can act on. */
enum class PackFailureReason {
    /** The phone could not reach the server, or the connection dropped. */
    NoInternet,

    /** The server answered with an error status. */
    ServerError,

    /** The phone has less free space than the download needs. */
    NoSpace,

    /** The downloaded file did not match its recorded size or checksum and was discarded. */
    Damaged,

    /** Downloads are limited to Wi-Fi and the phone is on mobile data or a hotspot. */
    WifiOnly,
    Other;

    companion object {
        private const val NO_SPACE_MESSAGE = "No space left on device"

        /** Maps a low-level [error] to the reason the user sees. */
        fun of(error: IOException): PackFailureReason =
            when {
                error is UnknownHostException ||
                    error is ConnectException ||
                    error is NoRouteToHostException ||
                    error is SocketTimeoutException -> NoInternet
                error.message?.contains(NO_SPACE_MESSAGE, ignoreCase = true) == true -> NoSpace
                // A reset or broken pipe while reading the body is a dropped connection.
                error is SocketException -> NoInternet
                else -> Other
            }
    }
}
