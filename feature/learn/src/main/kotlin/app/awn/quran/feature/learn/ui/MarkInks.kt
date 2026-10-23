package app.awn.quran.feature.learn.ui

import androidx.compose.ui.graphics.Color
import app.awn.quran.core.ui.theme.AwnLessonMarks
import app.awn.quran.feature.learn.course.WordMark

/** The ink of a [WordMark] on the light pages (Awn and Light modes). */
internal fun dayMarkInk(mark: WordMark): Color =
    when (mark) {
        WordMark.Ghunnah -> AwnLessonMarks.dayGhunnah
        WordMark.Qalqalah -> AwnLessonMarks.dayQalqalah
        WordMark.Madd -> AwnLessonMarks.dayMadd
        WordMark.Ikhfa -> AwnLessonMarks.dayIkhfa
        WordMark.Idgham -> AwnLessonMarks.dayIdgham
        WordMark.Iqlab -> AwnLessonMarks.dayIqlab
        WordMark.Heavy -> AwnLessonMarks.dayHeavy
        WordMark.Silent -> AwnLessonMarks.daySilent
        WordMark.Ending -> AwnLessonMarks.dayEnding
    }

/** The ink of a [WordMark] on the dark pages (Dark and Black modes). */
internal fun nightMarkInk(mark: WordMark): Color =
    when (mark) {
        WordMark.Ghunnah -> AwnLessonMarks.nightGhunnah
        WordMark.Qalqalah -> AwnLessonMarks.nightQalqalah
        WordMark.Madd -> AwnLessonMarks.nightMadd
        WordMark.Ikhfa -> AwnLessonMarks.nightIkhfa
        WordMark.Idgham -> AwnLessonMarks.nightIdgham
        WordMark.Iqlab -> AwnLessonMarks.nightIqlab
        WordMark.Heavy -> AwnLessonMarks.nightHeavy
        WordMark.Silent -> AwnLessonMarks.nightSilent
        WordMark.Ending -> AwnLessonMarks.nightEnding
    }
