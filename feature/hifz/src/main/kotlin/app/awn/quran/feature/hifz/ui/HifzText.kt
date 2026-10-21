@file:Suppress("TooManyFunctions") // One small function per name the screens show.

package app.awn.quran.feature.hifz.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.LessonPart
import app.awn.quran.feature.hifz.method.LessonSize
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PartKind

/** Splits sorted page numbers into runs of consecutive pages: 3, 4, 5, 9 becomes 3..5 and 9..9. */
internal fun consecutiveRuns(pages: List<Int>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    for (page in pages.sorted()) {
        val last = runs.lastOrNull()
        if (last != null && page == last.last + 1) {
            runs[runs.lastIndex] = last.first..page
        } else {
            runs += page..page
        }
    }
    return runs
}

/** "Page 24", "Pages 19 to 23" or "Pages 3 to 5, 9" for the pages of one part. */
@Composable
internal fun pagesText(pages: List<Int>): String {
    val runs = consecutiveRuns(pages)
    val pieces = runs.map { run ->
        if (run.first == run.last) run.first.toString()
        else stringResource(R.string.pages_span, run.first, run.last)
    }
    val joined = pieces.joinToString(", ")
    val isOnePage = pages.size == 1
    return if (isOnePage) stringResource(R.string.pages_one, joined)
    else stringResource(R.string.pages_many, joined)
}

/** "ayahs 154 to 163" inside one surah, "ayahs 2:280 to 3:5" across surahs. */
@Composable
internal fun ayahRangeText(first: AyahRef, last: AyahRef): String =
    when {
        first == last -> stringResource(R.string.ayah_range_single, first.ayah)
        first.surah == last.surah ->
            stringResource(R.string.ayah_range_same_surah, first.ayah, last.ayah)
        else -> stringResource(R.string.ayah_range_cross, first.toString(), last.toString())
    }

@Composable
internal fun lessonDetailText(part: LessonPart): String {
    val ayahs = ayahRangeText(part.first, part.last)
    return if (part.firstPage == part.lastPage) {
        stringResource(R.string.lesson_detail_page, part.firstPage, ayahs)
    } else {
        stringResource(R.string.lesson_detail_pages, part.firstPage, part.lastPage, ayahs)
    }
}

@Composable
internal fun methodName(method: HifzMethodId): String =
    stringResource(
        when (method) {
            HifzMethodId.SabaqSabqiManzil -> R.string.method_sabaq_sabqi_manzil
            HifzMethodId.ThreeByThree -> R.string.method_three_by_three
            HifzMethodId.AyahChain -> R.string.method_ayah_chain
            HifzMethodId.FixedAyahs -> R.string.method_fixed_ayahs
            HifzMethodId.TrackOnly -> R.string.method_track_only
        },
    )

@Composable
internal fun methodDetail(method: HifzMethodId): String =
    stringResource(
        when (method) {
            HifzMethodId.SabaqSabqiManzil -> R.string.method_sabaq_sabqi_manzil_detail
            HifzMethodId.ThreeByThree -> R.string.method_three_by_three_detail
            HifzMethodId.AyahChain -> R.string.method_ayah_chain_detail
            HifzMethodId.FixedAyahs -> R.string.method_fixed_ayahs_detail
            HifzMethodId.TrackOnly -> R.string.method_track_only_detail
        },
    )

@Composable
internal fun statusName(status: PageStatus): String =
    stringResource(
        when (status) {
            PageStatus.NotStarted -> R.string.status_not_started
            PageStatus.Learning -> R.string.status_learning
            PageStatus.Memorised -> R.string.status_memorised
            PageStatus.Solid -> R.string.status_solid
        },
    )

@Composable
internal fun daysText(days: Int): String = pluralStringResource(R.plurals.days_count, days, days)

@Composable
internal fun pagesCountText(count: Int): String =
    pluralStringResource(R.plurals.pages_count, count, count)

@Composable
internal fun lessonSizeText(size: LessonSize): String =
    when (size) {
        LessonSize.OnePage -> stringResource(R.string.lesson_one_page)
        LessonSize.HalfPage -> stringResource(R.string.lesson_half_page)
        is LessonSize.Ayahs -> pluralStringResource(R.plurals.ayahs_count, size.count, size.count)
    }

@Composable
internal fun ayahsCountText(count: Int): String =
    pluralStringResource(R.plurals.ayahs_count, count, count)

/** The row title for a part, which depends on whether the method has the three named parts. */
@Composable
internal fun partTitle(kind: PartKind, method: HifzMethodId): String {
    val isSabaq = method == HifzMethodId.SabaqSabqiManzil
    return stringResource(
        when (kind) {
            PartKind.NewLesson -> if (isSabaq) R.string.part_sabaq else R.string.part_new_lesson
            PartKind.Recent -> R.string.part_sabqi
            PartKind.Old -> if (isSabaq) R.string.part_manzil else R.string.part_revision
        },
    )
}

@Composable
internal fun startLabel(kind: PartKind, method: HifzMethodId): String {
    val isSabaq = method == HifzMethodId.SabaqSabqiManzil
    return stringResource(
        when (kind) {
            PartKind.NewLesson -> if (isSabaq) R.string.start_sabaq else R.string.start_lesson
            PartKind.Recent -> R.string.start_sabqi
            PartKind.Old -> if (isSabaq) R.string.start_manzil else R.string.start_revision
        },
    )
}
