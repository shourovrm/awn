package app.awn.quran.feature.read.page

/** What the buttons under one print of the Prints screen offer, decided from the print's state. */
internal sealed interface PrintControls {

    /**
     * The print is not on the phone; [downloadBytes] is its size, null while the manifest loads.
     */
    data class Download(val downloadBytes: Long?) : PrintControls

    /** The print is downloading; [progress] runs from 0 to 1. */
    data class Downloading(val progress: Float) : PrintControls

    /** The print is on the phone and not in use: it can be deleted or put to use. */
    data object DeleteOrUse : PrintControls

    /** The print is in use: it is marked and cannot be deleted. */
    data object InUse : PrintControls
}

/**
 * Returns the controls for the print with [status]. A print that is in use but whose font has not
 * arrived yet (the reader draws the Hafs text meanwhile) still offers the download, because the
 * user cannot see that print until it is on the phone.
 */
internal fun printControlsOf(status: FontPackStatus, isInUse: Boolean): PrintControls {
    val progress = status.progress
    return when {
        progress != null && !status.isInstalled -> PrintControls.Downloading(progress)
        !status.isInstalled -> PrintControls.Download(status.downloadBytes)
        isInUse -> PrintControls.InUse
        else -> PrintControls.DeleteOrUse
    }
}
