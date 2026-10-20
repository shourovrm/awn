package app.awn.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AwnVersionTest {

    @Test
    fun taggedCommitUsesTagNumbers() {
        val version = AwnVersion.fromGit(exactTag = "v0.12.7", commitCount = 321)

        assertEquals("0.12.7", version.versionName)
        assertEquals(1_207, version.baseVersionCode)
    }

    @Test
    fun untaggedCommitUsesZeroVersionAndCommitCount() {
        val version = AwnVersion.fromGit(exactTag = null, commitCount = 42)

        assertEquals("0.0.0-42", version.versionName)
        assertEquals(42, version.baseVersionCode)
    }

    @Test
    fun abiOffsetIsAddedInTheMillionsPlace() {
        val version = AwnVersion.fromGit(exactTag = "v1.2.3", commitCount = 5)

        assertEquals(1_010_203, version.versionCodeForAbi("armeabi-v7a"))
        assertEquals(2_010_203, version.versionCodeForAbi("arm64-v8a"))
        assertEquals(3_010_203, version.versionCodeForAbi("x86_64"))
    }

    @Test
    fun nonVersionTagIsTreatedAsUntagged() {
        val version = AwnVersion.fromGit(exactTag = "data-v1", commitCount = 9)

        assertEquals("0.0.0-9", version.versionName)
    }

    @Test
    fun unknownAbiFailsLoudly() {
        val version = AwnVersion.fromGit(exactTag = null, commitCount = 1)

        assertThrows(IllegalArgumentException::class.java) { version.versionCodeForAbi("mips") }
    }
}
