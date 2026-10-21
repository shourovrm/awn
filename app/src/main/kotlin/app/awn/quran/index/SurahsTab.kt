package app.awn.quran.index

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import java.text.NumberFormat

/** The meaning of a surah's name in the user's language, or null when it is not available. */
@Composable
internal fun surahMeaning(chapter: Chapter): String? {
    val englishMeanings = stringArrayResource(R.array.surah_meanings_en)
    return if (LocalConfiguration.current.locales[0].language == BANGLA_LANGUAGE) {
        chapter.nameBangla
    } else {
        englishMeanings.getOrNull(chapter.number - 1)
    }
}

private const val BANGLA_LANGUAGE = "bn"

@Composable
internal fun pageLabel(page: Int): String =
    stringResource(R.string.index_page, NumberFormat.getIntegerInstance().format(page))

/** The Surahs tab as one flat list: each juz header followed by the surahs that begin in it. */
internal fun surahListRows(groups: List<JuzGroup>): List<SurahListRow> = groups.flatMap { group ->
    listOf<SurahListRow>(SurahListRow.Header(group)) + group.surahs.map { SurahListRow.Surah(it) }
}

/** The list position of [surah]'s row, or null when nothing was read yet or it is not listed. */
internal fun rowIndexOfSurah(rows: List<SurahListRow>, surah: Int?): Int? {
    if (surah == null) return null
    val index = rows.indexOfFirst { it is SurahListRow.Surah && it.entry.chapter.number == surah }
    return index.takeIf { it >= 0 }
}

/**
 * The surahs under their juz. [listState] belongs to the index screen, which creates it already
 * scrolled to the surah read last, so a visit to another tab and back keeps the user's place.
 */
@Composable
internal fun SurahsTab(
    state: IndexState,
    rows: List<SurahListRow>,
    listState: LazyListState,
    onOpen: (page: Int, ayah: AyahRef?) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(rows.size) { index ->
            when (val row = rows[index]) {
                is SurahListRow.Header ->
                    GroupHeader(
                        title = stringResource(R.string.index_juz_header, row.group.juz),
                        page = row.group.startPage,
                    )
                is SurahListRow.Surah ->
                    SurahRow(row.entry, state.showSurahNameMeaning) {
                        onOpen(row.entry.page, null)
                    }
            }
        }
    }
}

internal sealed interface SurahListRow {
    data class Header(val group: JuzGroup) : SurahListRow

    data class Surah(val entry: SurahEntry) : SurahListRow
}

/**
 * A juz heading inside a list: text only, with the juz's first page at the end. A filled band here
 * read as heavy next to the rows, and in the night modes it stood out more than the content.
 */
@Composable
internal fun GroupHeader(title: String, page: Int) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.screenMargin,
                    top = AwnSpacing.medium,
                    bottom = AwnSpacing.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AwnTypography.bodyStrong,
            color = field.buttonFill,
            modifier = Modifier.weight(1f),
        )
        Text(text = pageLabel(page), style = AwnTypography.small, color = field.content)
    }
}

@Composable
private fun SurahRow(entry: SurahEntry, showMeaning: Boolean, onClick: () -> Unit) {
    val field = Awn.field
    val chapter = entry.chapter
    val meaning = if (showMeaning) surahMeaning(chapter) else null
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = NumberFormat.getIntegerInstance().format(chapter.number),
            style = AwnTypography.bodyStrong,
            color = field.buttonFill,
            modifier = Modifier.widthIn(min = AwnSpacing.huge),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text =
                    if (meaning == null) {
                        chapter.nameSimple
                    } else {
                        stringResource(R.string.index_surah_title, chapter.nameSimple, meaning)
                    },
                style = AwnTypography.body,
                color = field.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = surahDetail(chapter),
                style = AwnTypography.small,
                color = field.content,
            )
        }
        Text(text = pageLabel(entry.page), style = AwnTypography.small, color = field.content)
    }
}

@Composable
private fun surahDetail(chapter: Chapter): String {
    val place =
        stringResource(
            if (chapter.revelationPlace == MAKKAH) R.string.index_makki else R.string.index_madani,
        )
    val count =
        pluralStringResource(R.plurals.index_ayah_count, chapter.versesCount, chapter.versesCount)
    return stringResource(R.string.index_surah_detail, place, count)
}

private const val MAKKAH = "makkah"
