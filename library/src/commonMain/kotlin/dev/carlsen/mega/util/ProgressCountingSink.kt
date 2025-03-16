package dev.carlsen.mega.util

import kotlinx.io.Buffer
import kotlinx.io.RawSink

class ProgressCountingSink(
    private val delegate: RawSink,
    private val totalBytes: Long,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
) : RawSink {
    private var bytesWritten: Long = 0

    override fun write(source: Buffer, byteCount: Long) {
        delegate.write(source, byteCount)
        bytesWritten += byteCount
        onProgress(bytesWritten, totalBytes)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}