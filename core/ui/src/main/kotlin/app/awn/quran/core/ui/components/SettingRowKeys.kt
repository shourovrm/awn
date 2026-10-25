package app.awn.quran.core.ui.components

/**
 * The names of settings rows that search can open. A page gives its row the key through `rowKey`;
 * the Settings search index in :app lists the same keys. The prefix is the page. The Learn page
 * keeps the `recite.` prefix on its three keys because the rows were first built on the Recite page
 * and the keys are only ever compared inside the app.
 */
object SettingRowKeys {
    const val QURAN_MUSHAF = "quran.mushaf"
    const val QURAN_PAGE_INFO = "quran.page_info"
    const val QURAN_TRANSLATIONS = "quran.translations"
    const val QURAN_TRANSLITERATION = "quran.transliteration"
    const val QURAN_WORD_BY_WORD = "quran.word_by_word"
    const val QURAN_SIZE_ARABIC = "quran.size_arabic"
    const val QURAN_SIZE_TRANSLATION = "quran.size_translation"
    const val QURAN_SIZE_TRANSLITERATION = "quran.size_transliteration"
    const val QURAN_SIZE_WORD_BY_WORD = "quran.size_word_by_word"
    const val QURAN_HIGHLIGHT_BOOKMARKS = "quran.highlight_bookmarks"
    const val QURAN_VOLUME_KEYS = "quran.volume_keys"
    const val QURAN_KEEP_SCREEN_ON = "quran.keep_screen_on"
    const val QURAN_ROTATION = "quran.rotation"
    const val QURAN_SURAH_MEANING = "quran.surah_meaning"

    const val AUDIO_RECITER = "audio.reciter"
    const val AUDIO_WORD_RECITER = "audio.word_reciter"
    const val AUDIO_DOWNLOAD_BY = "audio.download_by"
    const val AUDIO_WIFI_ONLY = "audio.wifi_only"
    const val AUDIO_STOP_ON_CLOSE = "audio.stop_on_close"
    const val AUDIO_DOWNLOADS = "audio.downloads"

    const val STORAGE_SPACE_USED = "storage.space_used"
    const val STORAGE_MUSHAF = "storage.mushaf"
    const val STORAGE_TRANSLATIONS = "storage.translations"
    const val STORAGE_TRANSLITERATION = "storage.transliteration"
    const val STORAGE_WORD_BY_WORD = "storage.word_by_word"
    const val STORAGE_WORD_AUDIO = "storage.word_audio"
    const val STORAGE_LESSON_SOUNDS = "storage.lesson_sounds"
    const val STORAGE_RECITERS = "storage.reciters"
    const val STORAGE_RECITATION_CHECKER = "storage.recitation_checker"

    const val RECITE_HIDE_WORDS = "recite.hide_words"
    const val RECITE_HINT = "recite.hint"
    const val RECITE_MEMORISED_AFTER = "recite.memorised_after"
    const val RECITE_FOLLOW_PACE = "recite.follow_pace"

    const val LEARN_REMINDER = "recite.reminder"
    const val LEARN_REMINDER_TIME = "recite.reminder_time"
    const val LEARN_LESSON_LANGUAGE = "recite.lesson_language"

    const val COLOURS_DAY = "colours.day"
    const val COLOURS_NIGHT = "colours.night"
    const val COLOURS_FOLLOW_PHONE = "colours.follow_phone"

    /** The Language row lives on the hub itself, not on a page. */
    const val HUB_LANGUAGE = "hub.language"

    const val BACKUP_SAVE = "backup.save"
    const val BACKUP_RESTORE = "backup.restore"

    const val ABOUT_NOTICE = "about.notice"
    const val ABOUT_CREDITS = "about.credits"
    const val ABOUT_DEVELOPER = "about.developer"
    const val ABOUT_VERSION = "about.version"
}
