package dev.polisms.filter.libgm

import java.util.concurrent.locks.ReentrantLock

object LibgmOperationLock {
    private val lock = ReentrantLock()

    @Throws(InterruptedException::class)
    fun <T> run(block: () -> T): T {
        lock.lockInterruptibly()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
