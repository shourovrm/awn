@file:Suppress(
    "MatchingDeclarationName",
) // The mappers are the subject; the names object only holds their strings.

package app.awn.quran.feature.hifz.data

import app.awn.quran.feature.hifz.method.RevisionSpacing

/** The stored names of [RevisionSpacing]; saved in the plan, so they never change. */
internal object RevisionSpacingName {
    const val MANZIL_CYCLE = "manzil_cycle"
    const val FIXED_STEPS = "fixed_steps"
    const val ADAPTIVE = "adaptive"
}

/** The spacing a plan row stores, or null when the row predates the setting. */
internal fun spacingOf(name: String?, steps: String?): RevisionSpacing? =
    when (name) {
        null -> null
        RevisionSpacingName.MANZIL_CYCLE -> RevisionSpacing.ManzilCycle
        RevisionSpacingName.ADAPTIVE -> RevisionSpacing.Adaptive
        RevisionSpacingName.FIXED_STEPS ->
            steps
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.map { it.trim().toInt() }
                ?.let { RevisionSpacing.FixedSteps(it) } ?: RevisionSpacing.FixedSteps()
        else -> error("Unknown revision spacing '$name' in the hifz database.")
    }

internal fun RevisionSpacing.storedName(): String =
    when (this) {
        RevisionSpacing.ManzilCycle -> RevisionSpacingName.MANZIL_CYCLE
        RevisionSpacing.Adaptive -> RevisionSpacingName.ADAPTIVE
        is RevisionSpacing.FixedSteps -> RevisionSpacingName.FIXED_STEPS
    }

internal fun RevisionSpacing.storedSteps(): String? =
    (this as? RevisionSpacing.FixedSteps)?.stepDays?.joinToString(",")
