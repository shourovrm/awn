package app.awn.quran.feature.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.PageLine
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.page.AyahRange
import app.awn.quran.feature.read.page.MushafPage
import app.awn.quran.feature.read.page.PageDecorations
import app.awn.quran.feature.read.page.SelectionBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Touch behaviour of the Mushaf page: one ayah on each of lines 1 to 3 of a 450 dp page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MushafPageGestureTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val inlinePageWork = InlinePageWorkRule()

    private val pageWidth = 300
    private val pageHeight = 450
    private val slotHeight = pageHeight / 15f

    private fun layout() =
        PageLayout(
            3,
            ScriptId.QpcHafs,
            (1..3).map { ayah ->
                PageLine(
                    ayah,
                    listOf(
                        LineItem.WordItem(ReadFixture.word(2, ayah, 1, "كلمة")),
                        LineItem.WordItem(ReadFixture.word(2, ayah, 2, "كلمة")),
                        LineItem.AyahEnd(AyahRef(2, ayah)),
                    ),
                )
            },
        )

    private val taps = mutableListOf<String>()
    private val longPresses = mutableListOf<AyahRef>()
    private val drags = mutableListOf<AyahRange>()
    private var rangeEnds = 0
    private var bounds: SelectionBounds? = null
    private var trackedBounds: Rect? = null

    private fun setPage(selected: Set<AyahRef> = emptySet(), trackedWord: WordKey? = null) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AwnTheme {
                Box(Modifier.width(pageWidth.dp).height(pageHeight.dp)) {
                    MushafPage(
                        layout = layout(),
                        chapters = ReadFixture().text.chapterList,
                        fontFamily = null,
                        plainFamily = null,
                        decorations = PageDecorations(selectedAyahs = selected),
                        showPageInfo = false,
                        onWordClick = { taps += "word" },
                        onTap = { taps += "tap" },
                        onAyahLongPress = { longPresses += it },
                        onRangeDrag = { drags += it },
                        onRangeEnd = { rangeEnds++ },
                        onSelectionBounds = { bounds = it },
                        trackedWord = trackedWord,
                        onTrackedWordBounds = { trackedBounds = it },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun lineCentre(slot: Int, x: Float = pageWidth / 2f) =
        Offset(x, (slot - 0.5f) * slotHeight)

    @Test
    fun aTapIsReportedAtOnceWithoutWaitingForASecondTap() {
        setPage()

        // The clock does not advance: a double-tap detector would still be waiting.
        composeRule.onRoot().performTouchInput { click(lineCentre(1)) }

        assertEquals(1, taps.count { it == "tap" })
    }

    @Test
    fun aTapInTheMarginAndATapBetweenLinesEachToggleOnce() {
        setPage()

        composeRule.onRoot().performTouchInput { click(Offset(2f, pageHeight - 5f)) }
        assertEquals(listOf("tap"), taps)

        composeRule.onRoot().performTouchInput { click(Offset(pageWidth - 2f, slotHeight * 5f)) }
        assertEquals(listOf("tap", "tap"), taps)
    }

    @Test
    fun aLongPressInTheMarginSelectsTheNearestAyah() {
        setPage()

        composeRule.onRoot().performTouchInput { down(Offset(1f, lineCentre(2).y)) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().performTouchInput { up() }

        assertEquals(listOf(AyahRef(2, 2)), longPresses)
        assertEquals(1, rangeEnds)
        assertTrue(taps.isEmpty())
    }

    @Test
    fun aLongPressBelowTheLastLineSelectsTheLastAyah() {
        setPage()

        composeRule.onRoot().performTouchInput { down(Offset(200f, pageHeight - 2f)) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().performTouchInput { up() }

        assertEquals(listOf(AyahRef(2, 3)), longPresses)
    }

    @Test
    fun draggingAfterALongPressExtendsTheSelectionToARange() {
        setPage()

        composeRule.onRoot().performTouchInput { down(lineCentre(1)) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().performTouchInput { moveTo(lineCentre(2)) }
        composeRule.onRoot().performTouchInput { moveTo(lineCentre(3)) }
        assertEquals(0, rangeEnds)
        composeRule.onRoot().performTouchInput { up() }

        assertEquals(listOf(AyahRef(2, 1)), longPresses)
        assertEquals(
            listOf(
                AyahRange(AyahRef(2, 1), AyahRef(2, 2)),
                AyahRange(AyahRef(2, 1), AyahRef(2, 3)),
            ),
            drags,
        )
        assertEquals(1, rangeEnds)
        assertTrue(taps.isEmpty())
    }

    @Test
    fun draggingUpwardGivesTheSameKindOfRange() {
        setPage()

        composeRule.onRoot().performTouchInput { down(lineCentre(3)) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().performTouchInput { moveTo(lineCentre(1)) }
        composeRule.onRoot().performTouchInput { up() }

        assertEquals(listOf(AyahRange(AyahRef(2, 1), AyahRef(2, 3))), drags)
    }

    @Test
    fun thePageReportsWhereTheSelectedAyahsAre() {
        setPage(selected = setOf(AyahRef(2, 2), AyahRef(2, 3)))
        composeRule.mainClock.advanceTimeByFrame()

        val reported = bounds
        assertNotNull(reported)
        assertEquals(slotHeight, reported!!.firstLine.top, 1f)
        assertEquals(3 * slotHeight, reported.all.bottom, 1f)
    }

    @Test
    fun noSelectionReportsNoBounds() {
        setPage()
        composeRule.mainClock.advanceTimeByFrame()

        assertNull(bounds)
    }

    @Test
    fun thePageReportsWhereTheTrackedWordIs() {
        setPage(trackedWord = WordKey(2, 2, 1))
        composeRule.mainClock.advanceTimeByFrame()

        val reported = trackedBounds
        assertNotNull(reported)
        assertEquals(slotHeight, reported!!.top, 1f)
        assertEquals(2 * slotHeight, reported.bottom, 1f)
    }

    @Test
    fun aTrackedWordOnAnotherPageReportsNothing() {
        setPage(trackedWord = WordKey(2, 99, 1))
        composeRule.mainClock.advanceTimeByFrame()

        assertNull(trackedBounds)
    }
}
