package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the stored [ReadSettings] and applies the user's changes to them. */
@Suppress("TooManyFunctions") // One setter per setting.
class ReadSettingsViewModel(private val store: ReadSettingsStore) : ViewModel() {

    val settings: StateFlow<ReadSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReadSettings())

    fun setScript(script: ScriptId) = change { it.copy(script = script) }

    fun setPageView(pageView: PageView) = change { it.copy(pageView = pageView) }

    fun setArabicSizeStep(step: Int) = change {
        it.copy(arabicSizeStep = TextScale.clampStep(step))
    }

    fun setTranslationSizeStep(step: Int) = change {
        it.copy(translationSizeStep = TextScale.clampStep(step))
    }

    /** Moves the Arabic and the translation size together by [delta] steps. */
    fun changeTextSize(delta: Int) = change {
        it.withStepMoved(TextLayer.Arabic, delta).withStepMoved(TextLayer.Translation, delta)
    }

    fun setQuranOrientation(orientation: ScreenOrientation) = change {
        it.copy(quranOrientation = orientation)
    }

    fun setAppOrientation(orientation: ScreenOrientation) = change {
        it.copy(appOrientation = orientation)
    }

    fun setHighlightBookmarks(enabled: Boolean) = change { it.copy(highlightBookmarks = enabled) }

    fun setVolumeKeysTurnPages(enabled: Boolean) = change { it.copy(volumeKeysTurnPages = enabled) }

    fun setShowTransliteration(show: Boolean) = change { it.copy(showTransliteration = show) }

    fun setShowWordByWord(show: Boolean) = change { it.copy(showWordByWord = show) }

    fun setTranslationEnabled(id: TranslationId, enabled: Boolean) = change {
        it.copy(translations = it.translations.withTranslation(id, enabled))
    }

    fun moveTranslationUp(id: TranslationId) = change {
        it.copy(translations = it.translations.withTranslationMovedUp(id))
    }

    private fun change(transform: (ReadSettings) -> ReadSettings) {
        viewModelScope.launch { store.update(transform) }
    }
}

/** Returns the one settings view model of the current screen owner. */
@Composable
fun rememberReadSettingsViewModel(dependencies: ReadDependencies): ReadSettingsViewModel =
    viewModel(
        factory =
            viewModelFactory {
                initializer { ReadSettingsViewModel(dependencies.settingsStore) }
            },
    )

/** Adds [id] at the end of the order, or removes it; the order of the others is kept. */
internal fun List<TranslationId>.withTranslation(
    id: TranslationId,
    enabled: Boolean,
): List<TranslationId> =
    when {
        enabled && id !in this -> this + id
        !enabled -> this - id
        else -> this
    }

/** Swaps [id] with the translation before it; the first one stays where it is. */
internal fun List<TranslationId>.withTranslationMovedUp(id: TranslationId): List<TranslationId> {
    val index = indexOf(id)
    if (index <= 0) return this
    val reordered = toMutableList()
    reordered[index] = this[index - 1]
    reordered[index - 1] = id
    return reordered
}
