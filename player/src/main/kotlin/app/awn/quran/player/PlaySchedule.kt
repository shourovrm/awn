package app.awn.quran.player

import app.awn.quran.core.data.AyahRef

/** Which audio a [PlaySchedule] position stands for. */
internal enum class TurnKind {
    /** The reciter's opening Basmalah, heard before the first ayah of a surah. */
    Basmalah,
    Ayah,
}

/** Where cumulative playback is: step [number] of [total] (both start at 1). */
data class PlayStep(val number: Int, val total: Int)

/**
 * One thing to play: the [kind] of audio for [ayah], inside cumulative [step] when there is one.
 */
internal data class Turn(val kind: TurnKind, val ayah: AyahRef, val step: PlayStep?)

/**
 * The order in which a range plays. A pure function of a [Position] and the [PlaybackOptions] read
 * at each boundary, so repeat counts can change while audio plays and the controller can ask what
 * comes next before the current ayah ends (to preload it).
 *
 * The range is cut into units. Normally a unit is one ayah. With cumulative playback a unit is a
 * step of the memorisation drill: for ayahs 1 to 4 the units are 1, 2, 1+2, 3, 1+2+3, 4, 1+2+3+4.
 * Each ayah of a unit plays [PlaybackOptions.repeatEachAyah] times before the next ayah of the
 * unit.
 *
 * The Basmalah is a turn of its own before the first ayah of a surah (not surah 1, where it is the
 * ayah itself, and not surah 9, which has none). It plays once per surah for the whole session, so
 * repeating an ayah, repeating the range or a cumulative step that includes ayah 1 never plays it
 * again.
 */
@Suppress("TooManyFunctions") // Cursor moves: after play, after unit, before unit, arrive.
internal class PlaySchedule(
    private val ayahs: List<AyahRef>,
    cumulative: Boolean,
    /** False for a single word, which plays alone with no Basmalah. */
    private val withBasmalah: Boolean = true,
) {

    /** A cursor into the schedule. Immutable, so peeking at the next one changes nothing. */
    data class Position(
        val round: Int,
        val unit: Int,
        val member: Int,
        /** Plays of the current ayah finished so far. */
        val completedPlays: Int,
        /** True while the Basmalah before the current ayah is the turn. */
        val inBasmalah: Boolean,
        /** Surahs whose Basmalah has been scheduled already. */
        val basmalahSurahs: Set<Int>,
    )

    private val isCumulative = cumulative && ayahs.size > 1
    private var units: List<List<Int>> = unitsFor(isCumulative)
    private var showsSteps = isCumulative

    /** The first position, which includes the Basmalah when the range starts at a surah start. */
    fun start(): Position =
        arrive(Position(0, 0, 0, 0, inBasmalah = false, basmalahSurahs = emptySet()))

    fun turnAt(position: Position): Turn {
        val kind = if (position.inBasmalah) TurnKind.Basmalah else TurnKind.Ayah
        val step = if (showsSteps) PlayStep(position.unit + 1, units.size) else null
        return Turn(kind, ayahAt(position), step)
    }

    fun ayahAt(position: Position): AyahRef = ayahs[units[position.unit][position.member]]

    /**
     * Returns the position after the turn at [position] has played, or null when the range is done.
     * [wholeSurahFiles] is true when the audio is one file per surah: the file already covers the
     * ayahs of its surah, so the schedule moves on to the next surah.
     */
    @Suppress("ReturnCount") // Guard clauses: basmalah, repeat, next member, next unit.
    fun afterPlay(
        position: Position,
        options: PlaybackOptions,
        wholeSurahFiles: Boolean = false,
    ): Position? {
        if (position.inBasmalah) return position.copy(inBasmalah = false)
        val plays = position.completedPlays + 1
        if (!isFinished(plays, options.repeatEachAyah)) {
            return position.copy(completedPlays = plays)
        }
        if (position.member + 1 < units[position.unit].size) {
            return arrive(position.copy(member = position.member + 1, completedPlays = 0))
        }
        return afterUnit(position, options, wholeSurahFiles)
    }

    /** Skips the rest of the current unit: what "next" does. */
    fun afterUnit(
        position: Position,
        options: PlaybackOptions,
        wholeSurahFiles: Boolean = false,
    ): Position? {
        val leavingSurah = ayahAt(position).surah
        var candidate = nextUnit(position, options)
        while (
            wholeSurahFiles && candidate != null && isInSurah(candidate, position, leavingSurah)
        ) {
            candidate = nextUnit(candidate, options)
        }
        return candidate
    }

    /** True when [candidate] is still in the round of [position] and in surah [surah]. */
    private fun isInSurah(candidate: Position, position: Position, surah: Int): Boolean =
        candidate.round == position.round && ayahAt(candidate).surah == surah

    /** The unit before the current one, or the start of the current unit at the first one. */
    fun beforeUnit(position: Position, wholeSurahFiles: Boolean = false): Position {
        var unit = maxOf(position.unit - 1, 0)
        if (wholeSurahFiles && position.unit > 0) {
            val surah = ayahs[units[unit][0]].surah
            while (unit > 0 && ayahs[units[unit - 1][0]].surah == surah) unit--
        }
        return position.copy(unit = unit, member = 0, completedPlays = 0, inBasmalah = false)
    }

    /** Switches to one ayah per unit; valid only at [start], when a surah file is found. */
    fun dropCumulative() {
        units = unitsFor(cumulative = false)
        showsSteps = false
    }

    @Suppress("ReturnCount") // Guard clauses: next unit, range finished, next round.
    private fun nextUnit(position: Position, options: PlaybackOptions): Position? {
        if (position.unit + 1 < units.size) {
            return arrive(position.copy(unit = position.unit + 1, member = 0, completedPlays = 0))
        }
        val rounds = position.round + 1
        if (isFinished(rounds, options.repeatRange)) return null
        return arrive(position.copy(round = rounds, unit = 0, member = 0, completedPlays = 0))
    }

    /** Puts the Basmalah in front of an ayah 1 whose surah has not had one yet. */
    private fun arrive(position: Position): Position {
        val ayah = ayahAt(position)
        val needsBasmalah =
            withBasmalah &&
                ayah.ayah == 1 &&
                ayah.surah != SURAH_WITH_BASMALAH_AS_AYAH &&
                ayah.surah != SURAH_WITHOUT_BASMALAH &&
                ayah.surah !in position.basmalahSurahs
        if (!needsBasmalah) return position.copy(inBasmalah = false)
        return position.copy(
            inBasmalah = true,
            basmalahSurahs = position.basmalahSurahs + ayah.surah,
        )
    }

    private fun unitsFor(cumulative: Boolean): List<List<Int>> {
        if (!cumulative) return ayahs.indices.map { listOf(it) }
        val steps = mutableListOf<List<Int>>()
        for (newest in ayahs.indices) {
            steps += listOf(newest)
            if (newest > 0) steps += (0..newest).toList()
        }
        return steps
    }

    /** True once [completed] plays reach [wanted]; an infinite repeat never finishes. */
    private fun isFinished(completed: Int, wanted: Int): Boolean =
        wanted != PlaybackOptions.INFINITE_REPEAT && completed >= wanted

    private companion object {
        const val SURAH_WITH_BASMALAH_AS_AYAH = 1
        const val SURAH_WITHOUT_BASMALAH = 9
    }
}
