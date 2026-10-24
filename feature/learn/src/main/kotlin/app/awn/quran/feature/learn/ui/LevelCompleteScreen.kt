package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.formatCount
import app.awn.quran.feature.learn.words.WordLevel
import app.awn.quran.feature.learn.words.WordLevels

private val BADGE_SIZE = AwnSpacing.levelBadgeSize

// "Say: My Lord, increase me in knowledge" is the last four words of this ayah.
private val INCREASE_KNOWLEDGE_AYAH = AyahRef(20, 114)
private const val INCREASE_KNOWLEDGE_WORD_COUNT = 4

/**
 * The full screen after the session that finished a word level: the level number on a badge, one
 * plain fact, the ayah about knowledge and a button for the next level (none after the last).
 */
@Composable
internal fun LevelCompleteFrame(level: WordLevel, onNextLevel: () -> Unit, onLater: () -> Unit) {
    val nextLevel = WordLevels.all.firstOrNull { it.number == level.number + 1 }
    Column(
        modifier = Modifier.fillMaxSize().padding(AwnSpacing.screenMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(AwnSpacing.medium, Alignment.CenterVertically),
        ) {
            LevelBadge(level.number)
            Text(
                text = stringResource(R.string.level_complete_title, level.number),
                style = AwnTypography.title,
                color = Awn.field.content,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.level_complete_text, formatCount(level.lastRank)),
                style = AwnTypography.body,
                color = Awn.field.content,
                textAlign = TextAlign.Center,
            )
            KnowledgeAyah()
        }
        if (nextLevel != null) {
            AwnButton(
                label = stringResource(R.string.level_complete_start, nextLevel.number),
                onClick = onNextLevel,
                modifier = Modifier.fillMaxWidth(),
            )
            AwnButton(
                label = stringResource(R.string.level_complete_later),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Awn.field.softFill,
                contentColor = Awn.field.content,
            )
        } else {
            AwnButton(
                label = stringResource(R.string.end_done),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LevelBadge(number: Int) {
    Column(
        modifier = Modifier.size(BADGE_SIZE).clip(CircleShape).background(Awn.field.buttonFill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.level_complete_badge),
            style = AwnTypography.small,
            color = Awn.field.onButton,
        )
        Text(
            text = formatCount(number),
            style = AwnTypography.poster,
            color = Awn.field.onButton,
        )
    }
}

/** The Arabic comes from the Quran text, so it is the same as on the Quran screen. */
@Composable
private fun KnowledgeAyah() {
    val ayah by rememberAyah(INCREASE_KNOWLEDGE_AYAH)
    val words = ayah?.words.orEmpty().takeLast(INCREASE_KNOWLEDGE_WORD_COUNT)
    Text(
        text = words.joinToString(" ") { it.text },
        style = arabicStyle(AwnTypography.title).copy(textAlign = TextAlign.Center),
        color = Awn.field.content,
        modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
    )
    Text(
        text = stringResource(R.string.level_complete_ayah_translation),
        style = AwnTypography.small,
        color = Awn.field.mutedContent,
        textAlign = TextAlign.Center,
    )
}
