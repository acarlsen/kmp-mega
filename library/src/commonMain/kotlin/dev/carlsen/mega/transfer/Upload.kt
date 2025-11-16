package dev.carlsen.mega.transfer

import dev.carlsen.mega.Mega
import dev.carlsen.mega.MegaUtils
import dev.carlsen.mega.dto.FSNode
import dev.carlsen.mega.dto.FileAttr
import dev.carlsen.mega.dto.UploadCompleteMsg
import dev.carlsen.mega.dto.UploadCompleteResp
import dev.carlsen.mega.model.MegaException
import dev.carlsen.mega.model.NodeType
import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Upload(
    private val mega: Mega,
    val parentHash: String,
    val name: String,
    val uploadUrl: String,
    val iv: ByteArray,
    val kiv: ByteArray,
    val kbytes: ByteArray,
    val masterKey: ByteArray,
    val ukey: IntArray,
    val chunks: List<ChunkSize>,
    val chunkMacs: Array<ByteArray>,
    var completionHandle: ByteArray,
) {
    private val mutex = Mutex()
    
    // Cache cipher objects - create once, reuse for all chunks
    private val ctrCipher by lazy { MegaUtils.getAesCTRCipher(kbytes) }
    private val cbcCipher by lazy { MegaUtils.getAesCBCCipher(kbytes) }
    private val masterCipher by lazy { MegaUtils.getAesCBCCipher(masterKey) }

    /**
     * Returns the number of chunks in the upload.
     */
    fun chunks(): Int = chunks.size

    /**
     * Returns the position in the file and the size of the chunk
     */
    fun chunkLocation(id: Int): Pair<Long, Int> {
        if (id < 0 || id >= chunks.size) {
            throw IllegalArgumentException("Invalid chunk ID")
        }
        return Pair(chunks[id].position, chunks[id].size)
    }

    /**
     * Uploads the chunk with the given id
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun uploadChunk(id: Int, chunk: ByteArray) {
        val (chkStart, chkSize) = chunkLocation(id)

        if (chunk.size != chkSize) {
            throw MegaException("Upload chunk is wrong size")
        }

        // Prepare encryption parameters
        val ctrIv = MegaUtils.bytesToA32(kiv)
        ctrIv[2] = (chkStart / 0x1000000000).toInt()
        ctrIv[3] = (chkStart / 0x10).toInt()
        val bctrIv = MegaUtils.a32ToBytes(ctrIv)

        // Create MAC for this chunk using cached cipher
        // Optimized: Reuse buffers to avoid allocations in loop
        var block = iv
        val paddedChunk = MegaUtils.paddnull(chunk, 16)
        val blockBuffer = ByteArray(16)  // Reused buffer
        
        for (i in paddedChunk.indices step 16) {
            // Copy into reused buffer instead of allocating new array
            paddedChunk.copyInto(blockBuffer, 0, i, minOf(i + 16, paddedChunk.size))
            val encryptedBlock = cbcCipher.encryptWithIvBlocking(block, blockBuffer)
            block = encryptedBlock
        }

        // Encrypt the chunk with CTR mode using cached cipher
        val encryptedChunk = ctrCipher.encryptWithIvBlocking(bctrIv, chunk)

        // Prepare upload URL
        val chunkUrl = "${uploadUrl}/${chkStart}"
        var completionHandle: ByteArray? = null

        // Upload with retries
        var lastError: Exception? = null
        var sleepTime = Mega.minSleepTime

        // Retry loop
        for (retry in 0 until Mega.RETRIES + 1) {
            try {
                val response: HttpResponse = mega.httpClient.post(chunkUrl) {
                    setBody(encryptedChunk)
                }

                if (response.status.value == 200) {
                    val chunkResp = response.bodyAsBytes()
                    if (chunkResp.isNotEmpty()) {
                        completionHandle = chunkResp
                    }
                    lastError = null
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

        if (lastError != null) {
            throw lastError
        }

        // Update completion handle on success
        completionHandle?.let {
            mutex.withLock {
                this.completionHandle = it
            }
        }

        // Update chunk MACs on success
        mutex.withLock {
            if (chunkMacs.isNotEmpty()) {
                chunkMacs[id] = block.copyOf()
            }
        }
    }

    /**
     * Completes the upload and returns the created node
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun finish(): FSNode {
        // Calculate MAC for all chunks using cached cipher
        var macData = ByteArray(16)
        for (chunkMac in chunkMacs) {
            val encryptedBlock = cbcCipher.encryptWithIvBlocking(macData, chunkMac)
            macData = encryptedBlock
        }

        // Convert to uint32 array
        val t = MegaUtils.bytesToA32(macData)
        val metaMac = intArrayOf(t[0] xor t[1], t[2] xor t[3])

        // Encrypt file attributes
        val attr = FileAttr(name)
        val attrData = MegaUtils.encryptAttr(kbytes, attr)

        // Create encryption key
        val key = intArrayOf(
            ukey[0] xor ukey[4], ukey[1] xor ukey[5],
            ukey[2] xor metaMac[0], ukey[3] xor metaMac[1],
            ukey[4], ukey[5], metaMac[0], metaMac[1]
        )

        // Convert key to bytes
        val buf = MegaUtils.a32ToBytes(key)

        // Encrypt the key with master key using cached cipher
        val zeroIv = ByteArray(16)

        // Encrypt first part of the key
        val firstPart = masterCipher.encryptWithIvBlocking(zeroIv, buf.copyOfRange(0, 16))
        firstPart.copyInto(buf, 0, 0, 16)

        // Encrypt second part of the key
        val secondPart = masterCipher.encryptWithIvBlocking(zeroIv, buf.copyOfRange(16, 32))
        secondPart.copyInto(buf, 16, 0, 16)

        // Prepare completion message
        val uploadCompleteMsg = UploadCompleteMsg(
            cmd = "p",
            t = parentHash,
            n = listOf(
                UploadCompleteMsg.UploadNode(
                    h = completionHandle.decodeToString(),
                    t = NodeType.FILE,
                    a = attrData,
                    k = MegaUtils.base64urlEncode(buf)
                )
            )
        )

        // Send API request
        val request = Json.encodeToString(arrayOf(uploadCompleteMsg))
        val result = mega.apiRequest(request)

        // Parse response
        val response = Json.decodeFromString<Array<UploadCompleteResp>>(result)[0]

        // Add node to filesystem
        return response.f.firstOrNull() ?: throw MegaException("Upload failed")
    }
}