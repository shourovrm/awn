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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
 * page 312 shows Maryam 96 and typing 19 and 96 shows page 312.
 */
@Composable
internal fun GoToPageDialog(
    quranText: QuranTextRepository,
    script: ScriptId,
    chapters: List<Chapter>,
    onGo: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolver = remember(chapters, script) { GoToPageResolver(quranText, script, chapters) }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("") }
    var surah by rememberSaveable { mutableStateOf("") }
    var ayah by rememberSaveable { mutableStateOf("") }
    fun apply(fields: GoToFields) {
        page = fields.page
        surah = fields.surah
        ayah = fields.ayah
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Awn.field.background, AwnShapes.control)
                    .padding(AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = stringResource(R.string.go_title), style = AwnTypography.lead)
            NumberField(stringResource(R.string.go_page), page) { typed ->
                page = typed
                scope.launch { apply(resolver.afterPageEdited(GoToFields(typed, surah, ayah))) }
            }
            NumberField(
                label = stringResource(R.string.go_surah),
                value = surah,
                supporting = chapters.firstOrNull { it.number == surah.toIntOrNull() }?.localName(),
            ) { typed ->
                surah = typed
                scope.launch {
                    apply(resolver.afterSurahOrAyahEdited(GoToFields(page, typed, ayah)))
                }
            }
            NumberField(stringResource(R.string.go_ayah), ayah) { typed ->
                ayah = typed
                scope.launch {
                    apply(resolver.afterSurahOrAyahEdited(GoToFields(page, surah, typed)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.go_cancel)) }
                TextButton(
                    onClick = { resolver.targetPage(GoToFields(page, surah, ayah))?.let(onGo) },
                    enabled = resolver.targetPage(GoToFields(page, surah, ayah)) != null,
                ) {
                    Text(stringResource(R.string.go_action))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter(Char::isDigit)) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
