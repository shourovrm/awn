package app.awn.quran.feature.read

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnFieldColors
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.LineToken
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.PageRow
import app.awn.quran.feature.read.page.PlayingFade
import app.awn.quran.feature.read.page.ayahFills
import app.awn.quran.feature.read.page.layoutPage
import app.awn.quran.feature.read.quran.highlightsFor
import app.awn.quran.feature.read.quran.isFullyVisible
import app.awn.quran.feature.read.quran.topMarginFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingHighlightTest {
    private val field =
        AwnFieldColors(
            background = Color.White,
            content = Color.Black,
            buttonFill = Color.Black,
            onButton = Color.White,
            confirmedWord = Color.Green,
            tentativeWord = Color.Yellow,
            wrongWord = Color.Red,
            hiddenWordBlock = Color.Gray,
            barBackground = Color.Black,
            onBar = Color.White,
            selectedAyahFill = Color.Blue.copy(alpha = 0.3f),
            playingAyahFill = Color.Green.copy(alpha = 0.2f),
            playingWordFill = Color.Yellow.copy(alpha = 0.45f),
            bookmarkedAyahFill = Color.Gray.copy(alpha = 0.16f),
            isLight = true,
        )

    private fun word(ayah: Int, position: Int) =
        LineToken.WordToken(WordKey(2, ayah, position), "w$ayah-$position")

    private val geometry =
        layoutPage(
            rows =
                listOf(
                    PageRow.TextRow(1, List(4) { word(1, it + 1) }),
                    PageRow.TextRow(2, List(4) { word(2, it + 1) }),
                ),
            isCentredBlock = false,
            widthPx = 1000f,
            heightPx = 1500f,
            tokenWidthEm = { 2f },
        )

    @Test
    fun theNewPlayingAyahIsLitWhileTheOldOneFadesOut() {
        val fade =
            PlayingFade(
                incoming = AyahRef(2, 2),
                incomingAlpha = 0.25f,
                outgoing = AyahRef(2, 1),
                outgoingAlpha = 0.75f,
            )

        val fills = ayahFills(geometry, PageDecorations(), field, fade)

        assertEquals(2, fills.size)
        val outgoing = fills.first { it.rect.top == geometry.slotTop(1) }
        val incoming = fills.first { it.rect.top == geometry.slotTop(2) }
        assertEquals(0.2f * 0.75f, outgoing.color.alpha, 0.01f)
        assertEquals(0.2f * 0.25f, incoming.color.alpha, 0.01f)
    }

    @Test
    fun aSteadyHighlightUsesTheFullFill() {
        val decorations = PageDecorations(playingAyah = AyahRef(2, 1))

        val fills = ayahFills(geometry, decorations, field)

        assertEquals(1, fills.size)
        assertEquals(0.2f, fills.single().color.alpha, 0.01f)
    }

    @Test
    fun aSelectedRangeFillsEveryAyahInIt() {
        val decorations = PageDecorations(selectedAyahs = setOf(AyahRef(2, 1), AyahRef(2, 2)))

        val fills = ayahFills(geometry, decorations, field)

        assertEquals(2, fills.size)
    }

    private val pageA = setOf(AyahRef(2, 1), AyahRef(2, 2), AyahRef(2, 3))
    private val pageB = setOf(AyahRef(2, 4), AyahRef(2, 5))

    @Test
    fun theMarksOfAPageWithoutThePlayingAyahDoNotChangeWhenPlaybackMoves() {
        val before = highlightsFor(pageB, null, AyahRef(2, 1), 1, emptySet())
        val afterNextWord = highlightsFor(pageB, null, AyahRef(2, 1), 2, emptySet())
        val afterNextAyah = highlightsFor(pageB, null, AyahRef(2, 2), 1, emptySet())

        assertEquals(before, afterNextWord)
        assertEquals(before, afterNextAyah)
        assertNull(before.playingAyah)
    }

    @Test
    fun thePageWithThePlayingAyahGetsTheAyahAndWord() {
        val marks = highlightsFor(pageA, null, AyahRef(2, 2), 3, emptySet())

        assertEquals(AyahRef(2, 2), marks.playingAyah)
        assertEquals(WordKey(2, 2, 3), marks.playingWord)
    }

    @Test
    fun aSelectedRangeIsCutToThePageItIsOn() {
        val range = AyahRange(AyahRef(2, 2), AyahRef(2, 4))

        assertEquals(
            setOf(AyahRef(2, 2), AyahRef(2, 3)),
            highlightsFor(pageA, range, null, null, emptySet()).selectedAyahs,
        )
        assertEquals(
            setOf(AyahRef(2, 4)),
            highlightsFor(pageB, range, null, null, emptySet()).selectedAyahs,
        )
    }

    @Test
    fun bookmarksOnOtherPagesDoNotReachAPage() {
        val saved = setOf(AyahRef(2, 1), AyahRef(2, 5), AyahRef(3, 1))

        assertEquals(
            setOf(AyahRef(2, 1)),
            highlightsFor(pageA, null, null, null, saved).bookmarkedAyahs,
        )
    }

    @Test
    fun aRangeKnowsItsAyahsInEitherDragDirection() {
        val downward = AyahRange.between(AyahRef(2, 2), AyahRef(2, 5))
        val upward = AyahRange.between(AyahRef(2, 5), AyahRef(2, 2))

        assertEquals(downward, upward)
        assertTrue(AyahRef(2, 3) in downward)
        assertFalse(AyahRef(2, 6) in downward)
        assertTrue(AyahRange(AyahRef(2, 2)).isSingle)
    }

    @Test
    fun anAyahThatIsWhollyOnScreenDoesNotScrollTheList() {
        assertTrue(isFullyVisible(offset = 100, size = 300, visibleTop = 50, visibleBottom = 800))
        assertFalse(isFullyVisible(offset = 40, size = 300, visibleTop = 50, visibleBottom = 800))
        assertFalse(isFullyVisible(offset = 600, size = 300, visibleTop = 50, visibleBottom = 800))
    }

    @Test
    fun theTopMarginIsFivePercentOfTheListHeight() {
        assertEquals(40, topMarginFor(800))
        assertEquals(0, topMarginFor(0))
    }
}
