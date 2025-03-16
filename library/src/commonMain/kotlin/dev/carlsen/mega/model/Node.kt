package dev.carlsen.mega.model

import kotlinx.datetime.Instant

/**
 * Filesystem node
 */
class Node(
    var name: String = "",
    var hash: String = "",
    var parent: Node? = null,
    private val children: MutableList<Node> = mutableListOf(),
    var nodeType: Int = 0,
    var size: Long = 0,
    var timestamp: Instant = Instant.fromEpochMilliseconds(0),
    var meta: NodeMeta = NodeMeta(),
) {
    fun getChildren(): List<Node> = children

    /**
     * Remove a child node
     *
     * @param c The child node to remove
     * @return true if the child was found and removed, false otherwise
     */
    fun removeChild(c: Node): Boolean {
        val index = children.indexOfFirst { it.hash == c.hash }
        if (index >= 0) {
            children.removeAt(index)
            return true
        }
        return false
    }

    /**
     * Add a child node
     *
     * @param c The child node to add
     */
    fun addChild(c: Node) {
        children.add(c)
    }

    val isFolder: Boolean
        get() = nodeType == NodeType.FOLDER

    val isFile: Boolean
        get() = nodeType == NodeType.FILE
}