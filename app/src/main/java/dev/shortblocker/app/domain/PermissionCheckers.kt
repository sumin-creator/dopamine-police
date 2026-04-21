package dev.shortblocker.app.domain

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.shortblocker.app.data.PermissionSnapshot

fun Context.buildPermissionSnapshot(serviceClass: Class<out AccessibilityService>): PermissionSnapshot {
    return PermissionSnapshot(
        accessibility = isAccessibilityServiceEnabled(serviceClass),
        usageStats = hasUsageStatsAccess(),
        notifications = hasNotificationAccess(),
    )
}

fun Context.hasNotificationAccess(): Boolean {
    val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return enabled
    }
    return enabled && ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.hasUsageStatsAccess(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun Context.isAccessibilityServiceEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
    val enabled = Settings.Secure.getInt(
        contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0,
    ) == 1
    if (!enabled) {
        return false
    }
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val expected = "$packageName/${serviceClass.name}"
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
