package app.awn.quran.feature.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val FIELD_ALPHA = 0.12f

/** One entry of the sheet's list: a group heading or a reciter. */
private sealed interface SheetRow {
    data class Heading(val titleRes: Int) : SheetRow

    data class Entry(val reciter: Reciter) : SheetRow
}

/**
 * The reciter sheet: a search field, then "Downloaded for this page" (only when [page] is given),
 * "With downloads" and "All", with a tick on the current reciter. Choosing a reciter selects it and
 * closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterSheet(feature: AudioFeature, page: Int?, onDismiss: () -> Unit) {
    val viewModel: ReciterSheetViewModel = viewModel(factory = feature.viewModelFactory)
    LaunchedEffect(page) { viewModel.load(page) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val field = Awn.field
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = field.background,
        contentColor = field.content,
    ) {
        SearchField(state.query, viewModel::search)
        val rows = sheetRows(state.groups)
        LazyColumn {
            items(rows) { row ->
                when (row) {
                    is SheetRow.Heading ->
                        Text(
                            text = stringResource(row.titleRes),
                            style = AwnTypography.small,
                            modifier =
                                Modifier.padding(
                                    horizontal = AwnSpacing.screenMargin,
                                    vertical = AwnSpacing.small,
                                ),
                        )
                    is SheetRow.Entry ->
                        ReciterEntry(
                            reciter = row.reciter,
                            isSelected = row.reciter.id == state.selectedId,
                            onClick = {
                                viewModel.select(row.reciter.id)
                                onDismiss()
                            },
                        )
                }
            }
        }
    }
}

private fun sheetRows(groups: ReciterGroups): List<SheetRow> = buildList {
    fun addGroup(titleRes: Int, reciters: List<Reciter>) {
        if (reciters.isEmpty()) return
        add(SheetRow.Heading(titleRes))
        reciters.forEach { add(SheetRow.Entry(it)) }
    }
    addGroup(R.string.reciter_group_page, groups.downloadedForPage)
    addGroup(R.string.reciter_group_downloads, groups.withDownloads)
    addGroup(R.string.reciter_group_all, groups.all)
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val field = Awn.field
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = AwnSpacing.screenMargin)
                .heightIn(min = AwnSpacing.touchTarget)
                .clip(AwnShapes.control)
                .background(field.content.copy(alpha = FIELD_ALPHA))
                .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListenIcon(R.drawable.ic_listen_search, null)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = AwnTypography.body.copy(color = field.content),
            cursorBrush = SolidColor(field.content),
            modifier = Modifier.weight(1f).padding(start = AwnSpacing.small),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.reciter_search),
                        style = AwnTypography.body,
                        color = field.content.copy(alpha = HINT_ALPHA),
                    )
                }
                inner()
            },
        )
    }
}

private const val HINT_ALPHA = 0.6f

@Composable
private fun ReciterEntry(reciter: Reciter, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = reciter.name, style = AwnTypography.body, modifier = Modifier.weight(1f))
        if (isSelected) {
            ListenIcon(R.drawable.ic_listen_check, stringResource(R.string.reciter_current))
        }
    }
}
