package app.awn.quran.core.data.downloads

/**
 * The user's "Wi-Fi only" choice and the connection it is judged against. One rule serves every
 * download: Mushaf, translation and word packs, the recitation checker and the reciter audio.
 */
interface DownloadNetworkRule {

    /** True while the user limits downloads to Wi-Fi (Settings, Audio). */
    fun isWifiOnly(): Boolean

    /** True on mobile data or a hotspot, where a download costs the user money. */
    fun isMetered(): Boolean

    /** True when a download may start on the current connection. */
    fun mayDownload(): Boolean = !isWifiOnly() || !isMetered()

    /** No limit: every connection may download. For tests and code built without settings. */
    object Unrestricted : DownloadNetworkRule {
        override fun isWifiOnly(): Boolean = false

        override fun isMetered(): Boolean = false
    }
}
