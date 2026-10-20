package app.awn.quran.feature.read.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.Quarter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName

/** Surah name at the left; the juz, and the quarter when one starts on the page, at the right. */
@Composable
internal fun PageHeaderRow(layout: PageLayout, chapters: List<Chapter>) {
    val info = pageHeaderInfo(layout)
    val surahName = headerChapter(layout, chapters)?.localName().orEmpty()
    Row(
        Modifier.fillMaxWidth().height(AwnSpacing.large).padding(horizontal = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = surahName,
            style = AwnTypography.small,
            color = Awn.field.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = juzAndQuarterLabel(info),
            style = AwnTypography.small,
            color = Awn.field.content,
            maxLines = 1,
        )
    }
}

/** "Juz 2", or "Juz 2 · ¼ Hizb 3" on a page where a quarter starts. */
@Composable
internal fun juzAndQuarterLabel(info: PageHeaderInfo): String {
    val quarter = info.startingQuarter ?: return stringResource(R.string.page_juz, info.juz)
    return stringResource(R.string.page_juz_and_hizb, info.juz, quarterLabel(quarter))
}

@Composable
private fun quarterLabel(quarter: Quarter): String {
    val resource =
        when (quarter.quartersIntoHizb) {
            0 -> R.string.page_hizb
            1 -> R.string.page_hizb_quarter
            2 -> R.string.page_hizb_half
            else -> R.string.page_hizb_three_quarters
        }
    return stringResource(resource, quarter.hizb)
}

/** The page number at the outer corner: right on odd pages, left on even pages, as in print. */
@Composable
internal fun PageNumberRow(page: Int) {
    Row(
        Modifier.fillMaxWidth().height(AwnSpacing.large).padding(horizontal = AwnSpacing.small),
        horizontalArrangement = if (page % 2 == 1) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = page.toString(),
            style = AwnTypography.small,
            color = Awn.field.content,
            maxLines = 1,
        )
    }
}
