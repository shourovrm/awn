package app.awn.quran.feature.hifz.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.hifz.HifzState
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.DayAssignment
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.MistakeType
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.method.PartMinutes
import app.awn.quran.feature.hifz.method.WeakSpot

/** The Hifz page once a plan exists: method, today, map, mark and weak spots. */
@Composable
internal fun HifzMainContent(
    state: HifzState,
    surahName: (Int) -> String,
    actions: HifzActions,
    modifier: Modifier = Modifier,
) {
    val method = state.plan.method
    Column(
        modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        SettingsLinkRow(
            label = stringResource(R.string.hifz_method),
            value = methodName(method),
            onClick = actions.onOpenMethod,
        )
        if (method != HifzMethodId.TrackOnly) {
            TodaySection(state, actions)
        }
        Heading(stringResource(R.string.progress_map), Modifier.padding(top = AwnSpacing.medium))
        Text(
            text = state.progressMap.memorisedPages.toString(),
            style = AwnTypography.title,
            color = Awn.field.content,
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.progress_caption,
                    state.progressMap.pageCount,
                    state.progressMap.pageCount,
                ),
            style = AwnTypography.body,
            color = Awn.field.content,
        )
        ProgressMapView(state.progressMap, Modifier.padding(top = AwnSpacing.small))
        ActionRow(
            icon = R.drawable.ic_hifz_bookmark,
            title = stringResource(R.string.mark_title),
            detail = null,
            trailing = null,
            onClick = actions.onOpenMark,
            modifier = Modifier.padding(top = AwnSpacing.medium),
        )
        if (state.weakSpots.isNotEmpty()) {
            WeakSpotsSection(state.weakSpots, surahName, actions.onOpenWeakSpot)
        }
    }
}

@Composable
private fun TodaySection(state: HifzState, actions: HifzActions) {
    val assignment = state.assignment
    val method = state.plan.method
    Heading(stringResource(R.string.today), Modifier.padding(top = AwnSpacing.small))
    assignment.newLesson?.let { lesson ->
        PartRow(
            kind = PartKind.NewLesson,
            method = method,
            icon = R.drawable.ic_hifz_hide_words,
            detail = lessonDetailText(lesson.part),
            minutes = PartMinutes.forLesson(lesson.part.ayahCount),
            isDone = lesson.isDone,
            onClick = actions.onStartPart,
        )
    }
    assignment.recent?.let { group ->
        PartRow(
            kind = PartKind.Recent,
            method = method,
            icon = R.drawable.ic_hifz_revise,
            detail = stringResource(R.string.revision_detail_recent, pagesText(group.pages)),
            minutes = PartMinutes.forRevision(group.pages.size),
            isDone = group.isDone,
            onClick = actions.onStartPart,
        )
    }
    assignment.old?.let { group ->
        val isSabaq = method == HifzMethodId.SabaqSabqiManzil
        val detail =
            if (isSabaq) stringResource(R.string.revision_detail_old, pagesText(group.pages))
            else pagesText(group.pages)
        PartRow(
            kind = PartKind.Old,
            method = method,
            icon = R.drawable.ic_hifz_revise,
            detail = detail,
            minutes = PartMinutes.forRevision(group.pages.size),
            isDone = group.isDone,
            onClick = actions.onStartPart,
        )
    }
    if (assignment.revisionPageCount > 0) RevisionLoad(assignment)
    assignment.nextPart()?.let { next ->
        AwnButton(
            label = startLabel(next, method),
            onClick = { actions.onStartPart(next) },
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        )
    }
}

@Composable
private fun RevisionLoad(assignment: DayAssignment) {
    val fraction =
        (assignment.revisionPageCount.toFloat() / assignment.revisionPageLimit).coerceIn(0f, 1f)
    Column(Modifier.padding(top = AwnSpacing.small)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.revision_today),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.revision_load,
                        assignment.revisionPageLimit,
                        assignment.revisionPageCount,
                        assignment.revisionPageLimit,
                    ),
                style = AwnTypography.body,
                color = Awn.field.content,
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .padding(top = AwnSpacing.small)
                .heightIn(min = AwnSpacing.small)
                .clip(RoundedCornerShape(2.dp))
                .background(Awn.field.softFill),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction)
                    .heightIn(min = AwnSpacing.small)
                    .background(Awn.field.buttonFill),
            )
        }
    }
}

@Composable
private fun PartRow(
    kind: PartKind,
    method: HifzMethodId,
    @DrawableRes icon: Int,
    detail: String,
    minutes: Int,
    isDone: Boolean,
    onClick: (PartKind) -> Unit,
) {
    ActionRow(
        icon = icon,
        title = partTitle(kind, method),
        detail = detail,
        trailing =
            if (isDone) stringResource(R.string.part_done)
            else stringResource(R.string.minutes_short, minutes),
        onClick = { onClick(kind) },
    )
}

/** A soft row with an icon, a title, an optional detail line and optional text at the end. */
@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    trailing: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = AwnSpacing.settingsRowTall)
            .clip(AwnShapes.control)
            .background(field.softFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Column(Modifier.weight(1f).padding(horizontal = AwnSpacing.medium)) {
            Text(text = title, style = AwnTypography.bodyStrong, color = field.content)
            if (detail != null) {
                Text(
                    text = detail,
                    style = AwnTypography.small,
                    color = field.mutedContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = AwnTypography.bodyStrong,
                color = field.content,
                modifier = Modifier.padding(end = AwnSpacing.small),
            )
        }
        Icon(
            painter = painterResource(UiR.drawable.ic_ui_chevron),
            contentDescription = null,
            tint = field.content,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
    }
}

@Composable
private fun WeakSpotsSection(
    spots: List<WeakSpot>,
    surahName: (Int) -> String,
    onOpen: (AyahRef) -> Unit,
) {
    Heading(stringResource(R.string.weak_spots), Modifier.padding(top = AwnSpacing.medium))
    spots.forEach { spot ->
        val detailResource =
            if (spot.type == MistakeType.Skipped) R.plurals.weak_skipped_times
            else R.plurals.weak_other_word_times
        ActionRow(
            icon = R.drawable.ic_hifz_speaker,
            title =
                stringResource(
                    R.string.weak_spot_title,
                    surahName(spot.ayah.surah),
                    spot.ayah.surah,
                    spot.ayah.ayah,
                ),
            detail = pluralStringResource(detailResource, spot.count, spot.count),
            trailing = null,
            onClick = { onOpen(spot.ayah) },
        )
    }
}

/** A small heading in the accent colour. */
@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        modifier = modifier.semantics { heading() },
    )
}
