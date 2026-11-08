package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.course.CourseContent
import app.awn.quran.feature.learn.course.unitPages
import app.awn.quran.feature.learn.progress.UnitProgress
import app.awn.quran.feature.learn.progress.resumePage

/** A unit left in the middle, as the track list shows it: the page of how many, and how far. */
internal class UnitProgressLine(val pageNumber: Int, val pageCount: Int, val reachedFraction: Float)

/**
 * The lines for the units the user left in the middle, by lesson id. A unit on its first page has
 * none: it reads as not started.
 */
internal fun unitProgressLines(
    progress: List<UnitProgress>,
    content: CourseContent,
    languageCode: String,
): Map<String, UnitProgressLine> {
    val lines = mutableMapOf<String, UnitProgressLine>()
    for (saved in progress) {
        if (saved.page == 0) continue
        val lesson = content.lesson(saved.lessonId) ?: continue
        val pageCount = unitPages(lesson.text(languageCode)).size
        lines[saved.lessonId] =
            UnitProgressLine(
                pageNumber = resumePage(saved.page, pageCount) + 1,
                pageCount = pageCount,
                reachedFraction = (saved.furthestPage + 1).toFloat() / pageCount,
            )
    }
    return lines
}

/** A thin bar that fills from the left; [fraction] is between 0 and 1. */
@Composable
internal fun ThinProgressBar(fraction: Float) {
    val field = Awn.field
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(CircleShape)
                .background(field.softFill),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(BAR_HEIGHT)
                    .background(field.buttonFill),
        )
    }
}

private val BAR_HEIGHT = AwnSpacing.small / 2
