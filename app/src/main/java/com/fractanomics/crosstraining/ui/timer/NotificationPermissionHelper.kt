package com.fractanomics.crosstraining.ui.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility helper for managing notification and foreground service permissions,
 * including runtime permission requests on Android 13+ (API level 33+ / TIRAMISU).
 */
object NotificationPermissionHelper {

    const val POST_NOTIFICATIONS: String = Manifest.permission.POST_NOTIFICATIONS
    const val FOREGROUND_SERVICE: String = Manifest.permission.FOREGROUND_SERVICE
    const val FOREGROUND_SERVICE_MEDIA_PLAYBACK: String = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK

    val REQUIRED_MANIFEST_PERMISSIONS: List<String> = listOf(
        POST_NOTIFICATIONS,
        FOREGROUND_SERVICE,
        FOREGROUND_SERVICE_MEDIA_PLAYBACK
    )

    /**
     * Returns true if the device's Android version requires runtime permission for notifications (API 33+).
     */
    fun isRuntimePermissionRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Checks if the app currently has permission to post notifications.
     * On API < 33, runtime notification permission is not required and always returns true.
     * On API >= 33, queries [ContextCompat.checkSelfPermission] for [POST_NOTIFICATIONS].
     */
    fun hasNotificationPermission(
        context: Context?,
        sdkInt: Int = Build.VERSION.SDK_INT,
        permissionChecker: (Context?, String) -> Int = { ctx, perm ->
            if (ctx == null) PackageManager.PERMISSION_DENIED
            else ContextCompat.checkSelfPermission(ctx, perm)
        }
    ): Boolean {
        if (!isRuntimePermissionRequired(sdkInt)) {
            return true
        }
        return permissionChecker(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if the app should prompt the user for runtime notification permission before starting notifications.
     */
    fun shouldRequestNotificationPermission(
        context: Context?,
        sdkInt: Int = Build.VERSION.SDK_INT,
        permissionChecker: (Context?, String) -> Int = { ctx, perm ->
            if (ctx == null) PackageManager.PERMISSION_DENIED
            else ContextCompat.checkSelfPermission(ctx, perm)
        }
    ): Boolean {
        return isRuntimePermissionRequired(sdkInt) && !hasNotificationPermission(context, sdkInt, permissionChecker)
    }

    /**
     * Orchestrates starting a workout timer and foreground notification flow with permission gating.
     *
     * @param context Android context
     * @param onPermissionRequired Callback invoked when runtime permission needs to be requested from the user
     * @param onStartService Callback invoked when permission is already present and foreground service can start immediately
     */
    fun handleTimerStartWithPermission(
        context: Context?,
        onPermissionRequired: () -> Unit,
        onStartService: () -> Unit,
        sdkInt: Int = Build.VERSION_CODES.TIRAMISU,
        permissionChecker: (Context?, String) -> Int = { ctx, perm ->
            if (ctx == null) PackageManager.PERMISSION_DENIED
            else ContextCompat.checkSelfPermission(ctx, perm)
        }
    ) {
        if (shouldRequestNotificationPermission(context, sdkInt, permissionChecker)) {
            onPermissionRequired()
        } else {
            onStartService()
        }
    }
}
