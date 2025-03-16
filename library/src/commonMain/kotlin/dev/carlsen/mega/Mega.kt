@file:Suppress("MemberVisibilityCanBePrivate")

package dev.carlsen.mega

import dev.carlsen.mega.dto.DownloadMsg
import dev.carlsen.mega.dto.DownloadResp
import dev.carlsen.mega.dto.ErrorMsg
import dev.carlsen.mega.dto.Events
import dev.carlsen.mega.dto.FSEvent
import dev.carlsen.mega.dto.FSNode
import dev.carlsen.mega.dto.FileAttr
import dev.carlsen.mega.dto.FileAttrMsg
import dev.carlsen.mega.dto.FileDeleteMsg
import dev.carlsen.mega.dto.FilesMsg
import dev.carlsen.mega.dto.FilesResp
import dev.carlsen.mega.dto.GenericEvent
import dev.carlsen.mega.dto.LoginMsg
import dev.carlsen.mega.dto.LoginResp
import dev.carlsen.mega.dto.MoveFileMsg
import dev.carlsen.mega.dto.PreloginMsg
import dev.carlsen.mega.dto.PreloginResp
import dev.carlsen.mega.dto.QuotaMsg
import dev.carlsen.mega.dto.QuotaResp
import dev.carlsen.mega.dto.UploadCompleteMsg
import dev.carlsen.mega.dto.UploadCompleteMsg.UploadNode
import dev.carlsen.mega.dto.UploadCompleteResp
import dev.carlsen.mega.dto.UploadMsg
import dev.carlsen.mega.dto.UploadResp
import dev.carlsen.mega.model.MegaException
import dev.carlsen.mega.model.MegaFS
import dev.carlsen.mega.model.Node
import dev.carlsen.mega.model.NodeMeta
import dev.carlsen.mega.model.NodeType
import dev.carlsen.mega.transfer.ChunkSize
import dev.carlsen.mega.transfer.Download
import dev.carlsen.mega.transfer.Upload
import dev.carlsen.mega.util.CancellationToken
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Mega(
    val baseUrl: String = API_URL,
    val retries: Int = RETRIES,
    val requestTimeout: Duration = TIMEOUT,
    val connectionTimeout: Duration = TIMEOUT,
    val megaLogger: MegaLogger = MegaLogger(),
) {
    // Sequence number
    private var sn: Long = 0

    // Server state sn
    private var ssn: String = ""

    // Session ID
    private var sessionId: String = ""

    // Master key
    private var masterKey: ByteArray = ByteArray(0)

    // Filesystem object
    private val fileSystem: MegaFS = MegaFS()

    // HTTP Client
    internal var httpClient: HttpClient = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
            connectTimeoutMillis = connectionTimeout.inWholeMilliseconds
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    megaLogger.d("HTTP Client: $message")
                    println("HTTP Client: $message")
                }
            }
            level = LogLevel.BODY
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // Serialize the API requests
    private val apiMu = Mutex()

    // Polling
    private val coroutineContext = Dispatchers.IO + SupervisorJob()
    private val scope = CoroutineScope(coroutineContext)
    private var pollJob: Job? = null

    companion object {
        const val API_URL = "https://g.api.mega.co.nz"
        const val RETRIES = 10
        val TIMEOUT = 10.toDuration(DurationUnit.SECONDS)
        val minSleepTime = 10.toDuration(DurationUnit.MILLISECONDS) // for retries
        val maxSleepTime = 5.toDuration(DurationUnit.SECONDS) // for retries
    }

    /**
     * Logs in a user with the provided email, password, and optional MFA code.
     *
     * @param email The email address of the user.
     * @param password The password of the user.
     * @param mfa The multi-factor authentication code, if applicable.
     * @throws MegaException If the login process fails.
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun login(email: String, password: String, mfa: String? = null) {

        // preLogin
        val loginEmail = email.lowercase()

        val preLoginMsg = PreloginMsg(
            cmd = "us0",
            user = loginEmail,
        )

        val res = apiRequest(Json.encodeToString(listOf(preLoginMsg)))
        val preLoginResponse = Json.decodeFromString<List<PreloginResp>>(res)

        val accountSalt = if (preLoginResponse[0].version == 0) {
            throw MegaException("prelogin: no version returned")
        } else if (preLoginResponse[0].version > 2) {
            throw MegaException("prelogin: version ${preLoginResponse[0].version} account not supported")
        } else if (preLoginResponse[0].version == 2) {
            if (preLoginResponse[0].salt.isEmpty()) {
                throw MegaException("prelogin: no salt returned")
            }
            MegaUtils.base64urlDecode(preLoginResponse[0].salt)
        } else ByteArray(8)

        val accountVersion = preLoginResponse[0].version

        // login
        var passKey = MegaUtils.passwordKey(password)
        val userHandle = MegaUtils.stringHash(loginEmail, passKey)

        val loginMsg = if (accountVersion == 1) LoginMsg(
            cmd = "us",
            user = loginEmail,
            mfa = mfa,
            handle = userHandle,
            sessionKey = null,
        ) else {
            val derivedKey = MegaUtils.deriveKey(password, accountSalt)
            val sk = CryptographyRandom.nextBytes(16)
            passKey = derivedKey.first

            LoginMsg(
                cmd = "us",
                user = loginEmail,
                mfa = mfa,
                handle = MegaUtils.base64urlEncode(derivedKey.second),
                sessionKey = MegaUtils.base64urlEncode(sk),
            )
        }

        val result = apiRequest(Json.encodeToString(listOf(loginMsg)))
        val loginResponse = Json.decodeFromString<List<LoginResp>>(result)
        val encryptedMasterKey = MegaUtils.base64urlDecode(loginResponse[0].key)

        val provider = CryptographyProvider.Default
        val aes = provider.get(AES.ECB)
        val aesKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, passKey)
        val cipher = aesKey.cipher(padding = false)

        masterKey = cipher.decrypt(encryptedMasterKey)
        sessionId = MegaUtils.decryptSessionId(loginResponse[0].privk, loginResponse[0].csid, masterKey)

        loadFileSystem()

        pollJob = scope.launch {
            pollEvents()
        }
    }

    /**
     * Logs in a user using a session key.
     *
     * @param sessionKey The base64 encoded session key.
     * @throws MegaException If the login process fails.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fastLogin(sessionKey: String) {
        val session = Base64.UrlSafe.decode(sessionKey)
        masterKey = session.copyOf(16)
        sessionId = session.copyOfRange(16, session.size).decodeToString()

        loadFileSystem()

        pollJob = scope.launch {
            pollEvents()
        }
    }

    /**
     * Dumps the current session as a base64 encoded string.
     *
     * @return The base64 encoded session string, or null if the master key is empty.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun dumpSession(): String? {
        if (masterKey.isEmpty()) {
            return null
        }

        val sidBytes = sessionId.encodeToByteArray()
        val session = ByteArray(masterKey.size + sidBytes.size)
        masterKey.copyInto(session)
        sidBytes.copyInto(session, masterKey.size)
        return Base64.UrlSafe.encode(session)
    }

    /**
     * Logs out the user by resetting the session and stopping the polling job.
     *
     * This function performs the following actions:
     * 1. Resets the sequence number (`sn`) to 0.
     * 2. Clears the server state sequence number (`ssn`).
     * 3. Clears the session ID (`sid`).
     * 4. Cancels and waits for the polling job to complete.
     *
     * @throws Exception if an error occurs while cancelling the polling job.
     */
    suspend fun logout() {
        //accountSalt = ByteArray(0)
        sn = 0
        ssn = ""
        sessionId = ""
        //k = ByteArray(0)
        //uh = ByteArray(0)
        pollJob?.cancel()
        pollJob?.join()
    }

    /**
     * Get quota information from Mega
     *
     * @return QuotaResp containing storage and transfer quota information
     * @throws MegaException if request fails
     */
    suspend fun getQuota(): QuotaResp {
        // Create the quota request message
        val msg = QuotaMsg(
            cmd = "uq",
            xfer = 1,
            strg = 1
        )

        try {
            // Convert message to JSON and send the request
            val request = Json.encodeToString(arrayOf(msg))
            val result = apiRequest(request)

            // Parse the response
            return Json.decodeFromString<List<QuotaResp>>(result)[0]
        } catch (e: Exception) {
            when (e) {
                is MegaException -> throw e
                else -> throw MegaException("Failed to get quota information: ${e.message}")
            }
        }
    }

    /**
     * Retrieves the filesystem object.
     *
     * @return The `MegaFS` object representing the filesystem.
     */
    fun getFileSystem(): MegaFS {
        return fileSystem
    }

    /**
     * Retrieves a node from the filesystem by its hash.
     *
     * @param hash The hash of the node to retrieve.
     * @return The node corresponding to the given hash, or null if no such node exists.
     */
    fun getNodeByHash(hash: String): Node? {
        return fileSystem.lookup[hash]
    }

    /**
     * Retrieves the children of the specified node.
     *
     * @param node The node whose children are to be retrieved.
     * @return A list of child nodes of the specified node.
     */
    fun getChildren(node: Node): List<Node> {
        return node.getChildren()
    }

    /**
     * Create a directory in the filesystem
     *
     * @param name Name of the directory to create
     * @param parent Parent node where directory will be created
     * @return The created Node
     */
    suspend fun createDir(name: String, parent: Node?): Node {
        parent ?: throw MegaException("Parent node cannot be null")

        // Generate random compression key
        val compkey = IntArray(6) { Random.nextInt() }

        // Create master AES cipher
        val masterAes = MegaUtils.getAesECBCipher(key = masterKey)

        // Create file attributes
        val attr = FileAttr(name = name)
        val ukey = MegaUtils.a32ToBytes(compkey.sliceArray(0 until 4))

        // Encrypt attributes with key
        val attrData = MegaUtils.encryptAttr(ukey, attr)

        // Encrypt the key with master key
        val encryptedKey = ByteArray(ukey.size)
        MegaUtils.blockEncrypt(masterAes, encryptedKey, ukey)

        // Set the encrypted attributes and key in the message
        val msg = UploadCompleteMsg(
            cmd = "p",
            t = parent.hash,
            i = MegaUtils.randString(10),
            n = listOf(
                UploadNode(
                    h = "xxxxxxxx",
                    t = NodeType.FOLDER,
                    a = attrData,
                    k = MegaUtils.base64urlEncode(encryptedKey)
                )
            )
        )

        // Serialize and send request
        val request = Json.encodeToString(arrayOf(msg))
        val result = apiRequest(request)

        // Parse response
        val response = Json.decodeFromString<List<UploadCompleteResp>>(result)

        // Add the created node to our filesystem
        return addFSNode(response[0].f[0]) ?: throw MegaException("Failed to create directory")

    }

    /**
     * Rename a file or folder
     *
     * @param src The node to rename
     * @param name New name for the node
     * @throws MegaException if something goes wrong during the operation
     */
    suspend fun rename(src: Node?, name: String) {
        // Acquire lock to ensure thread safety during operation
        fileSystem.mutex.withLock {
            // Check if source node is valid
            src ?: throw MegaException("Source node cannot be null")

            // Create AES cipher with master key
            val masterAes = MegaUtils.getAesECBCipher(masterKey)

            // Create file attribute with new name
            val attr = FileAttr(name = name)

            // Encrypt the attributes with node's key
            val attrData = try {
                MegaUtils.encryptAttr(src.meta.key, attr)
            } catch (e: Exception) {
                throw MegaException("Failed to encrypt attributes: ${e.message}")
            }

            // Encrypt the node's key with master key
            val key = ByteArray(src.meta.compkey.size)
            try {
                MegaUtils.blockEncrypt(masterAes, key, src.meta.compkey)
            } catch (e: Exception) {
                throw MegaException("Failed to encrypt key: ${e.message}")
            }

            // Create the message to send to API
            val msg = FileAttrMsg(
                cmd = "a",
                attr = attrData,
                key = MegaUtils.base64urlEncode(key),
                n = src.hash,
                i = MegaUtils.randString(10)
            )

            // Serialize the message and send request
            val req = Json.encodeToString(listOf(msg))


            // Send API request
            apiRequest(req)

            // Update node's name on success
            src.name = name
        }
    }

    /**
     * Move a file from one location to another
     *
     * @param src The node to move
     * @param parent The destination parent node
     */
    suspend fun move(src: Node?, parent: Node?) {
        // Acquire lock to ensure thread safety during operation
        fileSystem.mutex.withLock {
            // Check arguments
            if (src == null || parent == null) {
                throw MegaException("Source or parent node cannot be null")
            }

            // Create the move file message
            val msg = MoveFileMsg(
                cmd = "m",
                n = src.hash,
                t = parent.hash,
                i = MegaUtils.randString(10)
            )

            try {
                // Serialize message to JSON and send API request
                val request = Json.encodeToString(arrayOf(msg))
                apiRequest(request)

                // Update local filesystem structure
                if (src.parent != null) {
                    src.parent?.removeChild(src)
                }

                parent.addChild(src)
                src.parent = parent

            } catch (e: Exception) {
                throw when (e) {
                    is MegaException -> e
                    else -> MegaException("Move operation failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete a file or directory from filesystem
     *
     * @param node The node to delete
     * @param destroy If true, permanently delete; if false, move to trash
     * @return null on success, exception on error
     */
    suspend fun delete(node: Node?, destroy: Boolean = false) {
        // Check arguments
        node ?: throw MegaException("Node cannot be null")

        // If not destroying, move to trash
        if (!destroy) {
            return move(node, fileSystem.trash)
        }

        // Get lock to ensure thread safety
        fileSystem.mutex.withLock {
            try {
                // Create delete message
                val msg = FileDeleteMsg(
                    cmd = "d",
                    n = node.hash,
                    i = MegaUtils.randString(10)
                )

                // Serialize and send request
                val request = Json.encodeToString(arrayOf(msg))
                apiRequest(request)

                // Remove from local filesystem
                val parent = fileSystem.lookup[node.hash]
                parent?.removeChild(node)
                fileSystem.lookup.remove(node.hash)
            } catch (e: Exception) {
                throw when (e) {
                    is MegaException -> e
                    else -> MegaException("Delete operation failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Download file and output bytes to Sink
     *
     * @param src Source node to download
     * @param fileOutputSink Sink to write file to
     * @param cancellationToken Cancellation token to cancel the download
     * @throws MegaException If the download process fails.
     */
    suspend fun downloadFile(src: Node, fileOutputSink: Sink, cancellationToken: CancellationToken) {
        try {
            val download = newDownload(src)
            for (id in 0 until download.chunks()) {
                // Check cancellation before each chunk
                cancellationToken.throwIfCancellationRequested()

                val chunk = download.downloadChunk(id)

                // Check cancellation before writing each chunk
                cancellationToken.throwIfCancellationRequested()

                fileOutputSink.write(chunk)

            }
            download.finish()
        } catch (e: Exception) {
            throw when (e) {
                is CancellationException -> e
                is MegaException -> e
                else -> MegaException("Download failed: ${e.message}", e)
            }
        } finally {
            // Close the file
            runCatching { fileOutputSink.close() }
        }
    }

    /**
     * Uploads a file to the specified destination node.
     *
     * @param destNode The destination node where the file will be uploaded.
     * @param name The name of the file to be uploaded.
     * @param fileSize The size of the file to be uploaded.
     * @param fileInputSource The source from which the file data will be read.
     * @param cancellationToken The cancellation token to cancel the upload.
     * @return The uploaded Node.
     * @throws MegaException If the upload process fails.
     */
    suspend fun uploadFile(
        destNode: Node,
        name: String,
        fileSize: Long,
        fileInputSource: Source,
        cancellationToken: CancellationToken,
    ): Node {
        if (name.isEmpty()) throw MegaException("File name cannot be empty")
        if (fileSize < 0) throw MegaException("File size must be greater than or equal to 0")
        if (!destNode.isFolder && destNode.nodeType != NodeType.ROOT) throw MegaException("Destination node must be a folder")

        try {
            val upload = newUpload(destNode, name, fileSize)
            for (id in 0 until upload.chunks()) {
                // Check cancellation before each chunk
                cancellationToken.throwIfCancellationRequested()

                val (_, chunkSize) = upload.chunkLocation(id)

                // Read chunk from file
                val chunk = fileInputSource.readByteArray(chunkSize)

                // Check cancellation before uploading chunk
                cancellationToken.throwIfCancellationRequested()

                // Upload chunk
                upload.uploadChunk(id, chunk)
            }
            val fsNode = upload.finish()
            val node = addFSNode(fsNode)
            return node ?: throw MegaException("Failed to add node to filesystem")
        } catch (e: Exception) {
            throw when (e) {
                is CancellationException -> e
                is MegaException -> e
                else -> MegaException("Upload failed: ${e.message}", e)
            }
        } finally {
            // Close the file
            runCatching { fileInputSource.close() }
        }
    }

    internal suspend fun apiRequest(json: String): String {
        // Serialize the API requests
        return apiMu.withLock {
            try {
                val url = buildString {
                    append("${baseUrl}/cs?id=${sn}")
                    if (sessionId.isNotEmpty()) {
                        append("&sid=$sessionId")
                    }
                }

                var sleepTime = minSleepTime // Initial backoff time
                var result: String? = null
                var lastError: Exception? = null

                // Retry loop
                for (i in 0..retries) {
                    try {
                        if (i > 0) {
                            megaLogger.d("Retry API request $i/${retries}: ${lastError?.message}")
                            delay(sleepTime)
                            sleepTime = (sleepTime.inWholeMilliseconds * 2).coerceAtMost(maxSleepTime.inWholeMilliseconds).toDuration(DurationUnit.MILLISECONDS)
                        }

                        val response: HttpResponse = httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(json)
                        }

                        // Check status code
                        if (response.status.value != 200) {
                            throw MegaException("Http Status: ${response.status.value}")
                        }

                        val responseBody = response.bodyAsText()

                        // Check for error messages in short responses
                        if (responseBody == "-3" || responseBody == "-4") {
                            continue
                        }
                        if (responseBody.length < 6) {
                            try {
                                val errorMsg = Json.decodeFromString<List<ErrorMsg>>(responseBody)
                                errorMsg.firstOrNull()?.let { err ->
                                    MegaError.parseError(err)?.let { throw it }
                                }
                            } catch (e: SerializationException) {
                                try {
                                    val errorMsg = Json.decodeFromString<ErrorMsg>(responseBody)
                                    MegaError.parseError(errorMsg)?.let { throw it }
                                } catch (e2: SerializationException) {
                                    // do nothing
                                }
                            }
                        }

                        // Success
                        result = responseBody
                        break
                    } catch (e: Exception) {
                        lastError = e
                        if (e is MegaException && e.code == MegaError.EAGAIN.code) {
                            // Only retry on certain errors
                            continue
                        }
                    }
                }

                // Increment sequence number
                sn++
                result ?: throw lastError ?: MegaException("API request failed after retries")
            } catch (e: Exception) {
                throw when (e) {
                    is MegaException -> e
                    else -> MegaException("API request failed: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun loadFileSystem() {
        val msg = FilesMsg(
            cmd = "f",
            c = 1,
        )

        val result = apiRequest(Json.encodeToString(listOf(msg)))
        val filesResponse = Json.decodeFromString<List<FilesResp>>(result)

        for (sk in filesResponse[0].ok) {
            fileSystem.skmap[sk.handle] = sk.key
        }

        for (itm in filesResponse[0].f) {
            try {
                addFSNode(itm)
            } catch (e: Exception) {
                megaLogger.d("Couldn't decode FSNode: $itm")
                continue
            }
        }

        ssn = filesResponse[0].sn
    }

    private suspend fun addFSNode(itm: FSNode): Node? {
        var compKey: IntArray? = null
        var key: IntArray? = null
        var attr = FileAttr(name = "")
        var node: Node?
        var parent: Node?

        val masterAes = MegaUtils.getAesECBCipher(masterKey)

        when {
            itm.type == NodeType.FOLDER || itm.type == NodeType.FILE -> {
                val args = itm.key?.split(":")
                if (args == null || args.size < 2) {
                    throw MegaException("not enough args in item.Key: ${itm.key}")
                }
                val itemUser = args[0]
                var itemKey = args[1]
                val itemKeyParts = itemKey.split("/")
                if (itemKeyParts.size >= 2) {
                    itemKey = itemKeyParts[0]
                    // the other part is maybe a share key handle?
                }


                when {
                    // File or folder owned by current user
                    itemUser == itm.user -> {
                        val buf = try {
                            MegaUtils.base64urlDecode(itemKey)
                        } catch (e: Exception) {
                            throw MegaException("Failed to decode key")
                        }

                        try {
                            MegaUtils.blockDecrypt(masterAes, buf, buf)
                            compKey = MegaUtils.bytesToA32(buf)
                        } catch (e: Exception) {
                            throw MegaException("Failed to decrypt key: ${e.message}")
                        }
                    }

                    // Shared folder
                    !itm.sharingUser.isNullOrEmpty() -> {
                        // https://github.com/meganz/sdk/blob/master/src/megaclient.cpp#L10089
                        val buf = try {
                            MegaUtils.base64urlDecode(itemKey)
                        } catch (e: Exception) {
                            throw MegaException("Failed to decode shared key")
                        }

                        try {
                            fileSystem.skmap[itm.handle] = itemKey
                            MegaUtils.blockDecrypt(masterAes, buf, buf)
                            compKey = MegaUtils.bytesToA32(buf)
                        } catch (e: Exception) {
                            throw MegaException("Failed to process shared folder: ${e.message}")
                        }
                    }

                    // Shared file
                    else -> {
                        val k = fileSystem.skmap[itemUser] ?: throw MegaException("couldn't find decryption key for shared file")

                        try {
                            val b = MegaUtils.base64urlDecode(k)
                            MegaUtils.blockDecrypt(masterAes, b, b)
                            val block = MegaUtils.getAesECBCipher(b)

                            val buf = MegaUtils.base64urlDecode(itemKey)
                            MegaUtils.blockDecrypt(block, buf, buf)
                            compKey = MegaUtils.bytesToA32(buf)
                        } catch (e: Exception) {
                            throw MegaException("Failed to process shared file: ${e.message}")
                        }
                    }
                }

                key = when {
                    itm.type == NodeType.FILE -> {
                        if (compKey.size < 8) {
                            megaLogger.d("Ignoring item: compkey too short (${compKey.size}): $itm")
                            return null
                        }
                        intArrayOf(
                            compKey[0] xor compKey[4],
                            compKey[1] xor compKey[5],
                            compKey[2] xor compKey[6],
                            compKey[3] xor compKey[7]
                        )
                    }

                    else -> compKey
                }

                try {
                    val bkey = MegaUtils.a32ToBytes(key)
                    attr = MegaUtils.decryptAttr(bkey, itm.attr)
                } catch (e: Exception) {
                    megaLogger.d("Failed to decrypt attribute: ${e.message}")
                    attr = FileAttr(name = "BAD ATTRIBUTE")
                }
            }
        }

        // Using mutex for filesystem operations
        fileSystem.mutex.withLock {
            // Look up existing node or create new one
            node = fileSystem.lookup[itm.handle]
            if (node == null) {
                node = Node(
                    nodeType = itm.type,
                    size = itm.fileSize ?: 0,
                    timestamp = Instant.fromEpochSeconds(itm.ts)
                )
                fileSystem.lookup[itm.handle] = node!!
            }

            // Handle parent node
            parent = fileSystem.lookup[itm.parent]
            if (parent != null) {
                parent!!.removeChild(node!!)
                parent!!.addChild(node!!)
            } else if (itm.parent.isNotEmpty()) {
                parent = Node(
                    children = mutableListOf(node!!),
                    nodeType = NodeType.FOLDER
                )
                fileSystem.lookup[itm.parent] = parent!!
            }

            // Handle node metadata based on type
            when (itm.type) {
                NodeType.FILE -> {
                    val meta = NodeMeta()
                    meta.key = MegaUtils.a32ToBytes(key!!)
                    meta.iv = MegaUtils.a32ToBytes(intArrayOf(compKey!![4], compKey[5], 0, 0))
                    meta.mac = MegaUtils.a32ToBytes(intArrayOf(compKey[6], compKey[7]))
                    meta.compkey = MegaUtils.a32ToBytes(compKey)
                    node!!.meta = meta
                }

                NodeType.FOLDER -> {
                    val meta = NodeMeta()
                    meta.key = MegaUtils.a32ToBytes(key!!)
                    meta.compkey = MegaUtils.a32ToBytes(compKey!!)
                    node!!.meta = meta
                }

                NodeType.ROOT -> {
                    attr = FileAttr(name = "Cloud Drive")
                    fileSystem.root = node
                }

                NodeType.INBOX -> {
                    attr = FileAttr(name = "InBox")
                    fileSystem.inbox = node
                }

                NodeType.TRASH -> {
                    attr = FileAttr(name = "Trash")
                    fileSystem.trash = node
                }
            }

            // Handle shared directories
            if (itm.sharingUser?.isNotEmpty() == true) {
                fileSystem.sroots.add(node!!)
            }

            // Set final node properties
            node!!.name = attr.name
            node!!.hash = itm.handle
            node!!.parent = parent
            node!!.nodeType = itm.type

            return node
        }
    }

    private suspend fun pollEvents() {
        var err: MegaException? = null
        var sleepTime = minSleepTime // initial backoff time

        while (pollJob?.isActive == true) {
            try {
                if (err != null) {
                    megaLogger.d("pollEvents: error from server: ${err.message}")
                    delay(sleepTime)
                    sleepTime = (sleepTime.inWholeMilliseconds * 2).coerceAtMost(maxSleepTime.inWholeMilliseconds).milliseconds
                } else {
                    // reset sleep time to minimum on success
                    sleepTime = minSleepTime
                }

                val url = "${baseUrl}/sc?sn=$ssn&sid=$sessionId"

                val response = try {
                    httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                    }
                } catch (e: Exception) {
                    megaLogger.d("pollEvents: Error fetching status: ${e.message}")
                    err = MegaException(e.message, e.cause)
                    continue
                }

                if (response.status.value != 200) {
                    megaLogger.d("pollEvents: Error from server: ${response.status.value}")
                    err = MegaException("HTTP status: ${response.status.value}")
                    continue
                }

                val buf = response.bodyAsText()

                // First attempt to parse as an array
                try {
                    val events = Json.decodeFromString<Events>(buf)

                    // If wait URL is set, then fetch it and continue - we
                    // don't expect anything else if we have a wait URL
                    if (events.w?.isNotEmpty() == true) {
                        if (events.e.isNotEmpty()) {
                            megaLogger.d("pollEvents: Unexpected event with w set: $buf")
                        }

                        try {
                            httpClient.get(events.w)
                        } catch (e: Exception) {
                            // Just log and continue - not critical
                            megaLogger.d("pollEvents: Error fetching wait URL: ${e.message}")
                        }

                        err = null
                        continue
                    }

                    events.sn?.let {
                        ssn = it
                    }

                    // For each event in the array, parse it
                    for (evRaw in events.e) {
                        // First attempt to unmarshal as an error message
                        try {
                            val errorMsg = Json.decodeFromJsonElement<ErrorMsg>(evRaw)
                            megaLogger.d("pollEvents: Error message received $evRaw")
                            val parsedError = MegaError.parseError(errorMsg)
                            if (parsedError != null) {
                                megaLogger.e("pollEvents: Event from server was error: ${parsedError.message}")
                            }
                            continue
                        } catch (e: Exception) {
                            // Not an error message, continue processing
                        }

                        // Now unmarshal as a generic event
                        try {
                            val genericEvent = Json.decodeFromJsonElement<GenericEvent>(evRaw)
                            megaLogger.d("pollEvents: Parsing event ${genericEvent.cmd}: $evRaw")

                            // Work out what to do with the event
                            when (genericEvent.cmd) {
                                "t" -> processAddNode(evRaw)      // node addition
                                "u" -> processUpdateNode(evRaw)   // node update
                                "d" -> processDeleteNode(evRaw)    // node deletion
                                "s", "s2" -> {}  // share addition/update/revocation
                                "c" -> {}        // contact addition/update
                                "k" -> {}        // crypto key request
                                "fa" -> {}       // file attribute update
                                "ua" -> {}       // user attribute update
                                "psts" -> {}     // account updated
                                "ipc" -> {}      // incoming pending contact request (to us)
                                "opc" -> {}      // outgoing pending contact request (from us)
                                "upci" -> {}     // incoming pending contact request update
                                "upco" -> {}     // outgoing pending contact request update
                                "ph" -> {}       // public links handles
                                "se" -> {}       // set email
                                "mcc" -> {}      // chat creation / peer's invitation / peer's removal
                                "mcna" -> {}     // granted / revoked access to a node
                                "uac" -> {}      // user access control
                                else -> {
                                    megaLogger.d("pollEvents: Unknown message ${genericEvent.cmd} received: $evRaw")
                                }
                            }

                        } catch (e: Exception) {
                            megaLogger.e("pollEvents: Couldn't parse event from server: ${e.message}: $evRaw", e)
                        }
                    }

                    err = null

                } catch (e: Exception) {
                    // Try parsing as a lone error message
                    try {
                        val errorMsg = Json.decodeFromString<ErrorMsg>(buf)
                        err = MegaError.parseError(errorMsg)
                        if (err?.code == MegaError.EAGAIN.code) {
                            // This is normal, just retry
                        } else if (err != null) {
                            megaLogger.e("pollEvents: Error received from server: ${err.message}")
                        }
                    } catch (e2: Exception) {
                        megaLogger.e("pollEvents: Bad response received from server: $buf")
                        err = MegaException("Bad response from server", e.cause)
                    }
                }

            } catch (e: Exception) {
                // Catch any other exceptions to ensure the loop continues
                megaLogger.e("pollEvents: Unexpected error: ${e.message}")
                err = MegaException("Unexpected error: ${e.message}", e.cause)
            }
        }
    }

    private suspend fun processAddNode(evRaw: JsonElement) {
        try {
            val event = Json.decodeFromJsonElement<FSEvent>(evRaw)
            for (item in event.t?.files ?: emptyList()) {
                addFSNode(item)
            }
        } catch (e: Exception) {
            throw MegaException("Failed to process add node event: ${e.message}")
        }
    }

    private suspend fun processUpdateNode(evRaw: JsonElement) {
        try {
            val event = Json.decodeFromJsonElement<FSEvent>(evRaw)
            val node = fileSystem.hashLookup(event.n ?: "") ?: throw MegaException("Node not found")
            if (event.attr == null) throw MegaException("No attribute found")

            val attr = try {
                MegaUtils.decryptAttr(node.meta.key, event.attr)
            } catch (e: Exception) {
                FileAttr(name = "BAD ATTRIBUTE")
            }

            node.name = attr.name
            node.timestamp = Instant.fromEpochSeconds(event.ts ?: 0)
        } catch (e: Exception) {
            if (e is MegaException) throw e
            throw MegaException("Failed to process update node event: ${e.message}")
        }
    }

    private suspend fun processDeleteNode(evRaw: JsonElement) {
        try {
            val event = Json.decodeFromJsonElement<FSEvent>(evRaw)
            if (event.n == null) throw MegaException("No node hash found")
            val node = fileSystem.hashLookup(event.n)

            if (node?.parent != null) {
                node.parent?.removeChild(node)
                fileSystem.lookup.remove(node.hash)
            }
        } catch (e: Exception) {
            throw MegaException("Failed to process delete node event: ${e.message}")
        }
    }

    private suspend fun newDownload(src: Node): Download {
        // Prepare download message
        val msg = DownloadMsg(
            cmd = "g",
            g = 1,
            n = src.hash,
            ssl = 2
        )

        // Get the node's key
        val key = src.meta.key

        // Send request to get download URL
        val request = Json.encodeToString(arrayOf(msg))
        val result = apiRequest(request)

        // Parse response
        val response = Json.decodeFromString<Array<DownloadResp>>(result)[0]

        // Check for embedded error
        response.err?.let { err ->
            MegaError.parseError(err)?.let { throw it }
        }

        // Decrypt attributes to verify key
        MegaUtils.decryptAttr(key, response.attr)

        // Calculate chunk sizes
        val chunks = MegaUtils.getChunkSizes(response.size.toLong())

        // Adjust download URL for HTTPS if needed
        var downloadUrl = response.g
        if (downloadUrl.startsWith("http://")) {
            downloadUrl = "https://${downloadUrl.removePrefix("http://")}"
        }

        return Download(
            mega = this,
            src = src,
            resourceUrl = downloadUrl,
            chunks = chunks,
            chunkMacs = MutableList(chunks.size) { null }
        )
    }

    private suspend fun newUpload(parent: Node, name: String, fileSize: Long): Upload {
        val uploadMsg = UploadMsg(
            cmd = "u",
            s = fileSize,
            ssl = 2,
        )

        // Send request to get download URL
        val request = Json.encodeToString(arrayOf(uploadMsg))
        val result = apiRequest(request)

        // Parse response
        val response = Json.decodeFromString<Array<UploadResp>>(result)[0]

        // Generate random encryption key
        val ukey = IntArray(6) { Random.nextInt() }

        // Convert parts of the key to byte arrays for cryptography operations
        val kbytes = MegaUtils.a32ToBytes(ukey.sliceArray(0..3))
        val kiv = MegaUtils.a32ToBytes(intArrayOf(ukey[4], ukey[5], 0, 0))

        // Create IV for encryption
        val iv = MegaUtils.a32ToBytes(intArrayOf(ukey[4], ukey[5], ukey[4], ukey[5]))

        // Calculate chunk sizes for the file
        var chunks = MegaUtils.getChunkSizes(fileSize)

        // For zero-sized files, add a single empty chunk
        if (chunks.isEmpty()) {
            chunks = listOf(ChunkSize(0, 0))
        }

        // Ensure HTTPS if configured
        var uploadUrl = response.p
        if (uploadUrl.startsWith("http://")) {
            uploadUrl = "https://" + uploadUrl.removePrefix("http://")
        }

        // Create and return the Upload object
        return Upload(
            mega = this,
            parentHash = parent.hash,
            name = name,
            uploadUrl = uploadUrl,
            iv = iv,
            kiv = kiv,
            kbytes = kbytes,
            masterKey = masterKey,
            ukey = ukey,
            chunks = chunks,
            chunkMacs = Array(chunks.size) { ByteArray(0) },
            completionHandle = ByteArray(0)
        )
    }
}
