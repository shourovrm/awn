package app.awn.quran.player

import app.awn.quran.core.data.AyahRef
import java.io.File
import java.net.URI

private const val PRIMARY_DOMAIN_SUFFIX = ".quranicaudio.com"
private const val FALLBACK_DOMAIN_SUFFIX = ".quranicaudio.org"
private const val NUMBER_DIGITS = 3

/** Builds and rewrites the URLs of reciter audio. */
object AudioUrls {

    /** Fills `{SSS}` and `{AAA}` of [pattern] with the zero-padded surah and ayah numbers. */
    fun ayahUrl(pattern: String, ayah: AyahRef): String =
        pattern.replace("{SSS}", padded(ayah.surah)).replace("{AAA}", padded(ayah.ayah))

    fun surahUrl(pattern: String, surah: Int): String = pattern.replace("{SSS}", padded(surah))

    /**
     * Returns [url] on the `.quranicaudio.org` domain, or null when it is not on a
     * `.quranicaudio.com` host (so there is nothing else to try).
     */
    fun fallbackUrl(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()
        if (host == null || !host.endsWith(PRIMARY_DOMAIN_SUFFIX)) return null
        val fallbackHost = host.removeSuffix(PRIMARY_DOMAIN_SUFFIX) + FALLBACK_DOMAIN_SUFFIX
        return url.replaceFirst(host, fallbackHost)
    }

    internal fun padded(number: Int): String = number.toString().padStart(NUMBER_DIGITS, '0')
}

/**
 * Where downloaded audio lives: `<root>/<reciterId>/<SSS><AAA>.mp3` for an ayah and
 * `<root>/<reciterId>/<SSS>.mp3` for a whole surah. A file is written under the name `<file>.part`
 * and renamed when complete, so a file with the final name is always whole.
 */
class LocalAudioStore(val root: File) {

    fun reciterDirectory(reciterId: String): File {
        require(SAFE_NAME.matches(reciterId)) { "Unsafe reciter id: $reciterId" }
        return File(root, reciterId)
    }

    fun ayahKey(reciterId: String, ayah: AyahRef): String =
        "$reciterId/${AudioUrls.padded(ayah.surah)}${AudioUrls.padded(ayah.ayah)}$EXTENSION"

    fun surahKey(reciterId: String, surah: Int): String =
        "$reciterId/${AudioUrls.padded(surah)}$EXTENSION"

    /**
     * Returns the file a key from [ayahKey] or [surahKey] names, or null when the key does not have
     * that shape. The check keeps a crafted key from leaving [root].
     */
    fun fileForKey(key: String): File? {
        val match = KEY_PATTERN.matchEntire(key) ?: return null
        return File(File(root, match.groupValues[1]), match.groupValues[2])
    }

    private companion object {
        const val EXTENSION = ".mp3"
        val SAFE_NAME = Regex("[A-Za-z0-9_-]+")
        val KEY_PATTERN = Regex("([A-Za-z0-9_-]+)/(\\d{3}(?:\\d{3})?\\.mp3)")
    }
}
