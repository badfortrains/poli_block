package dev.polisms.filter.libgm

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object LibgmOperationLock {
    private val lock = ReentrantLock()

    fun <T> run(block: () -> T): T = lock.withLock(block)
}
