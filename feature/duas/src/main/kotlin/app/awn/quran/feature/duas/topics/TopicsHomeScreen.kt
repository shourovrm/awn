@file:Suppress(
    "MatchingDeclarationName",
) // TopicsHomeActions is the contract; the screen follows it.

package app.awn.quran.feature.duas.topics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.components.AwnSearchField
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.Topic
import app.awn.quran.feature.duas.content.TopicCatalogue
import app.awn.quran.feature.duas.state.DuasState
import app.awn.quran.feature.duas.state.inLanguage
import app.awn.quran.feature.duas.ui.DuaListRow
import app.awn.quran.feature.duas.ui.DuaSearchResultRow
import app.awn.quran.feature.duas.ui.DuasTopBar
import app.awn.quran.feature.duas.ui.FilterChip

/** What the buttons of the topics home do; a null category shows every topic. */
internal class TopicsHomeActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onCategoryChange: (String?) -> Unit,
    val onOpenTopic: (Topic) -> Unit,
    val onOpenResult: (Dua) -> Unit,
)

/**
 * The first Topics screen: search, one chip per category, then one row per topic with its category
 * and number of ayahs. A query shows the matching ayahs instead, found by the topic, the part, the
 * Arabic or the translation.
 */
@Composable
internal fun TopicsHomeScreen(
    topics: TopicCatalogue,
    category: String?,
    cardState: DuasState,
    language: String,
    actions: TopicsHomeActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        DuasTopBar(
            title = stringResource(R.string.topics_title),
            subtitle =
                pluralStringResource(
                    R.plurals.topics_count,
                    topics.topics.size,
                    topics.topics.size,
                ),
            onBack = actions.onBack,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "search") {
                AwnSearchField(
                    query = cardState.query,
                    onQueryChange = actions.onQueryChange,
                    placeholder = stringResource(R.string.topics_search_hint),
                    clearDescription = stringResource(R.string.duas_search_clear),
                    modifier =
                        Modifier.padding(
                            horizontal = AwnSpacing.screenMargin,
                            vertical = AwnSpacing.small,
                        ),
                )
            }
            if (cardState.query.isBlank()) {
                topicItems(topics, category, language, actions)
            } else {
                resultItems(cardState, actions)
            }
        }
    }
}

private fun LazyListScope.topicItems(
    topics: TopicCatalogue,
    category: String?,
    language: String,
    actions: TopicsHomeActions,
) {
    item(key = "chips") { CategoryChips(topics, category, language, actions.onCategoryChange) }
    item(key = "heading") { SectionHeading(stringResource(R.string.topics_heading)) }
    val shown = topics.topics.filter { category == null || it.categoryKey == category }
    val categoryNames = topics.categories.associate { it.key to it.name.inLanguage(language) }
    shown.forEachIndexed { index, topic ->
        item(key = "topic-${topic.key}") {
            val ayahs = ayahCount(topic)
            DuaListRow(
                number = index + 1,
                title = topic.name.inLanguage(language),
                detail =
                    stringResource(
                        R.string.topics_detail,
                        categoryNames.getValue(topic.categoryKey),
                        pluralStringResource(R.plurals.topics_ayah_count, ayahs, ayahs),
                    ),
                onClick = { actions.onOpenTopic(topic) },
            )
        }
    }
}

@Composable
private fun CategoryChips(
    topics: TopicCatalogue,
    selected: String?,
    language: String,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        FilterChip(stringResource(R.string.topics_all), selected == null) { onSelect(null) }
        topics.categories.forEach { category ->
            FilterChip(category.name.inLanguage(language), selected == category.key) {
                onSelect(category.key)
            }
        }
    }
}

private fun LazyListScope.resultItems(state: DuasState, actions: TopicsHomeActions) {
    val results = state.searchHits.mapNotNull { hit -> state.cards[hit.dua.id] }
    if (results.isEmpty()) {
        item(key = "no-results") {
            Text(
                text = stringResource(R.string.topics_no_results),
                style = AwnTypography.body,
                color = Awn.field.content,
                modifier = Modifier.padding(AwnSpacing.screenMargin),
            )
        }
        return
    }
    item(key = "results-heading") {
        SectionHeading(stringResource(R.string.topics_results_heading, results.size))
    }
    results.forEach { card ->
        item(key = "result-${card.dua.id}") {
            DuaSearchResultRow(card = card, query = state.query, onClick = actions.onOpenResult)
        }
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier =
            Modifier.padding(
                    start = AwnSpacing.screenMargin,
                    end = AwnSpacing.screenMargin,
                    top = AwnSpacing.medium,
                    bottom = AwnSpacing.small,
                )
                .semantics { heading() },
    )
}

/** How many ayahs [topic] has over all its parts. */
internal fun ayahCount(topic: Topic): Int =
    topic.parts.flatMap { it.passages }.sumOf { it.lastAyah - it.firstAyah + 1 }
