@file:Suppress("TooManyFunctions") // One small composable per part of the page.

package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.LevelSection
import app.awn.quran.feature.learn.course.LevelState
import app.awn.quran.feature.learn.course.PathRow
import app.awn.quran.feature.learn.course.PathState
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.words.LearnSets
import kotlin.math.roundToInt

private const val PERCENT = 100
private const val GRID_COLUMNS = 5
private val RING_WIDTH = 2.dp

/**
 * The Quran words page: how many words the user knows, today's set with its ten words, the pages
 * the user reads and memorises, and the sets. Only words are listed here; the Reading, Tajweed and
 * Grammar lessons have their own tracks, and a grammar lesson shows as one line after the set that
 * brings it.
 */
@Composable
internal fun WordsTrackScreen(
    state: WordsTrackState,
    actions: WordsTrackActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openLevelNumbers by rememberSaveable { mutableStateOf(state.levels.currentLevelNumbers()) }
    val texts = rememberWordTexts(state.keysToDraw(openLevelNumbers))
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = trackName(LearnTrack.Words),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            SummaryBlock(state.hub)
            TodaySetCard(state, actions.hub, texts)
            if (state.pageLinks.isNotEmpty()) {
                Heading(stringResource(R.string.words_pages_heading))
                state.pageLinks.forEach { PageLink(it, actions.onOpenPageWords) }
            }
            Heading(stringResource(R.string.words_sets_heading))
            state.levels.forEach { section ->
                val isOpen = section.level.number in openLevelNumbers
                LevelHeader(section) {
                    val number = section.level.number
                    openLevelNumbers =
                        if (isOpen) openLevelNumbers - number else openLevelNumbers + number
                }
                if (isOpen) {
                    LevelPath { section.rows.forEach { SetsPathRow(it, state, actions, texts) } }
                }
            }
            WordListLinks(state.hub, actions.hub)
        }
    }
}

@Composable
private fun SummaryBlock(hub: HubState) {
    val field = Awn.field
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.words_summary_counts, hub.wordsKnown, hub.wordsMet),
            style = AwnTypography.bodyStrong,
            color = field.content,
        )
        Text(
            text =
                stringResource(
                    R.string.course_coverage_title,
                    (hub.coverage * PERCENT).roundToInt(),
                ),
            style = AwnTypography.body,
            color = field.mutedContent,
        )
    }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.packProgressHeight)
                .clip(AwnShapes.wordBlock)
                .background(field.softFill),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(hub.coverage.toFloat().coerceIn(0f, 1f))
                    .heightIn(min = AwnSpacing.packProgressHeight)
                    .background(field.buttonFill),
        )
    }
}

@Composable
private fun TodaySetCard(state: WordsTrackState, actions: HubActions, texts: Map<WordKey, String>) {
    val field = Awn.field
    val plan = state.hub.plan
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = AwnSpacing.small)
                .clip(AwnShapes.control)
                .background(field.barBackground)
                .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.words_today_set, state.todaySet),
                style = AwnTypography.lead,
                color = field.onBar,
            )
            Text(
                text =
                    if (plan.isEmpty) stringResource(R.string.course_today_done)
                    else planSummary(plan),
                style = AwnTypography.small,
                color = field.onBar,
            )
        }
        TodayWordGrid(state.todayWords, texts)
        if (state.hub.meanings != MeaningsState.Ready) {
            MeaningsNeeded(state.hub.meanings, actions.onDownloadMeanings)
        }
        if (!plan.isEmpty) {
            AwnButton(
                label = stringResource(R.string.words_start_minutes, state.hub.sessionMinutes),
                onClick = actions.onStart,
                modifier = Modifier.fillMaxWidth(),
                containerColor = field.onBar,
                contentColor = field.barBackground,
            )
        }
    }
}

