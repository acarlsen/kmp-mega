# [kmp-mega](https://github.com/acarlsen/kmp-mega)
![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/acarlsen/kmp-mega/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.carlsen.mega/mega)](https://central.sonatype.com/namespace/dev.carlsen.mega)
[![Kotlin version](https://img.shields.io/badge/Kotlin-2.2.20-blueviolet?logo=kotlin&logoColor=white)](http://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![badge][badge-jvm]
![badge][badge-android]
![badge][badge-ios]
![badge][badge-mac]

Mega client SDK implemented for Kotlin Multiplatform.
Basically a re-implementation of the [go-mega](https://github.com/t3rm1n4l/go-mega) library in Kotlin.

This library is currently focused on file access, so not all features available in official SDK's are available.

Features:
* Login (also with 2FA)
* Get quota
* Fetch filesystem
* Download file
* Upload file
* Create folder
* Delete file/folder
* Move file/folder
* Rename file/folder

Whats missing:
* Shared folder/file support not working (decryption fails)
* Setting modified time of uploaded file
* Link support
* Get user support
* Empty trash option

## Platform Support
- Android
- Desktop
- iOS
- MacOS Native

## To include in your project

Add the repository:
```kotlin
repositories {
    mavenCentral()
}
```

Put in your dependencies block:

```kotlin
implementation("dev.carlsen.mega:mega:1.0.0-beta04")
```

## How to use

```kotlin
// Initialize the SDK
val mega = Mega()

// Log in to your MEGA account
mega.login("email@example.com", "password")

// Access your files
val fs = mega.getFileSystem()
val rootNode = fs.root
val files = mega.getChildren(rootNode!!)

// Download a file
val file = files.first { it.name == "example.pdf" }
SystemFileSystem.sink(Path("download.pdf")).use { fileOutputSink ->
    mega.downloadFile(
        src = file,
        fileOutputSink = ProgressCountingSink(
            delegate = fileOutputSink,
            totalBytes = file.size,
            onProgress = { b, t ->
                println("Downloaded $b of $t bytes")
            }
        ).buffered(),
        cancellationToken = CancellationToken.default()
    )
}

// Upload a file
val fileToUpload = Path("documents/report.pdf")
SystemFileSystem.source(fileToUpload).use { fileSource ->
    mega.uploadFile(
        destNode = rootNode,
        name = "uploaded-report.pdf",
        fileSize = fileSource.size(),
        fileInputSource = fileSource.buffered(),
        cancellationToken = CancellationToken.default()
    )
}

// Create a folder
val newFolder = mega.createFolder(rootNode, "My New Folder")

// Delete a node (with optional permanent deletion)
mega.delete(file, destroy = true)

// Enable logging
mega.logger.addListener(object : LogListener {
    override fun onLogMessage(level: LogLevel, message: String, throwable: Throwable?) {
        println("[$level] $message")
        throwable?.printStackTrace()
    }
})

// Cancel operations with cancellation token
val cancellationToken = CancellationToken()
try {
    mega.uploadFile(
        destNode = rootNode,
        name = "large-file.zip",
        fileSize = largeFileSource.size(),
        fileInputSource = largeFileSource,
        cancellationToken = cancellationToken
    )
} catch (e: Exception) {
    // Handle cancellation
}

// Call cancel() from another thread to stop the operation
cancellationToken.cancel()

// Logout when done
mega.logout()
```


[badge-android]: http://img.shields.io/badge/android-6EDB8D.svg?style=flat

[badge-ios]: http://img.shields.io/badge/ios-CDCDCD.svg?style=flat

[badge-js]: http://img.shields.io/badge/js-F8DB5D.svg?style=flat

[badge-jvm]: http://img.shields.io/badge/jvm-DB413D.svg?style=flat

[badge-linux]: http://img.shields.io/badge/linux-2D3F6C.svg?style=flat

[badge-windows]: http://img.shields.io/badge/windows-4D76CD.svg?style=flat

[badge-mac]: http://img.shields.io/badge/macos-111111.svg?style=flat

[badge-watchos]: http://img.shields.io/badge/watchos-C0C0C0.svg?style=flat

[badge-tvos]: http://img.shields.io/badge/tvos-808080.svg?style=flat

[badge-wasm]: https://img.shields.io/badge/wasm-624FE8.svg?style=flat

[badge-nodejs]: https://img.shields.io/badge/nodejs-68a063.svg?style=flat
