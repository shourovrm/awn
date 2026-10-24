package app.awn.quran.feature.duas.content

import android.content.res.AssetManager

/** Reads `duas.json`; the content slice writes the body. */
object DuaCatalogueParser {
    /** Parses the file's text; an unknown group, opening or prophet key fails with the dua id. */
    fun parse(json: String): DuaCatalogue = TODO("content slice")
}

/** Loads the bundled collection once. */
class DuaCatalogueRepository(private val assets: AssetManager) {
    private val catalogue: DuaCatalogue by lazy {
        DuaCatalogueParser.parse(assets.open(FILE_NAME).bufferedReader().use { it.readText() })
    }

    fun catalogue(): DuaCatalogue = catalogue

    companion object {
        const val FILE_NAME = "duas.json"
    }
}
