package app.awn.quran

import app.awn.quran.core.data.AyahRef

internal object Routes {
    const val PAGE_ARGUMENT = "page"
    const val SURAH_ARGUMENT = "surah"
    const val AYAH_ARGUMENT = "ayah"
    const val NOT_GIVEN = -1

    /** The Quran screen, opened above the tabs; without a page it opens the page read last. */
    const val QURAN =
        "quran?$PAGE_ARGUMENT={$PAGE_ARGUMENT}&$SURAH_ARGUMENT={$SURAH_ARGUMENT}" +
            "&$AYAH_ARGUMENT={$AYAH_ARGUMENT}"
    /** The three tabs (Quran, Learn, Hifz) over the bottom bar. The app starts here. */
    const val TABS = "tabs"
    const val BOOKMARKS = "bookmarks"
    const val SEARCH = "search"
    const val DUAS = "duas?group={group}"
    const val TOPICS = "topics"
    const val DUAS_GROUP_ARGUMENT = "group"

    /** The Duas screen, opened at the cards of [group] when it is given. */
    fun duas(group: String? = null): String = if (group == null) "duas" else "duas?group=$group"

    const val SETTINGS = "settings"
    const val NOTICE = "notice"
    const val CREDITS = "credits"

    fun quran(page: Int? = null, ayah: AyahRef? = null): String =
        "quran?$PAGE_ARGUMENT=${page ?: NOT_GIVEN}" +
            "&$SURAH_ARGUMENT=${ayah?.surah ?: NOT_GIVEN}&$AYAH_ARGUMENT=${ayah?.ayah ?: NOT_GIVEN}"
}
