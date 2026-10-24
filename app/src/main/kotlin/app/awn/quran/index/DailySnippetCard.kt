package app.awn.quran.index

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.awn.quran.core.data.Chapter
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.SnippetPicker
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.content.SnippetShown
import app.awn.quran.feature.learn.content.snippetReference
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** The snippet on the Quran tab, how to write its reference, and what a tap on the card does. */
internal class DailySnippetCard(
    val snippet: Snippet,
    val language: LearnLanguage,
    val referenceOf: (Snippet) -> String,
    val onShowAnother: () -> Unit,
)

/**
 * The day's snippet first; each tap on the card shows another one until all [snippets] have been
 * seen in this session. Null when there are no snippets.
 */
@Composable
internal fun rememberDailySnippetCard(
    snippets: List<Snippet>,
    chapters: List<Chapter>,
    language: LearnLanguage,
): DailySnippetCard? {
    var shown by
        remember(snippets) {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val dayIndex =
                if (snippets.isEmpty()) 0 else SnippetRepository.indexForDay(snippets.size, today)
            mutableStateOf(SnippetShown.startingAt(dayIndex))
        }
    if (snippets.isEmpty()) return null
    return DailySnippetCard(
        snippet = snippets[shown.currentIndex],
        language = language,
        referenceOf = { snippet ->
            snippetReference(snippet.source, language) { surah -> banglaSurahName(chapters, surah) }
        },
        onShowAnother = { shown = SnippetPicker.next(shown, snippets.size, Random.Default) },
    )
}

/**
 * The Bangla name of a surah for a reference line: the pack's Bangla name when it has one, else the
 * Latin name, the same fallback Learn uses.
 */
internal fun banglaSurahName(chapters: List<Chapter>, surah: Int): String {
    val chapter = chapters.firstOrNull { it.number == surah } ?: return surah.toString()
    return chapter.nameBangla?.takeIf { it.isNotBlank() } ?: chapter.nameSimple
}
