package dev.carlsen.mega.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

object Hashcash {

    private const val NUM_REPLICATIONS = 262144
    private const val TOKEN_SLOT_SIZE = 48
    private const val DONE_CTX_CHECK_WHEN_NTH_ITERATION = 1000

    fun parse(header: String): Pair<Int, String>? {
        val parts = header.split(":")
        if (parts.size != 4) return null

        val version = parts[0].toIntOrNull()
        if (version != 1) return null

        val easiness = parts[1].toIntOrNull()
        if (easiness == null || easiness !in 0..255) return null

        return easiness to parts[3]
    }

    suspend fun solve(
        token: String,
        easiness: Int,
        timeoutMillis: Long,
        workers: Int = 4,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = withTimeoutOrNull(timeoutMillis) {
        val resultChannel = Channel<String>(Channel.CONFLATED)

        // Launch workers in the scope of withTimeoutOrNull for structured concurrency.
        // This ensures all workers are cancelled when one finds a solution or on timeout.
        repeat(workers) {
            launch(dispatcher) { // Use the injected dispatcher
                // The generateCash function will suspend and check for cancellation.
                val result = generateCash(token, easiness)
                if (result != null) {
                    resultChannel.trySend(result)
                }
            }
        }

        // Wait for the first result from any worker.
        resultChannel.receive()
    }

    private suspend fun CoroutineScope.generateCash(
        token: String,
        easiness: Int,
    ): String? {
        val threshold = ((((easiness and 63) shl 1) + 1).toUInt() shl ((easiness shr 6) * 7 + 3))
        val tokenBytes = token.decodeBase64()?.toByteArray() ?: return null

        val paddedToken = if (tokenBytes.size % 16 != 0) {
            tokenBytes + ByteArray(16 - tokenBytes.size % 16)
        } else {
            tokenBytes
        }

        val buffer = ByteArray(4 + NUM_REPLICATIONS * TOKEN_SLOT_SIZE)
        for (i in 0 until NUM_REPLICATIONS) {
            paddedToken.copyInto(buffer, 4 + i * TOKEN_SLOT_SIZE)
        }

        val prefix = ByteArray(4)
        var iterations = 0

        while (isActive) {
            if (++iterations % DONE_CTX_CHECK_WHEN_NTH_ITERATION == 0) {
                yield() // Allow other workers to run
            }

            // Increment prefix (little-endian)
            for (j in prefix.indices) {
                prefix[j]++
                if (prefix[j] != 0.toByte()) break
                if (j == prefix.size - 1) return null // Overflow
            }

            prefix.copyInto(buffer, 0, 0, 4)

            val hash = buffer.toByteString().sha256()
            val hashValue = Buffer().write(hash.substring(0, 4)).readInt().toUInt()

            if (hashValue <= threshold) {
                return prefix.toByteString().base64Url()
            }
        }
        return null
    }
}
