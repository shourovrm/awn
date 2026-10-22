package app.awn.quran.feature.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.ui.pagesText
import app.awn.quran.feature.hifz.ui.partTitle

const val HIFZ_BAR_PART_TAG = "hifz-bar-part"

private const val CHIP_ALPHA = 0.14f
private const val HEADER_ALPHA = 0.7f

/**
 * The part's name and ayah as a pill at the start of the bar. Tapping it opens a menu with a muted
 * line saying what is practised, Finish part and Close. The text shortens with "..." before any
 * other button of the bar loses room.
 */
@Composable
internal fun PartChip(
    request: PracticeRequest,
    currentAyah: AyahRef?,
    colors: HifzBarColors,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isOpen by remember { mutableStateOf(false) }
    val title = partTitle(request.kind, request.method)
    val detail = partDetail(request, currentAyah)
    Box(modifier) {
        PartPill(
            label = stringResource(R.string.bar_part_chip, title, detail),
            colors = colors,
            onClick = { isOpen = true },
        )
        PartMenu(
            isOpen = isOpen,
            header =
                listOf(title, ayahRangeText(request.firstAyah, request.lastAyah), detail)
                    .joinToString(" \u00b7 "),
            onDismiss = { isOpen = false },
            onFinish = {
                isOpen = false
                onFinish()
            },
            onClose = {
                isOpen = false
                onClose()
            },
        )
    }
}

// The ayah being recited for a new lesson, the pages for a revision part.
@Composable
private fun partDetail(request: PracticeRequest, currentAyah: AyahRef?): String =
    if (request.kind == PartKind.NewLesson) {
        stringResource(R.string.bar_ayah, (currentAyah ?: request.firstAyah).ayah)
    } else {
        pagesText(request.pages)
    }

@Composable
private fun PartPill(label: String, colors: HifzBarColors, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = AwnSpacing.compactButton)
            .clip(AwnShapes.control)
            .background(colors.content.copy(alpha = CHIP_ALPHA))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(HIFZ_BAR_PART_TAG)
            .padding(start = AwnSpacing.small, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AwnTypography.bodyStrong,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            painter = painterResource(R.drawable.ic_hifz_more),
            contentDescription = null,
            tint = colors.content,
        )
    }
}

@Composable
private fun PartMenu(
    isOpen: Boolean,
    header: String,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onDismiss,
        containerColor = Awn.field.background,
    ) {
        Text(
            text = header,
            style = AwnTypography.small,
            color = Awn.field.content.copy(alpha = HEADER_ALPHA),
            modifier = Modifier.padding(horizontal = AwnSpacing.medium, vertical = 4.dp),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bar_finish_part), style = AwnTypography.body) },
            onClick = onFinish,
            modifier = Modifier.testTag(HIFZ_BAR_FINISH_TAG),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bar_close), style = AwnTypography.body) },
            onClick = onClose,
            modifier = Modifier.testTag(HIFZ_BAR_CLOSE_TAG),
        )
    }
}

/** "2:255-257" for one surah, "2:285-3:2" across two, "2:255" for one ayah. */
internal fun ayahRangeText(first: AyahRef, last: AyahRef): String =
    when {
        first == last -> "${first.surah}:${first.ayah}"
        first.surah == last.surah -> "${first.surah}:${first.ayah}-${last.ayah}"
        else -> "${first.surah}:${first.ayah}-${last.surah}:${last.ayah}"
    }
