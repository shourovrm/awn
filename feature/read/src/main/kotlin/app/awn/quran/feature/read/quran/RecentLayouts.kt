package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId

/**
 * The page layouts loaded most recently, kept for the whole process. The Quran screen's state is
 * rebuilt when the reader comes back from Settings; with the layout of the page still in memory the
 * page is on screen in the first frame instead of after a database read.
 *
 * A layout depends on the installed packs, so [clear] is called whenever a pack is installed or
 * removed.
 */
internal object RecentLayouts {
    private const val CAPACITY = 12
    private const val LOAD_FACTOR = 0.75f

    private data class Key(val source: QuranTextRepository, val script: ScriptId, val page: Int)

    private val entries =
        object : LinkedHashMap<Key, PageLayout>(CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, PageLayout>) =
                size > CAPACITY
        }

    @Synchronized
    fun get(source: QuranTextRepository, script: ScriptId, page: Int): PageLayout? =
        entries[Key(source, script, page)]

    @Synchronized
    fun put(source: QuranTextRepository, layout: PageLayout) {
        entries[Key(source, layout.script, layout.page)] = layout
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
