package app.awn.quran.update

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric supplies the real org.json that the plain JVM lacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReleaseJsonTest {

    @Test
    fun releasesAndTheirAssetsAreRead() {
        val json =
            """
            [{"tag_name":"v0.1.3","prerelease":true,"assets":[
              {"name":"app-github-arm64-v8a-release.apk","size":12345,
               "browser_download_url":"https://example.test/a.apk"}]},
             {"tag_name":"data-v1","assets":[]}]
            """
        val releases = parseReleases(json)
        assertEquals(listOf("v0.1.3", "data-v1"), releases.map { it.tagName })
        val asset = releases.first().assets.single()
        assertEquals(12345L, asset.sizeBytes)
        assertEquals("https://example.test/a.apk", asset.downloadUrl)
    }
}
