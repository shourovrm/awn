package app.awn.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version codes must only ever grow along the history: a tagged release must be higher than every
 * untagged build before it, and every untagged build after a tag must be higher than that tag.
 * The input is the output of `git describe --tags --match 'v*' --long` (null when no tag exists).
 */
class AwnVersionTest {

    @Test
    fun taggedCommitUsesTagNumbersAndZeroCommits() {
        val version = AwnVersion.fromGit(describe = "v0.12.7-0-gabc1234", commitCount = 321)

        assertEquals("0.12.7", version.versionName)
        assertEquals(1_207 * 10_000, version.baseVersionCode)
    }

    @Test
    fun commitsAfterATagAreAddedToTheTagCode() {
        val version = AwnVersion.fromGit(describe = "v0.1.0-5-gdeadbee", commitCount = 400)

        assertEquals("0.1.0-5", version.versionName)
        assertEquals(100 * 10_000 + 5, version.baseVersionCode)
    }

    @Test
    fun noTagYetUsesZeroVersionAndCommitCount() {
        val version = AwnVersion.fromGit(describe = null, commitCount = 290)

        assertEquals("0.0.0-290", version.versionName)
        assertEquals(290, version.baseVersionCode)
    }

    @Test
    fun firstTagIsHigherThanEveryUntaggedBuildBeforeIt() {
        val lastUntagged = AwnVersion.fromGit(describe = null, commitCount = 9_999)
        val firstTag = AwnVersion.fromGit(describe = "v0.1.0-0-g1111111", commitCount = 10_000)

        assertTrue(firstTag.baseVersionCode > lastUntagged.baseVersionCode)
    }

    @Test
    fun nextTagIsHigherThanEveryBuildAfterThePreviousTag() {
        val afterFirstTag = AwnVersion.fromGit(describe = "v0.1.0-9999-g2222222", commitCount = 1)
        val patchTag = AwnVersion.fromGit(describe = "v0.1.1-0-g3333333", commitCount = 1)

        assertTrue(patchTag.baseVersionCode > afterFirstTag.baseVersionCode)
    }

    @Test
    fun abiOffsetIsAboveTheHighestBaseCode() {
        val version = AwnVersion.fromGit(describe = "v0.1.2-3-g4444444", commitCount = 5)
        val base = 102 * 10_000 + 3

        assertEquals(100_000_000 + base, version.versionCodeForAbi("armeabi-v7a"))
        assertEquals(200_000_000 + base, version.versionCodeForAbi("arm64-v8a"))
        assertEquals(300_000_000 + base, version.versionCodeForAbi("x86_64"))
    }

    @Test
    fun highestAllowedVersionStaysBelowTheAbiStep() {
        val version = AwnVersion.fromGit(describe = "v0.99.99-9999-g5555555", commitCount = 1)

        assertTrue(version.baseVersionCode < AwnVersion.ABI_OFFSET_STEP)
    }

    @Test
    fun describeWithoutAVersionTagIsTreatedAsUntagged() {
        val version = AwnVersion.fromGit(describe = "data-v1-3-g6666666", commitCount = 9)

        assertEquals("0.0.0-9", version.versionName)
        assertEquals(9, version.baseVersionCode)
    }

    @Test
    fun tooManyCommitsSinceATagFailLoudly() {
        assertThrows(IllegalArgumentException::class.java) {
            AwnVersion.fromGit(describe = "v0.1.0-10000-g7777777", commitCount = 1)
        }
    }

    @Test
    fun majorVersionAboveZeroFailsLoudly() {
        assertThrows(IllegalArgumentException::class.java) {
            AwnVersion.fromGit(describe = "v1.0.0-0-g8888888", commitCount = 1)
        }
    }

    @Test
    fun unknownAbiFailsLoudly() {
        val version = AwnVersion.fromGit(describe = null, commitCount = 1)

        assertThrows(IllegalArgumentException::class.java) { version.versionCodeForAbi("mips") }
    }
}
