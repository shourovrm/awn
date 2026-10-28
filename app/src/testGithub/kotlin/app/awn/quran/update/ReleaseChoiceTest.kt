package app.awn.quran.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseChoiceTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://example.test/$name", 100L)

    private fun release(tagName: String, vararg assetNames: String) =
        AppRelease(tagName, assetNames.map(::asset))

    @Test
    fun theNewestAppReleaseWinsByVersionNotByListOrder() {
        val releases = listOf(release("v0.1.2"), release("v0.1.10"), release("v0.1.3"))
        assertEquals("v0.1.10", newestAppRelease(releases)?.tagName)
    }

    @Test
    fun dataReleasesAreSkipped() {
        val releases = listOf(release("data-v1"), release("models-v1"), release("v0.1.2"))
        assertEquals("v0.1.2", newestAppRelease(releases)?.tagName)
    }

    @Test
    fun noAppReleaseGivesNull() {
        assertNull(newestAppRelease(listOf(release("data-v1"))))
    }

    @Test
    fun theApkFollowsTheOrderOfThePhoneAbis() {
        val assets =
            listOf(
                asset("app-github-armeabi-v7a-release.apk"),
                asset("app-github-arm64-v8a-release.apk"),
                asset("SHA256SUMS.txt"),
            )
        val chosen = pickApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-github-arm64-v8a-release.apk", chosen?.name)
    }

    @Test
    fun aLaterAbiIsUsedWhenTheFirstHasNoApk() {
        val assets = listOf(asset("app-github-armeabi-v7a-release.apk"))
        val chosen = pickApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-github-armeabi-v7a-release.apk", chosen?.name)
    }

    @Test
    fun noMatchingApkGivesNull() {
        val assets = listOf(asset("app-github-x86_64-release.apk"))
        assertNull(pickApkAsset(assets, listOf("arm64-v8a")))
    }

    @Test
    fun checksumLineIsFoundByFileNameIgnoringSha256sumPrefixes() {
        val text =
            """
            aaaa  ./app-github-armeabi-v7a-release.apk
            BBBB  ./app-github-arm64-v8a-release.apk
            """
                .trimIndent()
        assertEquals("bbbb", expectedChecksum(text, "app-github-arm64-v8a-release.apk"))
        assertNull(expectedChecksum(text, "other.apk"))
    }
}
