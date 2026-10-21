package app.awn.quran.core.data.fonts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CpalNightPatchTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun paletteZeroStartsAtThePaletteOneRecordAfterPatching() {
        val patched = patchedOrFail(buildFont(paletteStartIndices = listOf(0, 4, 8)))

        assertEquals(listOf(4, 4, 8), paletteStartIndices(patched))
    }

    @Test
    fun onlyTheIndexAndTheTwoChecksumsChange() {
        val font = buildFont(paletteStartIndices = listOf(0, 4))
        val patched = patchedOrFail(font)

        val changedByteCount = font.indices.count { font[it] != patched[it] }

        // One index byte (0 to 4), the CPAL table checksum and the whole-font adjustment.
        assertTrue("changed bytes: $changedByteCount", changedByteCount <= MOST_CHANGED_BYTES)
        assertEquals(font.size, patched.size)
    }

    @Test
    fun checksumsStayValidAfterPatching() {
        val patched = patchedOrFail(buildFont(listOf(0, 4)))

        val cpalRecord = tableRecord(patched, "CPAL")
        assertEquals(
            tableChecksum(patched, cpalRecord.offset, cpalRecord.length),
            cpalRecord.checksum,
        )
        assertEquals(WHOLE_FONT_MAGIC, tableChecksum(patched, 0, patched.size))
    }

    @Test
    fun aFontWithOnePaletteIsLeftAlone() {
        assertNull(CpalNightPatch.patched(buildFont(paletteStartIndices = listOf(0))))
    }

    @Test
    fun aFontWithoutCpalIsLeftAlone() {
        assertNull(CpalNightPatch.patched(buildFont(listOf(0, 4), includeCpal = false)))
    }

    @Test
    fun bytesThatAreNotAFontAreLeftAlone() {
        assertNull(CpalNightPatch.patched(ByteArray(8)))
        assertNull(CpalNightPatch.patched(ByteArray(0)))
    }

    @Test
    fun aTruncatedFontIsLeftAlone() {
        val font = buildFont(listOf(0, 4))
        assertNull(CpalNightPatch.patched(font.copyOf(font.size - TRUNCATED_BYTES)))
    }

    @Test
    fun theNightCopyIsWrittenOnceAndReusedWhileTheSourceIsUnchanged() {
        val source = temporaryFolder.newFile("p3.ttf").apply { writeBytes(buildFont(listOf(0, 4))) }

        val first = NightFontFiles.nightFileFor(source)
        val writtenAt = first.lastModified()
        val second = NightFontFiles.nightFileFor(source)

        assertEquals(first, second)
        assertEquals(writtenAt, second.lastModified())
        assertEquals(listOf(4, 4), paletteStartIndices(first.readBytes()))
        assertEquals(listOf(0, 4), paletteStartIndices(source.readBytes()))
    }

    @Test
    fun theNightCopyIsRewrittenWhenTheSourceIsNewer() {
        val source = temporaryFolder.newFile("p3.ttf").apply { writeBytes(buildFont(listOf(0, 4))) }
        val first = NightFontFiles.nightFileFor(source)
        source.writeBytes(buildFont(listOf(0, 6)))
        source.setLastModified(first.lastModified() + SECOND_MILLIS)

        val second = NightFontFiles.nightFileFor(source)

        assertEquals(listOf(6, 6), paletteStartIndices(second.readBytes()))
    }

    @Test
    fun aFontWithoutColourReturnsTheSourceItself() {
        val source =
            temporaryFolder.newFile("p4.ttf").apply {
                writeBytes(buildFont(listOf(0, 4), includeCpal = false))
            }

        assertEquals(source, NightFontFiles.nightFileFor(source))
    }

    @Test
    fun aRealTajweedPageFontGetsItsNightPalette() {
        val real = REAL_FONT_CANDIDATES.map(::File).firstOrNull { it.isFile }
        assumeTrue("no real V4 font on this machine", real != null)
        val original = checkNotNull(real).readBytes()

        val patched = patchedOrFail(original)

        assertEquals(paletteStartIndices(original)[1], paletteStartIndices(patched)[0])
        assertEquals(WHOLE_FONT_MAGIC, tableChecksum(patched, 0, patched.size))
        assertArrayEquals(
            "tables other than CPAL and head are untouched",
            tableBytes(original, "glyf"),
            tableBytes(patched, "glyf"),
        )
    }

    private fun patchedOrFail(font: ByteArray): ByteArray =
        checkNotNull(CpalNightPatch.patched(font)) { "the font was not patched" }

    // A small font builder: the patch reads only the table directory, head and CPAL.

    private fun buildFont(
        paletteStartIndices: List<Int>,
        includeCpal: Boolean = true,
    ): ByteArray {
        val headTable = ByteArray(HEAD_LENGTH)
        val tables = buildList {
            add("head" to headTable)
            if (includeCpal) add("CPAL" to buildCpal(paletteStartIndices))
        }
        val directoryLength = SFNT_HEADER_BYTES + tables.size * TABLE_RECORD_BYTES
        val font = ByteBuffer.allocate(directoryLength + tables.sumOf { padded(it.second.size) })
        font.order(ByteOrder.BIG_ENDIAN)
        font.putInt(SFNT_VERSION_TRUETYPE)
        font.putShort(tables.size.toShort())
        font.position(SFNT_HEADER_BYTES)
        var nextOffset = directoryLength
        for ((tag, bytes) in tables) {
            font.put(tag.toByteArray(Charsets.US_ASCII))
            font.putInt(tableChecksum(bytes, 0, bytes.size).toInt())
            font.putInt(nextOffset)
            font.putInt(bytes.size)
            nextOffset += padded(bytes.size)
        }
        nextOffset = directoryLength
        for ((_, bytes) in tables) {
            font.position(nextOffset)
            font.put(bytes)
            nextOffset += padded(bytes.size)
        }
        val result = font.array()
        // Like a font compiler, give the font a valid whole-font checksum adjustment.
        val adjustmentAt = tableRecord(result, "head").offset + ADJUSTMENT_OFFSET_IN_HEAD
        val adjustment = (WHOLE_FONT_MAGIC - tableChecksum(result, 0, result.size)) and UNSIGNED_INT
        ByteBuffer.wrap(result).putInt(adjustmentAt, adjustment.toInt())
        return result
    }

    private fun buildCpal(paletteStartIndices: List<Int>): ByteArray {
        val colourRecordCount = 16
        val headerLength = CPAL_HEADER_BYTES + paletteStartIndices.size * 2
        val table = ByteBuffer.allocate(headerLength + colourRecordCount * 4)
        table.putShort(0) // version 0
        table.putShort(2) // entries per palette
        table.putShort(paletteStartIndices.size.toShort())
        table.putShort(colourRecordCount.toShort())
        table.putInt(headerLength)
        paletteStartIndices.forEach { table.putShort(it.toShort()) }
        repeat(colourRecordCount) { table.putInt(0xFF000000.toInt() or (it * 3)) }
        return table.array()
    }

    private fun paletteStartIndices(font: ByteArray): List<Int> {
        val cpal = tableRecord(font, "CPAL")
        val paletteCount = readUnsignedShort(font, cpal.offset + PALETTE_COUNT_OFFSET)
        return List(paletteCount) {
            readUnsignedShort(font, cpal.offset + CPAL_HEADER_BYTES + it * 2)
        }
    }

    private fun tableBytes(font: ByteArray, tag: String): ByteArray {
        val record = tableRecord(font, tag)
        return font.copyOfRange(record.offset, record.offset + record.length)
    }

    private class Record(val offset: Int, val length: Int, val checksum: Long)

    private fun tableRecord(font: ByteArray, tag: String): Record {
        val tableCount = readUnsignedShort(font, TABLE_COUNT_OFFSET)
        for (index in 0 until tableCount) {
            val at = SFNT_HEADER_BYTES + index * TABLE_RECORD_BYTES
            if (String(font, at, TAG_BYTES, Charsets.US_ASCII) == tag) {
                return Record(
                    offset = ByteBuffer.wrap(font).getInt(at + 8),
                    length = ByteBuffer.wrap(font).getInt(at + 12),
                    checksum = ByteBuffer.wrap(font).getInt(at + 4).toLong() and UNSIGNED_INT,
                )
            }
        }
        error("no $tag table")
    }

    /** The sfnt checksum: the sum of the big-endian 32-bit words, the tail padded with zeros. */
    private fun tableChecksum(bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var at = 0
        while (at < length) {
            var word = 0L
            for (byteIndex in 0 until 4) {
                val isInside = at + byteIndex < length
                val value = if (isInside) bytes[offset + at + byteIndex].toLong() and 0xFF else 0L
                word = (word shl 8) or value
            }
            sum = (sum + word) and UNSIGNED_INT
            at += 4
        }
        return sum
    }

    private fun padded(length: Int) = (length + 3) / 4 * 4

    private fun readUnsignedShort(bytes: ByteArray, at: Int) =
        ByteBuffer.wrap(bytes).getShort(at).toInt() and 0xFFFF

    private companion object {
        const val SFNT_VERSION_TRUETYPE = 0x00010000
        const val SFNT_HEADER_BYTES = 12
        const val TABLE_RECORD_BYTES = 16
        const val TABLE_COUNT_OFFSET = 4
        const val TAG_BYTES = 4
        const val HEAD_LENGTH = 54
        const val ADJUSTMENT_OFFSET_IN_HEAD = 8
        const val CPAL_HEADER_BYTES = 12
        const val PALETTE_COUNT_OFFSET = 4
        const val TRUNCATED_BYTES = 20
        const val MOST_CHANGED_BYTES = 1 + 4 + 4
        const val UNSIGNED_INT = 0xFFFFFFFFL
        const val WHOLE_FONT_MAGIC = 0xB1B0AFBAL
        const val SECOND_MILLIS = 1_000L
        val REAL_FONT_CANDIDATES =
            listOf(
                "/tmp/v4new/p106.ttf",
                "/home/rms/repos/awn/sessions/recite-mocks/p1-v4.ttf",
            )
    }
}
