package app.awn.quran.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun aNewerTagIsAnUpdate() {
        assertTrue(isNewerRelease(releaseTag = "v0.1.3", installedVersionName = "0.1.2"))
        assertTrue(isNewerRelease(releaseTag = "v0.2.0", installedVersionName = "0.1.9"))
    }

    @Test
    fun theSameTagIsNotAnUpdate() {
        assertFalse(isNewerRelease(releaseTag = "v0.1.2", installedVersionName = "0.1.2"))
    }

    @Test
    fun anOlderTagIsNotAnUpdate() {
        assertFalse(isNewerRelease(releaseTag = "v0.1.1", installedVersionName = "0.1.2"))
    }

    @Test
    fun theCommitSuffixOfABuildIsIgnored() {
        assertFalse(isNewerRelease(releaseTag = "v0.1.2", installedVersionName = "0.1.2-36"))
        assertTrue(isNewerRelease(releaseTag = "v0.1.3", installedVersionName = "0.1.2-36"))
    }

    @Test
    fun partsAreComparedAsNumbersNotText() {
        assertTrue(isNewerRelease(releaseTag = "v0.1.10", installedVersionName = "0.1.9"))
    }

    @Test
    fun aTagWithoutVersionNumbersIsNotAnUpdate() {
        assertFalse(isNewerRelease(releaseTag = "data-v1", installedVersionName = "0.1.2"))
        assertFalse(isNewerRelease(releaseTag = "v0.x.1", installedVersionName = "0.1.2"))
    }

    @Test
    fun anInstalledVersionThatCannotBeReadIsNotUpdated() {
        assertFalse(isNewerRelease(releaseTag = "v0.1.3", installedVersionName = ""))
    }

    @Test
    fun theVersionNameDropsTheLetterV() {
        assertEquals("0.1.3", releaseVersionName("v0.1.3"))
        assertEquals("0.1.3", releaseVersionName("0.1.3"))
    }

    @Test
    fun parsingNeedsThreeNumbers() {
        assertEquals(ReleaseVersion(0, 1, 3), parseReleaseVersion("v0.1.3"))
        assertNull(parseReleaseVersion("v0.1"))
        assertNull(parseReleaseVersion("v0.1.2.4"))
    }
}
