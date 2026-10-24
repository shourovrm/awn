package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R

/** The word page's bar and position, with [page] drawn once for each word of [plan]. */
@Composable
internal fun WordPagerScreen(
    plan: WordPagerPlan,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (rank: Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = plan.startIndex) { plan.ranks.size }
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.word_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        if (plan.ranks.size > 1) {
            Text(
                text =
                    stringResource(
                        R.string.word_position,
                        pagerState.currentPage + 1,
                        plan.ranks.size,
                    ),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            page(plan.ranks[index])
        }
    }
}

/** The plan of [route], kept while the route stays the same. */
@Composable
internal fun rememberWordPagerPlan(route: LearnRoute.WordPage, formCount: Int): WordPagerPlan =
    remember(route, formCount) { wordPagerPlan(route.rank, route.from, formCount) }
