package app.awn.quran.feature.duas.ui

import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.duas.card.DuaCardData
import app.awn.quran.feature.duas.card.DuaWordTile
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaOpening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuaPartTranslationTest {

    private fun tile(position: Int, meaning: String) =
        DuaWordTile(
            key = WordKey(2, 201, position),
            arabic = "w$position",
            meaning = meaning,
            transliteration = "",
        )

    @Test
    fun meaningsOfTheDuaWordsAreJoinedInOrder() {
        val tiles = listOf(tile(1, "Our Lord"), tile(2, "give us"), tile(3, "good"))

        assertEquals("Our Lord give us good", duaPartTranslationOf(tiles))
    }

    @Test
    fun blankMeaningsAreLeftOutOfTheJoin() {
        val tiles = listOf(tile(1, "Our Lord"), tile(2, ""), tile(3, "   "), tile(4, "good"))

        assertEquals("Our Lord good", duaPartTranslationOf(tiles))
    }

    @Test
    fun noMeaningsGiveNoPartTranslation() {
        assertNull(duaPartTranslationOf(listOf(tile(1, ""), tile(2, " "))))
        assertNull(duaPartTranslationOf(emptyList()))
    }

    private fun cardWithWholeTranslation(): DuaCardData =
        DuaCardData(
            dua =
                Dua(
                    "d",
                    WordKey(2, 201, 1),
                    WordKey(2, 201, 3),
                    "g",
                    DuaOpening.Rabbana,
                    prophetKey = null,
                    isStarter = false,
                ),
            words = emptyList(),
            reference = "Al-Baqarah 2:201",
            translation = "whole ayah translation",
        )

    @Test
    fun withoutDuaOnlyTheWholeTranslationIsShown() {
        val tiles = listOf(tile(1, "Our Lord"))

        val shown = translationShownOf(cardWithWholeTranslation(), tiles, showDuaOnly = false)

        assertEquals("whole ayah translation", shown)
    }

    @Test
    fun duaOnlyShowsThePartTranslationWhenMeaningsExist() {
        val tiles = listOf(tile(1, "Our Lord"), tile(2, "give us"))
        val shown = translationShownOf(cardWithWholeTranslation(), tiles, showDuaOnly = true)

        assertEquals("Our Lord give us", shown)
    }

    @Test
    fun duaOnlyFallsBackToTheWholeTranslationWithoutMeanings() {
        val shown = translationShownOf(cardWithWholeTranslation(), emptyList(), showDuaOnly = true)

        assertEquals("whole ayah translation", shown)
    }
}
