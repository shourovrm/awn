package app.awn.quran.update

import java.io.File

/** Why the update row shows an error. */
internal enum class UpdateFailure {
    NoConnection,
    WifiRequired,
    NotFound,
    DownloadFailed,
}

/** What the update row shows; each state is one row. */
internal sealed interface UpdateState {

    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val versionName: String, val release: AppRelease, val apk: ReleaseAsset) :
        UpdateState

    data class Downloading(val available: Available, val percent: Int) : UpdateState

    data class Ready(val versionName: String, val apkFile: File) : UpdateState

    /** Retry downloads [failedDownload] again, or checks again when it is null. */
    data class Failed(val reason: UpdateFailure, val failedDownload: Available?) : UpdateState
}
