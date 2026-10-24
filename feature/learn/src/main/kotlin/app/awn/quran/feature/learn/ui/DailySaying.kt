package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.content.snippetReference
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The day's saying as the Learn hub shows it: Arabic, its rendering, and where it is from. */
internal data class DailySaying(val arabic: String, val translation: String, val source: String) {
    /** What the share sheet sends: the three parts on separate lines. */
    val shareText: String
        get() = "$arabic\n$translation\n$source"
}

/**
 * The saying for [epochDay], or null when there are no snippets. [surahName] gives the Bangla name
 * of a surah for the source line of an ayah.
 */
internal fun dailySayingOf(
    snippets: List<Snippet>,
    epochDay: Long,
    language: LearnLanguage,
    surahName: (Int) -> String,
): DailySaying? {
    val snippet = SnippetRepository.forDay(snippets, epochDay) ?: return null
    return DailySaying(
        arabic = snippet.arabic,
        translation = snippet.rendering(language),
        source = snippetReference(snippet.source, language, surahName),
    )
}

/** The outcome of reading the snippets: [saying] is null when there are none. */
internal class SayingLoad(val saying: DailySaying?)

/**
 * The day's saying, read from the snippet file off the main thread. The result is null until the
 * file is read, so the hub can wait for it and the card does not push the rows down after they are
 * drawn.
 */
@Composable
internal fun rememberSayingLoad(
    repository: SnippetRepository,
    languageCode: String,
    environment: LearnEnvironment?,
): SayingLoad? {
    val language = if (languageCode == "bn") LearnLanguage.Bangla else LearnLanguage.English
    val load by
        produceState<SayingLoad?>(null, repository, language, environment) {
            val snippets = withContext(Dispatchers.IO) { repository.all() }
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val saying =
                dailySayingOf(snippets, today, language) { surah ->
                    environment?.surahName(surah) ?: surah.toString()
                }
            value = SayingLoad(saying)
        }
    return load
}

/**
 * The saying on a soft card at the top of the hub: Arabic on the right, the rendering, and the
 * source with a share button at its end. The text itself does nothing when tapped.
 */
@Composable
internal fun DailySayingCard(
    saying: DailySaying,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AwnShapes.control)
                .background(field.softFill)
                .padding(
                    start = AwnSpacing.medium,
                    top = AwnSpacing.medium,
                    end = AwnSpacing.small,
                ),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Text(
            text = saying.arabic,
            style = AwnTypography.quranLead.copy(textDirection = TextDirection.Rtl),
            color = field.content,
            modifier = Modifier.fillMaxWidth().padding(end = AwnSpacing.small),
        )
        Text(
            text = saying.translation,
            style = AwnTypography.small,
            color = field.content,
            modifier = Modifier.padding(end = AwnSpacing.small),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = saying.source,
                style = AwnTypography.small.copy(fontWeight = FontWeight.SemiBold),
                color = field.content,
                modifier = Modifier.weight(1f),
            )
            AwnIconButton(
                icon = UiR.drawable.ic_ui_share,
                contentDescription = stringResource(R.string.saying_share),
                onClick = onShare,
                tint = field.buttonFill,
            )
        }
    }
}
