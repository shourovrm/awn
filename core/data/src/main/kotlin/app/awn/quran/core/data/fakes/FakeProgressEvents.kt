package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents

/** Records every call so a test can assert what a feature reported. */
class FakeProgressEvents : ProgressEvents {

    val openedPages = mutableListOf<Int>()
    val greenAyahs = mutableListOf<AyahRef>()
    val hifzCompletions = mutableListOf<Pair<AyahRef, Int>>()

    override suspend fun pageOpened(page: Int) {
        openedPages += page
    }

    override suspend fun ayahTrackedGreen(ayah: AyahRef) {
        greenAyahs += ayah
    }

    override suspend fun hifzCompleted(ayah: AyahRef, skips: Int) {
        hifzCompletions += ayah to skips
    }
}
