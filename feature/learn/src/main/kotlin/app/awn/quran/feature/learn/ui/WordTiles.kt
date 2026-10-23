package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.course.ArabicLetters
import app.awn.quran.feature.learn.course.LessonBlock
import app.awn.quran.feature.learn.course.WordMark
import app.awn.quran.feature.learn.course.WordTile
import app.awn.quran.feature.learn.sound.LocalUnitSounds
import kotlinx.coroutines.launch

/** Opens an ayah in the Quran; null where the host has no Quran screen (tests, previews). */
val LocalOpenAyah = compositionLocalOf<((AyahRef) -> Unit)?> { null }

/**
 * Quran words two to a row, each with its caption and the surah it comes from. A tap plays the word
 * with the user's reciter; Play all plays them in order. The tile of the word playing is filled, so
 * the reader can follow along.
 */
@Composable
internal fun WordTiles(block: LessonBlock.Words) {
    val sounds = LocalUnitSounds.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<WordKey?>(null) }
    val play = { words: List<WordKey> ->
        scope.launch {
            for (word in words) {
                playing = word
                sounds.playWord(word)
            }
            playing = null
        }
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        block.items.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
                row.forEach { tile ->
                    WordTileView(
                        tile = tile,
                        mark = block.mark,
                        isPlaying = tile.key == playing,
                        onPlay = { play(listOf(tile.key)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        AwnButton(
            label = stringResource(R.string.unit_grid_play_all),
            onClick = { play(block.items.map { it.key }) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WordTileView(
    tile: WordTile,
    mark: WordMark?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    val field = Awn.field
    val background = if (isPlaying) field.buttonFill else field.softFill
    val content = if (isPlaying) field.onButton else field.content
    Box(
        modifier =
            modifier
                .heightIn(min = AwnSpacing.wordTileHeight)
                .clip(AwnShapes.control)
                .background(background)
                .clickable(role = Role.Button, onClick = onPlay)
                .padding(AwnSpacing.small),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_learn_play),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(AwnSpacing.smallIconSize).align(Alignment.TopStart),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MarkedWord(tile, markColor(mark, isPlaying), content)
            Text(
                text = tile.caption,
                style = AwnTypography.small,
                color = content,
                textAlign = TextAlign.Center,
            )
            AyahLink(AyahRef(tile.key.surah, tile.key.ayah), content)
        }
    }
}

/** The word in the learning font, with the letters the lesson names in [markColor]. */
@Composable
private fun MarkedWord(tile: WordTile, markColor: Color, content: Color) {
    val environment = LocalLearnEnvironment.current
    val texts = rememberWordTexts(remember(tile.key) { setOf(tile.key) })
    val word = ArabicLetters.withoutPauseSign(texts[tile.key].orEmpty())
    val styled = remember(word, tile.letters, markColor) { colourLetters(word, tile, markColor) }
    Text(
        text = styled,
        style =
            AwnTypography.title.copy(
                fontFamily = environment?.arabic?.fontFamily ?: AwnTypography.title.fontFamily,
                fontSize = AwnTypography.quran.fontSize * WORD_SCALE,
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Center,
            ),
        color = content,
    )
}

private fun colourLetters(word: String, tile: WordTile, markColor: Color): AnnotatedString {
    val marked = ArabicLetters.rangesAt(word, tile.letters)
    return buildAnnotatedString {
        append(word)
        marked.forEach { range ->
            addStyle(SpanStyle(color = markColor), range.first, range.last + 1)
        }
    }
}

/** The surah and ayah of a word; a tap opens it in the Quran when the host can. */
@Composable
private fun AyahLink(ayah: AyahRef, content: Color) {
    val environment = LocalLearnEnvironment.current
    val openAyah = LocalOpenAyah.current
    val label = environment?.ayahReference(ayah) ?: ayah.toString()
    val clickable =
        if (openAyah != null) {
            // The link is small text inside a tile; it still gets a full-size touch area.
            Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) {
                openAyah(ayah)
            }
        } else {
            Modifier
        }
    Text(
        text = label,
        style = AwnTypography.small,
        color = if (openAyah != null) content else Awn.field.mutedContent,
        modifier = clickable.testTag(WORD_TILE_AYAH_TAG),
    )
}

/** A short aside on a soft fill. */
@Composable
internal fun TipView(tip: LessonBlock.Tip) {
    RichText(
        text = tip.text,
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(Awn.field.softFill)
                .padding(AwnSpacing.medium),
    )
}

/**
 * The ink of the marked letters. While a tile plays it is filled with the button colour, where the
 * rule inks would be hard to read, so the whole word takes the button's text colour.
 */
@Composable
private fun markColor(mark: WordMark?, isPlaying: Boolean): Color {
    val field = Awn.field
    return when {
        isPlaying -> field.onButton
        mark == null -> field.buttonFill
        field.isLight -> dayMarkInk(mark)
        else -> nightMarkInk(mark)
    }
}

private const val TILES_PER_ROW = 2

// A tile word is drawn larger than ayah text: it stands alone and the coloured letters must read.
private const val WORD_SCALE = 1.4f

internal const val WORD_TILE_AYAH_TAG = "word-tile-ayah"
