package dev.carlsen.mega

import dev.carlsen.mega.model.NodeType
import dev.carlsen.mega.util.CancellationToken
import dev.carlsen.mega.util.ProgressCountingSink
import dev.carlsen.mega.util.ProgressCountingSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MegaTest {

    private val mega = Mega()
    private val testFolderName = "unit-test-static"
    private val testDownloadFileName = "image.jpg"

    @Test
    fun `test - get filesystem without login has null root`() = runTest {
        val fs = mega.getFileSystem()
        assertNull(fs.root)
    }

    @Test
    fun `test - filesystem has valid root with test folder`() = runTest {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        assertNotNull(fs.root)

        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == "test" && it.nodeType == NodeType.FOLDER }
        assertNotNull(testFolder)
        mega.logout()
    }

    @Test
    fun `test - perform fast login`() = runTest {
        mega.login(megaUserName, megaPassword)
        val session = mega.dumpSession()
        assertNotNull(session)
        mega.logout()
        val newMega = Mega().apply { fastLogin(session) }
        val fs = newMega.getFileSystem()
        assertNotNull(fs.root)
        mega.logout()
    }

    @Test
    fun `test - get quota`() = runTest {
        mega.login(megaUserName, megaPassword)
        val quota = mega.getQuota()
        assertNotNull(quota)
        mega.logout()
    }

    @Test
    fun `test - create and delete folder in root`() = runTest {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val nodeFolder = mega.createDir("testFolderXXX", fs.root)
        assertNotNull(nodeFolder)
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == "testFolderXXX" && it.nodeType == NodeType.FOLDER }
        assertNotNull(testFolder)

        mega.delete(nodeFolder, destroy = true)
        mega.logout()
    }

    @Test
    fun `test - create and move folder to trash`() = runBlocking {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val nodeFolder = mega.createDir("testFolderToDelete", fs.root)
        delay(1000)
        mega.delete(nodeFolder, destroy = false)
        val testFolder = fs.trash?.getChildren()?.firstOrNull { it.name == "testFolderToDelete" && it.nodeType == NodeType.FOLDER }
        assertNotNull(testFolder)
        mega.logout()
    }

    @Test
    fun `test - rename folder`() = runBlocking {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == testFolderName && it.nodeType == NodeType.FOLDER }
        val testFolderToRename = testFolder!!.getChildren().firstOrNull { it.name == "unit-test-folder-rename" && it.nodeType == NodeType.FOLDER }
        mega.rename(testFolderToRename, "unit-test-folder-renamed")
        val renamedFolder = mega.getChildren(testFolder).firstOrNull { it.name == "unit-test-folder-renamed" && it.nodeType == NodeType.FOLDER }
        assertNotNull(renamedFolder)
        delay(1000)
        mega.rename(renamedFolder, "unit-test-folder-rename")
        mega.logout()
    }

    @Test
    fun `test - rename file`() = runBlocking {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == testFolderName && it.nodeType == NodeType.FOLDER }
        val testFile = testFolder?.getChildren()?.firstOrNull { it.name == "test-file-rename.txt" && it.nodeType == NodeType.FILE }
        mega.rename(testFile, "test-file-renamed.txt")
        val renamedFile = testFolder?.getChildren()?.firstOrNull { it.name == "test-file-renamed.txt" && it.nodeType == NodeType.FILE }
        assertNotNull(renamedFile)
        delay(1000)
        mega.rename(renamedFile, "test-file-rename.txt")
        mega.logout()
    }

    @Test
    fun `test - download file`() = runTest {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == testFolderName && it.nodeType == NodeType.FOLDER }
        val testFile = testFolder?.getChildren()?.firstOrNull { it.name == testDownloadFileName && it.nodeType == NodeType.FILE }
        assertNotNull(testFile)
        SystemFileSystem.sink(Path("test.jpg")).use { fileOutputSink ->
            mega.downloadFile(
                src = testFile,
                fileOutputSink = ProgressCountingSink(
                    delegate = fileOutputSink,
                    totalBytes = testFile.size,
                    onProgress = { b, t ->
                        println("Downloaded $b of $t bytes")
                    }
                ).buffered(),
                cancellationToken = CancellationToken.default()
            )
        }
        mega.logout()
    }

    @Test
    fun `test - cancel download file`() = runTest {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == testFolderName && it.nodeType == NodeType.FOLDER }
        val testFile = testFolder?.getChildren()?.firstOrNull { it.name == testDownloadFileName && it.nodeType == NodeType.FILE }
        assertNotNull(testFile)

        // Use a custom cancellation token that we can control
        val cancellationToken = CancellationToken()
        var wasCancelled = false
        var downloadedBytes = 0L

        try {
            SystemFileSystem.sink(Path("test_cancelled.jpg")).use { fileOutputSink ->
                // Cancel after a threshold is reached (e.g., 10% of the file)
                val cancellationThreshold = testFile.size / 10

                mega.downloadFile(
                    src = testFile,
                    fileOutputSink = ProgressCountingSink(
                        delegate = fileOutputSink,
                        totalBytes = testFile.size,
                        onProgress = { bytesDownloaded, total ->
                            downloadedBytes = bytesDownloaded
                            println("Downloaded $bytesDownloaded of $total bytes")

                            // Cancel download once we reach the threshold
                            if (bytesDownloaded >= cancellationThreshold && !cancellationToken.isCancellationRequested()) {
                                println("Cancelling download at $bytesDownloaded bytes")
                                cancellationToken.cancel()
                            }
                        }
                    ).buffered(),
                    cancellationToken = cancellationToken
                )
            }
        } catch (e: Exception) {
            wasCancelled = e.message?.contains("cancelled", ignoreCase = true) == true ||
                          e.cause?.message?.contains("cancelled", ignoreCase = true) == true
        }

        // Assert that download was actually cancelled
        assertTrue(cancellationToken.isCancellationRequested())
        assertTrue(wasCancelled)

        // Assert that we downloaded something, but not the entire file
        assertTrue(downloadedBytes > 0)
        assertTrue(downloadedBytes < testFile.size)

        mega.logout()
    }

    @Test
    fun `test - upload file and delete it`() = runBlocking {
        mega.login(megaUserName, megaPassword)
        val fs = mega.getFileSystem()
        val rootChildren = mega.getChildren(fs.root!!)
        val testFolder = rootChildren.firstOrNull { it.name == testFolderName && it.nodeType == NodeType.FOLDER }
        val fileUploadName = "testFile${Clock.System.now().toEpochMilliseconds()}.jpg"

        val file = Path("src/commonTest/resources/test.jpg")
        val uploadResultNode = SystemFileSystem.source(file).use { fileInputSource ->
            mega.uploadFile(
                destNode = testFolder!!,
                name = fileUploadName,
                fileSize = 2705239,
                fileInputSource = ProgressCountingSource(
                    delegate = fileInputSource,
                    totalBytes = 2705239,
                    onProgress = { b, t ->
                        println("Upload $b of $t bytes")
                    }
                ).buffered(),
                cancellationToken = CancellationToken.default()
            )
        }
        delay(1000)
        val uploadedFile = mega.getChildren(testFolder!!).firstOrNull { it.name == fileUploadName && it.nodeType == NodeType.FILE }
        assertNotNull(uploadedFile)
        assertEquals(uploadResultNode.timestamp.toEpochMilliseconds(), uploadedFile.timestamp.toEpochMilliseconds())
        delay(1000)
        mega.delete(uploadedFile, destroy = true)
        mega.logout()
    }
}

expect val megaUserName: String
expect val megaPassword: String