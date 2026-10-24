package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import kotlinx.coroutines.launch

/** The title with the back link, and which page of how many is on screen. */
@Composable
internal fun PagerBar(title: String, currentPage: Int, pageCount: Int, onBack: () -> Unit) {
    LearnBar(
        title = title,
        startLinkLabel = stringResource(R.string.back),
        onStartLinkClick = onBack,
        actions = {
            Text(
                text = stringResource(R.string.unit_page_count, currentPage + 1, pageCount),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        },
    )
}

/**
 * One dot per page; the current page is a wide bar and the pages [isQuestionPage] picks are rings,
 * so the user sees how far the reading goes and where the questions start.
 */
@Composable
internal fun PageDots(
    pageCount: Int,
    pagerState: PagerState,
    isQuestionPage: (index: Int) -> Boolean = { false },
) {
    val scope = rememberCoroutineScope()
    val field = Awn.field
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val isCurrent = index == pagerState.currentPage
            val description = stringResource(R.string.unit_go_to_page, index + 1)
            Box(
                modifier =
                    Modifier.size(DOT_TOUCH_SIZE)
                        .semantics { contentDescription = description }
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                contentAlignment = Alignment.Center,
            ) {
                val dotWidth = if (isCurrent) CURRENT_DOT_WIDTH else DOT_SIZE
                Box(
                    Modifier.size(width = dotWidth, height = DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> field.buttonFill
                                isQuestionPage(index) -> field.mutedContent
                                else -> field.softFill
                            },
                        ),
                )
            }
        }
    }
}

private val DOT_SIZE = AwnSpacing.small
private val CURRENT_DOT_WIDTH = AwnSpacing.medium

// Each dot is tappable; the touch area is wider than the dot so a row of them stays usable.
private val DOT_TOUCH_SIZE = AwnSpacing.large
