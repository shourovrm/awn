package app.awn.quran.listen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.R
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.listen.ListenField
import app.awn.quran.feature.listen.ListenPanel
import app.awn.quran.feature.listen.ListenViewModel

private const val BANGLA = "bn"

/**
 * The Listen mode on its green field: the reciter and download lists, every surah to play from its
 * first ayah, and the player panel at the bottom once audio is loaded.
 */
@Composable
fun ListenRoute(
    viewModel: ListenViewModel,
    quranText: QuranTextRepository,
    onOpenReciters: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val chapters by produceState(emptyList<Chapter>(), quranText) { value = quranText.chapters() }
    val useBanglaNames = LocalConfiguration.current.locales[0].language == BANGLA
    AwnTheme(field = ListenField) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = AwnSpacing.screenMargin)) {
                item {
                    Text(
                        text = stringResource(R.string.mode_listen),
                        style = AwnTypography.title,
                        modifier = Modifier.padding(vertical = AwnSpacing.medium),
                    )
                }
                item { TextRow(stringResource(R.string.settings_reciter), onOpenReciters) }
                item { TextRow(stringResource(R.string.settings_downloads), onOpenDownloads) }
                item {
                    Text(
                        text = stringResource(R.string.listen_choose_surah),
                        style = AwnTypography.lead,
                        modifier = Modifier.padding(vertical = AwnSpacing.small),
                    )
                }
                items(chapters, key = { it.number }) { chapter ->
                    SurahRow(chapter, useBanglaNames) {
                        viewModel.start(AyahRef(chapter.number, 1), null)
                    }
                }
            }
            ListenPanel(viewModel)
        }
    }
}

@Composable
private fun TextRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small),
    )
}

@Composable
private fun SurahRow(chapter: Chapter, useBanglaName: Boolean, onClick: () -> Unit) {
    val name = if (useBanglaName) chapter.nameBangla ?: chapter.nameSimple else chapter.nameSimple
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.surah_row, chapter.number, name),
            style = AwnTypography.body,
        )
        Text(text = chapter.nameArabic, style = AwnTypography.body)
    }
}
