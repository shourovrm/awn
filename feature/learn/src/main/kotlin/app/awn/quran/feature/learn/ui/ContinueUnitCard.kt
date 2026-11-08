package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.LearnTracks
import app.awn.quran.feature.learn.course.unitPages
import app.awn.quran.feature.learn.progress.UnitProgress
import app.awn.quran.feature.learn.progress.continueUnit
import app.awn.quran.feature.learn.progress.resumePage
import app.awn.quran.feature.learn.words.CourseSnapshot

/** The unit the hub offers to continue: where it is in its track, and the page on screen. */
internal data class ContinueUnit(
    val lessonId: String,
    val track: LearnTrack,
    val number: Int,
    val title: String,
    val pageNumber: Int,
    val pageCount: Int,
)

/**
 * The unit the user left in the middle most recently, ready for the hub's card; null when there is
 * none or it belongs to no track.
 */
internal fun continueUnitCard(
    progress: List<UnitProgress>,
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    choices: CourseChoices,
): ContinueUnit? {
    val saved = continueUnit(progress) ?: return null
    val text = lookups.content.lesson(saved.lessonId)?.text(lookups.languageCode)
    val track = LearnTracks.trackOf(saved.lessonId, lookups.content)
    val number = track?.let {
        val input = trackInput(lookups, snapshot, choices)
        LearnTracks.units(it, input).firstOrNull { unit -> unit.id == saved.lessonId }?.number
    }
    return if (text == null || track == null || number == null) {
        null
    } else {
        val pageCount = unitPages(text).size
        ContinueUnit(
            lessonId = saved.lessonId,
            track = track,
            number = number,
            title = text.title,
            pageNumber = resumePage(saved.page, pageCount) + 1,
            pageCount = pageCount,
        )
    }
}

/** The hub's card for [card]: the word Continue, then the unit and its page. */
@Composable
internal fun ContinueCard(card: ContinueUnit, onOpen: () -> Unit) {
    val field = Awn.field
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.softFill)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(AwnSpacing.medium),
    ) {
        Text(
            text = stringResource(R.string.continue_label),
            style = AwnTypography.small,
            color = field.mutedContent,
        )
        Text(
            text =
                stringResource(
                    R.string.continue_unit_line,
                    card.number,
                    card.title,
                    card.pageNumber,
                    card.pageCount,
                ),
            style = AwnTypography.bodyStrong,
            color = field.content,
        )
    }
}
