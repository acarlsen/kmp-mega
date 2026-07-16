package dev.carlsen.mega.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Computes a MEGA file fingerprint — the value stored in a file node's `c`
 * attribute. MEGA's desktop sync client rejects files that lack it with
 * "File fingerprint missing", so every uploaded file must carry one.
 *
 * This mirrors MEGA's SDK (`FileFingerprint::genfingerprint` +
 * `serializefingerprint`): a 16-byte CRC (four big-endian CRC32 lanes over the
 * plaintext) followed by the modification time, URL-safe-Base64 encoded without
 * padding. Pure common Kotlin, so it runs on every KMP target.
 *
 * From tkarabela/moonwatch-android
 */
object MegaFingerprint {

    private const val CRC_LANES = 4
    private const val CRC_BYTES = 16 // CRC_LANES * 4
    private const val MAXFULL = 8192 // full-coverage threshold
    private const val SPARSE_BLOCK = 64 // 4 * CRC_BYTES
    private const val SPARSE_BLOCKS = MAXFULL / (SPARSE_BLOCK * CRC_LANES) // 32 per lane
    private const val TOTAL_BLOCKS = CRC_LANES * SPARSE_BLOCKS // 128

    /**
     * Convenience for data that is already fully in memory.
     *
     * @param data the plaintext file contents
     * @param mtimeSeconds file modification time in whole seconds since the epoch
     */
    fun compute(data: ByteArray, mtimeSeconds: Long): String =
        Builder(data.size.toLong(), mtimeSeconds).apply { update(data) }.finish()

    /**
     * Streaming fingerprint computation for data that cannot be held in memory.
     * Feed the plaintext bytes in file order via [update], then call [finish].
     *
     * Files up to 8 KiB are fully covered by the CRC and buffered whole; larger
     * files only need 128 fixed 64-byte blocks, which are collected as the
     * stream passes. Either way the builder holds at most 8 KiB of state.
     */
    class Builder(private val fileSize: Long, private val mtimeSeconds: Long) {

        private var position = 0L

        private val small: ByteArray? =
            if (fileSize <= MAXFULL) ByteArray(fileSize.toInt()) else null

        private val blocks: ByteArray? =
            if (fileSize > MAXFULL) ByteArray(TOTAL_BLOCKS * SPARSE_BLOCK) else null

        // Block idx (= lane * 32 + j) reads [offset, offset + 64); offsets are
        // ascending in idx, matching MEGA's sparse coverage.
        private val blockOffsets: LongArray? =
            if (fileSize > MAXFULL) {
                val maxOffset = fileSize - SPARSE_BLOCK
                LongArray(TOTAL_BLOCKS) { idx -> maxOffset * idx / (TOTAL_BLOCKS - 1) }
            } else null

        // First block not yet fully received; earlier blocks never need more bytes.
        private var nextBlock = 0

        fun update(data: ByteArray, offset: Int = 0, length: Int = data.size) {
            check(position + length <= fileSize) {
                "Received more than the declared $fileSize bytes"
            }
            if (small != null) {
                data.copyInto(small, position.toInt(), offset, offset + length)
            } else {
                collectSparseBlocks(data, offset, length)
            }
            position += length
        }

        private fun collectSparseBlocks(data: ByteArray, offset: Int, length: Int) {
            val begin = position
            val end = position + length
            for (b in nextBlock until TOTAL_BLOCKS) {
                val blockStart = blockOffsets!![b]
                if (blockStart >= end) break
                val blockEnd = blockStart + SPARSE_BLOCK
                // Copy the overlap of this block with the incoming data. Blocks may
                // overlap each other, so one byte can land in several of them.
                val from = maxOf(blockStart, begin)
                val to = minOf(blockEnd, end)
                if (to > from) {
                    data.copyInto(
                        destination = blocks!!,
                        destinationOffset = b * SPARSE_BLOCK + (from - blockStart).toInt(),
                        startIndex = offset + (from - begin).toInt(),
                        endIndex = offset + (to - begin).toInt(),
                    )
                }
                if (blockEnd <= end) nextBlock = b + 1
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        fun finish(): String {
            check(position == fileSize) {
                "Expected $fileSize bytes but received $position"
            }
            val crc = ByteArray(CRC_BYTES)
            when {
                small != null && fileSize <= CRC_BYTES -> {
                    // Tiny file: content copied verbatim, remainder left as zero.
                    small.copyInto(crc, 0, 0, small.size)
                }

                small != null -> {
                    // Full coverage: one CRC32 over each of four contiguous segments.
                    val size = small.size
                    for (i in 0 until CRC_LANES) {
                        val begin = (i.toLong() * size / CRC_LANES).toInt()
                        val end = ((i + 1).toLong() * size / CRC_LANES).toInt()
                        val c = Crc32()
                        c.update(small, begin, end - begin)
                        writeBigEndian(crc, i * 4, c.value)
                    }
                }

                else -> {
                    // Sparse coverage: each lane CRCs its 32 collected blocks, which
                    // sit contiguously in the block buffer.
                    for (i in 0 until CRC_LANES) {
                        val c = Crc32()
                        c.update(blocks!!, i * SPARSE_BLOCKS * SPARSE_BLOCK, SPARSE_BLOCKS * SPARSE_BLOCK)
                        writeBigEndian(crc, i * 4, c.value)
                    }
                }
            }

            val fingerprint = crc + serialize64(mtimeSeconds)
            return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(fingerprint)
        }
    }

    /** Writes the low 32 bits of [value] big-endian (MEGA stores each CRC via htonl). */
    private fun writeBigEndian(out: ByteArray, offset: Int, value: Long) {
        val v = value.toInt()
        out[offset] = (v ushr 24).toByte()
        out[offset + 1] = (v ushr 16).toByte()
        out[offset + 2] = (v ushr 8).toByte()
        out[offset + 3] = v.toByte()
    }

    /** MEGA's `Serialize64`: a count byte, then [value]'s significant bytes little-endian. */
    private fun serialize64(value: Long): ByteArray {
        var v = value
        val valueBytes = ArrayList<Byte>(8)
        while (v != 0L) {
            valueBytes.add((v and 0xFF).toByte())
            v = v ushr 8
        }
        val out = ByteArray(valueBytes.size + 1)
        out[0] = valueBytes.size.toByte()
        for (k in valueBytes.indices) out[k + 1] = valueBytes[k]
        return out
    }
}

/** Standard CRC-32 (IEEE 802.3, polynomial 0xEDB88320), same as java.util.zip.CRC32. */
internal class Crc32 {

    private var crc = -1 // 0xFFFFFFFF

    /** The current checksum as an unsigned 32-bit value. */
    val value: Long
        get() = crc.inv().toLong() and 0xFFFFFFFFL

    fun update(data: ByteArray, offset: Int, length: Int) {
        var c = crc
        for (i in offset until offset + length) {
            c = TABLE[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
        }
        crc = c
    }

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) {
                c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1
            }
            c
        }
    }
}
