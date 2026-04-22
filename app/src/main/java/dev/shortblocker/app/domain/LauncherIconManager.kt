package dev.shortblocker.app.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.shortblocker.app.data.LauncherLogo

object LauncherIconManager {
    private val logoToAliasClassName = mapOf(
        LauncherLogo.POLICE_VIDEO_SCAN to "dev.shortblocker.app.MainActivityPoliceVideoScan",
        LauncherLogo.SIREN_PLAY_BLOCK to "dev.shortblocker.app.MainActivitySirenPlayBlock",
        LauncherLogo.CAMERA_CHECKPOINT to "dev.shortblocker.app.MainActivityCameraCheckpoint",
        LauncherLogo.PATROL_ALERT to "dev.shortblocker.app.MainActivityPatrolAlert",
        LauncherLogo.SHIELD_DETECT to "dev.shortblocker.app.MainActivityShieldDetect",
        LauncherLogo.RADAR_REEL to "dev.shortblocker.app.MainActivityRadarReel",
    )

    fun apply(context: Context, logo: LauncherLogo) {
        val packageManager = context.packageManager
        val selectedAlias = logoToAliasClassName[logo] ?: return

        logoToAliasClassName.values.forEach { aliasClassName ->
            val state = if (aliasClassName == selectedAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, aliasClassName),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