@Composable
private fun TodayWordGrid(words: List<SetWordCell>, texts: Map<WordKey, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        words.chunked(GRID_COLUMNS).forEach { rowWords ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                rowWords.forEach { word ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = texts[word.key].orEmpty(),
                            style = arabicStyle(AwnTypography.lead),
                            color = Awn.field.onBar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        word.meaning?.let {
                            Text(
                                text = it,
                                style = AwnTypography.caption.copy(textAlign = TextAlign.Center),
                                color = Awn.field.onBar,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // A short last row keeps its cells the width of the full rows.
                repeat(GRID_COLUMNS - rowWords.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PageLink(link: PageLinkRow, onOpen: (Int) -> Unit) {
    val isLastRead = link.kind == PageLinkKind.LastRead
    SettingsLinkRow(
        label =
            if (isLastRead) stringResource(R.string.words_last_read, link.page)
            else stringResource(R.string.words_hifz_today, link.page),
        value =
            pluralStringResource(
                R.plurals.words_page_detail,
                link.wordCount,
                link.wordCount,
                link.knownCount,
            ),
        icon = if (isLastRead) R.drawable.ic_learn_page else R.drawable.ic_learn_hifz,
        onClick = { onOpen(link.page) },
    )
}

@Composable
private fun SetsPathRow(
    row: PathRow,
    state: WordsTrackState,
    actions: WordsTrackActions,
    texts: Map<WordKey, String>,
) {
    when (row) {
        is PathRow.SetStep -> {
            val title =
                if (row.state == PathState.Done || row.metCount > 0) {
                    stringResource(
                        R.string.words_set_known,
                        row.number,
                        row.knownCount,
                        LearnSets.SET_SIZE,
                    )
                } else {
                    stringResource(R.string.words_set_plain, row.number)
                }
            val isToday = row.number == state.todaySet
            SetRow(
                pathState = row.state,
                title = title,
                preview = previewText(state.previewKeys[row.number], texts),
                isToday = isToday,
                onStart = actions.hub.onStart.takeIf { isToday && !state.hub.plan.isEmpty },
                onClick = { actions.onOpenSet(row.number) },
            )
        }
        is PathRow.LessonStep -> GrammarLine(row, state, actions.hub)
        // Folded and "later" rows belong to the hub path; this page groups by level instead.
        is PathRow.Folded,
        is PathRow.Later -> Unit
    }
}

/** A level's header row; a finished level shows a check, the open one a ring. */
@Composable
private fun LevelHeader(section: LevelSection, onToggle: () -> Unit) {
    val markerState =
        when (section.state) {
            LevelState.Finished -> PathState.Done
            LevelState.Current -> PathState.Next
            LevelState.Later -> PathState.Upcoming
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowTall)
                .clip(AwnShapes.control)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        SetMarker(markerState, section.level.number)
        Text(
            text =
                stringResource(
                    R.string.words_level_title,
                    section.level.number,
                    formatCount(section.level.wordCount),
                ),
            style = AwnTypography.bodyStrong,
            color = Awn.field.content,
        )
    }
}

private fun List<LevelSection>.currentLevelNumbers(): Set<Int> = filter {
    it.state == LevelState.Current
}
    .map { it.level.number }
    .toSet()

@Composable
private fun GrammarLine(row: PathRow.LessonStep, state: WordsTrackState, actions: HubActions) {
    val title = state.hub.lessonTitles[row.lessonId].orEmpty()
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button) { actions.onLesson(row.lessonId) }
                .padding(start = PATH_DOT_COLUMN + AwnSpacing.small),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.words_grammar_after, title, row.triggerSet ?: 0),
            style = AwnTypography.small,
            color = Awn.field.mutedContent,
        )
    }
}

/**
 * One set on the path: its dot on the line, "Set 3 · 2 of 10 known" and its first words in Arabic.
 * Today's set is outlined with a Start button; sets after it are muted but still open.
 */
@Composable
private fun SetRow(
    pathState: PathState,
    title: String,
    preview: String?,
    isToday: Boolean,
    onStart: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val field = Awn.field
    val isMuted = pathState == PathState.Upcoming && !isToday
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowTall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The dot sits in its own column, outside the outlined card, centred on the path line.
        PathDot(pathState, isToday)
        Row(
            modifier =
                Modifier.weight(1f)
                    .clip(AwnShapes.control)
                    .then(
                        if (isToday) Modifier.border(PATH_LINE, field.buttonFill, AwnShapes.control)
                        else Modifier,
                    )
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(vertical = AwnSpacing.small, horizontal = AwnSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AwnTypography.bodyStrong,
                    color = if (isMuted) field.mutedContent else field.content,
                )
                preview?.let {
                    Text(
                        text = it,
                        style = arabicStyle(AwnTypography.body),
                        color = field.mutedContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            onStart?.let {
                AwnButton(
                    label = stringResource(R.string.words_set_start),
                    onClick = it,
                    modifier = Modifier.padding(start = AwnSpacing.small),
                )
            }
        }
    }
}

/**
 * The rows of an open level with one vertical line behind them, so the line is not cut by the
 * grammar lines or by the gaps between rows.
 */
@Composable
private fun LevelPath(content: @Composable () -> Unit) {
    val lineColour = Awn.field.softFill
    Column(
        modifier =
            Modifier.fillMaxWidth().drawBehind {
                val lineX = PATH_DOT_COLUMN.toPx() / 2
                val inset = PATH_LINE_INSET.toPx()
                drawLine(
                    lineColour,
                    Offset(lineX, inset),
                    Offset(lineX, size.height - inset),
                    PATH_LINE.toPx(),
                )
            },
    ) {
        content()
    }
}

/** A done set is a green dot, today's set a larger ochre one, later sets a pale dot. */
@Composable
private fun PathDot(pathState: PathState, isToday: Boolean) {
    val field = Awn.field
    val colour =
        when {
            isToday -> AwnColors.ochre
            pathState == PathState.Done -> field.buttonFill
            else -> field.softFill
        }
    val size = if (isToday) PATH_TODAY_DOT else PATH_DOT
    Box(Modifier.width(PATH_DOT_COLUMN), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(colour))
    }
}

private val PATH_DOT_COLUMN = AwnSpacing.pathDotColumn
private val PATH_DOT = AwnSpacing.pathDot
private val PATH_TODAY_DOT = AwnSpacing.pathTodayDot
private val PATH_LINE = 2.dp
private val PATH_LINE_INSET = AwnSpacing.small

/**
 * Done is a filled disc with a check, the next set a ring with its number, later sets a faint ring.
 */
@Composable
private fun SetMarker(state: PathState, number: Int) {
    val field = Awn.field
    val base = Modifier.size(AwnSpacing.extraLarge).clip(CircleShape)
    val markerModifier =
        when (state) {
            PathState.Done -> base.background(field.buttonFill)
            PathState.Next -> base.border(RING_WIDTH, field.content, CircleShape)
            PathState.Upcoming -> base.border(RING_WIDTH, field.softFill, CircleShape)
        }
    Box(modifier = markerModifier, contentAlignment = Alignment.Center) {
        if (state == PathState.Done) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = null,
                tint = field.onButton,
                modifier = Modifier.size(AwnSpacing.smallIconSize),
            )
        } else {
            Text(
                text = number.toString(),
                style = AwnTypography.small,
                color = if (state == PathState.Next) field.content else field.mutedContent,
            )
        }
    }
}

@Composable
private fun WordListLinks(hub: HubState, actions: HubActions) {
    SettingsLinkRow(
        label = stringResource(R.string.course_word_list),
        value =
            pluralStringResource(
                R.plurals.course_word_list_detail,
                hub.wordsMet,
                hub.wordsMet,
                hub.wordsKnown,
            ),
        icon = R.drawable.ic_learn_list,
        onClick = actions.onWordList,
    )
    SettingsLinkRow(
        label = stringResource(R.string.course_quiz),
        icon = R.drawable.ic_learn_quiz,
        onClick = actions.onQuiz,
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.medium),
    )
}

/** The first words of a set, in one line; null until their texts are loaded. */
private fun previewText(keys: List<WordKey>?, texts: Map<WordKey, String>): String? {
    val words = keys.orEmpty().mapNotNull { texts[it] }
    return if (words.isEmpty()) null else words.joinToString(" ")
}

/** [style] in the learning Arabic font, right to left. */
@Composable
internal fun arabicStyle(style: TextStyle): TextStyle {
    val environment = LocalLearnEnvironment.current
    return style.copy(
        fontFamily = environment?.arabic?.fontFamily ?: style.fontFamily,
        textDirection = TextDirection.Rtl,
    )
}
