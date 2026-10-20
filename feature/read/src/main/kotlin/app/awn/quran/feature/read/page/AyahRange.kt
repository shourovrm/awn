package app.awn.quran.feature.read.page

import app.awn.quran.core.data.AyahRef

/** The ayahs from [first] to [last] in reading order; one ayah when they are equal. */
data class AyahRange(val first: AyahRef, val last: AyahRef = first) {
    operator fun contains(ayah: AyahRef): Boolean = ayah >= first && ayah <= last

    val isSingle: Boolean
        get() = first == last

    companion object {
        /** The range between two ayahs given in either order, as when a drag goes upward. */
        fun between(anchor: AyahRef, other: AyahRef): AyahRange =
            if (anchor <= other) AyahRange(anchor, other) else AyahRange(other, anchor)
    }
}
