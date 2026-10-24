package app.awn.quran.feature.read.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.quranfont.indoPakScripts
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.ReadSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Mushaf chooser: which page type is in use, and installing and removing the font of
 * each page type. A page type that the user installs from here becomes the one in use as soon as
 * its font is on the phone.
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

    /** Stops the running or queued download of the font of [script]. */
    fun cancel(script: ScriptId) = fontPacks.cancel(script)

    /** Deletes the font of [script]; the page type in use is never removed. */
    fun remove(script: ScriptId) {
        if (settings.value.script == script) return
        fontPacks.remove(script)
    }

    /**
     * Draws pages in [script]. A Madani print that is not installed draws the Hafs text until it
     * is. An IndoPak print cannot draw without its downloaded layouts, so it downloads first and
     * becomes the page type when it is installed.
     */
    fun use(script: ScriptId) {
        if (script in indoPakScripts && statuses.value[script]?.isInstalled != true) {
            install(script)
            return
        }
        viewModelScope.launch { dependencies.settingsStore.update { it.copy(script = script) } }
    }
}
