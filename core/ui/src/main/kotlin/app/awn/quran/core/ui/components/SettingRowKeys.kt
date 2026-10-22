package app.awn.quran.core.ui.components

/**
 * The names of settings rows that search can open. A page gives its row the key through `rowKey`;
 * the Settings search index in :app lists the same keys. The prefix is the page.
 */
object SettingRowKeys {
    const val MUSHAF_PRINT = "mushaf.print"
    const val MUSHAF_PAGE_INFO = "mushaf.page_info"

    const val READING_TRANSLATIONS = "reading.translations"
    const val READING_TRANSLITERATION = "reading.transliteration"
    const val READING_WORD_BY_WORD = "reading.word_by_word"
    const val READING_WORD_LANGUAGE = "reading.word_language"
    const val READING_SIZE_ARABIC = "reading.size_arabic"
    const val READING_SIZE_TRANSLATION = "reading.size_translation"
    const val READING_SIZE_TRANSLITERATION = "reading.size_transliteration"
    const val READING_SIZE_WORD_BY_WORD = "reading.size_word_by_word"
    const val READING_HIGHLIGHT_BOOKMARKS = "reading.highlight_bookmarks"
    const val READING_VOLUME_KEYS = "reading.volume_keys"
    const val READING_ORIENTATION_QURAN = "reading.orientation_quran"
    const val READING_ORIENTATION_APP = "reading.orientation_app"

    const val AUDIO_RECITER = "audio.reciter"
    const val AUDIO_WORD_RECITER = "audio.word_reciter"
    const val AUDIO_DOWNLOAD_BY = "audio.download_by"
    const val AUDIO_WIFI_ONLY = "audio.wifi_only"
    const val AUDIO_STOP_ON_CLOSE = "audio.stop_on_close"
    const val AUDIO_DOWNLOADS = "audio.downloads"
    const val AUDIO_SPACE_USED = "audio.space_used"

    const val DOWNLOADS_SPACE_USED = "downloads.space_used"
    const val DOWNLOADS_PAGE_TYPES = "downloads.page_types"
    const val DOWNLOADS_TRANSLATIONS = "downloads.translations"
    const val DOWNLOADS_TRANSLITERATION = "downloads.transliteration"
    const val DOWNLOADS_WORD_BY_WORD = "downloads.word_by_word"
    const val DOWNLOADS_WORD_AUDIO = "downloads.word_audio"
    const val DOWNLOADS_LESSON_SOUNDS = "downloads.lesson_sounds"
    const val DOWNLOADS_RECITERS = "downloads.reciters"

    const val RECITE_SPEED = "recite.speed"
    const val RECITE_HIDE_WORDS = "recite.hide_words"
    const val RECITE_HINT = "recite.hint"
    const val RECITE_REMINDER = "recite.reminder"
    const val RECITE_REMINDER_TIME = "recite.reminder_time"
    const val RECITE_MEMORISED_AFTER = "recite.memorised_after"

    const val APPEARANCE_DAY = "appearance.day"
    const val APPEARANCE_NIGHT = "appearance.night"
    const val APPEARANCE_FOLLOW_DEVICE = "appearance.follow_device"
    const val APPEARANCE_LANGUAGE = "appearance.language"
    const val APPEARANCE_SURAH_MEANING = "appearance.surah_meaning"

    const val BACKUP_EXPORT = "backup.export"
    const val BACKUP_IMPORT = "backup.import"

    const val ABOUT_NOTICE = "about.notice"
    const val ABOUT_CREDITS = "about.credits"
    const val ABOUT_VERSION = "about.version"
}
