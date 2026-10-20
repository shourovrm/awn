package app.awn.quran.feature.read.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.ReadSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Mushaf settings page: which page type is in use, installing and removing the font of
 * each page type, and the "Show page info" switch. A page type that the user installs from here
 * becomes the one in use as soon as its font is on the phone.
 */
class MushafSettingsViewModel(private val dependencies: ReadDependencies) : ViewModel() {

    val settings: StateFlow<ReadSettings> =
        dependencies.settingsStore.settings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ReadSettings(),
        )

    private val fontPacks =
        FontPackController(dependencies.packs, dependencies.downloads, viewModelScope)

    /** The status of each page type, in the order of [pageTypeScripts]. */
    val statuses: StateFlow<Map<ScriptId, FontPackStatus>> = fontPacks.statuses

    private var scriptToUseWhenInstalled: ScriptId? = null

    init {
        viewModelScope.launch {
            fontPacks.statuses.collect { statuses ->
                val waiting = scriptToUseWhenInstalled ?: return@collect
                if (statuses[waiting]?.isInstalled == true) {
                    scriptToUseWhenInstalled = null
                    use(waiting)
                }
            }
        }
    }

    /** Downloads the font of [script] and makes it the page type in use when it is installed. */
    fun install(script: ScriptId) {
        scriptToUseWhenInstalled = script
        fontPacks.install(script)
    }

    /** Deletes the font of [script]; the page type in use is never removed. */
    fun remove(script: ScriptId) {
        if (settings.value.script == script) return
        fontPacks.remove(script)
    }

    /** Draws pages in [script]; it must be installed, or pages fall back to the Hafs text. */
    fun use(script: ScriptId) {
        viewModelScope.launch { dependencies.settingsStore.update { it.copy(script = script) } }
    }

    fun setShowPageInfo(show: Boolean) {
        viewModelScope.launch { dependencies.settingsStore.update { it.copy(showPageInfo = show) } }
    }
}
