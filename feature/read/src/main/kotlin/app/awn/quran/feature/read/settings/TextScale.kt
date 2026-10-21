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
     * The printed page has two sizes: step 0 fits the width, step 1 prints it 15 % larger. Line
     * breaks are fixed, so the glyphs stop growing once the widest line fills the width; measured
     * on the real fonts that is 4 to 12 % (median 8 %) and steps 2 to 4 changed nothing but the
     * space between lines. A wider page comes from rotating the screen.
     */
    const val MUSHAF_LAST_STEP = 1
    private const val MUSHAF_SPAN_PER_STEP = 0.15f

    private const val LEGACY_SMALL_ZOOM = 1.0f
    private const val LEGACY_MEDIUM_ZOOM = 1.15f
    private const val LEGACY_LARGE_ZOOM = 1.3f
    private const val LAST_STEP = STEP_COUNT - 1
    private const val SPAN_PER_STEP = (MAX_SCALE - MIN_SCALE) / LAST_STEP

    /** The factor for [step]; a step outside 0..7 is clamped first. */
    fun scaleOf(step: Int): Float = MIN_SCALE + clampStep(step) * SPAN_PER_STEP

    fun clampStep(step: Int): Int = step.coerceIn(0, LAST_STEP)

    /**
     * The factor that prints the Mushaf page larger than the width: 1.0 at step 0, 1.15 at step 1.
     */
    fun mushafScaleOf(step: Int): Float = 1f + clampMushafStep(step) * MUSHAF_SPAN_PER_STEP

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
