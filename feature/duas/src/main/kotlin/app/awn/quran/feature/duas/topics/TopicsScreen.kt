package app.awn.quran.feature.duas.topics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.Topic
import app.awn.quran.feature.duas.content.TopicCatalogue
import app.awn.quran.feature.duas.state.DuasState
import app.awn.quran.feature.duas.state.DuasViewModel
import app.awn.quran.feature.duas.state.inLanguage
import app.awn.quran.feature.duas.ui.DuaCardsActions
import app.awn.quran.feature.duas.ui.DuaCardsScreen
import kotlinx.coroutines.launch

private const val TOPICS_MODEL_KEY = "topics"

/**
 * Ayahs by topic: the topic list with search and category chips, one topic's parts, and the cards
 * of a part or of a whole topic, which are the dua cards with Listen, Recite and Word by word.
 * [onOpenDuasGroup] opens a group of the Duas collection; [onOpenQuran] opens the Quran screen.
 */
@Composable
fun TopicsScreen(
    dependencies: DuasDependencies,
    onBack: () -> Unit,
    onOpenQuran: (page: Int, ayah: AyahRef) -> Unit,
    onOpenDuasGroup: (groupKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topics by
        produceState<TopicCatalogue?>(null, dependencies) { value = dependencies.loadTopics() }
    val duas by
        produceState<DuaCatalogue?>(null, dependencies) { value = dependencies.loadCatalogue() }
    val viewModel = rememberTopicsViewModel(dependencies)
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.onLanguageChanged(language) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openTopicKey by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = state.deck != null || openTopicKey != null) {
        if (state.deck != null) viewModel.closeDeck() else openTopicKey = null
    }

    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        Box(modifier.fillMaxSize()) {
            val catalogue = topics ?: return@Box
            val openTopic = catalogue.topics.firstOrNull { it.key == openTopicKey }
            when {
                state.deck != null && state.deckDuas.isNotEmpty() ->
                    TopicCards(state, catalogue, language, dependencies, viewModel, onOpenQuran)
                openTopic != null ->
                    TopicPage(
                        openTopic,
                        relatedDuas(openTopic, duas, language),
                        language,
                        viewModel,
                    ) { action ->
                        when (action) {
                            TopicPageExit.Back -> openTopicKey = null
                            is TopicPageExit.Duas -> onOpenDuasGroup(action.groupKey)
                        }
                    }
                else ->
                    TopicsHomeScreen(
                        topics = catalogue,
                        category = category,
                        cardState = state,
                        language = language,
                        actions =
                            TopicsHomeActions(
                                onBack = onBack,
                                onQueryChange = viewModel::setQuery,
                                onCategoryChange = { category = it },
                                onOpenTopic = { openTopicKey = it.key },
                                onOpenResult = viewModel::openSearchResult,
                            ),
                    )
            }
        }
    }
}

/** The card model of the topics, kept apart from the Duas one by its key. */
@Composable
private fun rememberTopicsViewModel(dependencies: DuasDependencies): DuasViewModel =
    viewModel(
        key = TOPICS_MODEL_KEY,
        factory =
            viewModelFactory {
                initializer {
                    DuasViewModel(
                        dependencies.copy(
                            loadCatalogue = { dependencies.loadTopics().asCardCatalogue() },
                        ),
                    )
                }
            },
    )

/** How the topic page is left: back to the list, or to a Duas group. */
private sealed interface TopicPageExit {
    data object Back : TopicPageExit

    data class Duas(val groupKey: String) : TopicPageExit
}

@Composable
private fun TopicPage(
    topic: Topic,
    duasGroup: RelatedDuas?,
    language: String,
    viewModel: DuasViewModel,
    onExit: (TopicPageExit) -> Unit,
) {
    TopicPartsScreen(
        topic = topic,
        duasGroup = duasGroup,
        language = language,
        actions =
            TopicPartsActions(
                onBack = { onExit(TopicPageExit.Back) },
                onOpenAll = {
                    viewModel.openGroups(
                        topic.parts.map { TopicCatalogue.partGroupKey(topic, it) },
                        deckKey = topic.key,
                    )
                },
                onOpenPart = { part ->
                    viewModel.openGroup(TopicCatalogue.partGroupKey(topic, part))
                },
                onOpenDuas = { onExit(TopicPageExit.Duas(it)) },
            ),
    )
}

@Composable
private fun TopicCards(
    state: DuasState,
    topics: TopicCatalogue,
    language: String,
    dependencies: DuasDependencies,
    viewModel: DuasViewModel,
    onOpenQuran: (page: Int, ayah: AyahRef) -> Unit,
) {
    val deck = checkNotNull(state.deck)
    val scope = rememberCoroutineScope()
    val cards = state.deckDuas
    DuaCardsScreen(
        title = deckTitle(deck.groupKey, topics, state, language),
        duas = cards,
        startIndex = deck.startIndex.coerceIn(0, cards.lastIndex),
        state = state,
        dependencies = dependencies,
        actions =
            DuaCardsActions(
                onBack = viewModel::closeDeck,
                onSearch = viewModel::closeDeck,
                onToggleSaved = viewModel::toggleSaved,
                onOpenMushaf = { card ->
                    scope.launch {
                        onOpenQuran(
                            viewModel.pageOf(card),
                            AyahRef(card.start.surah, card.start.ayah),
                        )
                    }
                },
                onCleanRecitation = viewModel::recordCleanRecitation,
                onRequestWordTiles = viewModel::requestWordTiles,
            ),
    )
}

/** A whole topic's deck is named after the topic; a part's deck after "Topic · Part". */
private fun deckTitle(
    deckKey: String?,
    topics: TopicCatalogue,
    state: DuasState,
    language: String,
): String {
    val topic = topics.topics.firstOrNull { it.key == deckKey }
    if (topic != null) return topic.name.inLanguage(language)
    val part = state.catalogue?.groups?.firstOrNull { it.key == deckKey }
    return part?.name?.inLanguage(language).orEmpty()
}

private fun relatedDuas(topic: Topic, duas: DuaCatalogue?, language: String): RelatedDuas? {
    val group = duas?.groups?.firstOrNull { it.key == topic.duasGroupKey }
    return if (duas == null || group == null) null
    else RelatedDuas(group.key, group.name.inLanguage(language), duas.duasIn(group.key).size)
}
