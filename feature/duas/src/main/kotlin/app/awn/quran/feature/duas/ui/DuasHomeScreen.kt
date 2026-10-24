@file:Suppress("MatchingDeclarationName") // DuasHomeActions is the contract; the screen follows it.

package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.awn.quran.feature.duas.state.DuaGroupRow
import app.awn.quran.feature.duas.state.DuasFilter
import app.awn.quran.feature.duas.state.DuasState
import app.awn.quran.feature.duas.state.filteredDuaCount
import app.awn.quran.feature.duas.state.groupRows
import app.awn.quran.feature.duas.state.inLanguage
import app.awn.quran.feature.duas.state.starterDuas

/** What the buttons of the Duas home do. */
internal class DuasHomeActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (DuasFilter) -> Unit,
    val onOpenStarters: () -> Unit,
    val onOpenGroup: (groupKey: String) -> Unit,
    val onOpenResult: (Dua) -> Unit,
)

/** The first Duas screen: search, filters, the ten starters and the groups. */
@Composable
internal fun DuasHomeScreen(
    state: DuasState,
    language: String,
    actions: DuasHomeActions,
    modifier: Modifier = Modifier,
) {
    val catalogue = state.catalogue
    val duaCount = catalogue?.let { filteredDuaCount(it, state.filter, state.progress) }
    Column(modifier.fillMaxSize()) {
        DuasTopBar(
            title = stringResource(R.string.duas_title),
            subtitle = duaCount?.let { pluralStringResource(R.plurals.duas_count, it, it) },
            onBack = actions.onBack,
        )
        if (catalogue != null) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "search") {
                    AwnSearchField(
                        query = state.query,
                        onQueryChange = actions.onQueryChange,
                        placeholder = stringResource(R.string.duas_search_hint),
                        clearDescription = stringResource(R.string.duas_search_clear),
                        modifier =
                            Modifier.padding(
                                horizontal = AwnSpacing.screenMargin,
                                vertical = AwnSpacing.small,
                            ),
                    )
                }
                if (state.query.isBlank()) {
                    browseItems(state, language, actions)
                } else {
                    searchItems(state, actions)
                }
            }
        }
    }
}

private fun LazyListScope.browseItems(
    state: DuasState,
    language: String,
    actions: DuasHomeActions,
) {
    val catalogue = state.catalogue ?: return
    val starters = starterDuas(catalogue, state.filter, state.progress)
    val rows = groupRows(catalogue, state.filter, state.progress)
    item(key = "chips") {
        DuaFilterChips(selected = state.filter, onSelect = actions.onFilterChange)
    }
    if (rows.isEmpty()) {
        item(key = "empty") {
            Text(
                text = stringResource(R.string.duas_list_empty),
                style = AwnTypography.body,
                color = Awn.field.content,
                modifier = Modifier.padding(AwnSpacing.screenMargin),
            )
        }
        return
    }
    if (starters.isNotEmpty()) {
        item(key = "starters") {
            DuaListRow(
                number = starters.size,
                title = stringResource(R.string.duas_starters_title),
                detail = stringResource(R.string.duas_starters_detail),
                onClick = actions.onOpenStarters,
                isSoft = true,
                modifier =
                    Modifier.padding(
                        horizontal = AwnSpacing.screenMargin,
                        vertical = AwnSpacing.small,
                    ),
            )
        }
    }
    item(key = "groups-heading") { GroupsHeading() }
    items(rows.size, key = { "group-${rows[it].group.key}" }) { index ->
        GroupRow(rows[index], language, actions.onOpenGroup)
    }
}

@Composable
private fun GroupsHeading() {
    Text(
        text = stringResource(R.string.duas_groups_heading),
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

@Composable
private fun GroupRow(row: DuaGroupRow, language: String, onOpenGroup: (String) -> Unit) {
    val countText = pluralStringResource(R.plurals.duas_count, row.duaCount, row.duaCount)
    val detail =
        if (row.learnedCount > 0) {
            stringResource(R.string.duas_group_detail_learned, countText, row.learnedCount)
        } else {
            countText
        }
    DuaListRow(
        number = row.number,
        title = row.group.name.inLanguage(language),
        detail = detail,
        onClick = { onOpenGroup(row.group.key) },
    )
}

private fun LazyListScope.searchItems(state: DuasState, actions: DuasHomeActions) {
    val results = state.searchHits.mapNotNull { hit -> state.cards[hit.dua.id] }
    if (results.isEmpty()) {
        item(key = "no-results") { DuaSearchEmpty() }
        return
    }
    item(key = "results-heading") {
        Text(
            text = stringResource(R.string.duas_results_heading, results.size),
            style = AwnTypography.bodyStrong,
            color = Awn.field.buttonFill,
            modifier =
                Modifier.padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small)
                    .semantics { heading() },
        )
    }
    items(results.size, key = { "result-${results[it].dua.id}" }) { index ->
        DuaSearchResultRow(
            card = results[index],
            query = state.query,
            onClick = actions.onOpenResult,
        )
    }
}
