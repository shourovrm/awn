package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import kotlinx.coroutines.launch

/**
 * The Go to page dialog: a page field, and surah and ayah fields that fill each other in, so typing
 * page 312 shows Maryam 96 and typing 19 and 96 shows page 312. It opens with the page field
 * focused and holding [currentPage], selected, so typing replaces it.
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
    val resolver = remember(chapters, script) { GoToPageResolver(quranText, script, chapters) }
    val scope = rememberCoroutineScope()
    val pageFocus = remember { FocusRequester() }
    var pageField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val text = currentPage.toString()
            mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
        }
    var surahField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
    var ayahField by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
    fun currentFields() = GoToFields(pageField.text, surahField.text, ayahField.text)
    fun apply(fields: GoToFields) {
        pageField = pageField.copy(text = fields.page)
        surahField = surahField.copy(text = fields.surah)
        ayahField = ayahField.copy(text = fields.ayah)
    }

    LaunchedEffect(Unit) { pageFocus.requestFocus() }
    // The surah and ayah of the prefilled page, so the three fields agree from the start.
    LaunchedEffect(resolver) {
        if (surahField.text.isEmpty()) apply(resolver.afterPageEdited(currentFields()))
    }

    val surahName = chapters.firstOrNull { it.number == surahField.text.toIntOrNull() }?.localName()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Awn.field.background, AwnShapes.control)
                    .padding(AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = stringResource(R.string.go_title), style = AwnTypography.lead)
            NumberField(
                label = stringResource(R.string.go_page),
                value = pageField,
                modifier = Modifier.focusRequester(pageFocus),
            ) { typed ->
                pageField = typed
                scope.launch { apply(resolver.afterPageEdited(currentFields())) }
            }
            NumberField(
                label = stringResource(R.string.go_surah),
                value = surahField,
                supporting = surahName,
            ) { typed ->
                surahField = typed
                scope.launch { apply(resolver.afterSurahOrAyahEdited(currentFields())) }
            }
            NumberField(label = stringResource(R.string.go_ayah), value = ayahField) { typed ->
                ayahField = typed
                scope.launch { apply(resolver.afterSurahOrAyahEdited(currentFields())) }
            }
            GoToButtons(
                target = resolver.targetPage(currentFields()),
                onGo = onGo,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun GoToButtons(target: Int?, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.go_cancel)) }
        TextButton(onClick = { target?.let(onGo) }, enabled = target != null) {
            Text(stringResource(R.string.go_action))
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onChange: (TextFieldValue) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = { typed -> onChange(typed.copy(text = typed.text.filter(Char::isDigit))) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}
