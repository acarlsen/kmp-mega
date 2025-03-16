package dev.carlsen.mega.transfer

/**
 * Describes the size and position of a chunk in a file
 *
 * @property position Starting byte position of the chunk within the file
 * @property size Size of the chunk in bytes
 */
data class ChunkSize(
    val position: Long,
    val size: Int
)