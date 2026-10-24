package app.awn.quran.feature.duas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.state.DuasViewModel
import app.awn.quran.feature.duas.state.inLanguage
import kotlinx.coroutines.launch

/**
 * The Duas collection: the home with search, filters and groups, and the cards of the list a tap
 * opened. Back from the cards returns to the home. [onOpenQuran] opens the Quran screen at the
 * page and ayah of a dua.
 */
@Composable
fun DuasScreen(
    dependencies: DuasDependencies,
    onBack: () -> Unit,
    onOpenQuran: (page: Int, ayah: AyahRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuasViewModel =
        viewModel(factory = viewModelFactory { initializer { DuasViewModel(dependencies) } })
    // The activity's locale is the app language set in Settings.
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.onLanguageChanged(language) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.deck != null) { viewModel.closeDeck() }

    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Box(modifier.fillMaxSize()) {
            val catalogue = state.catalogue
            val deck = state.deck
            val deckDuas = state.deckDuas
            if (deck != null && catalogue != null && deckDuas.isNotEmpty()) {
                val title =
                    catalogue.groups.firstOrNull { it.key == deck.groupKey }?.name?.inLanguage(language)
                DuaCardsScreen(
                    title = title ?: stringResource(R.string.duas_starters_title),
                    duas = deckDuas,
                    startIndex = deck.startIndex.coerceIn(0, deckDuas.lastIndex),
                    state = state,
                    dependencies = dependencies,
                    actions =
                        DuaCardsActions(
                            onBack = viewModel::closeDeck,
                            onSearch = viewModel::closeDeck,
                            onToggleSaved = viewModel::toggleSaved,
                            onOpenMushaf = { dua ->
                                scope.launch { onOpenQuran(viewModel.pageOf(dua), dua.start.ayahRef()) }
                            },
                            onCleanRecitation = viewModel::recordCleanRecitation,
                            onRequestWordTiles = viewModel::requestWordTiles,
                        ),
                )
            } else {
                DuasHomeScreen(
                    state = state,
                    language = language,
                    actions =
                        DuasHomeActions(
                            onBack = onBack,
                            onQueryChange = viewModel::setQuery,
                            onFilterChange = viewModel::setFilter,
                            onOpenStarters = viewModel::openStarters,
                            onOpenGroup = viewModel::openGroup,
                            onOpenResult = viewModel::openSearchResult,
                        ),
                )
            }
        }
    }
}

private fun WordKey.ayahRef(): AyahRef = AyahRef(surah, ayah)
