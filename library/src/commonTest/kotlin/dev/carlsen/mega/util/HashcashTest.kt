package dev.carlsen.mega.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HashcashTest {

    @Test
    fun testParseValidHeader() {
        val header = "1:192:1633897886:6470e06d773e05a8"
        val result = Hashcash.parse(header)
        assertNotNull(result)
        assertEquals(192, result.first)
        assertEquals("6470e06d773e05a8", result.second)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSolveAndVerifyChallenge() = runBlocking {
        val token = "6470e06d773e05a8"
        val easiness = 192 // High easiness for a very fast test

        val solution = Hashcash.solve(
            token = token,
            easiness = easiness,
            timeoutMillis = 30000, // runTest uses virtual time, so this is generous
        )

        assertNotNull(solution, "Solver failed to find a solution in time.")

        // Now, independently verify the solution is correct
        val prefix = solution.decodeBase64()!!.toByteArray()
        assertNotNull(prefix)
        assertEquals(4, prefix.size)

        val threshold = ((((easiness and 63) shl 1) + 1).toUInt() shl ((easiness shr 6) * 7 + 3))

        val tokenBytes = token.decodeBase64()!!.toByteArray()
        val paddedToken = if (tokenBytes.size % 16 != 0) {
            tokenBytes + ByteArray(16 - tokenBytes.size % 16)
        } else {
            tokenBytes
        }

        val buffer = ByteArray(4 + 262144 * 48)
        prefix.copyInto(buffer, 0, 0, 4)
        for (i in 0 until 262144) {
            paddedToken.copyInto(buffer, 4 + i * 48)
        }

        val hash = buffer.toByteString().sha256().toByteArray()
        val hashValue = hash.toBigEndianUInt()

        assertTrue(hashValue <= threshold, "Hash value $hashValue should be <= threshold $threshold")
    }

    private fun ByteArray.toBigEndianUInt(): UInt {
        require(size >= 4) { "Byte array must have at least 4 bytes" }
        return ((this[0].toUInt() and 0xFFu) shl 24) or
                ((this[1].toUInt() and 0xFFu) shl 16) or
                ((this[2].toUInt() and 0xFFu) shl 8) or
                (this[3].toUInt() and 0xFFu)
    }
}
