package app.awn.quran.feature.duas.topics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Topic
import app.awn.quran.feature.duas.content.TopicPart
import app.awn.quran.feature.duas.state.inLanguage
import app.awn.quran.feature.duas.ui.DuaListRow
import app.awn.quran.feature.duas.ui.DuasTopBar

internal const val TOPIC_ALL_TAG = "topic-go-through-all"

/** The Duas group a topic links to: its key, its name in the app language and its size. */
internal data class RelatedDuas(val key: String, val name: String, val count: Int)

/** What the buttons of one topic's page do. */
internal class TopicPartsActions(
    val onBack: () -> Unit,
    val onOpenAll: () -> Unit,
    val onOpenPart: (TopicPart) -> Unit,
    val onOpenDuas: (groupKey: String) -> Unit,
)

/**
 * One topic: a button that goes through every ayah of it, then one row per part with its number of
 * ayahs, then the matching Duas group when there is one, with its name and number of duas.
 */
@Composable
internal fun TopicPartsScreen(
    topic: Topic,
    duasGroup: RelatedDuas?,
    language: String,
    actions: TopicPartsActions,
    modifier: Modifier = Modifier,
) {
    val total = ayahCount(topic)
    Column(modifier.fillMaxSize()) {
        DuasTopBar(
            title = topic.name.inLanguage(language),
            subtitle = pluralStringResource(R.plurals.topics_ayah_count, total, total),
            onBack = actions.onBack,
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AwnButton(
                label = pluralStringResource(R.plurals.topics_go_through_all, total, total),
                onClick = actions.onOpenAll,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small)
                        .testTag(TOPIC_ALL_TAG),
            )
            SectionHeading(stringResource(R.string.topics_parts_heading))
            topic.parts.forEachIndexed { index, part ->
                val ayahs = part.passages.sumOf { it.lastAyah - it.firstAyah + 1 }
                DuaListRow(
                    number = index + 1,
                    title = part.name.inLanguage(language),
                    detail = pluralStringResource(R.plurals.topics_ayah_count, ayahs, ayahs),
                    onClick = { actions.onOpenPart(part) },
                )
            }
            if (duasGroup != null) {
                SectionHeading(stringResource(R.string.topics_related_duas))
                DuaListRow(
                    number = duasGroup.count,
                    title = duasGroup.name,
                    detail =
                        pluralStringResource(
                            R.plurals.duas_count,
                            duasGroup.count,
                            duasGroup.count,
                        ),
                    onClick = { actions.onOpenDuas(duasGroup.key) },
                    isSoft = true,
                    modifier = Modifier.padding(horizontal = AwnSpacing.screenMargin),
                )
            }
        }
    }
}
