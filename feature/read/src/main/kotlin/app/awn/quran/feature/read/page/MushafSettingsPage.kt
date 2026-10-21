package app.awn.quran.feature.read.page

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.PackListKind

/** The page of the Mushaf that every preview image shows. */
private const val PREVIEW_PAGE = 50

/** What the settings page shows about one page type besides its installed state. */
internal data class PageTypeText(
    @StringRes val name: Int,
    @StringRes val year: Int,
    @DrawableRes val preview: Int,
)

/** The name of the print [script] as the app shows it, for summaries outside this module. */
@StringRes fun pageTypeNameRes(script: ScriptId): Int = pageTypeText(script).name

internal fun pageTypeText(script: ScriptId): PageTypeText =
    when (script) {
        ScriptId.V1 ->
            PageTypeText(
                R.string.mushaf_type_v1,
                R.string.mushaf_year_v1,
                R.drawable.mushaf_preview_v1,
            )
        ScriptId.V4Tajweed ->
            PageTypeText(
                R.string.mushaf_type_v4,
                R.string.mushaf_year_v4,
                R.drawable.mushaf_preview_v4,
            )
        else ->
            PageTypeText(
                R.string.mushaf_type_v2,
                R.string.mushaf_year_v2,
                R.drawable.mushaf_preview_v2,
            )
    }

/**
 * The Mushaf page of Settings: a preview of page 50 in the print in use, a row that opens the list
 * of prints (see `PackListKind.PageTypes`) and the "Page details" switch for the surah name, juz
 * and page number around the Mushaf page. The Settings hub links to this composable.
 */
@Composable
fun MushafSettingsPage(
    dependencies: ReadDependencies,
    onOpenPackList: (PackListKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MushafSettingsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { MushafSettingsViewModel(dependencies) } },
        )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val text = pageTypeText(settings.script)
    val name = stringResource(text.name)

    Column(modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(text.preview),
            contentDescription =
                stringResource(R.string.mushaf_preview_description, name, PREVIEW_PAGE),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.fillMaxWidth()
                    .height(AwnSpacing.previewHeight)
                    .padding(top = AwnSpacing.medium),
        )
        SettingsLinkRow(
            label = stringResource(R.string.mushaf_print),
            value = stringResource(R.string.mushaf_print_value, name, stringResource(text.year)),
            onClick = { onOpenPackList(PackListKind.PageTypes) },
            rowKey = SettingRowKeys.MUSHAF_PRINT,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.mushaf_show_page_info),
            checked = settings.showPageInfo,
            onCheckedChange = viewModel::setShowPageInfo,
            rowKey = SettingRowKeys.MUSHAF_PAGE_INFO,
        )
    }
}
