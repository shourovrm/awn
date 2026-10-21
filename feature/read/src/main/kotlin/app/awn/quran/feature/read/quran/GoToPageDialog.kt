package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import java.text.NumberFormat
import kotlinx.coroutines.launch

/**
 * The Go to page card: a page field, and surah and ayah fields that fill each other in, so typing
 * page 312 shows Maryam 96 and typing 19 and 96 shows page 312. It opens with the page field
 * focused and holding [currentPage], selected, so typing replaces it. The keyboard's action key
 * does what the Go button does. Digits typed in Bangla or Arabic-Indic work like 0-9.
 */
@Composable
internal fun GoToPageDialog(
    currentPage: Int,
    quranText: QuranTextRepository,
    script: ScriptId,
    chapters: List<Chapter>,
    onGo: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val numberFormat = remember {
        NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }
    }
    val resolver =
        remember(chapters, script) {
            GoToPageResolver(quranText, script, chapters, formatNumber = numberFormat::format)
        }
    val scope = rememberCoroutineScope()
    val pageFocus = remember { FocusRequester() }
    var pageField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val text = numberFormat.format(currentPage)
            mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
        }
    var surahField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var ayahField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    fun currentFields() = GoToFields(pageField.text, surahField.text, ayahField.text)
    fun apply(fields: GoToFields) {
        pageField = pageField.copy(text = fields.page)
        surahField = surahField.copy(text = fields.surah)
        ayahField = ayahField.copy(text = fields.ayah)
    }
    fun go() {
        resolver.targetPage(currentFields())?.let(onGo)
    }

    LaunchedEffect(Unit) { pageFocus.requestFocus() }
    // The surah and ayah of the prefilled page, so the three fields agree from the start.
    LaunchedEffect(resolver) {
        if (surahField.text.isEmpty()) apply(resolver.afterPageEdited(currentFields()))
    }

    val fields = currentFields()
    val target = resolver.targetPage(fields)

    FloatingCard(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.go_title),
            style = AwnTypography.lead,
            color = Awn.field.content,
        )
        GoField(
            label = stringResource(R.string.go_page),
            value = pageField,
            modifier = Modifier.focusRequester(pageFocus),
            onSubmit = ::go,
        ) { typed ->
            pageField = typed
            scope.launch { apply(resolver.afterPageEdited(currentFields())) }
        }
        SurahAndAyahFields(
            surahField = surahField,
            ayahField = ayahField,
            onSubmit = ::go,
            onSurahChange = { typed ->
                surahField = typed
                scope.launch { apply(resolver.afterSurahOrAyahEdited(currentFields())) }
            },
            onAyahChange = { typed ->
                ayahField = typed
                scope.launch { apply(resolver.afterSurahOrAyahEdited(currentFields())) }
            },
        )
        GoHint(fields, resolver, chapters, numberFormat)
        GoActions(canGo = target != null, onGo = ::go, onDismiss = onDismiss)
    }
}

@Composable
private fun SurahAndAyahFields(
    surahField: TextFieldValue,
    ayahField: TextFieldValue,
    onSubmit: () -> Unit,
    onSurahChange: (TextFieldValue) -> Unit,
    onAyahChange: (TextFieldValue) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        GoField(
            label = stringResource(R.string.go_surah),
            value = surahField,
            modifier = Modifier.weight(1f),
            onSubmit = onSubmit,
            onChange = onSurahChange,
        )
        GoField(
            label = stringResource(R.string.go_ayah),
            value = ayahField,
            modifier = Modifier.weight(1f),
            onSubmit = onSubmit,
            onChange = onAyahChange,
        )
    }
}

@Composable
private fun GoActions(canGo: Boolean, onGo: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(top = AwnSpacing.small)) {
        AwnButton(
            label = stringResource(R.string.go_action),
            onClick = onGo,
            enabled = canGo,
            modifier = Modifier.fillMaxWidth(),
        )
        CardTextButton(label = stringResource(R.string.go_cancel), onClick = onDismiss)
    }
}

/** One line under the fields: why the numbers do not work, else the name of the surah typed. */
@Composable
private fun GoHint(
    fields: GoToFields,
    resolver: GoToPageResolver,
    chapters: List<Chapter>,
    numberFormat: NumberFormat,
) {
    val field = Awn.field
    val problem = resolver.problemOf(fields)
    val surahName =
        chapters.firstOrNull { it.number == parseTypedNumber(fields.surah) }?.localName()
    when (problem) {
        is GoToProblem.PageOutOfRange ->
            Text(
                text =
                    stringResource(R.string.go_error_page, numberFormat.format(problem.pageCount)),
                style = AwnTypography.small,
                color = field.wrongWord,
            )
        is GoToProblem.SurahOutOfRange ->
            Text(
                text =
                    stringResource(
                        R.string.go_error_surah,
                        numberFormat.format(problem.surahCount),
                    ),
                style = AwnTypography.small,
                color = field.wrongWord,
            )
        is GoToProblem.AyahOutOfRange ->
            Text(
                text =
                    stringResource(
                        R.string.go_error_ayah,
                        numberFormat.format(problem.surah),
                        numberFormat.format(problem.ayahCount),
                    ),
                style = AwnTypography.small,
                color = field.wrongWord,
            )
        null ->
            if (surahName != null) {
                Text(text = surahName, style = AwnTypography.small, color = field.mutedContent)
            }
    }
}

/** A number field with a small label above and a soft fill, 48dp tall, no outline. */
@Composable
private fun GoField(
    label: String,
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
    onChange: (TextFieldValue) -> Unit,
) {
    val field = Awn.field
    Column(modifier) {
        Text(
            text = label,
            style = AwnTypography.small,
            color = field.mutedContent,
            modifier = Modifier.padding(bottom = AwnSpacing.small / 2),
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = AwnSpacing.touchTarget)
                    .clip(AwnShapes.searchField)
                    .background(field.softFill)
                    .padding(horizontal = AwnSpacing.medium),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { typed ->
                    onChange(typed.copy(text = typed.text.filter { it.isDigit() }))
                },
                singleLine = true,
                textStyle = AwnTypography.body.copy(color = field.content),
                cursorBrush = SolidColor(field.buttonFill),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}
