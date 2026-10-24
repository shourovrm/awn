package app.awn.quran.feature.hifz.method

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Picks the old pages to revise [today] under one [spacing]. */
class DueRevision(
    val spacing: RevisionSpacing,
    val today: LocalDate,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * At most [room] pages of [candidates], in page order. The Manzil cycle takes the pages that
     * waited longest, as many as one cycle needs a day; the other spacings take the pages whose day
     * has come, the most overdue first, so a pile of due pages is worked off at the daily limit.
     */
    fun pick(candidates: List<PageRecord>, settings: MethodSettings, room: Int): List<Int> {
        if (room <= 0 || candidates.isEmpty()) return emptyList()
        val chosen =
            when (spacing) {
                RevisionSpacing.ManzilCycle -> cycle(candidates, settings.manzilCycleDays, room)
                RevisionSpacing.Adaptive -> dueFirst(candidates.filter { it.card != null }, room)
                is RevisionSpacing.FixedSteps -> dueFirst(candidates, room)
            }
        return chosen.map { it.page }.sorted()
    }

    /**
     * The day [record] is next due under this spacing, or null in the Manzil cycle, which has no
     * fixed day, and for a page the memory model has not scheduled yet.
     */
    fun nextDayOf(record: PageRecord): LocalDate? =
        when (spacing) {
            RevisionSpacing.ManzilCycle -> null
            RevisionSpacing.Adaptive -> record.card?.let { cardDay(it.dueMillis) }
            is RevisionSpacing.FixedSteps -> fixedDay(record, spacing)
        }

    private fun cycle(candidates: List<PageRecord>, cycleDays: Int, room: Int): List<PageRecord> {
        val perDay = (candidates.size + cycleDays - 1) / cycleDays
        return candidates
            .sortedWith(
                compareBy<PageRecord, LocalDate?>(nullsFirst()) { it.lastRevisedOn }
                    .thenBy { it.page },
            )
            .take(minOf(perDay, room))
    }

    private fun dueFirst(candidates: List<PageRecord>, room: Int): List<PageRecord> =
        candidates
            .mapNotNull { record -> nextDayOf(record)?.let { day -> record to day } }
            .filter { (_, day) -> day <= today }
            .sortedWith(compareBy({ it.second }, { it.first.page }))
            .take(room)
            .map { it.first }

    private fun cardDay(dueMillis: Long): LocalDate =
        Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate()

    // A page marked known by hand has no dates yet; it waits for the first revision day its
    // marking spread it to, or is due at once without one.
    private fun fixedDay(record: PageRecord, spacing: RevisionSpacing.FixedSteps): LocalDate {
        val from = record.lastRevisedOn ?: record.learnedOn
        val card = record.card
        return when {
            from != null -> from.plusDays(spacing.stepDays[stepIndex(record, spacing)].toLong())
            card != null -> cardDay(card.dueMillis)
            else -> today
        }
    }

    private fun stepIndex(record: PageRecord, spacing: RevisionSpacing.FixedSteps): Int =
        record.spacingStep.coerceIn(0, spacing.stepDays.lastIndex)
}
