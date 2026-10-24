package app.awn.quran.feature.duas.state

import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaCatalogue
import app.awn.quran.feature.duas.content.DuaGroup
import app.awn.quran.feature.duas.content.LocalName
import app.awn.quran.feature.duas.data.DuaProgress

/** The name in the app language; Bangla falls back to English when the data has no Bangla. */
fun LocalName.inLanguage(language: String): String =
    if (language == BANGLA_LANGUAGE) bangla.ifBlank { english } else english

private const val BANGLA_LANGUAGE = "bn"

/** One row of the Groups list: the number badge, the name, and the counts the filter leaves. */
data class DuaGroupRow(
    val group: DuaGroup,
    /** Position in the data's order, from 1. */
    val number: Int,
    val duaCount: Int,
    val learnedCount: Int,
)

/** The duas of one group that pass [filter], in the data's order. */
fun duasOfGroup(
    catalogue: DuaCatalogue,
    groupKey: String,
    filter: DuasFilter,
    progress: DuaProgress,
): List<Dua> = catalogue.duasIn(groupKey).filter { filter.accepts(it, progress) }

fun starterDuas(catalogue: DuaCatalogue, filter: DuasFilter, progress: DuaProgress): List<Dua> =
    catalogue.starters.filter { filter.accepts(it, progress) }

/** Group rows in the data's order; a group with no dua left by the filter has no row. */
fun groupRows(
    catalogue: DuaCatalogue,
    filter: DuasFilter,
    progress: DuaProgress,
): List<DuaGroupRow> =
    catalogue.groups.mapIndexedNotNull { index, group ->
        val duas = duasOfGroup(catalogue, group.key, filter, progress)
        if (duas.isEmpty()) {
            null
        } else {
            DuaGroupRow(
                group = group,
                number = index + 1,
                duaCount = duas.size,
                learnedCount = duas.count { progress.isLearned(it.id) },
            )
        }
    }

/** How many duas the filter leaves in the whole collection. */
fun filteredDuaCount(catalogue: DuaCatalogue, filter: DuasFilter, progress: DuaProgress): Int =
    catalogue.duas.count { filter.accepts(it, progress) }
