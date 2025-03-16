package dev.carlsen.mega.util

import co.touchlab.stately.concurrency.Synchronizable
import co.touchlab.stately.concurrency.synchronize
import kotlin.coroutines.cancellation.CancellationException

class CancellationToken : AutoCloseable, Synchronizable() {
    private val registrations = mutableListOf<CancellationListener>()
    private var cancellationRequested = false
    private var closed = false

    override fun close() {
        synchronize {
            if (!closed) {
                val registrations: List<CancellationListener> = ArrayList(registrations)
                for (registration in registrations) {
                    registration.close()
                }
                this.registrations.clear()
                closed = true
            }
        }
    }

    fun cancel() {
        synchronize {
            throwIfClosed()
            if (!cancellationRequested) {
                cancellationRequested = true
                notifyListeners(registrations)
            }
        }
    }

    fun isCancellationRequested(): Boolean {
        synchronize {
            throwIfClosed()
        }
        return cancellationRequested
    }

    fun throwIfCancellationRequested() {
        synchronize {
            throwIfClosed()
            if (cancellationRequested) {
                throw CancellationException("Cancelled")
            }
        }
    }

    private fun throwIfClosed() {
        check(!closed) { "Object already closed" }
    }

    fun register(action: () -> Unit): CancellationListener {
        val ctr = CancellationListener(this, action)
        synchronize {
            if (cancellationRequested) {
                ctr.runAction()
            } else {
                registrations.add(ctr)
            }
        }
        return ctr
    }

    fun unregister(registration: CancellationListener) {
        synchronize {
            registrations.remove(registration)
        }
    }

    private fun notifyListeners(registrations: List<CancellationListener>) {
        val listeners = registrations.toMutableList()
        listeners.forEach { listener ->
            listener.runAction()
        }
    }

    companion object {
        fun default(): CancellationToken {
            return CancellationToken()
        }
    }
}