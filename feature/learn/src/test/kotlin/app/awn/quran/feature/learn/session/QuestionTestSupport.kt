package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.words.WordFrequency

/** Forms 1..[formCount], each with a made-up meaning "meaning N", for the question tests. */
class FakeFrequency(override val formCount: Int = 200) : WordFrequency {
    override val wordCount: Int = formCount * 3

    override fun occurrenceCount(rank: Int): Int = 3

    override fun rankOf(key: WordKey): Int = key.wordPosition

    override fun representative(rank: Int): WordKey = WordKey(2, rank, 1)

    override fun occurrences(rank: Int): List<WordKey> = listOf(representative(rank))
}

/** Meanings come from [meanings] by rank; a rank without an entry has no meaning installed. */
class FakeInfoSource(
    private val meanings: Map<Int, String> = (1..200).associateWith { "meaning $it" },
    private val letterCounts: (Int) -> Int = { 4 },
    private val ayahWordCounts: (Int) -> Int = { 10 },
) : WordInfoSource {
    override suspend fun info(rank: Int): WordInfo? {
        val meaning = meanings[rank] ?: return null
        return WordInfo(
            rank = rank,
            representative = WordKey(2, rank, 1),
            meaning = meaning,
            letterCount = letterCounts(rank),
            ayahWordCount = ayahWordCounts(rank),
        )
    }
}
