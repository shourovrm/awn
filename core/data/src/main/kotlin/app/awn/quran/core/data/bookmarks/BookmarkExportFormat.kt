package app.awn.quran.core.data.bookmarks

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Bookmark
import app.awn.quran.core.data.RecentPage
import app.awn.quran.core.data.SavedAyah
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The contents of one export file. */
data class BookmarkExport(
    val bookmarks: List<Bookmark>,
    val savedAyahs: List<SavedAyah>,
    val recentPages: List<RecentPage>,
)

/**
 * Reads and writes the export document:
 * `{"format_version":1,"bookmarks":[{"page":5,"created_at":1}],"saved_ayahs":[{"surah":2,"ayah":255,
 * "note":null,"created_at":1}],"recent_pages":[{"page":5,"opened_at":1}]}`. Timestamps are epoch
 * milliseconds.
 */
class BookmarkExportFormat {

    fun write(export: BookmarkExport): String {
        val document = JSONObject()
        document.put(KEY_FORMAT_VERSION, FORMAT_VERSION)
        document.put(
            KEY_BOOKMARKS,
            JSONArray(
                export.bookmarks.map {
                    JSONObject().put(KEY_PAGE, it.page).put(KEY_CREATED_AT, it.createdAt)
                },
            ),
        )
        document.put(
            KEY_SAVED_AYAHS,
            JSONArray(
                export.savedAyahs.map {
                    JSONObject()
                        .put(KEY_SURAH, it.ayah.surah)
                        .put(KEY_AYAH, it.ayah.ayah)
                        .put(KEY_NOTE, it.note ?: JSONObject.NULL)
                        .put(KEY_CREATED_AT, it.createdAt)
                },
            ),
        )
        document.put(
            KEY_RECENT_PAGES,
            JSONArray(
                export.recentPages.map {
                    JSONObject().put(KEY_PAGE, it.page).put(KEY_OPENED_AT, it.openedAt)
                },
            ),
        )
        return document.toString()
    }

    /** Throws [IllegalArgumentException] naming the problem when the text is not a valid export. */
    fun read(json: String): BookmarkExport {
        try {
            val document = JSONObject(json)
            val version = document.getInt(KEY_FORMAT_VERSION)
            require(version in 1..FORMAT_VERSION) {
                "Export format_version is $version but this app reads up to $FORMAT_VERSION."
            }
            return BookmarkExport(
                bookmarks = readBookmarks(document.getJSONArray(KEY_BOOKMARKS)),
                savedAyahs = readSavedAyahs(document.getJSONArray(KEY_SAVED_AYAHS)),
                recentPages = readRecentPages(document.getJSONArray(KEY_RECENT_PAGES)),
            )
        } catch (error: JSONException) {
            throw IllegalArgumentException(
                "The bookmark file is malformed: ${error.message}",
                error,
            )
        }
    }

    private fun readBookmarks(rows: JSONArray): List<Bookmark> =
        objectsOf(rows).map { Bookmark(readPage(it), it.getLong(KEY_CREATED_AT)) }

    private fun readSavedAyahs(rows: JSONArray): List<SavedAyah> =
        objectsOf(rows).map {
            val surah = it.getInt(KEY_SURAH)
            val ayah = it.getInt(KEY_AYAH)
            require(surah in 1..LAST_SURAH && ayah >= 1) {
                "The bookmark file holds an invalid ayah $surah:$ayah."
            }
            val note = if (it.isNull(KEY_NOTE)) null else it.getString(KEY_NOTE)
            SavedAyah(AyahRef(surah, ayah), note, it.getLong(KEY_CREATED_AT))
        }

    private fun readRecentPages(rows: JSONArray): List<RecentPage> =
        objectsOf(rows).map { RecentPage(readPage(it), it.getLong(KEY_OPENED_AT)) }

    private fun readPage(row: JSONObject): Int {
        val page = row.getInt(KEY_PAGE)
        require(page in 1..LAST_PAGE) { "The bookmark file holds an invalid page $page." }
        return page
    }

    private fun objectsOf(rows: JSONArray): List<JSONObject> =
        (0 until rows.length()).map { rows.getJSONObject(it) }

    private companion object {
        const val FORMAT_VERSION = 1
        const val LAST_PAGE = 604
        const val LAST_SURAH = 114
        const val KEY_FORMAT_VERSION = "format_version"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_SAVED_AYAHS = "saved_ayahs"
        const val KEY_RECENT_PAGES = "recent_pages"
        const val KEY_PAGE = "page"
        const val KEY_SURAH = "surah"
        const val KEY_AYAH = "ayah"
        const val KEY_NOTE = "note"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_OPENED_AT = "opened_at"
    }
}
