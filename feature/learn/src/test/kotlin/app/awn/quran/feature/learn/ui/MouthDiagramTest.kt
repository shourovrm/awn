package app.awn.quran.feature.learn.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.LessonBlock
import app.awn.quran.feature.learn.course.MouthArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The mouth drawing of the lesson on where letters are made: parsing, labels and areas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MouthDiagramTest {

    @get:Rule val composeRule = createComposeRule()

    private fun blocksOf(blockJson: String): List<LessonBlock> =
        CourseContentReader.parse(
                """{"formatVersion":1,"references":[],"lessons":[{"id":"tajweed-11",
                "course":"tajweed","order":11,"introducedBy":[],"licence":"x","reviewed":false,
                "languages":{"en":{"title":"Where letters are made","screens":[
                {"kind":"screen","title":"Throat","blocks":[$blockJson]}]}}}]}""",
            )
            .lessons
            .single()
            .text("en")
            .screens
            .single()
            .blocks

    @Test
    fun aMouthBlockKeepsItsAreas() {
        val blocks = blocksOf("""{"type":"mouth","areas":["lips","nose","open_mouth"]}""")

        assertEquals(
            LessonBlock.Mouth(setOf(MouthArea.Lips, MouthArea.Nose, MouthArea.OpenMouth)),
            blocks.single(),
        )
    }

    @Test
    fun aMouthBlockWithoutAreasFillsNothing() {
        val blocks = blocksOf("""{"type":"mouth","areas":[]}""")

        assertEquals(LessonBlock.Mouth(emptySet()), blocks.single())
    }

    @Test
    fun anUnknownAreaFailsLoudly() {
        assertThrows(IllegalStateException::class.java) {
            blocksOf("""{"type":"mouth","areas":["teeth"]}""")
        }
    }

    @Test
    fun everyAreaHasAStoredNameThatReadsBack() {
        MouthArea.entries.forEach { area ->
            assertEquals(area, MouthArea.ofStored(area.stored))
        }
    }

    @Test
    fun theDrawingNamesAllFiveAreasWhateverIsFilledIn() {
        composeRule.setContent { AwnTheme { MouthDiagram(highlighted = setOf(MouthArea.Throat)) } }

        listOf("Throat", "Tongue", "Lips", "Nose", "Open mouth").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule
            .onNodeWithContentDescription("Side view of the mouth and throat")
            .assertIsDisplayed()
    }

    @Test
    fun theLessonBlockListDrawsTheDiagram() {
        val blocks = blocksOf("""{"type":"mouth","areas":["tongue"]}""")

        composeRule.setContent { AwnTheme { LessonBlocks(blocks) } }

        composeRule.onNodeWithText("Tongue").assertIsDisplayed()
    }
}
