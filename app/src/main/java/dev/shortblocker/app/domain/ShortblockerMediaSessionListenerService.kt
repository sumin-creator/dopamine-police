package dev.shortblocker.app.domain

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.shortblocker.app.ShortblockerApplication
import kotlinx.coroutines.launch

class ShortblockerMediaSessionListenerService : NotificationListenerService() {
    private val application by lazy { applicationContext as ShortblockerApplication }

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncPermissions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        syncPermissions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private fun syncPermissions() {
        application.container.applicationScope.launch {
            application.container.store.updatePermissions(
                application.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java),
            )
        }
    }
}
