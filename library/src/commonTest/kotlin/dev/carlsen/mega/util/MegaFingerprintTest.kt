package dev.carlsen.mega.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MegaFingerprintTest {

    @Test
    fun crc32MatchesReferenceVector() {
        // Standard IEEE CRC-32 check value.
        val data = "123456789".encodeToByteArray()
        val c = Crc32()
        c.update(data, 0, data.size)
        assertEquals(0xCBF43926L, c.value)
    }

    @Test
    fun tinyFileCopiesContentVerbatim() {
        assertEquals(
            "aGVsbG8AAAAAAAAAAAAAAAEB",
            MegaFingerprint.compute("hello".encodeToByteArray(), 1L),
        )
    }

    @Test
    fun fullCoverageFingerprint() {
        val data = ByteArray(100) { it.toByte() }
        assertEquals(
            "2IDUDIExcicOatw3dZqBIQQA8VNl",
            MegaFingerprint.compute(data, 1_700_000_000L),
        )
    }

    @Test
    fun sparseCoverageFingerprint() {
        val data = ByteArray(20_000) { (it * 31 % 256).toByte() }
        assertEquals(
            "W2ap7LrBxeddyTmOHG3uqQQA8VNl",
            MegaFingerprint.compute(data, 1_700_000_000L),
        )
    }

    @Test
    fun streamingBuilderMatchesOneShotCompute() {
        // Cover the sparse path with overlapping blocks (just above 8192), block
        // boundaries straddling chunk boundaries, and a large spread-out file.
        for (size in intArrayOf(8_193, 9_000, 20_000, 100_003)) {
            val data = ByteArray(size) { (it * 31 % 256).toByte() }
            val expected = MegaFingerprint.compute(data, 1_700_000_000L)
            for (chunkSize in intArrayOf(1_000, 4_096, 7)) {
                val builder = MegaFingerprint.Builder(size.toLong(), 1_700_000_000L)
                var pos = 0
                while (pos < size) {
                    val len = minOf(chunkSize, size - pos)
                    builder.update(data.copyOfRange(pos, pos + len))
                    pos += len
                }
                assertEquals(expected, builder.finish(), "size=$size chunkSize=$chunkSize")
            }
        }
    }
}
