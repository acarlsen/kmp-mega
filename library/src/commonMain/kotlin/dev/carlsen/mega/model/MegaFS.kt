package dev.carlsen.mega.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mega filesystem object
 */
class MegaFS {
    var root: Node? = null
    var trash: Node? = null
    var inbox: Node? = null
    var sroots: MutableList<Node> = mutableListOf()
    val lookup: MutableMap<String, Node> = mutableMapOf()
    val skmap: MutableMap<String, String> = mutableMapOf()
    val mutex = Mutex()

    /**
     * Get filesystem root node
     *
     * @return Root node
     */
    suspend fun getRoot(): Node? {
        mutex.withLock {
            return root
        }
    }

    /**
     * Get filesystem trash node
     *
     * @return Trash node
     */
    suspend fun getTrash(): Node? {
        mutex.withLock {
            return trash
        }
    }

    /**
     * Get inbox node
     *
     * @return Inbox node
     */
    suspend fun getInbox(): Node? {
        mutex.withLock {
            return inbox
        }
    }

    /**
     * Get a node pointer from its hash
     *
     * @param h Hash of the node
     * @return Node or null if not found
     */
    suspend fun hashLookup(h: String): Node? {
        mutex.withLock {
            return lookup[h]
        }
    }

    /**
     * Get the list of child nodes for a given node
     *
     * @param n Parent node
     * @return List of child nodes
     */
    suspend fun getChildren(n: Node?): List<Node> {
        mutex.withLock {
            if (n == null) {
                throw MegaException("Node cannot be null")
            }

            val node = lookup[n.hash] ?: throw MegaException("Node not found")

            return node.getChildren()
        }
    }

    /**
     * Retrieve all the nodes in the given node tree path by name
     * This method returns array of nodes up to the matched subpath
     * (in same order as input names array) even if the target node is not located.
     *
     * @param root Root node to start search from
     * @param ns List of names to follow in path
     * @return List of nodes matching the path
     */
    suspend fun pathLookup(root: Node?, ns: List<String>): List<Node> {
        mutex.withLock {
            if (root == null) {
                throw MegaException("Root node cannot be null")
            }

            val nodePath = mutableListOf<Node>()
            var found = true
            var children = root.getChildren()

            for (name in ns) {
                found = false
                for (n in children) {
                    if (n.name == name) {
                        nodePath.add(n)
                        children = n.getChildren()
                        found = true
                        break
                    }
                }

                if (!found) {
                    break
                }
            }

            if (!found) {
                throw MegaException("Node not found")
            }

            return nodePath
        }
    }

    /**
     * Get top level directory nodes shared by other users
     *
     * @return List of shared root nodes
     */
    suspend fun getSharedRoots(): List<Node> {
        mutex.withLock {
            return sroots.toList()
        }
    }

    companion object {
        /**
         * Create a new MegaFS object
         */
        fun newMegaFS(): MegaFS {
            return MegaFS()
        }
    }
}

