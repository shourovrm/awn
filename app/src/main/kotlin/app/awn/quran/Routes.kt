package app.awn.quran

import app.awn.quran.core.data.AyahRef

internal object Routes {
    const val PAGE_ARGUMENT = "page"
    const val SURAH_ARGUMENT = "surah"
    const val AYAH_ARGUMENT = "ayah"
    const val NOT_GIVEN = -1

    /** The Quran screen. The app starts here; without a page it opens the page read last. */
    const val QURAN =
        "quran?$PAGE_ARGUMENT={$PAGE_ARGUMENT}&$SURAH_ARGUMENT={$SURAH_ARGUMENT}" +
            "&$AYAH_ARGUMENT={$AYAH_ARGUMENT}"
    const val INDEX = "index"
    const val SEARCH = "search"
    const val LEARN = "learn"
    const val SETTINGS = "settings"
    const val NOTICE = "notice"
    const val CREDITS = "credits"
    const val LISTEN = "listen"
    const val RECITERS = "reciters"
    const val DOWNLOADS = "downloads"
    const val RECITE = "recite"

    fun quran(page: Int? = null, ayah: AyahRef? = null): String =
        "quran?$PAGE_ARGUMENT=${page ?: NOT_GIVEN}" +
            "&$SURAH_ARGUMENT=${ayah?.surah ?: NOT_GIVEN}&$AYAH_ARGUMENT=${ayah?.ayah ?: NOT_GIVEN}"
}
