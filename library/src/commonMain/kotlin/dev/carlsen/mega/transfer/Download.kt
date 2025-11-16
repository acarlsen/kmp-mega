package dev.carlsen.mega.transfer

import dev.carlsen.mega.Mega
import dev.carlsen.mega.MegaUtils
import dev.carlsen.mega.model.MegaException
import dev.carlsen.mega.model.Node
import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Download class containing the internal state of a download
 */
class Download(
    private val mega: Mega,
    private val src: Node,
    private val resourceUrl: String,
    private val chunks: List<ChunkSize>,
    private val chunkMacs: MutableList<ByteArray?> = MutableList(chunks.size) { null },
) {
    private val mutex = Mutex()
    
    // Cache cipher objects - create once, reuse for all chunks
    private val ctrCipher by lazy { MegaUtils.getAesCTRCipher(src.meta.key) }
    private val cbcCipher by lazy { MegaUtils.getAesCBCCipher(src.meta.key) }


    /**
     * Get number of chunks in the download
     */
    fun chunks(): Int = chunks.size

    /**
     * Get the position and size of a chunk
     */
    private fun chunkLocation(id: Int): Pair<Long, Int> {
        if (id < 0 || id >= chunks.size) {
            throw MegaException("Invalid chunk ID")
        }

        return Pair(chunks[id].position, chunks[id].size)
    }

    /**
     * Download a specific chunk
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun downloadChunk(id: Int): ByteArray {
        if (id < 0 || id >= chunks.size) {
            throw MegaException("Invalid chunk ID")
        }

        // Get chunk location
        val (chkStart, chkSize) = chunkLocation(id)

        // Build download URL for this chunk
        val chunkUrl = "${resourceUrl}/${chkStart}-${chkStart + chkSize - 1}"

        var chunk: ByteArray? = null
        var sleepTime = Mega.minSleepTime
        var lastError: Exception? = null

        // Retry loop
        for (retry in 0 until Mega.RETRIES + 1) {
            try {
                val response: HttpResponse = mega.httpClient.get(chunkUrl)

                if (response.status.value == 200) {
                    chunk = response.bodyAsBytes()
                    break
                } else {
                    lastError = MegaException("HTTP status: ${response.status.value}")
                }
            } catch (e: Exception) {
                lastError = e
            }

            delay(sleepTime.inWholeMilliseconds)
            sleepTime = (sleepTime.inWholeMilliseconds * 2)
                .coerceAtMost(Mega.maxSleepTime.inWholeMilliseconds).toDuration(DurationUnit.MILLISECONDS)
        }

        if (chunk == null) {
            throw lastError ?: MegaException("Failed to download chunk after retries")
        }

        if (chunk.size != chkSize) {
            throw MegaException("Wrong size for downloaded chunk")
        }

        // Decrypt the block using cached cipher
        val ctrIv = MegaUtils.bytesToA32(src.meta.iv)
        ctrIv[2] = (chkStart / 0x1000000000).toInt()
        ctrIv[3] = (chkStart / 0x10).toInt()
        val bctrIv = MegaUtils.a32ToBytes(ctrIv)
        val decryptedChunk = ctrCipher.decryptWithIvBlocking(bctrIv, chunk)

        // Update chunk macs using cached cipher
        val t = MegaUtils.bytesToA32(src.meta.iv)
        val iv = MegaUtils.a32ToBytes(intArrayOf(t[0], t[1], t[0], t[1]))

        val paddedChunk = MegaUtils.paddnull(decryptedChunk, 16)
        var block = iv
        val blockBuffer = ByteArray(16)  // Reused buffer

        for (i in paddedChunk.indices step 16) {
            // Copy into reused buffer instead of allocating new array
            paddedChunk.copyInto(blockBuffer, 0, i, minOf(i + 16, paddedChunk.size))
            val encryptedBlock = cbcCipher.encryptWithIvBlocking(block, blockBuffer)
            block = encryptedBlock
        }

        // Store the MAC for this chunk
        mutex.withLock {
            if (chunkMacs.size > 0) {
                chunkMacs[id] = block.copyOf()
            }
        }

        return decryptedChunk
    }

    /**
     * Check integrity of the download
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun finish(): Boolean = mutex.withLock {
        // Can't check a 0 sized file
        if (chunkMacs.isEmpty()) {
            return@withLock true
        }

        // Use cached cipher for final MAC verification
        // An all-zero IV for MAC calculations
        var block = ByteArray(16)
        for (chunkMac in chunkMacs.filterNotNull()) {
            val encryptedBlock = cbcCipher.encryptWithIvBlocking(block, chunkMac)
            block = encryptedBlock
        }

        val tMac = MegaUtils.bytesToA32(block)
        val btMac = MegaUtils.a32ToBytes(
            intArrayOf(
                (tMac[0] xor tMac[1]),
                (tMac[2] xor tMac[3])
            )
        )

        if (!btMac.contentEquals(src.meta.mac)) {
            throw MegaException("MAC verification failed")
        }

        return@withLock true
    }
}