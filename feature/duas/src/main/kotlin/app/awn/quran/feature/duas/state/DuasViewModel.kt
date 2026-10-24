package app.awn.quran.feature.duas.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.card.CardRequest
import app.awn.quran.feature.duas.card.DuaCardLoader
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.DuaSearch
import app.awn.quran.feature.duas.content.DuaSearchEntry
import app.awn.quran.feature.duas.content.DuaSearchHit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds what the Duas home and the cards show, and what a tap on either changes. Each tap on the
 * two screens is one function here, which is why there are more than the usual number.
 */
@Suppress("TooManyFunctions")
class DuasViewModel(
    private val dependencies: DuasDependencies,
    private val search: (String, List<DuaSearchEntry>) -> List<DuaSearchHit> = DuaSearch::search,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val loader = DuaCardLoader(dependencies.quranText, dependencies.translations)
    private val language = MutableStateFlow(DEFAULT_LANGUAGE)
    private val mutableState = MutableStateFlow(DuasState())

    val state: StateFlow<DuasState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { readCatalogue() }
        viewModelScope.launch {
            dependencies.progress.progress.collect { progress ->
                mutableState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch { keepCardsCurrent() }
    }

    private suspend fun readCatalogue() {
        val catalogue = withContext(workDispatcher) { dependencies.loadCatalogue() }
        mutableState.update { it.copy(catalogue = catalogue) }
    }

    // The cards depend on the ticked translation and on the app language, so they are read again
    // when either changes.
    private suspend fun keepCardsCurrent() {
        val catalogues = mutableState.map { it.catalogue }.filterNotNull().distinctUntilChanged()
        val firstTranslation =
            dependencies.enabledTranslations.map { it.firstOrNull() }.distinctUntilChanged()
        combine(catalogues, firstTranslation, language) { catalogue, translationId, appLanguage ->
                catalogue to CardRequest(translationId, appLanguage)
            }
            .collectLatest { (catalogue, request) ->
                val cards = withContext(workDispatcher) { loader.load(catalogue.duas, request) }
                mutableState.update { current ->
                    current
                        .copy(cards = cards.associateBy { it.dua.id }, wordTiles = emptyMap())
                        .withSearchHits(request.language)
                }
            }
    }

    /** Tells the model the app language, "en" or "bn"; names and meanings follow it. */
    fun onLanguageChanged(appLanguage: String) {
        language.value = appLanguage
    }

    fun setFilter(filter: DuasFilter) = mutableState.update { it.copy(filter = filter) }

    fun setQuery(query: String) = mutableState.update {
        it.copy(query = query).withSearchHits(language.value)
    }

    fun openGroup(groupKey: String) = openDeck { catalogue, state ->
        val duas = duasOfGroup(catalogue, groupKey, state.filter, state.progress)
        DuaDeck(groupKey, duas.map { it.id }, startIndex = 0)
    }

    fun openStarters() = openDeck { catalogue, state ->
        val duas = starterDuas(catalogue, state.filter, state.progress)
        DuaDeck(groupKey = null, duas.map { it.id }, startIndex = 0)
    }

    /** Opens the card of a search result among all the duas of its group. */
    fun openSearchResult(dua: Dua) = openDeck { catalogue, _ ->
        val ids = catalogue.duasIn(dua.groupKey).map { it.id }
        DuaDeck(dua.groupKey, ids, startIndex = ids.indexOf(dua.id))
    }

    private fun openDeck(build: (DuaCatalogue, DuasState) -> DuaDeck) =
        mutableState.update { state ->
            val catalogue = state.catalogue ?: return@update state
            state.copy(deck = build(catalogue, state))
        }

    fun closeDeck() = mutableState.update { it.copy(deck = null) }

    fun toggleSaved(dua: Dua) {
        viewModelScope.launch { dependencies.progress.toggleSaved(dua.id) }
    }

    fun recordCleanRecitation(dua: Dua) {
        viewModelScope.launch { dependencies.progress.recordCleanRecitation(dua.id) }
    }

    /** Reads the word tiles of [dua] unless they are already read. */
    fun requestWordTiles(dua: Dua) {
        val card = mutableState.value.cards[dua.id] ?: return
        if (dua.id in mutableState.value.wordTiles) return
        viewModelScope.launch {
            val tiles = withContext(workDispatcher) { loader.wordTiles(card, language.value) }
            mutableState.update { it.copy(wordTiles = it.wordTiles + (dua.id to tiles)) }
        }
    }

    /** The page of the dua's first ayah in the print the reader shows. */
    suspend fun pageOf(dua: Dua): Int {
        val script: ScriptId = dependencies.pageScript.first()
        return loader.pageOf(dua, script)
    }

    private fun DuasState.withSearchHits(appLanguage: String): DuasState {
        val catalogue = catalogue
        if (query.isBlank() || catalogue == null || cards.isEmpty()) {
            return copy(searchHits = emptyList())
        }
        val groupNames = catalogue.groups.associate { it.key to it.name.inLanguage(appLanguage) }
        val entries =
            catalogue.duas.mapNotNull { dua ->
                val card = cards[dua.id] ?: return@mapNotNull null
                DuaSearchEntry(
                    dua = dua,
                    arabic = card.duaArabic,
                    translation = card.translation,
                    groupName = groupNames.getValue(dua.groupKey),
                )
            }
        return copy(searchHits = search(query, entries))
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
