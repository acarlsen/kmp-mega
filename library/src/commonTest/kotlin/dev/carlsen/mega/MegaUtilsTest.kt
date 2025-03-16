@file:OptIn(DelicateCryptographyApi::class)

package dev.carlsen.mega

import dev.carlsen.mega.dto.FileAttr
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MegaUtilsTest {

    @Test
    fun testRandString() {
        val length = 10
        val result = MegaUtils.randString(length)

        assertEquals(length, result.length)
        assertTrue(result.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun testGetChunkSizes() {
        // Test with a small file (less than 8 chunks)
        val smallFileSize = 500_000L
        val smallChunks = MegaUtils.getChunkSizes(smallFileSize)

        assertEquals(3, smallChunks.size)
        assertEquals(0L, smallChunks[0].position)
        assertEquals(131072, smallChunks[0].size)
        assertEquals(smallFileSize.toInt(), smallChunks.sumOf { it.size })

        // Test with a large file (more than 8 chunks)
        val largeFileSize = 10_000_000L
        val largeChunks = MegaUtils.getChunkSizes(largeFileSize)

        assertTrue(largeChunks.size > 8)
        assertEquals(0L, largeChunks[0].position)
        assertEquals(131072, largeChunks[0].size)
        assertEquals(36 * 131072, largeChunks[0].size + largeChunks[1].size + largeChunks[2].size +
                largeChunks[3].size + largeChunks[4].size + largeChunks[5].size +
                largeChunks[6].size + largeChunks[7].size)
        assertEquals(1048576, largeChunks[8].size)
        assertEquals(largeFileSize.toInt(), largeChunks.sumOf { it.size })
    }

    @Test
    fun testBlockEncryptAndDecrypt() {
        val key = CryptographyRandom.nextBytes(16)
        val cipher = MegaUtils.getAesECBCipher(key)

        val src = ByteArray(32) { (it * 3).toByte() } // 2 blocks of 16 bytes
        val encrypted = ByteArray(32)
        val decrypted = ByteArray(32)

        assertTrue(MegaUtils.blockEncrypt(cipher, encrypted, src))
        assertTrue(MegaUtils.blockDecrypt(cipher, decrypted, encrypted))

        assertContentEquals(src, decrypted)
    }

    @Test
    fun testEncryptAndDecryptAttr() {
        val key =  CryptographyRandom.nextBytes(16)
        val attr = FileAttr(
            name = "test.txt",
        )

        val encrypted = MegaUtils.encryptAttr(key, attr)
        val decrypted = MegaUtils.decryptAttr(key, encrypted)

        assertEquals(attr.name, decrypted.name)
    }

    @Test
    fun testGetAesECBCipher() {
        val key = CryptographyRandom.nextBytes(16)
        val cipher = MegaUtils.getAesECBCipher(key)

        assertNotNull(cipher)

        // Test encryption/decryption
        val data = ByteArray(16) { it.toByte() }
        val encrypted = cipher.encryptBlocking(data)
        val decrypted = cipher.decryptBlocking(encrypted)

        assertContentEquals(data, decrypted)
    }

    @Test
    fun testGetAesCBCCipher() {
        val key = CryptographyRandom.nextBytes(16)
        val cipher = MegaUtils.getAesCBCCipher(key)

        assertNotNull(cipher)

        // Test encryption/decryption
        val data = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16)
        val encrypted = cipher.encryptWithIvBlocking(iv, data)
        val decrypted = cipher.decryptWithIvBlocking(iv, encrypted)

        assertContentEquals(data, decrypted)
    }

    @Test
    fun testGetAesCTRCipher() {
        val key = CryptographyRandom.nextBytes(16)
        val cipher = MegaUtils.getAesCTRCipher(key)

        assertNotNull(cipher)

        // Test encryption/decryption
        val data = ByteArray(32) { it.toByte() }
        val iv = ByteArray(16)
        val encrypted = cipher.encryptWithIvBlocking(iv, data)

        // CTR mode is symmetric - encrypt is same as decrypt
        val decrypted = cipher.decryptWithIvBlocking(iv, encrypted)

        assertContentEquals(data, decrypted)
    }
}