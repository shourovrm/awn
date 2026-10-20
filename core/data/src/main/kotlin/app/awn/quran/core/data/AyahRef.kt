package app.awn.quran.core.data

/** One ayah: surah 1 to 114 and ayah from 1. A [WordKey] without the word position. */
data class AyahRef(val surah: Int, val ayah: Int) : Comparable<AyahRef> {

    override fun compareTo(other: AyahRef): Int =
        compareValuesBy(this, other, AyahRef::surah, AyahRef::ayah)

    override fun toString(): String = "$surah:$ayah"
}
