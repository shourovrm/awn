package app.awn.quran.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** Decides whether the current connection may be used for a download. */
fun interface DownloadNetworkPolicy {

    suspend fun mayDownload(): Boolean

    companion object {
        val Always = DownloadNetworkPolicy { true }
    }
}

/** Thrown by a download that the [DownloadNetworkPolicy] refused. */
class DownloadNotAllowedException : IOException("Downloads are limited to unmetered networks")

/**
 * Refuses downloads on a metered connection (mobile data, hotspot) while [wifiOnly] returns true.
 * Needs the `ACCESS_NETWORK_STATE` permission, which this module's manifest declares.
 */
class UnmeteredOnlyPolicy(
    private val context: Context,
    private val wifiOnly: suspend () -> Boolean,
) : DownloadNetworkPolicy {

    override suspend fun mayDownload(): Boolean {
        if (!wifiOnly()) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }
}
