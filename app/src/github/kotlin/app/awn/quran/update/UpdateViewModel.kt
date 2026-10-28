package app.awn.quran.update

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.player.DownloadNetworkPolicy
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

private const val TAG = "UpdateViewModel"

private val NOT_FOUND = UpdateState.Failed(UpdateFailure.NotFound, failedDownload = null)

/**
 * Checks GitHub for a newer release of the app and downloads it. The check is a small request and
 * always runs; only the APK download follows the Wi-Fi only setting through [networkPolicy].
 */
internal class UpdateViewModel(
    private val releaseClient: ReleaseClient,
    private val apkDownloader: ApkDownloader,
    private val installedVersionName: String,
    private val supportedAbis: List<String>,
    private val networkPolicy: DownloadNetworkPolicy,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState

    private var runningJob: Job? = null

    fun check() {
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            mutableState.value = UpdateState.Checking
            mutableState.value = findUpdate()
        }
    }

    fun download(available: UpdateState.Available) {
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            mutableState.value = downloadAndVerify(available)
        }
    }

    /** Stops the download and goes back to the offer. */
    fun cancelDownload() {
        val downloading = mutableState.value as? UpdateState.Downloading ?: return
        runningJob?.cancel()
        mutableState.value = downloading.available
    }

    /** Repeats the step that failed. */
    fun retry() {
        val failed = mutableState.value as? UpdateState.Failed ?: return
        val failedDownload = failed.failedDownload
        if (failedDownload == null) check() else download(failedDownload)
    }

    private suspend fun findUpdate(): UpdateState =
        try {
            updateStateFor(newestAppRelease(releaseClient.listReleases()))
        } catch (failure: HttpStatusException) {
            Log.w(TAG, "GitHub refused the release list", failure)
            NOT_FOUND
        } catch (failure: IOException) {
            Log.w(TAG, "Could not reach GitHub for the release list", failure)
            UpdateState.Failed(UpdateFailure.NoConnection, failedDownload = null)
        } catch (failure: JSONException) {
            Log.w(TAG, "GitHub answered with a release list that cannot be read", failure)
            UpdateState.Failed(UpdateFailure.NotFound, failedDownload = null)
        }

    private fun updateStateFor(newest: AppRelease?): UpdateState =
        when {
            newest == null -> NOT_FOUND
            !isNewerRelease(newest.tagName, installedVersionName) -> UpdateState.UpToDate
            else -> offerFor(newest)
        }

    private fun offerFor(release: AppRelease): UpdateState {
        val apk = pickApkAsset(release.assets, supportedAbis) ?: return NOT_FOUND
        return UpdateState.Available(releaseVersionName(release.tagName), release, apk)
    }

    private suspend fun downloadAndVerify(available: UpdateState.Available): UpdateState {
        if (!networkPolicy.mayDownload()) {
            return UpdateState.Failed(UpdateFailure.WifiRequired, available)
        }
        val downloadJob = currentCoroutineContext()[Job]
        mutableState.value = UpdateState.Downloading(available, percent = 0)
        return try {
            val apkFile =
                apkDownloader.download(
                    release = available.release,
                    apk = available.apk,
                    versionName = available.versionName,
                    onPercent = { percent ->
                        // A cancelled download may still report once; the update keeps a
                        // cancelled row from turning back into Downloading.
                        mutableState.update { current ->
                            if (
                                downloadJob?.isActive == true && current is UpdateState.Downloading
                            ) {
                                UpdateState.Downloading(available, percent)
                            } else {
                                current
                            }
                        }
                    },
                )
            UpdateState.Ready(available.versionName, apkFile)
        } catch (failure: IOException) {
            Log.w(TAG, "Could not download ${available.apk.name}", failure)
            UpdateState.Failed(UpdateFailure.DownloadFailed, available)
        }
    }
}
