package app.awn.quran.core.quranfont

/**
 * Holds at most [capacity] values. A read or a write makes its key the most recent; when a new key
 * needs room, the key that was used longest ago is dropped.
 *
 * Not thread safe: the owner guards it with its own lock, because a read changes the order too.
 */
class LeastRecentlyUsedCache<Key, Value>(private val capacity: Int) {

    init {
        require(capacity > 0) { "A cache needs room for at least one entry, not $capacity" }
    }

    // Access order moves an entry to the most recent end on a read, which eviction relies on.
    private val entries =
        object : LinkedHashMap<Key, Value>(INITIAL_BUCKETS, HASH_LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Value>?): Boolean =
                size > capacity
        }

    /** The number of entries held now; never more than the capacity. */
    val size: Int
        get() = entries.size

    /** The value of [key] or null; a hit makes [key] the most recently used. */
    fun get(key: Key): Value? = entries[key]

    /** Stores [value] under [key], dropping the least recently used entry when full. */
    fun put(key: Key, value: Value) {
        entries[key] = value
    }

    /** The keys held now, for tests and for debugging the memory use. */
    fun keys(): Set<Key> = entries.keys.toSet()

    private companion object {
        const val INITIAL_BUCKETS = 16
        const val HASH_LOAD_FACTOR = 0.75f
    }
}
