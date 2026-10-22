package app.awn.quran.feature.learn.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.feature.learn.LearnBar
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.LessonReference

/** The choices the first screen of the course collects. */
internal data class FirstChoices(val includeLetters: Boolean, val minutes: Int)

/** What the course starts with before the user has chosen anything. */
internal val DEFAULT_FIRST_CHOICES = FirstChoices(includeLetters = false, minutes = 10)

private const val SHORT_DAY_MINUTES = 5
private const val MEDIUM_DAY_MINUTES = 10
private const val SHORT_QUIZ_QUESTIONS = 10
private const val LONG_QUIZ_QUESTIONS = 20

private val TIME_CHOICES = listOf(5, 10, 15)

/**
 * The first screen of the word course: whether the user reads Arabic yet, and the time each day.
 */
@Composable
internal fun FirstOpenScreen(
    onContinue: (FirstChoices) -> Unit,
    modifier: Modifier = Modifier,
    initial: FirstChoices = DEFAULT_FIRST_CHOICES,
) {
    var includeLetters by remember { mutableStateOf(initial.includeLetters) }
    var minutes by remember { mutableStateOf(initial.minutes) }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.first_title),
                style = AwnTypography.title,
                color = Awn.field.content,
            )
            Text(
                text = stringResource(R.string.first_intro),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
            GroupHeading(stringResource(R.string.first_letters_heading))
            RadioChoiceRow(
                title = stringResource(R.string.first_letters_yes),
                detail = stringResource(R.string.first_letters_yes_detail),
                isSelected = !includeLetters,
                onClick = { includeLetters = false },
            )
            RadioChoiceRow(
                title = stringResource(R.string.first_letters_no),
                detail = stringResource(R.string.first_letters_no_detail),
                isSelected = includeLetters,
                onClick = { includeLetters = true },
            )
            GroupHeading(stringResource(R.string.first_time_heading))
            TIME_CHOICES.forEach { choice ->
                RadioChoiceRow(
                    title = stringResource(R.string.course_minutes, choice),
                    detail = timeDetail(choice),
                    isSelected = minutes == choice,
                    onClick = { minutes = choice },
                )
            }
        }
        AwnButton(
            label = stringResource(R.string.first_continue),
            onClick = { onContinue(FirstChoices(includeLetters, minutes)) },
            modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
        )
    }
}

@Composable
private fun timeDetail(minutes: Int): String =
    stringResource(
        when (minutes) {
            SHORT_DAY_MINUTES -> R.string.first_time_5_detail
            MEDIUM_DAY_MINUTES -> R.string.first_time_10_detail
            else -> R.string.first_time_15_detail
        },
    )

@Composable
internal fun GroupHeading(text: String) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.medium, bottom = AwnSpacing.small),
    )
}

/** A choice with a ring, a title and an optional detail; the whole row selects it. */
@Composable
internal fun RadioChoiceRow(
    title: String,
    detail: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowTall)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        RadioMark(isSelected)
        Column {
            Text(text = title, style = AwnTypography.bodyStrong, color = Awn.field.content)
            detail?.let { Text(text = it, style = AwnTypography.small, color = Awn.field.content) }
        }
    }
}

/** What a self-started quiz practises. */
internal enum class QuizScope {
    All,
    ThisWeek,
    Weak,
}

/** How many questions a self-started quiz asks; [Int.MAX_VALUE] means until the user stops. */
internal enum class QuizLength(val questions: Int) {
    Ten(SHORT_QUIZ_QUESTIONS),
    Twenty(LONG_QUIZ_QUESTIONS),
    UntilStop(Int.MAX_VALUE),
}

internal data class QuizCounts(val all: Int, val thisWeek: Int, val weak: Int)

