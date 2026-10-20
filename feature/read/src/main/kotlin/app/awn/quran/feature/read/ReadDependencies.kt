package app.awn.quran.feature.read

import app.awn.quran.core.data.BookmarkRepository
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.feature.read.settings.ReadSettingsStore
import app.awn.quran.player.AudioPlayer

/** Everything the Read screens need from the rest of the app, handed over once by :app. */
data class ReadDependencies(
    val quranText: QuranTextRepository,
    val translations: TranslationRepository,
    val bookmarks: BookmarkRepository,
    val packs: PackRepository,
    val progressEvents: ProgressEvents,
    val audioPlayer: AudioPlayer,
    val settingsStore: ReadSettingsStore,
)
