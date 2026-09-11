package dev.polisms.filter.deletion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionFailureClassifierTest {
    @Test
    fun `auth marker in cause chain is recognized`() {
        val error = IllegalStateException("outer", Exception("failed to refresh auth token"))

        assertTrue(DeletionFailureClassifier.isAuthenticationFailure(error))
    }

    @Test
    fun `network failure is not classified as auth`() {
        assertFalse(DeletionFailureClassifier.isAuthenticationFailure(Exception("connection timed out")))
    }
}
