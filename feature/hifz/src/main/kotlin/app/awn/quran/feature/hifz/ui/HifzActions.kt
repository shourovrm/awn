package app.awn.quran.feature.hifz.ui

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.hifz.method.PartKind

/** What the main Hifz page can ask for. */
internal data class HifzActions(
    val onOpenMethod: () -> Unit,
    val onStartPart: (PartKind) -> Unit,
    val onOpenMark: () -> Unit,
    val onOpenWeakSpot: (AyahRef) -> Unit,
    val onOpenJournal: () -> Unit = {},
    val onOpenPage: (page: Int) -> Unit = {},
)
