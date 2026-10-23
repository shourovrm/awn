package app.awn.quran.feature.learn.ui

/** What a tap on the Quran words page can do besides the hub's own actions. */
internal class WordsTrackActions(
    val hub: HubActions,
    val onOpenSet: (Int) -> Unit,
    val onOpenPageWords: (Int) -> Unit,
)
