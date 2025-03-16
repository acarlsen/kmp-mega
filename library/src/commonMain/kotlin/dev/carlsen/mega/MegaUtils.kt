package dev.carlsen.mega

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import dev.carlsen.mega.dto.FileAttr
import dev.carlsen.mega.extension.modPow
import dev.carlsen.mega.model.MegaException
import dev.carlsen.mega.transfer.ChunkSize
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.operations.Cipher
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.min

object MegaUtils {
    @OptIn(DelicateCryptographyApi::class)
    fun passwordKey(password: String): ByteArray {
        val a = bytesToA32(paddnull(password.encodeToByteArray(), 4))
        var pkey = a32ToBytes(intArrayOf(0x93C467E3.toInt(), 0x7DB0C7A4, 0xD1BE3F81.toInt(), 0x0152CB56))

        val n = (a.size + 3) / 4
        val ciphers = mutableListOf<Cipher>()

        for (j in a.indices step 4) {
            val key = IntArray(4)
            for (k in 0 until 4) {
                if (j + k < a.size) {
                    key[k] = a[j + k]
                }
            }

            val bkey = a32ToBytes(key)

            val provider = CryptographyProvider.Default
            val aes = provider.get(AES.ECB)
            val aesKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, bkey)
            val cipher = aesKey.cipher(padding = false)

            ciphers.add(cipher)
        }

        // Perform the encryption iterations
        for (i in 65536 downTo 1) {
            for (j in 0 until n) {
                val newKey = ciphers[j].encryptBlocking(pkey)
                pkey = newKey
            }
        }

