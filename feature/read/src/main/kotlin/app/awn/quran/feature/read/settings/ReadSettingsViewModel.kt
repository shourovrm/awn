package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the stored [ReadSettings] and applies the user's changes to them. */
class ReadSettingsViewModel(
    private val store: ReadSettingsStore,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    val settings: StateFlow<ReadSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReadSettings())

    private val availableTranslationState = MutableStateFlow<List<TranslationInfo>>(emptyList())

    /** Every translation in the manifest, so the user can enable and order them. */
    val availableTranslations: StateFlow<List<TranslationInfo>> = availableTranslationState

    init {
        viewModelScope.launch {
            availableTranslationState.value = translationRepository.available()
        }
    }

    fun setScript(script: ScriptId) = change { it.copy(script = script) }

    fun setPageView(pageView: PageView) = change { it.copy(pageView = pageView) }

    fun setTextSize(textSize: TextSize) = change { it.copy(textSize = textSize) }

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
                initializer {
                    ReadSettingsViewModel(dependencies.settingsStore, dependencies.translations)
                }
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