// Three groups of choices and the start button.
@Suppress("LongMethod")
@Composable
internal fun QuizStartScreen(
    counts: QuizCounts,
    onStart: (QuizScope, QuizLength) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scope by remember { mutableStateOf(QuizScope.All) }
    var length by remember { mutableStateOf(QuizLength.Ten) }
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.quiz_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
        ) {
            GroupHeading(stringResource(R.string.quiz_practise))
            RadioChoiceRow(
                stringResource(R.string.quiz_all),
                pluralStringResource(R.plurals.quiz_words, counts.all, counts.all),
                scope == QuizScope.All,
                { scope = QuizScope.All },
            )
            RadioChoiceRow(
                stringResource(R.string.quiz_week),
                pluralStringResource(R.plurals.quiz_words, counts.thisWeek, counts.thisWeek),
                scope == QuizScope.ThisWeek,
                { scope = QuizScope.ThisWeek },
            )
            RadioChoiceRow(
                stringResource(R.string.quiz_weak),
                pluralStringResource(R.plurals.quiz_weak_detail, counts.weak, counts.weak),
                scope == QuizScope.Weak,
                { scope = QuizScope.Weak },
            )
            GroupHeading(stringResource(R.string.quiz_length))
            RadioChoiceRow(
                stringResource(R.string.quiz_10),
                null,
                length == QuizLength.Ten,
                { length = QuizLength.Ten },
            )
            RadioChoiceRow(
                stringResource(R.string.quiz_20),
                null,
                length == QuizLength.Twenty,
                { length = QuizLength.Twenty },
            )
            RadioChoiceRow(
                stringResource(R.string.quiz_until_stop),
                null,
                length == QuizLength.UntilStop,
                { length = QuizLength.UntilStop },
            )
        }
        AwnButton(
            label = stringResource(R.string.quiz_start),
            onClick = { onStart(scope, length) },
            enabled = counts.all > 0,
            modifier = Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin),
        )
    }
}

/** Where the lessons and the word meanings come from, with the licence of the lesson text. */
@Composable
internal fun SourcesScreen(
    references: List<LessonReference>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize()) {
        LearnBar(
            title = stringResource(R.string.sources_title),
            startLinkLabel = stringResource(R.string.back),
            onStartLinkClick = onBack,
        )
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            GroupHeading(stringResource(R.string.sources_lessons))
            Text(
                text = stringResource(R.string.sources_lessons_licence),
                style = AwnTypography.small,
                color = Awn.field.content,
            )
            references.forEach { reference ->
                SourceItem(
                    title = reference.label,
                    detail =
                        stringResource(
                            if (reference.label.startsWith(WRIGHT)) R.string.sources_wright_note
                            else R.string.sources_wikibooks_note,
                        ),
                    linkLabel = stringResource(R.string.sources_open_link, reference.label),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, reference.url.toUri()))
                    },
                )
            }
            BookSources()
            GroupHeading(stringResource(R.string.sources_words))
            SourceItem(
                stringResource(R.string.sources_quran_com),
                stringResource(R.string.sources_quran_com_detail),
                null,
                null,
            )
            SourceItem(
                stringResource(R.string.sources_font),
                stringResource(R.string.sources_font_detail),
                null,
                null,
            )
        }
    }
}

/** The Qaida, the recordings of the academy and the tajweed book that the units follow. */
@Composable
private fun BookSources() {
    val context = LocalContext.current
    SourceItem(
        stringResource(R.string.sources_qaida),
        stringResource(R.string.sources_qaida_detail),
        null,
        null,
    )
    SourceItem(
        stringResource(R.string.sources_academy),
        stringResource(R.string.sources_academy_detail),
        stringResource(
            R.string.sources_open_link,
            stringResource(R.string.sources_academy),
        ),
        { context.startActivity(Intent(Intent.ACTION_VIEW, ACADEMY_URL.toUri())) },
    )
    SourceItem(
        stringResource(R.string.sources_tajweed_book),
        stringResource(R.string.sources_tajweed_book_detail),
        null,
        null,
    )
}

private const val WRIGHT = "Wright"
private const val ACADEMY_URL = "https://www.youtube.com/channel/UCX766Oi2anGGIkIgMJXu9ng"

@Composable
private fun SourceItem(title: String, detail: String, linkLabel: String?, onClick: (() -> Unit)?) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .then(
                    if (onClick != null)
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = linkLabel,
                            onClick = onClick,
                        )
                    else Modifier,
                )
                .padding(vertical = AwnSpacing.small),
    ) {
        Text(text = title, style = AwnTypography.bodyStrong, color = Awn.field.content)
        Text(text = detail, style = AwnTypography.small, color = Awn.field.mutedContent)
    }
}
