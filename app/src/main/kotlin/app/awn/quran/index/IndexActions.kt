package app.awn.quran.index

import app.awn.quran.core.data.AyahRef

/** The buttons of the index's top bar and what each opens. */
class IndexActions(
    val onSearch: () -> Unit,
    val onLearn: () -> Unit,
    val onSettings: () -> Unit,
    /**
     * Opens the Quran screen at [page]; [ayah] is set when a bookmarked or searched ayah was
     * chosen, so the Quran screen selects it.
     */
    val onOpen: (page: Int, ayah: AyahRef?) -> Unit,
)