        return pkey
    }

    /**
     * Pads byte array b such that the size of resulting byte array is a multiple of q.
     */
    fun paddnull(b: ByteArray, q: Int): ByteArray {
        val rem = b.size % q
        if (rem != 0) {
            val l = q - rem
            return b + ByteArray(l)
        }
        return b
    }

    /**
     * Converts the byte array to IntArray considering the bytes to be in big endian order.
     */
    fun bytesToA32(b: ByteArray): IntArray {
        val length = (b.size + 3) / 4
        val a = IntArray(length)

        //val buffer = ByteBuffer.wrap(b)
        //buffer.order(ByteOrder.BIG_ENDIAN)
        val buffer = Buffer().apply { write(b) }

        for (i in 0 until length) {
            a[i] = if (i * 4 + 4 <= b.size) {
                //buffer.getInt(i * 4)
                buffer.readInt()
            } else {
                // Handle incomplete final int
                var value = 0
                for (j in 0 until min(4, b.size - i * 4)) {
                    value = (value shl 8) or (b[i * 4 + j].toInt() and 0xFF)
                }
                value
            }
        }

        return a
    }

    /**
     * Converts the IntArray to byte array where each int is encoded in big endian order.
     */
    fun a32ToBytes(a: IntArray): ByteArray {
//        val buffer = ByteBuffer.allocate(a.size * 4)
//        buffer.order(ByteOrder.BIG_ENDIAN)
//        for (v in a) {
//            buffer.putInt(v)
//        }
//        return buffer.array()

        val buffer = Buffer()
        for (v in a) {
            buffer.writeInt(v)
        }
        return buffer.readByteArray(buffer.size.toInt())
    }

    /**
     * Derives encryption key from password using PBKDF2
     */
    fun deriveKey(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val derivedKeyLength = 2 * 16 // 2 * AES block size (16 bytes)

        val provider = CryptographyProvider.Default
        val pbkdf2 = provider.get(dev.whyoleg.cryptography.algorithms.PBKDF2)

        val passwordBytes = password.encodeToByteArray()
        val secretDerivation = pbkdf2.secretDerivation(
            digest = SHA512,
            iterations = 100000,
            outputSize = derivedKeyLength.bytes,
            salt = salt
        )

        val derivedKey = secretDerivation.deriveSecretToByteArrayBlocking(passwordBytes)

        // Split the derived key into passkey and authKey
        val passkey = derivedKey.sliceArray(0 until 16)
        val authKey = derivedKey.sliceArray(16 until derivedKey.size)

        return Pair(passkey, authKey)
    }

    /**
     * Computes generic string hash. Uses k as the key for AES cipher.
     */
    fun stringHash(s: String, k: ByteArray): String {
        val a = bytesToA32(paddnull(s.encodeToByteArray(), 4))
        val h = IntArray(4) { 0 }

        // XOR each value with the appropriate position in h
        for (i in a.indices) {
            h[i and 3] = h[i and 3] xor a[i]
        }

        var hb = a32ToBytes(h)

        // Create AES cipher with the provided key
        val cipher = getAesECBCipher(k)

        // Perform multiple encryption rounds
        for (i in 16384 downTo 1) {
            hb = cipher.encryptBlocking(hb)
        }

        val ha = bytesToA32(paddnull(hb, 4))

        // Return the base64 encoded result using only specific elements
        return a32ToBase64(intArrayOf(ha[0], ha[2]))
    }

    /**
     * Converts uint32 slice to base64 encoded string.
     */
    private fun a32ToBase64(a: IntArray): String {
        val bytes = a32ToBytes(a)
        return base64urlEncode(bytes)
    }

    /**
     * Encodes byte array using base64 url encoding without `=` padding.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlEncode(b: ByteArray): String {
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(b)
    }

    /**
     * Decodes the string using unpadded base64 url decoding.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlDecode(s: String): ByteArray {
        var input = s
        // Handle compatibility with standard base64
        input = input.replace('+', '-').replace('/', '_')
        //return Base64.getUrlDecoder().decode(input)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(input)
    }

    /**
     * Decrypts the session ID using the given private key
     *
     * @param privk Base64 URL encoded private key
     * @param csid Base64 URL encoded encrypted session ID
     * @param mk Master key bytes
     * @return Base64 URL encoded decrypted session ID
     */
    fun decryptSessionId(privk: String, csid: String, mk: ByteArray): String {
        // Create AES cipher with master key
        val block = try {
            getAesECBCipher(mk)
        } catch (e: Exception) {
            throw MegaException("Failed to create cipher: ${e.message}")
        }

        // Decode and decrypt private key
        val pk = try {
            base64urlDecode(privk)
        } catch (e: Exception) {
            throw MegaException("Failed to decode private key: ${e.message}")
        }

        val decryptedPk = try {
            // In Kotlin we need to create a copy since inplace encryption isn't supported
            val pkCopy = pk.copyOf()
            blockDecrypt(block, pkCopy, pk)
            pkCopy
        } catch (e: Exception) {
            throw MegaException("Failed to decrypt private key: ${e.message}")
        }

        // Decode the encrypted session ID
        val c = try {
            base64urlDecode(csid)
        } catch (e: Exception) {
            throw MegaException("Failed to decode session ID: ${e.message}")
        }

        // Get MPI (Multi-Precision Integer) from the encrypted session ID
        val (m, _) = getMPI(c)

        // Get RSA components from private key
        val (p, q, d) = getRSAKey(decryptedPk)

        // Decrypt the session ID using RSA
        val r = decryptRSA(m, p, q, d)

        // Return the first 43 bytes as base64 URL encoded string
        return base64urlEncode(r.sliceArray(0 until 43))
    }

    /**
     * Gets a Multi-Precision Integer from byte array
     */
    private fun getMPI(b: ByteArray): Pair<BigInteger, ByteArray> {
        val pLen = ((b[0].toUByte().toUInt() * 256u) + b[1].toUByte().toUInt() + 7u) shr 3
        // Extract the big integer bytes (skip first 2 bytes that contain the length)
        val p = fromSignMagnitude(Sign.POSITIVE, b.sliceArray(2 until (pLen.toInt() + 2)))
        val newB = b.sliceArray((pLen.toInt() + 2) until b.size)
        return Pair(p, newB)
    }

    /**
     * Get the RSA components from a byte array
     */
    private fun getRSAKey(b: ByteArray): Triple<BigInteger, BigInteger, BigInteger> {
        val (p, b1) = getMPI(b)
        val (q, b2) = getMPI(b1)
        val (d, _) = getMPI(b2)

        return Triple(p, q, d)
    }

    /**
     * Decrypt a message using RSA private key components
     */
    private fun decryptRSA(m: BigInteger, p: BigInteger, q: BigInteger, d: BigInteger): ByteArray {
        val n = p.multiply(q)
        val r = m.modPow(d, n)

        return r.toByteArray().let {
            // Remove leading 0 byte if present (BigInteger sign byte)
            if (it.isNotEmpty() && it[0] == 0.toByte()) it.sliceArray(1 until it.size) else it
        }
    }

    /**
     * Generate a random string of specified length
     */
    fun randString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Calculate the chunk sizes for a file of the given size
     *
     * @param size Total file size in bytes
     * @return List of chunks with their positions and sizes
     */
    fun getChunkSizes(size: Long): List<ChunkSize> {
        val chunks = mutableListOf<ChunkSize>()
        var position = 0L
        var remainingSize = size

        var i = 1
        while (remainingSize > 0) {
            val chunkSize = when {
                i <= 8 -> i * 131072
                else -> 1048576
            }

            val actualChunkSize = minOf(chunkSize.toLong(), remainingSize).toInt()
            chunks.add(ChunkSize(position = position, size = actualChunkSize))

            position += actualChunkSize
            remainingSize -= actualChunkSize
            i++
        }

        return chunks
    }

    /**
     * Encrypt a block using ECB mode
     */
    fun blockEncrypt(cipher: Cipher, dst: ByteArray, src: ByteArray): Boolean {
        val blockSize = 16 // AES block size

        if (src.size != dst.size || src.size % blockSize != 0) {
            throw MegaException("Invalid block size for encryption")
        }

        // Process block by block
        for (i in src.indices step blockSize) {
            val encrypted = cipher.encryptBlocking(src.sliceArray(i until i + blockSize))
            encrypted.copyInto(dst, destinationOffset = i, startIndex = 0, endIndex = blockSize)
        }

        return true
    }

    /**
     * Block decrypt using ECB mode
     */
    fun blockDecrypt(cipher: Cipher, dst: ByteArray, src: ByteArray): Boolean {
        val blockSize = 16 // AES block size

        if (src.size > dst.size || src.size % blockSize != 0) {
            throw MegaException("Invalid block size for decryption")
        }

        // Process block by block
        for (i in src.indices step blockSize) {
            val decrypted = cipher.decryptBlocking(src.sliceArray(i until i + blockSize))
            decrypted.copyInto(dst, destinationOffset = i, startIndex = 0, endIndex = blockSize)
        }

        return true
    }

    /**
     * Decrypts file attributes
     *
     * @param key The decryption key
     * @param data Base64 URL encoded encrypted data
     * @return Decrypted FileAttr object
     * @throws MegaException if decryption or parsing fails
     */
    @OptIn(DelicateCryptographyApi::class)
    fun decryptAttr(key: ByteArray, data: String): FileAttr {
        try {
            // Create AES cipher in CBC mode with zero IV
            val iv = a32ToBytes(intArrayOf(0, 0, 0, 0))
            val cipher = getAesCBCCipher(key)

            // Decode base64 data
            val ddata = base64urlDecode(data)

            // Decrypt the data
            val buf = cipher.decryptWithIvBlocking(iv, ddata)

            // Check for MEGA prefix and parse JSON
            if (buf.size >= 4 && buf.decodeToString(0, 4) == "MEGA") {
                // Trim null bytes from the end
                val str = buf.decodeToString(4, buf.size).trimEnd { it == '\u0000' }

                // Find valid JSON object
                val jsonString = attrMatch.find(str)?.value ?: str

                // Parse the JSON
                return try {
                    Json.decodeFromString<FileAttr>(jsonString)
                } catch (e: Exception) {
                    throw MegaException("Failed to parse attributes: ${e.message}")
                }
            } else {
                throw MegaException("Invalid attribute format: MEGA prefix not found")
            }
        } catch (e: Exception) {
            if (e is MegaException) throw e
            throw MegaException("Decryption failed: ${e.message}")
        }
    }

    /**
     * Encrypts file attributes
     *
     * @param key The encryption key
     * @param attr FileAttr object to encrypt
     * @return Base64 URL encoded encrypted data
     * @throws MegaException if encryption fails
     */
    @OptIn(DelicateCryptographyApi::class)
    fun encryptAttr(key: ByteArray, attr: FileAttr): String {
        try {
            // Create AES cipher in CBC mode with zero IV
            val iv = a32ToBytes(intArrayOf(0, 0, 0, 0))
            val cipher = getAesCBCCipher(key)

            // Serialize the attribute object to JSON
            val data = Json.encodeToString(FileAttr.serializer(), attr)

            // Prepare the data with MEGA prefix
            val attrib = "MEGA".toByteArray() + data.toByteArray()
            val paddedAttrib = paddnull(attrib, 16)

            // Encrypt the data
            val encryptedData = cipher.encryptWithIvBlocking(iv, paddedAttrib)

            // Encode to base64 URL format
            return base64urlEncode(encryptedData)
        } catch (e: Exception) {
            throw MegaException("Encryption failed: ${e.message}")
        }
    }

    /**
     * Regular expression to match JSON objects
     */
    private val attrMatch = Regex("""(\{.*\})""")

    private fun fromSignMagnitude(sign: Sign, magnitude: ByteArray): BigInteger {
        val bigInt = BigInteger.fromByteArray(magnitude, Sign.POSITIVE)
        return if (sign == Sign.NEGATIVE) {
            -bigInt
        } else {
            bigInt
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    fun getAesECBCipher(key: ByteArray, padding: Boolean = false): Cipher {
        val provider = CryptographyProvider.Default
        val aes = provider.get(AES.ECB)
        val aesKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return aesKey.cipher(padding = padding)
    }

     fun getAesCBCCipher(key: ByteArray, padding: Boolean = false): AES.IvCipher {
        val provider = CryptographyProvider.Default
        val aes = provider.get(AES.CBC)
        val aesKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return aesKey.cipher(padding = padding)
    }

    fun getAesCTRCipher(key: ByteArray): AES.IvCipher {
        val provider = CryptographyProvider.Default
        val aes = provider.get(AES.CTR)
        val aesKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return aesKey.cipher()
    }

}

