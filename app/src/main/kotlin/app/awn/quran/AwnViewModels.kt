package app.awn.quran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.awn.quran.feature.listen.DownloadsViewModel
import app.awn.quran.feature.listen.ListenViewModel
import app.awn.quran.feature.listen.RecitersViewModel
import app.awn.quran.feature.recite.ReciteRange
import app.awn.quran.index.INDEX_SCRIPT
import app.awn.quran.index.IndexLoader
import app.awn.quran.index.IndexViewModel
import app.awn.quran.index.JuzStartQuarterTable
import app.awn.quran.search.SearchViewModel
import app.awn.quran.services.AwnServices
import app.awn.quran.settings.SettingsViewModel

/** Holds the recitation session and the chosen range so both survive a rotation. */
class ReciteViewModel(services: AwnServices) : ViewModel() {

    val session = services.createRecitationSession(viewModelScope)

    var range by mutableStateOf(ReciteRange(surah = 1, firstAyah = 1, lastAyah = 7))

    override fun onCleared() {
        // The microphone must be released when the screen is gone for good.
        session.stop()
    }
}

/**
 * Builds every view model of the app from [AwnServices]; there is no dependency-injection library.
 */
class AwnViewModelFactory(private val services: AwnServices) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel =
            when (modelClass) {
                ListenViewModel::class.java ->
                    ListenViewModel(
                        player = services.audioPlayer,
                        reciters = services.coreData.reciters,
                        quranText = services.coreData.quranText,
                        settings = services.listenSettings,
                    )
                RecitersViewModel::class.java ->
                    RecitersViewModel(
                        reciters = services.coreData.reciters,
                        downloads = services.audioDownloads,
                        settings = services.listenSettings,
                    )
                DownloadsViewModel::class.java ->
                    DownloadsViewModel(
                        downloads = services.audioDownloads,
                        quranText = services.coreData.quranText,
                        settings = services.listenSettings,
                        downloadScope = services.applicationScope,
                    )
                ReciteViewModel::class.java -> ReciteViewModel(services)
                IndexViewModel::class.java ->
                    IndexViewModel(
                        // The quarter table from core/data replaces JuzStartQuarterTable here.
                        loader = IndexLoader(services.coreData.quranText, JuzStartQuarterTable),
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
