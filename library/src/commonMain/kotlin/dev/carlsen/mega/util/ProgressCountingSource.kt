package dev.carlsen.mega.util

import kotlinx.io.Buffer
import kotlinx.io.RawSource

class ProgressCountingSource(
    private val delegate: RawSource,
    private val totalBytes: Long,
    private val onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
) : RawSource {
    private var bytesRead: Long = 0

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val bytesRead = delegate.readAtMostTo(sink, byteCount)
        this.bytesRead += bytesRead
        onProgress(this.bytesRead, totalBytes)
        return bytesRead
    }

    override fun close() {
        delegate.close()
    }
}