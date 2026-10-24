package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.awn.quran.feature.learn.content.SnippetPicker
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.content.SnippetShown
import app.awn.quran.feature.learn.content.snippetReference
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The day's saying as the Learn hub shows it: Arabic, its rendering, and where it is from. */
internal data class DailySaying(val arabic: String, val translation: String, val source: String) {
    /** What the share sheet sends: the three parts on separate lines. */
    val shareText: String
        get() = "$arabic\n$translation\n$source"
}

/** Every saying in file order, and the position of the one that belongs to today. */
internal class SayingDeck(val sayings: List<DailySaying>, val dayIndex: Int)

/**
 * All sayings, starting on the one for [epochDay], or null when there are no snippets. [surahName]
 * gives the Bangla name of a surah for the source line of an ayah.
 */
internal fun sayingDeckOf(
    snippets: List<Snippet>,
    epochDay: Long,
    language: LearnLanguage,
    surahName: (Int) -> String,
): SayingDeck? {
    if (snippets.isEmpty()) return null
    val sayings = snippets.map { snippet -> sayingOf(snippet, language, surahName) }
    return SayingDeck(sayings, SnippetRepository.indexForDay(snippets.size, epochDay))
}

private fun sayingOf(
    snippet: Snippet,
    language: LearnLanguage,
    surahName: (Int) -> String,
): DailySaying =
    DailySaying(
        arabic = snippet.arabic,
        translation = snippet.rendering(language),
        source = snippetReference(snippet.source, language, surahName),
    )

/** The outcome of reading the snippets: [deck] is null when there are none. */
internal class SayingLoad(val deck: SayingDeck?)

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
            val deck =
                sayingDeckOf(snippets, today, language) { surah ->
                    environment?.surahName(surah) ?: surah.toString()
                }
            value = SayingLoad(deck)
        }
    return load
}

/** How long one saying stays on the card before the card shows another by itself. */
private val SAYING_TURN_INTERVAL = 30.minutes

/** Keeps which sayings were seen across a turned screen: the current one first, then the rest. */
private val SnippetShownSaver =
    listSaver<SnippetShown, Int>(
        save = { shown -> listOf(shown.currentIndex) + shown.seenIndices },
        restore = { saved -> SnippetShown(saved.first(), saved.drop(1).toSet()) },
    )

/**
 * The day's saying at the top of the hub. A tap on the card shows another saying at random, as the
 * old Quran-tab card did, and the card also turns to another one every half hour.
 */
@Composable
internal fun DailySayingCard(
    deck: SayingDeck,
    onShare: (DailySaying) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by
        rememberSaveable(deck, stateSaver = SnippetShownSaver) {
            mutableStateOf(SnippetShown.startingAt(deck.dayIndex))
        }
    val showAnother = { shown = SnippetPicker.next(shown, deck.sayings.size, Random.Default) }
    LaunchedEffect(deck) {
        while (true) {
            delay(SAYING_TURN_INTERVAL)
            showAnother()
        }
    }
    val saying = deck.sayings[shown.currentIndex]
    SayingCard(saying, onShowAnother = showAnother, onShare = { onShare(saying) }, modifier)
}

/** Arabic on the right, the rendering, and the source with a share button at its end. */
@Composable
private fun SayingCard(
    saying: DailySaying,
    onShowAnother: () -> Unit,
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
                .clickable(
                    onClickLabel = stringResource(R.string.saying_another),
                    role = Role.Button,
                    onClick = onShowAnother,
                )
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
