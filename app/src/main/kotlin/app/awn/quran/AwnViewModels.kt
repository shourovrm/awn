package app.awn.quran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.awn.quran.index.CoreQuarterTable
import app.awn.quran.index.INDEX_SCRIPT
import app.awn.quran.index.IndexLoader
import app.awn.quran.index.IndexViewModel
import app.awn.quran.search.SearchViewModel
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.SettingsViewModel

/**
 * Builds every view model of the app from [AwnServices]; there is no dependency-injection library.
 */
class AwnViewModelFactory(private val services: AwnServices) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel =
            when (modelClass) {
                IndexViewModel::class.java ->
                    IndexViewModel(
                        loader = IndexLoader(services.coreData.quranText, CoreQuarterTable),
                        bookmarks = services.coreData.bookmarks,
                        showSurahNameMeaning = services.preferences.showSurahNameMeaning,
                    )
                SearchViewModel::class.java ->
                    SearchViewModel(
                        search = services.coreData.search,
                        quranText = services.coreData.quranText,
                        script = INDEX_SCRIPT,
                    )
                SettingsViewModel::class.java -> SettingsViewModel(services)
                else -> throw IllegalArgumentException("No view model for ${modelClass.name}.")
            }
        return viewModel as T
    }
}
