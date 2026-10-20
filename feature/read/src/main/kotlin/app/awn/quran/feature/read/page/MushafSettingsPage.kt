package app.awn.quran.feature.read.page

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadDependencies
import app.awn.quran.feature.read.settings.failureMessage

private const val PERCENT = 100
private const val INACTIVE_DOT_ALPHA = 0.3f

/** The page of the Mushaf that every preview image shows. */
private const val PREVIEW_PAGE = 50

/** What the settings page shows about one page type besides its installed state. */
internal data class PageTypeText(
    @StringRes val name: Int,
    @StringRes val year: Int,
    @DrawableRes val preview: Int,
)

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
 * The Mushaf page of Settings. Swiping goes through the page types; each shows a preview of page
 * 50, its name and print year, its size (installed, or to download) and one control: install, use
 * and remove, or "In use". The "Show page info" switch turns the surah name, juz and page number
 * around the Mushaf page on and off. The Settings hub links to this composable.
 */
@Composable
fun MushafSettingsPage(dependencies: ReadDependencies, modifier: Modifier = Modifier) {
    val viewModel: MushafSettingsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { MushafSettingsViewModel(dependencies) } },
        )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val pagerState =
        rememberPagerState(
            initialPage = pageTypeScripts.indexOf(settings.script).coerceAtLeast(0),
            pageCount = { pageTypeScripts.size },
        )

    Column(modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index
            ->
            val script = pageTypeScripts[index]
            val status = statuses.getValue(script)
            PageTypeCard(
                script = script,
                status = status,
                isInUse = settings.script == script && status.isInstalled,
                onInstall = { viewModel.install(script) },
                onUse = { viewModel.use(script) },
                onRemove = { viewModel.remove(script) },
            )
        }
        PagerDots(count = pageTypeScripts.size, selected = pagerState.currentPage)
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.settingsRowHeight)
                .padding(horizontal = AwnSpacing.screenMargin),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mushaf_show_page_info),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
            AwnSwitch(checked = settings.showPageInfo, onCheckedChange = viewModel::setShowPageInfo)
        }
    }
}

@Composable
private fun PageTypeCard(
    script: ScriptId,
    status: FontPackStatus,
    isInUse: Boolean,
    onInstall: () -> Unit,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    val text = pageTypeText(script)
    val name = stringResource(text.name)
    Column(
        Modifier.fillMaxSize().padding(AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Image(
            painter = painterResource(text.preview),
            contentDescription =
                stringResource(R.string.mushaf_preview_description, name, PREVIEW_PAGE),
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Text(text = name, style = AwnTypography.lead, color = Awn.field.content)
        Text(
            text = stringResource(text.year),
            style = AwnTypography.small,
            color = Awn.field.content,
        )
        Text(text = sizeLine(status), style = AwnTypography.small, color = Awn.field.content)
        PageTypeControl(status, isInUse, onInstall, onUse, onRemove)
    }
}

@Composable
private fun sizeLine(status: FontPackStatus): String {
    val installedBytes = status.installedBytes
    val downloadBytes = status.downloadBytes
    return when {
        status.isInstalled && installedBytes != null ->
            stringResource(R.string.mushaf_size_installed, megabytesOf(installedBytes))
        downloadBytes != null ->
            stringResource(R.string.mushaf_size_download, megabytesOf(downloadBytes))
        else -> ""
    }
}

@Composable
private fun PageTypeControl(
    status: FontPackStatus,
    isInUse: Boolean,
    onInstall: () -> Unit,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    val progress = status.progress
    when {
        progress != null -> {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Awn.field.buttonFill,
            )
            Text(
                text = stringResource(R.string.font_prompt_progress, (progress * PERCENT).toInt()),
                style = AwnTypography.small,
                color = Awn.field.content,
            )
        }
        isInUse ->
            Text(
                text = stringResource(R.string.mushaf_in_use),
                style = AwnTypography.bodyStrong,
                color = Awn.field.buttonFill,
                modifier = Modifier.heightIn(min = AwnSpacing.touchTarget),
            )
        status.isInstalled ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AwnButton(label = stringResource(R.string.mushaf_use), onClick = onUse)
                Text(
                    text = stringResource(R.string.mushaf_remove),
                    style = AwnTypography.bodyStrong,
                    color = Awn.field.buttonFill,
                    modifier =
                        Modifier.heightIn(min = AwnSpacing.touchTarget)
                            .clickable(role = Role.Button, onClick = onRemove)
                            .padding(vertical = AwnSpacing.small),
                )
            }
        else -> {
            status.failure?.let { failure ->
                Text(
                    text = failureMessage(failure),
                    style = AwnTypography.small,
                    color = Awn.field.wrongWord,
                )
            }
            AwnButton(label = stringResource(R.string.mushaf_install), onClick = onInstall)
        }
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val color =
                if (index == selected) Awn.field.buttonFill
                else Awn.field.content.copy(alpha = INACTIVE_DOT_ALPHA)
            Box(
                Modifier.padding(horizontal = AwnSpacing.switchPadding * 2)
                    .size(AwnSpacing.small)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
