package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayScheduleTest {

    private fun ayahs(surah: Int, first: Int, last: Int) = (first..last).map { AyahRef(surah, it) }

    /** Walks the whole schedule and writes each turn as "3:5", or "basmalah:2" for a Basmalah. */
    private fun walk(
        schedule: PlaySchedule,
        options: PlaybackOptions = PlaybackOptions(),
        wholeSurahFiles: Boolean = false,
        limit: Int = 200,
    ): List<String> {
        val turns = mutableListOf<String>()
        var position: PlaySchedule.Position? = schedule.start()
        while (position != null && turns.size < limit) {
            val turn = schedule.turnAt(position)
            turns +=
                if (turn.kind == TurnKind.Basmalah) "basmalah:${turn.ayah.surah}"
                else "${turn.ayah}"
            position = schedule.afterPlay(position, options, wholeSurahFiles)
        }
        return turns
    }

    @Test
    fun plainRangePlaysEachAyahOnce() {
        val schedule = PlaySchedule(ayahs(3, 5, 7), cumulative = false)
        assertEquals(listOf("3:5", "3:6", "3:7"), walk(schedule))
    }

    @Test
    fun cumulativeOfOneAyahIsThatAyah() {
        val schedule = PlaySchedule(ayahs(3, 5, 5), cumulative = true)
        assertEquals(listOf("3:5"), walk(schedule))
    }

    @Test
    fun cumulativeOfTwoAyahsIsOneTwoThenOneAndTwo() {
        val schedule = PlaySchedule(ayahs(3, 5, 6), cumulative = true)
        assertEquals(listOf("3:5", "3:6", "3:5", "3:6"), walk(schedule))
    }

    @Test
    fun cumulativeOfThreeAyahsIsOneTwoOneTwoThreeOneTwoThree() {
        val schedule = PlaySchedule(ayahs(3, 5, 7), cumulative = true)
        assertEquals(
            listOf("3:5", "3:6", "3:5", "3:6", "3:7", "3:5", "3:6", "3:7"),
            walk(schedule),
        )
    }

    @Test
    fun cumulativeOfFourAyahsHasSevenStepsInTheExactOrder() {
        val schedule = PlaySchedule(ayahs(3, 5, 8), cumulative = true)
        val expected = listOf(1, 2, 1, 2, 3, 1, 2, 3, 4, 1, 2, 3, 4).map { "3:${it + 4}" }
        assertEquals(expected, walk(schedule))

        val steps = mutableListOf<PlayStep?>()
        var position: PlaySchedule.Position? = schedule.start()
        while (position != null) {
            steps += schedule.turnAt(position).step
            position = schedule.afterPlay(position, PlaybackOptions())
        }
        assertEquals(13, steps.size)
        assertEquals(PlayStep(1, 7), steps.first())
        assertEquals(PlayStep(7, 7), steps.last())
        assertEquals(7, steps.filterNotNull().map { it.number }.toSet().size)
    }

    @Test
    fun repeatEachAyahRepeatsEveryAyahInsideACumulativeStep() {
        val schedule = PlaySchedule(ayahs(3, 5, 7), cumulative = true)
        val options = PlaybackOptions(repeatEachAyah = 2)
        assertEquals(
            listOf(
                "3:5",
                "3:5", // step 1
                "3:6",
                "3:6", // step 2
                "3:5",
                "3:5",
                "3:6",
                "3:6", // step 3
                "3:7",
                "3:7", // step 4
                "3:5",
                "3:5",
                "3:6",
                "3:6",
                "3:7",
                "3:7", // step 5
            ),
            walk(schedule, options),
        )
    }

    @Test
    fun repeatRangeRunsTheCumulativeDrillAgain() {
        val schedule = PlaySchedule(ayahs(3, 5, 6), cumulative = true)
        val options = PlaybackOptions(repeatRange = 2)
        assertEquals(
            listOf("3:5", "3:6", "3:5", "3:6", "3:5", "3:6", "3:5", "3:6"),
            walk(schedule, options),
        )
    }

    @Test
    fun plainRepeatsMultiplyEachAyahAndTheRange() {
        val schedule = PlaySchedule(ayahs(3, 5, 6), cumulative = false)
        val options = PlaybackOptions(repeatEachAyah = 2, repeatRange = 2)
        assertEquals(
            listOf("3:5", "3:5", "3:6", "3:6", "3:5", "3:5", "3:6", "3:6"),
            walk(schedule, options),
        )
    }

    @Test
    fun basmalahComesOnceBeforeTheFirstAyahOfASurah() {
        val schedule = PlaySchedule(ayahs(2, 1, 2), cumulative = false)
        assertEquals(listOf("basmalah:2", "2:1", "2:2"), walk(schedule))
    }

    @Test
    fun basmalahIsNotHeardBetweenRepeatsOfTheAyah() {
        val schedule = PlaySchedule(ayahs(2, 1, 2), cumulative = false)
        val options = PlaybackOptions(repeatEachAyah = 3)
        assertEquals(
            listOf("basmalah:2", "2:1", "2:1", "2:1", "2:2", "2:2", "2:2"),
            walk(schedule, options),
        )
    }

    @Test
    fun basmalahIsNotHeardBetweenRepeatsOfTheRange() {
        val schedule = PlaySchedule(ayahs(2, 1, 2), cumulative = false)
        val options = PlaybackOptions(repeatRange = 3)
        assertEquals(
            listOf("basmalah:2", "2:1", "2:2", "2:1", "2:2", "2:1", "2:2"),
            walk(schedule, options),
        )
    }

    @Test
    fun basmalahIsNotHeardAgainInACumulativeStepThatIncludesAyahOne() {
        val schedule = PlaySchedule(ayahs(2, 1, 3), cumulative = true)
        assertEquals(
            listOf("basmalah:2", "2:1", "2:2", "2:1", "2:2", "2:3", "2:1", "2:2", "2:3"),
            walk(schedule),
        )
    }

    @Test
    fun basmalahComesBeforeTheFirstAyahOfEachNewSurahInTheRange() {
        val schedule = PlaySchedule(listOf(AyahRef(2, 286), AyahRef(3, 1), AyahRef(3, 2)), false)
        assertEquals(listOf("2:286", "basmalah:3", "3:1", "3:2"), walk(schedule))
    }

    @Test
    fun noBasmalahStartsMidSurahForFatihahOrForAtTawbah() {
        assertEquals(listOf("2:5", "2:6"), walk(PlaySchedule(ayahs(2, 5, 6), false)))
        assertEquals(listOf("1:1", "1:2"), walk(PlaySchedule(ayahs(1, 1, 2), false)))
        assertEquals(listOf("9:1", "9:2"), walk(PlaySchedule(ayahs(9, 1, 2), false)))
    }

    @Test
    fun repeatCountsChangedWhilePlayingApplyAtTheNextBoundary() {
        val schedule = PlaySchedule(ayahs(3, 5, 6), cumulative = false)
        var position = schedule.start()
        position = schedule.afterPlay(position, PlaybackOptions(repeatEachAyah = 1))!!
        assertEquals(AyahRef(3, 6), schedule.ayahAt(position))
        // Raising the repeat while ayah 6 plays makes it play again instead of ending.
        val again = schedule.afterPlay(position, PlaybackOptions(repeatEachAyah = 2))
        assertEquals(AyahRef(3, 6), schedule.ayahAt(again!!))
        assertNull(schedule.afterPlay(again, PlaybackOptions(repeatEachAyah = 2)))
    }

    @Test
    fun infiniteRepeatNeverEnds() {
        val schedule = PlaySchedule(ayahs(3, 5, 6), cumulative = false)
        val options = PlaybackOptions(repeatRange = PlaybackOptions.INFINITE_REPEAT)
        assertEquals(50, walk(schedule, options, limit = 50).size)
    }

    @Test
    fun wholeSurahFilesSkipTheRestOfTheSurah() {
        val schedule = PlaySchedule(listOf(AyahRef(1, 1), AyahRef(1, 2), AyahRef(2, 1)), false)
        // The controller drops the Basmalah turn for a reciter with surah files.
        assertEquals(listOf("1:1", "basmalah:2", "2:1"), walk(schedule, wholeSurahFiles = true))
    }

    @Test
    fun nextSkipsTheRestOfACumulativeStep() {
        val schedule = PlaySchedule(ayahs(3, 5, 7), cumulative = true)
        val joined = schedule.start().let { schedule.afterUnit(it, PlaybackOptions())!! } // step 2
        val step3 = schedule.afterUnit(joined, PlaybackOptions())!!
        assertEquals(3, schedule.turnAt(step3).step!!.number)
        val back = schedule.beforeUnit(step3)
        assertEquals(2, schedule.turnAt(back).step!!.number)
    }
}
