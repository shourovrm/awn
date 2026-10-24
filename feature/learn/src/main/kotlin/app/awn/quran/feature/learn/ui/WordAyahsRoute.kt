package app.awn.quran.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.words.WordAyahSearch
import app.awn.quran.feature.learn.words.WordAyahSearchText
import app.awn.quran.feature.learn.words.wordAyahsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ayahs of the word at [LearnRoute.WordAyahs.rank]. The list shows at once; the texts the
 * search looks in (surah names, Arabic, translation) load in the background, about a second for the
 * commonest words, which appear in over 2,000 ayahs.
 */
@Composable
internal fun WordAyahsRoute(
    route: LearnRoute.WordAyahs,
    context: CourseScreenContext,
    modifier: Modifier,
) {
    val frequency = context.runtime.lookups.frequency
    val ayahs = remember(route.rank, frequency) { wordAyahsOf(frequency.occurrences(route.rank)) }
    val environment = LocalLearnEnvironment.current
    val search by
        produceState<WordAyahSearch?>(null, ayahs, environment) {
            val loaded = environment ?: return@produceState
            value =
                withContext(Dispatchers.IO) {
                    WordAyahSearch(ayahs.map { searchTextOf(it.ayah, loaded) })
                }
        }
    WordAyahsScreen(
        word = frequency.representative(route.rank),
        ayahs = ayahs,
        search = search,
        onOpenAyah = LocalLearnPageLinks.current.onOpenAyahInLines,
        onBack = { context.onRouteChange(route.from) },
        modifier = modifier,
    )
}

private suspend fun searchTextOf(ayah: AyahRef, environment: LearnEnvironment): WordAyahSearchText {
    val chapter = environment.chapters.firstOrNull { it.number == ayah.surah }
    val surahNames =
        listOfNotNull(
            chapter?.nameSimple,
            chapter?.nameArabic,
            chapter?.nameBangla,
            chapter?.nameBanglaMeaning,
        )
    val arabic = environment.quranText.wordsOfAyah(ayah).joinToString(" ") { it.textUthmani }
    return WordAyahSearchText(
        ayah,
        surahNames,
        listOfNotNull(arabic, environment.translation(ayah)),
    )
}
