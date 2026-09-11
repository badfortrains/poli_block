package dev.polisms.filter.deletion

object DeletionFailureClassifier {
    private val authMarkers = listOf(
        "auth token",
        "authentication",
        "not logged in",
        "permission denied",
        "status 401",
        "status 403",
        "unauthorized",
    )

    fun isAuthenticationFailure(error: Throwable): Boolean = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.lowercase() }
        .any { message -> authMarkers.any(message::contains) }
}
