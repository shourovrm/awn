package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.course.LessonBlock
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.launch

// A name is blank until its letter is tapped; a blank line keeps the tile's height.
private const val NO_BREAK_SPACE = " "
private const val DOT = "·"

/**
 * Groups: a grid of cards, each with the letters drawn with one stroke and a caption under them.
 */
@Composable
internal fun LetterGroups(block: LessonBlock.Groups) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        block.items.chunked(CHOICE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { group ->
                    LetterCard(group.letters, group.caption, Modifier.weight(1f))
                }
                repeat(CHOICE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LetterCard(letters: List<String>, caption: String, modifier: Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(Awn.field.softFill)
                .padding(AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(horizontalArrangement = Arrangement.Center) {
                letters.forEach {
                    ArabicText(it, AwnTypography.lessonLetterSize, Awn.field.content)
                }
            }
        }
        CaptionText(caption)
    }
}

/**
 * Pairs: a grid of cards, each with two letters to compare and a caption. A tap plays the pair's
 * sound when it has one.
 */
@Composable
internal fun LetterPairs(block: LessonBlock.Pairs) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        block.items.chunked(CHOICE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { pair ->
                    LetterPairCard(pair, Modifier.weight(1f))
                }
                repeat(CHOICE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LetterPairCard(pair: LessonBlock.LetterPair, modifier: Modifier) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(Awn.field.softFill)
                .clickable(
                    enabled = pair.clipIds.isNotEmpty(),
                    role = Role.Button,
                    onClick = { scope.launch { sounds.playClips(pair.clipIds) } },
                )
                .padding(AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pair.letters.forEachIndexed { index, letter ->
                    if (index > 0)
                        Text(text = DOT, style = AwnTypography.lead, color = Awn.field.content)
                    ArabicText(letter, AwnTypography.lessonLetterSize, Awn.field.content)
                }
            }
        }
        CaptionText(pair.caption)
    }
}

/**
 * Reveal: the letters in rows of five from the right, each name shown once its letter is tapped.
 */
@Composable
internal fun RevealGrid(block: LessonBlock.Reveal) {
    var revealed by remember(block) { mutableStateOf(emptySet<String>()) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            block.items.chunked(LETTER_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                    row.forEach { item ->
                        RevealTile(
                            letter = item.letter,
                            name = if (item.letter in revealed) item.name else NO_BREAK_SPACE,
                            onTap = { revealed = revealed + item.letter },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(LETTER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RevealTile(letter: String, name: String, onTap: () -> Unit, modifier: Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(Awn.field.softFill)
                .clickable(role = Role.Button, onClick = onTap)
                .padding(AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArabicText(letter, AwnTypography.quizLetterSize, Awn.field.content)
        // The name is Latin text; it keeps left-to-right order inside the right-to-left grid.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = name,
                style = AwnTypography.small,
                color = Awn.field.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** A caption is in the lesson's language; it keeps left-to-right order inside the grid. */
@Composable
private fun CaptionText(caption: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = caption,
            style = AwnTypography.small,
            color = Awn.field.content,
            textAlign = TextAlign.Center,
        )
    }
}
