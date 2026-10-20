package app.awn.quran.feature.read.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.flow.StateFlow

/** Holds the download catalogue for a screen; the work itself is in [CatalogueModel]. */
class CatalogueViewModel(packs: PackRepository, downloads: PackDownloads) : ViewModel() {

    private val catalogue = CatalogueModel(packs, downloads, viewModelScope)

    val state: StateFlow<CatalogueState> = catalogue.state

    fun install(id: String) = catalogue.install(id)

    fun cancel(id: String) = catalogue.cancel(id)

    fun remove(id: String) = catalogue.remove(id)

    /** Bundles the three actions of one item for [CatalogueItemRow]. */
    fun actionsFor(id: String) =
        CatalogueRowActions(
            onInstall = { install(id) },
            onCancel = { cancel(id) },
            onRemove = { remove(id) },
        )
}

/** Returns the catalogue view model of the current screen owner. */
@Composable
fun rememberCatalogueViewModel(dependencies: ReadDependencies): CatalogueViewModel =
    viewModel(
        factory =
            viewModelFactory {
                initializer { CatalogueViewModel(dependencies.packs, dependencies.downloads) }
            },
    )

/**
 * Returns the row to offer when the user turns on a setting whose download is missing, or null when
 * the setting has what it needs. A setting is served when any item of [group] in [language] is on
 * the phone; [language] null matches every language.
 */
internal fun rowToOffer(
    state: CatalogueState,
    group: CatalogueGroup,
    language: String?,
): CatalogueRow? {
    val candidates = state.rowsIn(group).filter { language == null || it.item.language == language }
    return if (candidates.isEmpty() || candidates.any { it.item.isInstalled }) {
        null
    } else {
        candidates.first()
    }
}

/**
 * Returns the rows of translations the user turned on whose download is missing, in the order the
 * user chose them. An id the manifests do not list is skipped: there is nothing to download.
 */
internal fun missingTranslations(
    enabled: List<TranslationId>,
    state: CatalogueState,
): List<CatalogueRow> =
    enabled.mapNotNull { id -> state.rowFor(id.id) }.filter { !it.item.isInstalled }
