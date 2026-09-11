package dev.polisms.filter.deletion

import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionTargetTest {
    @Test
    fun `target JSON round trips`() {
        val target = DeletionTarget("Example", "Message\nStop2End", 123456)

        assertEquals(target, DeletionTarget.decode(target.encode()))
    }

    @Test
    fun `null sender JSON round trips`() {
        val target = DeletionTarget(null, "Stop2End", 123456)

        assertEquals(target, DeletionTarget.decode(target.encode()))
    }
}
