package app.awn.quran.feature.hifz.data

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.PartGrade
import java.time.LocalDate

internal fun SessionWithResults.toJournalEntry(): JournalEntry {
    val part = toFinishedPart()
    return JournalEntry(
        day = LocalDate.ofEpochDay(session.day),
        atMillis = session.at,
        kind = part.kind,
        pages = part.pages,
        firstAyah = part.firstAyah,
        lastAyah = part.lastAyah,
        grade = part.grade,
        isComplete = session.isComplete,
        durationMillis = session.durationMillis,
        ayahAnswers =
            ayahs.associate { it.toAyahRef() to AyahFirmness.fromStoredName(it.firmness) },
        pageGrades = pages.associate { it.page to PartGrade.valueOf(it.grade) },
    )
}

/** The latest answer for each ayah, from answers ordered oldest first. */
internal fun List<SessionAyahEntity>.latestByAyah(): Map<AyahRef, AyahFirmness> = associate {
    it.toAyahRef() to AyahFirmness.fromStoredName(it.firmness)
}

private fun SessionAyahEntity.toAyahRef() = AyahRef(surah, ayah)
