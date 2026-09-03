package com.fractanomics.crosstraining.data.firebase

/**
 * Maps cloud synchronization exceptions and Firestore errors to clean, user-friendly UI messages.
 * Eliminates raw exception codes and crashes for network timeouts and permission errors.
 */
object CloudSyncErrorMapper {
    const val NETWORK_UNAVAILABLE_MESSAGE = "Network unavailable. Your data is saved locally on this device"
    const val GUEST_AUTH_PROMPT = "Please sign in to back up workouts to the cloud"
    const val SUCCESS_MESSAGE = "Cloud sync completed!"
    const val GENERIC_ERROR_MESSAGE = "Sync failed. Please try again later."
    const val PERMISSION_DENIED_MESSAGE = "Permission denied. Please verify your account credentials."

    fun isNetworkOrTimeout(error: Throwable?): Boolean {
        if (error == null) return false
        val message = error.message.orEmpty()
        val className = error.javaClass.name
        return error is kotlinx.coroutines.TimeoutCancellationException ||
                error is java.net.UnknownHostException ||
                error is java.net.SocketTimeoutException ||
                error is java.net.ConnectException ||
                error is java.io.IOException ||
                className.contains("FirebaseNetworkException", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ||
                message.contains("unavailable", ignoreCase = true) ||
                message.contains("offline", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ||
                message.contains("deadline_exceeded", ignoreCase = true)
    }

    fun isNetworkOrTimeout(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val lower = errorMessage.lowercase()
        return lower.contains("network") ||
                lower.contains("unavailable") ||
                lower.contains("offline") ||
                lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("deadline_exceeded") ||
                lower.contains("unknownhostexception") ||
                lower.contains("sockettimeoutexception") ||
                lower.contains("connectexception")
    }

    fun map(error: Throwable?): String {
        if (error == null) return GENERIC_ERROR_MESSAGE
        if (isNetworkOrTimeout(error)) return NETWORK_UNAVAILABLE_MESSAGE
        return mapMessage(error.message)
    }

    fun mapMessage(message: String?): String {
        if (message.isNullOrBlank()) return GENERIC_ERROR_MESSAGE
        if (isNetworkOrTimeout(message)) return NETWORK_UNAVAILABLE_MESSAGE
        val lower = message.lowercase()
        return when {
            lower.contains("permission_denied") || lower.contains("permission denied") || lower.contains("missing or insufficient permissions") ->
                PERMISSION_DENIED_MESSAGE
            lower.contains("not authenticated") || lower.contains("unauthenticated") || lower.contains("re-authentication") || lower.contains("sign in") ->
                GUEST_AUTH_PROMPT
            // Cleanly shield against raw Firestore exception codes
            lower.contains("firebasefirestoreexception") || lower.contains("firestore") ->
                GENERIC_ERROR_MESSAGE
            message.matches(Regex("^[A-Z0-9_]+(:.*)?$")) ->
                GENERIC_ERROR_MESSAGE
            else ->
                message
        }
    }
}
