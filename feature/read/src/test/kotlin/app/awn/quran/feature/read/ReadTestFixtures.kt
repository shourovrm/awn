package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.data.TranslationText
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.fakes.FakeBookmarkRepository
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeProgressEvents
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import app.awn.quran.core.data.fakes.ImmediatePackWorkScheduler
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.feature.read.settings.ReadSettingsStore
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Makes viewModelScope run immediately in tests. */
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) =
        Dispatchers.setMain(UnconfinedTestDispatcher())

    override fun finished(description: Description) = Dispatchers.resetMain()
}

class InMemorySettingsStore(initial: ReadSettings = ReadSettings()) : ReadSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<ReadSettings> = state

    override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
        state.value = transform(state.value)
    }
}

/** Page 1 (Al-Fatihah header, bismillah, ayah 1) and page 2 (Al-Baqarah) behind the fakes. */
class ReadFixture(settings: ReadSettings = ReadSettings()) {
    val text = FakeQuranTextRepository()
    val translations = FakeTranslationRepository()
    val bookmarks = FakeBookmarkRepository()
    val packs = FakePackRepository()
    val progress = FakeProgressEvents()
    val player = FakeAudioPlayer()
    val store = InMemorySettingsStore(settings)

    val dependencies =
        ReadDependencies(
            text,
            translations,
            bookmarks,
            packs,
            progress,
            player,
            store,
            PackDownloads(InMemoryWantedPackStore(), ImmediatePackWorkScheduler(packs)),
        )

    init {
        text.chapterList = listOf(fatiha, baqarah)
        text.pages[1] = pageOne()
        text.pages[2] = pageTwo()
        (1..FATIHA_VERSES).forEach { text.pageByAyah[AyahRef(1, it)] = 1 }
        text.pageByAyah[AyahRef(2, 1)] = 2
        val saheeh = TranslationId("en_saheeh")
        translations.infos += TranslationInfo(saheeh, "Saheeh International", "en", true)
        translations.texts[saheeh] =
            mutableMapOf(
                AyahRef(1, 1) to TranslationText(AyahRef(1, 1), 1, 1, "In the name of Allah"),
            )
        translations.wordMeanings["en" to AyahRef(1, 1)] = listOf("In (the) name", "(of) Allah")
        text.wordsByAyah[AyahRef(1, 1)] =
            listOf(word(1, 1, 1, FIRST_WORD), word(1, 1, 2, SECOND_WORD))
    }

    companion object {
        const val FATIHA_VERSES = 7
        const val FIRST_WORD = "بِسْمِ"

        /** [FIRST_WORD] as the Hafs font draws it: sukun is U+06E1 in that font's text. */
        const val FIRST_WORD_DRAWN = "بِسۡمِ"
        const val SECOND_WORD = "ٱللَّهِ"
        val fatiha =
            Chapter(1, "الفاتحة", "Al-Fatihah", "আল-ফাতিহা", FATIHA_VERSES, "makkah", false)
        val baqarah = Chapter(2, "البقرة", "Al-Baqarah", null, 286, "madinah", true)

        fun word(surah: Int, ayah: Int, position: Int, text: String) =
            Word(WordKey(surah, ayah, position), text, null, null, "t$position")

        fun pageOne() =
            PageLayout(
                1,
                ScriptId.QpcHafs,
                listOf(
                    PageLine(1, listOf(LineItem.SurahHeader(1))),
                    PageLine(2, listOf(LineItem.Basmala(1))),
                    PageLine(
                        3,
                        listOf(
                            LineItem.WordItem(word(1, 1, 1, FIRST_WORD)),
                            LineItem.WordItem(word(1, 1, 2, SECOND_WORD)),
                            LineItem.AyahEnd(AyahRef(1, 1)),
                        ),
                    ),
                ),
            )

        fun pageTwo() =
            PageLayout(
                2,
                ScriptId.QpcHafs,
                listOf(PageLine(1, listOf(LineItem.WordItem(word(2, 1, 1, "الم"))))),
            )
    }
}
