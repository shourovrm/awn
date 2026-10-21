package app.awn.quran.feature.hifz.data

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.fsrs.FsrsCard
import app.awn.quran.core.data.fsrs.FsrsState
import app.awn.quran.feature.hifz.method.FinishedPart
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.HifzPlan
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.MethodSettings
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PageLayoutFamily
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import java.time.LocalDate

private const val LESSON_PAGE = "page"
private const val LESSON_HALF_PAGE = "half_page"
private const val LESSON_AYAHS = "ayahs"

internal fun PlanEntity.toPlan(): HifzPlan {
    val size =
        when (lessonKind) {
            LESSON_PAGE -> LessonSize.OnePage
            LESSON_HALF_PAGE -> LessonSize.HalfPage
            LESSON_AYAHS -> LessonSize.Ayahs(lessonAyahs)
            else -> error("Unknown lesson size '$lessonKind' in the hifz database.")
        }
    val cursorSurahValue = cursorSurah
    val cursorAyahValue = cursorAyah
    val cursor =
        if (cursorSurahValue != null && cursorAyahValue != null) {
            AyahRef(cursorSurahValue, cursorAyahValue)
        } else {
            null
        }
    return HifzPlan(
        method = HifzMethodId.fromStoredName(method),
        settings =
            MethodSettings(
                lessonSize = size,
                manzilCycleDays = manzilCycleDays,
                recentDays = recentDays,
                ayahsPerDay = ayahsPerDay,
                revisionPageLimit = revisionPageLimit,
            ),
        cursor = cursor,
        printLayout = PageLayoutFamily.fromStoredName(printLayout),
        startedOn = LocalDate.ofEpochDay(startedOn),
    )
}

internal fun HifzPlan.toEntity(): PlanEntity {
    val (kind, ayahs) =
        when (val size = settings.lessonSize) {
            LessonSize.OnePage -> LESSON_PAGE to 0
            LessonSize.HalfPage -> LESSON_HALF_PAGE to 0
            is LessonSize.Ayahs -> LESSON_AYAHS to size.count
        }
    return PlanEntity(
        method = method.storedName,
        lessonKind = kind,
        lessonAyahs = ayahs,
        manzilCycleDays = settings.manzilCycleDays,
        recentDays = settings.recentDays,
        ayahsPerDay = settings.ayahsPerDay,
        revisionPageLimit = settings.revisionPageLimit,
        cursorSurah = cursor?.surah,
        cursorAyah = cursor?.ayah,
        printLayout = printLayout.storedName,
        startedOn = startedOn.toEpochDay(),
    )
}

internal fun PageProgressEntity.toRecord(): PageRecord {
    val storedStability = stability
    val storedDifficulty = difficulty
    val storedDue = due
    val card =
        if (storedStability != null && storedDifficulty != null && storedDue != null) {
            FsrsCard(
                state = FsrsState.entries.first { it.value == state },
                step = step,
                stability = storedStability,
                difficulty = storedDifficulty,
                dueMillis = storedDue,
                lastReviewMillis = lastReview,
            )
        } else {
            null
        }
    return PageRecord(
        page = page,
        status = PageStatus.fromValue(status),
        learnedOn = learnedOn?.let(LocalDate::ofEpochDay),
        lastRevisedOn = lastRevisedOn?.let(LocalDate::ofEpochDay),
        card = card,
    )
}

internal fun PageRecord.toEntity(): PageProgressEntity =
    PageProgressEntity(
        page = page,
        status = status.value,
        learnedOn = learnedOn?.toEpochDay(),
        lastRevisedOn = lastRevisedOn?.toEpochDay(),
        stability = card?.stability,
        difficulty = card?.difficulty,
        due = card?.dueMillis,
        lastReview = card?.lastReviewMillis,
        state = card?.state?.value ?: FsrsState.Review.value,
        step = card?.step ?: 0,
    )

private fun kindOf(stored: String): PartKind =
    when (stored) {
        SessionKind.NEW_LESSON -> PartKind.NewLesson
        SessionKind.RECENT -> PartKind.Recent
        SessionKind.OLD -> PartKind.Old
        else -> error("Unknown part kind '$stored' in the hifz database.")
    }

internal fun PartKind.storedName(): String =
    when (this) {
        PartKind.NewLesson -> SessionKind.NEW_LESSON
        PartKind.Recent -> SessionKind.RECENT
        PartKind.Old -> SessionKind.OLD
    }

internal fun SessionEntity.toFinishedPart(): FinishedPart {
    val first = firstSurah?.let { surah -> firstAyah?.let { AyahRef(surah, it) } }
    val last = lastSurah?.let { surah -> lastAyah?.let { AyahRef(surah, it) } }
    return FinishedPart(
        day = LocalDate.ofEpochDay(day),
        kind = kindOf(kind),
        pages = pages.split(',').filter { it.isNotBlank() }.map { it.trim().toInt() },
        firstAyah = first,
        lastAyah = last,
        grade = PartGrade.valueOf(grade),
    )
}

internal fun MistakeEntity.toRecord(): MistakeRecord =
    MistakeRecord(
        ayah = AyahRef(surah, ayah),
        type =
            when (kind) {
                MistakeKind.SKIPPED -> MistakeType.Skipped
                MistakeKind.OTHER_WORD -> MistakeType.OtherWord
                else -> error("Unknown mistake kind '$kind' in the hifz database.")
            },
        atMillis = at,
    )

internal fun MistakeRecord.toEntity(): MistakeEntity =
    MistakeEntity(
        surah = ayah.surah,
        ayah = ayah.ayah,
        kind = if (type == MistakeType.Skipped) MistakeKind.SKIPPED else MistakeKind.OTHER_WORD,
        at = atMillis,
    )
