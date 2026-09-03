package com.fractanomics.crosstraining.data.firebase

import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests verifying CloudSyncErrorMapper translations for Issue #486:
 * - Friendly snackbar: "Network unavailable. Your data is saved locally on this device"
 * - Guest prompt: "Please sign in to back up workouts to the cloud"
 * - Shielding against raw Firestore exception codes and crashes
 */
class CloudSyncErrorMapperTest {

    @Test
    fun isNetworkOrTimeout_detectsNetworkAndTimeoutExceptions() {
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(SocketTimeoutException("Read timed out")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(UnknownHostException("Unable to resolve host")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(ConnectException("Connection refused")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(IOException("Network error occurred")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(RuntimeException("FirebaseFirestoreException: UNAVAILABLE: The service is currently unavailable.")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(RuntimeException("DEADLINE_EXCEEDED: timed out waiting for response")))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout(RuntimeException("Device is offline")))
    }

    @Test
    fun isNetworkOrTimeout_detectsStringMessages() {
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("Network unavailable"))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("Connection timed out"))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("Client is offline"))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("FirebaseFirestoreException: UNAVAILABLE"))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("DEADLINE_EXCEEDED"))
        assertTrue(CloudSyncErrorMapper.isNetworkOrTimeout("java.net.SocketTimeoutException"))

        assertFalse(CloudSyncErrorMapper.isNetworkOrTimeout("PERMISSION_DENIED"))
        assertFalse(CloudSyncErrorMapper.isNetworkOrTimeout(null as String?))
        assertFalse(CloudSyncErrorMapper.isNetworkOrTimeout(null as Throwable?))
        assertFalse(CloudSyncErrorMapper.isNetworkOrTimeout(""))
    }

    @Test
    fun map_translatesNetworkExceptionsToFriendlyMessage() {
        val socketErr = SocketTimeoutException("timed out")
        assertEquals(CloudSyncErrorMapper.NETWORK_UNAVAILABLE_MESSAGE, CloudSyncErrorMapper.map(socketErr))

        val hostErr = UnknownHostException("firestore.googleapis.com")
        assertEquals(CloudSyncErrorMapper.NETWORK_UNAVAILABLE_MESSAGE, CloudSyncErrorMapper.map(hostErr))

        val rawFirestoreUnavailable = RuntimeException("FirebaseFirestoreException: UNAVAILABLE: backend unavailable")
        assertEquals(CloudSyncErrorMapper.NETWORK_UNAVAILABLE_MESSAGE, CloudSyncErrorMapper.map(rawFirestoreUnavailable))
    }

    @Test
    fun mapMessage_translatesGuestAndAuthErrors() {
        assertEquals(
            CloudSyncErrorMapper.GUEST_AUTH_PROMPT,
            CloudSyncErrorMapper.mapMessage("User not authenticated")
        )
        assertEquals(
            CloudSyncErrorMapper.GUEST_AUTH_PROMPT,
            CloudSyncErrorMapper.mapMessage("Please sign in to back up workouts to the cloud")
        )
        assertEquals(
            CloudSyncErrorMapper.GUEST_AUTH_PROMPT,
            CloudSyncErrorMapper.mapMessage("Re-authentication required")
        )
    }

    @Test
    fun mapMessage_shieldsAgainstRawFirestoreCodes() {
        // Permission denied
        assertEquals(
            CloudSyncErrorMapper.PERMISSION_DENIED_MESSAGE,
            CloudSyncErrorMapper.mapMessage("PERMISSION_DENIED: Missing or insufficient permissions")
        )

        // Raw Firestore exception class name without network keywords
        assertEquals(
            CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE,
            CloudSyncErrorMapper.mapMessage("FirebaseFirestoreException: INTERNAL")
        )

        // Raw upper-case Firestore code
        assertEquals(
            CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE,
            CloudSyncErrorMapper.mapMessage("INTERNAL_ERROR")
        )
    }

    @Test
    fun mapMessage_handlesNullAndEmptyGracefully() {
        assertEquals(CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE, CloudSyncErrorMapper.map(null))
        assertEquals(CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE, CloudSyncErrorMapper.mapMessage(null))
        assertEquals(CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE, CloudSyncErrorMapper.mapMessage(""))
        assertEquals(CloudSyncErrorMapper.GENERIC_ERROR_MESSAGE, CloudSyncErrorMapper.mapMessage("   "))
    }
}
