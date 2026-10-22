package app.awn.quran.feature.read.settings

import kotlin.math.abs

/**
 * The text-size steps: 8 values spread evenly from 0.85 to 1.6 times the base size. A step is
 * stored instead of the factor so that two devices never disagree about rounding.
 */
object TextScale {
    const val STEP_COUNT = 8
    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 1.6f

    /** Step 3 is 1.17, the nearest to the old Medium size of 1.15. */
    const val DEFAULT_STEP = 3

    /**
     * The sizes of the printed page, as multiples of its default size (the page that fits the width
     * in portrait). Below 1 the page is a narrower column; above 1 the lines are poured again once
     * a printed line no longer fits the width. A step is stored, not the factor.
     */
    private val mushafFactors = listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f, 2.0f)

    /** The step of the default size, 1.0. */
    const val MUSHAF_DEFAULT_STEP = 3

    /** The step the earlier two-size control stored for 1.15 times the width. */
    const val MUSHAF_LEGACY_LARGE_STEP = 4

    val MUSHAF_LAST_STEP: Int
        get() = mushafFactors.lastIndex

    private const val LEGACY_SMALL_ZOOM = 1.0f
    private const val LEGACY_MEDIUM_ZOOM = 1.15f
    private const val LEGACY_LARGE_ZOOM = 1.3f
    private const val LAST_STEP = STEP_COUNT - 1
    private const val SPAN_PER_STEP = (MAX_SCALE - MIN_SCALE) / LAST_STEP

    /** The factor for [step]; a step outside 0..7 is clamped first. */
    fun scaleOf(step: Int): Float = MIN_SCALE + clampStep(step) * SPAN_PER_STEP

    fun clampStep(step: Int): Int = step.coerceIn(0, LAST_STEP)

    /** The factor of the printed page for [step]; a step outside the range is clamped first. */
    fun mushafScaleOf(step: Int): Float = mushafFactors[clampMushafStep(step)]

    fun clampMushafStep(step: Int): Int = step.coerceIn(0, MUSHAF_LAST_STEP)

    /**
     * Maps the name of the old 3-step size (Small 1.0, Medium 1.15, Large 1.3) to the step with the
     * nearest factor; null for a name that was never stored.
     */
    fun stepForLegacyName(legacyName: String?): Int? {
        val legacyZoom =
            when (legacyName) {
                "Small" -> LEGACY_SMALL_ZOOM
                "Medium" -> LEGACY_MEDIUM_ZOOM
                "Large" -> LEGACY_LARGE_ZOOM
                else -> return null
            }
        return (0..LAST_STEP).minBy { step -> abs(scaleOf(step) - legacyZoom) }
    }
}
