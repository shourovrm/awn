package app.awn.quran.feature.learn

/** What the buttons of the Revise screen do. [memorise] is null when Hifz has no page to open. */
internal class ReviseActions(
    val onStartClick: () -> Unit,
    val onReviseEarlyClick: () -> Unit,
    val memorise: OpenPageAction?,
)
