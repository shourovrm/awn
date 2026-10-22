package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.course.LessonBlock

/** The blocks of one lesson screen, one under the other. */
@Composable
internal fun LessonBlocks(blocks: List<LessonBlock>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        blocks.forEach { block ->
            when (block) {
                is LessonBlock.Paragraph -> RichText(block.text)
                is LessonBlock.Bullets -> BulletList(block.items)
                is LessonBlock.Table -> LessonTable(block)
                is LessonBlock.Ayah -> AyahCard(block.ayah)
                is LessonBlock.Equation -> EquationView(block)
                is LessonBlock.Letters -> LetterGrid(block.items)
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                Text(text = BULLET, style = AwnTypography.body, color = Awn.field.content)
                RichText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LessonTable(table: LessonBlock.Table) {
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            table.header.forEach {
                Text(
                    text = it,
                    style = AwnTypography.bodyStrong,
                    color = Awn.field.content,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        table.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { cell -> RichText(cell, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** `A + B` over their glosses, then `= C` over its gloss: the parts of a word and the word. */
@Composable
private fun EquationView(equation: LessonBlock.Equation) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            equation.parts.forEachIndexed { index, part ->
                if (index > 0)
                    Text(text = PLUS, style = AwnTypography.lead, color = Awn.field.content)
                TermView(part)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            Text(text = EQUALS, style = AwnTypography.lead, color = Awn.field.content)
            TermView(equation.result)
        }
    }
}

@Composable
private fun TermView(term: LessonBlock.Term) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RichText(term.text, style = AwnTypography.lead, textAlign = TextAlign.Center)
        Text(
            text = term.gloss,
            style = AwnTypography.small,
            color = Awn.field.content,
            modifier = Modifier.padding(top = AwnSpacing.small),
        )
    }
}

/**
 * The letters in rows of five, read from right to left like the alphabet in a Qaida, each tile with
 * the letter's name under it.
 */
@Composable
private fun LetterGrid(items: List<LessonBlock.LetterName>) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            items.chunked(LETTERS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                    row.forEach { item -> LetterTile(item, Modifier.weight(1f)) }
                    repeat(LETTERS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LetterTile(item: LessonBlock.LetterName, modifier: Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(AwnSpacing.small))
                .background(Awn.field.softFill)
                .padding(vertical = AwnSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The Quran font draws a lone yaa without its dots, as the Mushaf writes a final yaa; the
        // alphabet grid uses the phone's own Arabic font so every letter shows its dots.
        Text(
            text = item.letter,
            style = AwnTypography.title,
            color = Awn.field.content,
            textAlign = TextAlign.Center,
        )
        // The name is Latin text; it keeps left-to-right order inside the right-to-left grid.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = item.name,
                style = AwnTypography.small,
                color = Awn.field.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private const val LETTERS_PER_ROW = 5
private const val PLUS = "+"
private const val EQUALS = "="

// A sign is a constant, not quoted text in a Text call, so the copy gate keeps its meaning.
private const val BULLET = "\u2022"
