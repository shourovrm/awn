package app.awn.quran.feature.learn.course

import kotlin.random.Random

/** An Arabic fragment in lesson markup (`[[بَ]]`) with the recorded sound that reads it. */
data class SoundMaterial(val arabic: String, val clip: String) {
    val clipIds: List<String>
        get() = clip.split("+")
}

/**
 * The question "which one did you hear": a sound plays, four Arabic fragments are offered and one
 * is right. The wrong ones come from the same unit and are chosen among letters that look or sound
 * alike, the slips of a beginner.
 */
object ListenPick {

    /** The pick of "I don't know": it matches no option, so the answer is shown and not counted. */
    const val NO_ANSWER = -1

    private const val OPTION_COUNT = 4

    /** Groups of letters that are mistaken for each other by shape or by sound. */
    private val LOOK_ALIKE_GROUPS =
        listOf("بتثن", "حخج", "دذ", "رز", "سش", "صض", "طظ", "عغ").map { it.toSet() }

    /** Every fragment of [text] that has a sound: its read-aloud items and its grid cells. */
    fun materialOf(text: LessonText): List<SoundMaterial> {
        val fromGrids =
            text.screens.flatMap { it.cells }.map { SoundMaterial(asMarkup(it.arabic), it.clip) }
        val fromReading =
            text.practiceItems.filterIsInstance<PracticeItem.ReadAloud>().map {
                SoundMaterial(it.read, it.clip)
            }
        return (fromReading + fromGrids).filter { it.clip.isNotBlank() }
    }

    /**
     * The practice pool of [text]: its own items and one listen question for each read-aloud item
     * when the unit has enough material. The same [seed] gives the same questions.
     */
    fun poolWithQuestions(text: LessonText, seed: Long): List<PracticeItem> {
        val material = materialOf(text)
        val questions =
            text.practiceItems.filterIsInstance<PracticeItem.ReadAloud>().mapIndexedNotNull {
                index,
                item ->
                question(SoundMaterial(item.read, item.clip), material, Random(seed + index))
            }
        return text.practiceItems + questions
    }

    /** The question for [correct], or null when [material] holds too few other fragments. */
    fun question(
        correct: SoundMaterial,
        material: List<SoundMaterial>,
        random: Random,
    ): PracticeItem.ListenPick? {
        val wrong = distractors(correct, material, OPTION_COUNT - 1, random)
        if (wrong.size < OPTION_COUNT - 1) return null
        val options = (wrong + correct).shuffled(random)
        return PracticeItem.ListenPick(
            LessonCheck(
                question = "",
                ayah = null,
                highlight = null,
                options = options.map { it.arabic },
                correctIndex = options.indexOf(correct),
                clip = correct.clip,
            ),
        )
    }

    /**
     * [count] fragments of [material] to offer beside [correct]: never its Arabic again, never a
     * fragment without a sound, each Arabic once, the ones that share a look-alike group with
     * [correct] first and the rest after them. Fewer than [count] when the unit has no more.
     */
    fun distractors(
        correct: SoundMaterial,
        material: List<SoundMaterial>,
        count: Int,
        random: Random,
    ): List<SoundMaterial> {
        val candidates =
            material
                .filter {
                    it.clip.isNotBlank() && it.arabic != correct.arabic && it.clip != correct.clip
                }
                .distinctBy { it.arabic }
        return candidates
            .shuffled(random)
            .sortedByDescending { sharedGroupCount(correct.arabic, it.arabic) }
            .take(count)
    }

    private fun sharedGroupCount(first: String, second: String): Int {
        val firstLetters = first.toSet()
        val secondLetters = second.toSet()
        return LOOK_ALIKE_GROUPS.count { group ->
            group.any { it in firstLetters } && group.any { it in secondLetters }
        }
    }

    private fun asMarkup(arabic: String): String =
        if (arabic.startsWith("[[")) arabic else "[[$arabic]]"
}
