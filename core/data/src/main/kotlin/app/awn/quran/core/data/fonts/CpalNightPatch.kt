package app.awn.quran.core.data.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Makes a colour font draw its night palette. Android always draws palette 0 of a COLR/CPAL font
 * and offers no way to choose another, but the tajweed page fonts keep their white-letter night
 * palette at index 1. A CPAL table lists, for each palette, the index of its first colour record;
 * pointing palette 0 at the first record of palette 1 makes the system draw the night colours.
 */
object CpalNightPatch {
    /**
     * Returns a copy of [font] whose palette 0 is palette 1, or null when there is nothing to do:
     * the bytes are not an sfnt font, the font has no CPAL table, CPAL has fewer than two palettes,
     * or a table lies outside the file.
     */
    @Suppress("ReturnCount") // Each guard is one reason a font cannot be patched.
    fun patched(font: ByteArray): ByteArray? {
        val tables = readTableDirectory(font) ?: return null
        val cpal = tables["CPAL"] ?: return null
        val head = tables["head"] ?: return null
        if (!cpal.fitsIn(font) || !head.fitsIn(font)) return null
        if (cpal.length < FIRST_PALETTE_INDEX_OFFSET + TWO_PALETTE_INDICES_BYTES) return null
        if (head.length < ADJUSTMENT_OFFSET_IN_HEAD + WORD_BYTES) return null

        val buffer = ByteBuffer.wrap(font.copyOf()).order(ByteOrder.BIG_ENDIAN)
        val paletteCount = buffer.getUnsignedShort(cpal.offset + PALETTE_COUNT_OFFSET)
        if (paletteCount < 2) return null
        // The record indices follow the 12-byte header; the first two are palette 0 and palette 1.
        val entriesPerPalette = buffer.getUnsignedShort(cpal.offset + ENTRIES_PER_PALETTE_OFFSET)
        val colourRecordCount = buffer.getUnsignedShort(cpal.offset + COLOUR_RECORD_COUNT_OFFSET)
        val nightFirstRecord =
            buffer.getUnsignedShort(cpal.offset + FIRST_PALETTE_INDEX_OFFSET + PALETTE_INDEX_BYTES)
        // Palette 1 must exist in full, or the system would read colours past the record array.
        if (nightFirstRecord + entriesPerPalette > colourRecordCount) return null
        buffer.putShort(cpal.offset + FIRST_PALETTE_INDEX_OFFSET, nightFirstRecord.toShort())

        // The font stays well-formed: a viewer that verifies checksums sees consistent numbers.
        buffer.putInt(
            cpal.recordOffset + CHECKSUM_OFFSET_IN_RECORD,
            sumOfWords(buffer.array(), cpal.offset, cpal.length).toInt(),
        )
        buffer.putInt(head.offset + ADJUSTMENT_OFFSET_IN_HEAD, 0)
        val wholeFont = sumOfWords(buffer.array(), 0, buffer.capacity())
        buffer.putInt(
            head.offset + ADJUSTMENT_OFFSET_IN_HEAD,
            ((WHOLE_FONT_CHECKSUM - wholeFont) and UNSIGNED_INT_MASK).toInt(),
        )
        return buffer.array()
    }

    private class TableRecord(val recordOffset: Int, val offset: Int, val length: Int) {
        fun fitsIn(font: ByteArray): Boolean =
            offset >= 0 && length >= 0 && offset.toLong() + length <= font.size
    }

    @Suppress("ReturnCount") // Each guard is one reason the bytes are not a font.
    private fun readTableDirectory(font: ByteArray): Map<String, TableRecord>? {
        if (font.size < SFNT_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.getInt(0)
        if (version !in SFNT_VERSIONS) return null
        val tableCount = buffer.getUnsignedShort(TABLE_COUNT_OFFSET)
        if (font.size < SFNT_HEADER_BYTES + tableCount * TABLE_RECORD_BYTES) return null
        return buildMap {
            for (index in 0 until tableCount) {
                val recordOffset = SFNT_HEADER_BYTES + index * TABLE_RECORD_BYTES
                val tag = String(font, recordOffset, TAG_BYTES, Charsets.US_ASCII)
                put(
                    tag,
                    TableRecord(
                        recordOffset = recordOffset,
                        offset = buffer.getInt(recordOffset + OFFSET_IN_RECORD),
                        length = buffer.getInt(recordOffset + LENGTH_IN_RECORD),
                    ),
                )
            }
        }
    }

    /** The sfnt checksum: the sum of big-endian 32-bit words, the last one padded with zeros. */
    private fun sumOfWords(bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var position = 0
        while (position < length) {
            var word = 0L
            for (byteIndex in 0 until WORD_BYTES) {
                val isInside = position + byteIndex < length
                val value =
                    if (isInside) bytes[offset + position + byteIndex].toLong() and BYTE_MASK
                    else 0L
                word = (word shl BITS_PER_BYTE) or value
            }
            sum = (sum + word) and UNSIGNED_INT_MASK
            position += WORD_BYTES
        }
        return sum
    }

    private fun ByteBuffer.getUnsignedShort(index: Int): Int =
        getShort(index).toInt() and UNSIGNED_SHORT_MASK

    private val SFNT_VERSIONS =
        setOf(0x00010000, 0x4F54544F, 0x74727565) // TrueType, 'OTTO', 'true'
    private const val SFNT_HEADER_BYTES = 12
    private const val TABLE_COUNT_OFFSET = 4
    private const val TABLE_RECORD_BYTES = 16
    private const val TAG_BYTES = 4
    private const val CHECKSUM_OFFSET_IN_RECORD = 4
    private const val OFFSET_IN_RECORD = 8
    private const val LENGTH_IN_RECORD = 12
    private const val ENTRIES_PER_PALETTE_OFFSET = 2
    private const val PALETTE_COUNT_OFFSET = 4
    private const val COLOUR_RECORD_COUNT_OFFSET = 6
    private const val FIRST_PALETTE_INDEX_OFFSET = 12
    private const val ADJUSTMENT_OFFSET_IN_HEAD = 8
    private const val PALETTE_INDEX_BYTES = 2
    private const val TWO_PALETTE_INDICES_BYTES = 2 * PALETTE_INDEX_BYTES
    private const val UNSIGNED_SHORT_MASK = 0xFFFF
    private const val WORD_BYTES = 4
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFFL
    private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
    private const val WHOLE_FONT_CHECKSUM = 0xB1B0AFBAL
}
