package dev.polisms.filter.libgm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibgmOperationLockTest {
    @Test
    fun `waiting operation exits when its worker thread is interrupted`() {
        val holderEntered = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val waiterStarted = CountDownLatch(1)
        val waiterInterrupted = AtomicBoolean(false)
        val holder = Thread {
            LibgmOperationLock.run {
                holderEntered.countDown()
                releaseHolder.await()
            }
        }

        holder.start()
        assertTrue(holderEntered.await(1, TimeUnit.SECONDS))

        val waiter = Thread {
            waiterStarted.countDown()
            try {
                LibgmOperationLock.run { error("waiter unexpectedly acquired the lock") }
            } catch (_: InterruptedException) {
                waiterInterrupted.set(true)
            }
        }
        waiter.start()
        assertTrue(waiterStarted.await(1, TimeUnit.SECONDS))

        waiter.interrupt()
        waiter.join(1_000)
        releaseHolder.countDown()
        holder.join(1_000)

        assertFalse("interrupted waiter should not remain blocked", waiter.isAlive)
        assertTrue(waiterInterrupted.get())
        assertFalse(holder.isAlive)
    }
}
