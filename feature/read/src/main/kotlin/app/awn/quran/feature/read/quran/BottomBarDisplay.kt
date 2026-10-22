package app.awn.quran.feature.read.quran

/** How the bottom bar of the Quran screen shows. */
internal enum class BottomBarDisplay {
    /** The whole bar. */
    Full,

    /** Only a small corner button that shows what is going on and brings the bar back. */
    Mini,

    /** Nothing. */
    None,
}

/**
 * Decides how the bottom bar (audio bar, recite bar or Hifz bar) shows. A tap on the page hides the
 * bars. A bar that is busy ([isBusy]: audio playing or paused, or reciting) must not vanish, so it
 * shrinks to the corner button instead; an idle bar just goes. Ending a recitation stays with the
 * bar's Stop, so hiding the bars never ends one.
 */
internal fun bottomBarDisplay(isBusy: Boolean, areBarsVisible: Boolean): BottomBarDisplay =
    when {
        areBarsVisible -> BottomBarDisplay.Full
        isBusy -> BottomBarDisplay.Mini
        else -> BottomBarDisplay.None
    }
