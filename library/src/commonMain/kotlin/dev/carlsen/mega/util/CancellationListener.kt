package dev.carlsen.mega.util

import co.touchlab.stately.concurrency.Synchronizable
import co.touchlab.stately.concurrency.synchronize

class CancellationListener(
    private val token: CancellationToken,
    val action: () -> Unit,
    private var closed: Boolean = false
) : AutoCloseable, Synchronizable() {

    override fun close() {
        synchronize {
            if (!closed) {
                closed = true
                token.unregister(this)
            }
        }
    }

    fun runAction() {
        synchronize {
            throwIfClosed()
            action()
            close()
        }
    }

    private fun throwIfClosed() {
        check(!closed) { "Object already closed" }
    }
}
