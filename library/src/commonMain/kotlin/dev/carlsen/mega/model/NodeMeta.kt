package dev.carlsen.mega.model

/**
 * Metadata for a Node
 */
data class NodeMeta(
    var key: ByteArray = ByteArray(0),
    var compkey: ByteArray = ByteArray(0),
    var iv: ByteArray = ByteArray(0),
    var mac: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as NodeMeta

        if (!key.contentEquals(other.key)) return false
        if (!compkey.contentEquals(other.compkey)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!mac.contentEquals(other.mac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + compkey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}