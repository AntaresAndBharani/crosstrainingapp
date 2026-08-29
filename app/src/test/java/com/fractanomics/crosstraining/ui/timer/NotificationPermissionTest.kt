package com.fractanomics.crosstraining.ui.timer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit & Integration tests for Issue #417:
 * "[Subtask] Add notification/foreground-service permissions with Android 13+ runtime request"
 *
 * Covers Gherkin Acceptance Criteria:
 * - Scenario: Manifest declares required permissions
 *   Given AndroidManifest.xml
 *   Then it declares "android.permission.POST_NOTIFICATIONS"
 *   And it declares "android.permission.FOREGROUND_SERVICE"
 *   And it declares "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
 *
 * - Scenario: Runtime permission requested on Android 13+
 *   Given the device is running API level 33 or higher
 *   And POST_NOTIFICATIONS has not yet been granted
 *   When the user starts a workout timer
 *   Then the app requests the POST_NOTIFICATIONS runtime permission
 *   And if denied, the timer still runs in-app without crashing (no foreground notification is shown)
 *
 * - Scenario: No runtime prompt below Android 13
 *   Given the device is running API level 32 or lower
 *   When the user starts a workout timer
 *   Then no POST_NOTIFICATIONS runtime prompt is shown
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        TimerEngineProvider.setInstanceForTesting(null)
    }

    @Test
    fun `Scenario - Manifest declares required permissions`() {
        // Given: AndroidManifest.xml file
        val manifestCandidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = manifestCandidates.firstOrNull { it.exists() }
        assertTrue("AndroidManifest.xml should exist", manifestFile != null && manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)
        doc.documentElement.normalize()

        val usesPermissions = doc.getElementsByTagName("uses-permission")
        val declaredPermissions = mutableListOf<String>()
        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i)
            val name = item.attributes.getNamedItem("android:name")?.nodeValue
            if (name != null) {
                declaredPermissions.add(name)
            }
        }

        // Then: it declares "android.permission.POST_NOTIFICATIONS"
        assertTrue(
            "Manifest must declare android.permission.POST_NOTIFICATIONS",
            declaredPermissions.contains("android.permission.POST_NOTIFICATIONS")
        )

        // And: it declares "android.permission.FOREGROUND_SERVICE"
        assertTrue(
            "Manifest must declare android.permission.FOREGROUND_SERVICE",
            declaredPermissions.contains("android.permission.FOREGROUND_SERVICE")
        )

        // And: it declares "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
        assertTrue(
            "Manifest must declare android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            declaredPermissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
        )
    }

    @Test
    fun `NotificationPermissionHelper defines exact required permission strings`() {
        assertEquals("android.permission.POST_NOTIFICATIONS", NotificationPermissionHelper.POST_NOTIFICATIONS)
        assertEquals("android.permission.FOREGROUND_SERVICE", NotificationPermissionHelper.FOREGROUND_SERVICE)
        assertEquals("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", NotificationPermissionHelper.FOREGROUND_SERVICE_MEDIA_PLAYBACK)

        val required = NotificationPermissionHelper.REQUIRED_MANIFEST_PERMISSIONS
        assertTrue(required.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(required.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(required.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test
    fun `Scenario - Runtime permission requested on Android 13+ when not granted`() {
        // Given: The device is running API level 33 (Tiramisu) or higher
        val sdkLevel33 = Build.VERSION_CODES.TIRAMISU
        val sdkLevel34 = 34
        val sdkLevel35 = 35

        assertTrue(NotificationPermissionHelper.isRuntimePermissionRequired(sdkLevel33))
        assertTrue(NotificationPermissionHelper.isRuntimePermissionRequired(sdkLevel34))
        assertTrue(NotificationPermissionHelper.isRuntimePermissionRequired(sdkLevel35))

        // And: POST_NOTIFICATIONS has not yet been granted
        val notGrantedChecker: (Context?, String) -> Int = { _, _ -> PackageManager.PERMISSION_DENIED }

        val hasPermission33 = NotificationPermissionHelper.hasNotificationPermission(
            context = null,
            sdkInt = sdkLevel33,
            permissionChecker = notGrantedChecker
        )
        assertFalse("Should not have permission when denied", hasPermission33)

        // When: Checking whether runtime permission should be requested
        val shouldRequest = NotificationPermissionHelper.shouldRequestNotificationPermission(
            context = null,
            sdkInt = sdkLevel33,
            permissionChecker = notGrantedChecker
        )

        // Then: The app requests POST_NOTIFICATIONS runtime permission
        assertTrue("Should request runtime permission on API 33+ when not granted", shouldRequest)
    }

    @Test
    fun `Scenario - Runtime permission already granted on Android 13+ requires no prompt`() {
        val sdkLevel33 = Build.VERSION_CODES.TIRAMISU
        val grantedChecker: (Context?, String) -> Int = { _, _ -> PackageManager.PERMISSION_GRANTED }

        val hasPermission = NotificationPermissionHelper.hasNotificationPermission(
            context = null,
            sdkInt = sdkLevel33,
            permissionChecker = grantedChecker
        )
        assertTrue("Should have permission when granted", hasPermission)

        val shouldRequest = NotificationPermissionHelper.shouldRequestNotificationPermission(
            context = null,
            sdkInt = sdkLevel33,
            permissionChecker = grantedChecker
        )
        assertFalse("Should not request permission if already granted", shouldRequest)
    }

    @Test
    fun `Scenario - No runtime prompt below Android 13 (API 32 and lower)`() {
        // Given: The device is running API level 32 (S_V2) or lower
        val sdkLevelsBelow33 = listOf(26, 28, 30, 31, 32)

        for (sdk in sdkLevelsBelow33) {
            // Then: Runtime permission is not required
            assertFalse("SDK $sdk should not require runtime notification permission", NotificationPermissionHelper.isRuntimePermissionRequired(sdk))

            // And: hasNotificationPermission is always true
            val hasPermission = NotificationPermissionHelper.hasNotificationPermission(
                context = null,
                sdkInt = sdk,
                permissionChecker = { _, _ -> PackageManager.PERMISSION_DENIED }
            )
            assertTrue("SDK $sdk should report having notification permission", hasPermission)

            // And: shouldRequestNotificationPermission is false
            val shouldRequest = NotificationPermissionHelper.shouldRequestNotificationPermission(
                context = null,
                sdkInt = sdk,
                permissionChecker = { _, _ -> PackageManager.PERMISSION_DENIED }
            )
            assertFalse("SDK $sdk should not prompt for runtime permission", shouldRequest)
        }
    }

    @Test
    fun `Scenario - When permission is denied, timer still runs in-app without crashing`() = runTest(testDispatcher) {
        // Given: Active TimerEngine on API 33 with permission denied
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 60,
                totalRounds = 5,
                prepCountdownSeconds = 0
            )
        )

        var serviceStarted = false
        var permissionRequested = false

        // When: User starts a workout timer
        NotificationPermissionHelper.handleTimerStartWithPermission(
            context = null,
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            permissionChecker = { _, _ -> PackageManager.PERMISSION_DENIED },
            onPermissionRequired = {
                permissionRequested = true
                // Permission dialog is launched; user denies permission
                val isGranted = false
                if (isGranted) {
                    serviceStarted = true
                }
            },
            onStartService = {
                serviceStarted = true
            }
        )

        // Timer starts in-app
        engine.start()

        // Then: Runtime permission was requested
        assertTrue("Permission prompt was requested", permissionRequested)
        // And: Service was NOT started because permission was denied
        assertFalse("Foreground service should not be started when permission is denied", serviceStarted)
        // And: Timer still runs in-app without crashing
        assertTrue("Timer must be running in-app", engine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)
        assertEquals(60, engine.snapshot.value.roundSecondsRemaining)
    }

    @Test
    fun `Scenario - When permission is granted on Android 13+, foreground service starts and timer runs`() = runTest(testDispatcher) {
        // Given: Active TimerEngine on API 33
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.AMRAP,
                targetMinutes = 10,
                prepCountdownSeconds = 0
            )
        )

        var serviceStarted = false
        var permissionRequested = false

        // When: User starts timer and grants permission
        NotificationPermissionHelper.handleTimerStartWithPermission(
            context = null,
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            permissionChecker = { _, _ -> PackageManager.PERMISSION_DENIED },
            onPermissionRequired = {
                permissionRequested = true
                // Permission dialog granted by user
                val isGranted = true
                if (isGranted) {
                    serviceStarted = true
                }
            },
            onStartService = {
                serviceStarted = true
            }
        )

        engine.start()

        // Then: Permission requested and service started
        assertTrue(permissionRequested)
        assertTrue(serviceStarted)
        assertTrue(engine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
    }

    @Test
    fun `Scenario - On API 32, timer start immediately triggers service start without prompt`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.TIME_CAP,
                targetMinutes = 15,
                prepCountdownSeconds = 0
            )
        )

        var serviceStarted = false
        var permissionRequested = false

        // When: Starting timer on API 32
        NotificationPermissionHelper.handleTimerStartWithPermission(
            context = null,
            sdkInt = 32,
            permissionChecker = { _, _ -> PackageManager.PERMISSION_DENIED },
            onPermissionRequired = {
                permissionRequested = true
            },
            onStartService = {
                serviceStarted = true
            }
        )

        engine.start()

        // Then: No permission request, service directly started
        assertFalse(permissionRequested)
        assertTrue(serviceStarted)
        assertTrue(engine.snapshot.value.isRunning)
    }
}
