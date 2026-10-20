package app.awn.quran.core.data

/** Implemented by :feature:learn and called by :feature:read and :feature:recite. */
interface ProgressEvents {

    suspend fun pageOpened(page: Int)

    /** The tracker marked every word of [ayah] as read correctly. */
    suspend fun ayahTrackedGreen(ayah: AyahRef)

    /** The user finished hiding-mode recitation of [ayah] after [skips] skipped words. */
    suspend fun hifzCompleted(ayah: AyahRef, skips: Int)
}
