package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsCard
import java.time.LocalDate

/** How well a page is known. [value] is stored in the database, so it never changes. */
enum class PageStatus(val value: Int) {
    NotStarted(0),
    Learning(1),
    Memorised(2),
    Solid(3);

    val isKnown: Boolean
        get() = this == Memorised || this == Solid

    companion object {
        fun fromValue(value: Int): PageStatus =
            entries.firstOrNull { it.value == value }
                ?: error("Unknown page status $value in the hifz database.")
    }
}

/** The five ways to memorise. [storedName] is saved in the database, so it never changes. */
enum class HifzMethodId(val storedName: String) {
    SabaqSabqiManzil("sabaq_sabqi_manzil"),
    ThreeByThree("three_by_three"),
    AyahChain("ayah_chain"),
    FixedAyahs("fixed_ayahs"),
    TrackOnly("track_only");

    companion object {
        fun fromStoredName(name: String): HifzMethodId =
            entries.firstOrNull { it.storedName == name }
                ?: error("Unknown hifz method '$name' in the hifz database.")
    }
}

/** The size of one new lesson. */
sealed interface LessonSize {
    data object OnePage : LessonSize

    data object HalfPage : LessonSize

    data class Ayahs(val count: Int) : LessonSize {
        init {
            require(count >= 1) { "A lesson needs at least one ayah, got $count." }
        }
    }
}

/** The settings of the chosen method. Each method reads only the ones that apply to it. */
data class MethodSettings(
    val lessonSize: LessonSize = LessonSize.OnePage,
    /** Sabaq, Sabqi, Manzil: every older page comes back once in this many days. */
    val manzilCycleDays: Int = DEFAULT_MANZIL_CYCLE_DAYS,
    /** Sabaq, Sabqi, Manzil: lessons younger than this many days are the recent lessons. */
    val recentDays: Int = DEFAULT_RECENT_DAYS,
    /** Methods with a fixed new lesson of ayahs. */
    val ayahsPerDay: Int = DEFAULT_AYAHS_PER_DAY,
    /** The most pages of revision one day asks for, so a missed day never piles up. */
    val revisionPageLimit: Int = DEFAULT_REVISION_PAGE_LIMIT,
) {
    companion object {
        const val DEFAULT_MANZIL_CYCLE_DAYS = 7
        const val DEFAULT_RECENT_DAYS = 7
        const val DEFAULT_AYAHS_PER_DAY = 5
        const val DEFAULT_REVISION_PAGE_LIMIT = 10
        val manzilCycleChoices = listOf(7, 15, 30)
        val revisionLimitChoices = listOf(5, 10, 15, 20)
    }
}

/**
 * The user's plan. [cursor] is the first ayah of the next new lesson; null starts from the first
 * page that is not known yet.
 */
data class HifzPlan(
    val method: HifzMethodId,
    val settings: MethodSettings,
    val cursor: AyahRef?,
    val printLayout: PageLayoutFamily,
    val startedOn: LocalDate,
)

/**
 * What the user knows of one page. [card] is the memory state used to time revision; it is null
 * until the page has had a graded review.
 */
data class PageRecord(
    val page: Int,
    val status: PageStatus,
    val learnedOn: LocalDate? = null,
    val lastRevisedOn: LocalDate? = null,
    val card: FsrsCard? = null,
)

/** How the user rates a part, from worst to best. */
enum class PartGrade {
    Forgot,
    Hard,
    Good,
    Easy,
}

/** What a finished part of a day was. */
enum class PartKind {
    NewLesson,
    Recent,
    Old,
}

/** One finished part of a day, read back from the log. */
data class FinishedPart(
    val day: LocalDate,
    val kind: PartKind,
    val pages: List<Int>,
    val firstAyah: AyahRef?,
    val lastAyah: AyahRef?,
    val grade: PartGrade,
)
