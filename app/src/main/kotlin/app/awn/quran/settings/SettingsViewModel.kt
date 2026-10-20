package app.awn.quran.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.services.AwnServices
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The choices of the "Memorised after" picker: the number of days an ayah must be recited. */
val MEMORISED_AFTER_DAYS = 1..10

/** The values the Listening and Learning groups of Settings show. */
data class SettingsState(
    val reciterName: String = "",
    val wifiOnly: Boolean = true,
    val downloadedBytes: Long = 0L,
    val memorisationThreshold: Int = 0,
)

/** How an export or import ended. */
enum class BackupResult {
    Exported,
    Imported,
    Failed,
}

/** Backs the app-level Settings groups; Reading and Reciting have their own models. */
class SettingsViewModel(private val services: AwnServices) : ViewModel() {

    private val reciterNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val downloadedBytes = MutableStateFlow(0L)

    val state: StateFlow<SettingsState> =
        combine(
                services.listenSettings.selectedReciterId,
                services.listenSettings.wifiOnly,
                services.learn.settings.memorisationThreshold,
                reciterNames,
                downloadedBytes,
            ) { reciterId, wifiOnly, threshold, names, bytes ->
                SettingsState(
                    reciterName = names[reciterId].orEmpty(),
                    wifiOnly = wifiOnly,
                    downloadedBytes = bytes,
                    memorisationThreshold = threshold,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                SettingsState(),
            )

    init {
        viewModelScope.launch {
            val reciters = services.coreData.reciters.reciters()
            reciterNames.value = reciters.associate { it.id to it.name }
            downloadedBytes.value = reciters.sumOf { services.audioDownloads.bytesUsed(it.id) }
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { services.listenSettings.setWifiOnly(wifiOnly) }
    }

    fun setMemorisationThreshold(days: Int) {
        require(days in MEMORISED_AFTER_DAYS) {
            "Memorised-after days must be 1 to 10 but was $days."
        }
        viewModelScope.launch { services.learn.settings.setMemorisationThreshold(days) }
    }

    /** Writes the bookmarks, saved ayahs and recent pages to the document at [uri]. */
    suspend fun exportTo(resolver: ContentResolver, uri: Uri): BackupResult =
        try {
            val json = services.coreData.bookmarks.exportJson()
            withContext(Dispatchers.IO) {
                val stream =
                    resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
                stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            BackupResult.Exported
        } catch (_: IOException) {
            BackupResult.Failed
        }

    /** Merges the bookmark file at [uri] into the stored data. */
    suspend fun importFrom(resolver: ContentResolver, uri: Uri): BackupResult =
        try {
            val json =
                withContext(Dispatchers.IO) {
                    val stream =
                        resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            services.coreData.bookmarks.importJson(json)
            BackupResult.Imported
        } catch (_: IOException) {
            BackupResult.Failed
        } catch (_: IllegalArgumentException) {
            // importJson reports a malformed or out-of-range file this way.
            BackupResult.Failed
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
