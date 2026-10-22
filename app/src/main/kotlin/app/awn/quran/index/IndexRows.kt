package app.awn.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import java.text.NumberFormat

private const val MAKKAH = "makkah"
private const val DETAIL_SEPARATOR = " · "

// A getter: the Arabic font family exists only once the theme has installed it.
private val ArabicNameStyle: TextStyle
    get() = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl)

/** Draws one row of the index list; [lastSurah] is marked in the Surahs list. */
@Composable
internal fun IndexRowItem(
    row: IndexRow,
    meanings: SurahMeanings,
    showMeaning: Boolean,
    lastSurah: Int?,
    onOpen: (page: Int) -> Unit,
) {
    when (row) {
        is JuzHeading -> JuzHeadingRow(row)
        is SurahRow -> {
            val chapter = row.chapter
            val isLast = chapter.number == lastSurah
            val detail =
                listOfNotNull(
                        if (showMeaning) meanings.of(chapter) else null,
                        placeLabel(chapter),
                        pluralStringResource(
                            R.plurals.index_ayah_count,
                            chapter.versesCount,
                            chapter.versesCount,
                        ),
                        if (isLast) stringResource(R.string.index_read_last) else null,
                    )
                    .joinToString(DETAIL_SEPARATOR)
            ListRow(
                badge = chapter.number,
                title = chapter.nameSimple,
                detail = detail,
                arabicName = chapter.nameArabic,
                page = row.page.takeIf { row.showPage },
                isMarked = isLast,
                onClick = { onOpen(row.page) },
            )
        }
        is JuzStartRow ->
            ListRow(
                badge = row.juz,
                title = stringResource(R.string.index_juz_header, row.juz),
                detail =
                    stringResource(
                        R.string.index_juz_start,
                        row.chapter.nameSimple,
                        row.startAyah.surah,
                        row.startAyah.ayah,
                    ),
                arabicName = row.chapter.nameArabic,
                page = row.page,
                isMarked = false,
                onClick = { onOpen(row.page) },
            )
        is PageRow ->
            ListRow(
                badge = row.page,
                title = row.chapter.nameSimple,
                detail = if (row.startsSurah) stringResource(R.string.index_surah_starts) else null,
                arabicName = row.chapter.nameArabic,
                page = null,
                isMarked = false,
                onClick = { onOpen(row.page) },
            )
    }
}

@Composable
private fun placeLabel(chapter: Chapter): String =
    stringResource(
        if (chapter.revelationPlace == MAKKAH) R.string.index_makki else R.string.index_madani,
    )

@Composable
private fun pageLabelShort(page: Int): String =
    stringResource(R.string.index_page_short, NumberFormat.getIntegerInstance().format(page))

/**
 * A juz heading: text only, no fill. It names the juz that begins the group and, when no surah
 * begins in the juz, the surah that continues through it.
 */
@Composable
private fun JuzHeadingRow(heading: JuzHeading) {
    val field = Awn.field
    val title =
        if (heading.continuedChapter == null) {
            stringResource(R.string.index_juz_header, heading.juz)
        } else {
            stringResource(
                R.string.index_juz_continues,
                heading.juz,
                heading.continuedChapter.nameSimple,
            )
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.railWidth,
                    top = AwnSpacing.medium,
                    bottom = AwnSpacing.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AwnTypography.bodyStrong,
            color = field.buttonFill,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (heading.showPage) {
            Text(
                text = stringResource(R.string.index_page_lower, heading.startPage),
                style = AwnTypography.bodyStrong,
                color = field.buttonFill,
            )
        }
    }
}

@Composable
private fun ListRow(
    badge: Int,
    title: String,
    detail: String?,
    arabicName: String,
    page: Int?,
    isMarked: Boolean,
    onClick: () -> Unit,
) {
    val field = Awn.field
    val markFill =
        if (isMarked) Modifier.background(field.softFill, AwnShapes.control) else Modifier
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = AwnSpacing.small, end = AwnSpacing.railWidth)
                .then(markFill)
                .clip(AwnShapes.control)
                .heightIn(min = AwnSpacing.giant)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        NumberBadge(badge)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AwnTypography.bodyStrong,
                color = field.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(text = detail, style = AwnTypography.small, color = field.content)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = arabicName,
                style = ArabicNameStyle,
                color = field.content,
                maxLines = 1,
            )
            if (page != null) {
                Text(
                    text = pageLabelShort(page),
                    style = AwnTypography.small,
                    color = field.content,
                )
            }
        }
    }
}

/** The ochre disc with the number of a surah, juz or page. */
@Composable
private fun NumberBadge(number: Int) {
    Box(
        modifier =
            Modifier.size(AwnSpacing.numberBadge).clip(CircleShape).background(AwnColors.ochre),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = NumberFormat.getIntegerInstance().format(number),
            style = AwnTypography.small.copy(fontWeight = AwnTypography.bodyStrong.fontWeight),
            color = AwnColors.ink,
            maxLines = 1,
        )
    }
}
