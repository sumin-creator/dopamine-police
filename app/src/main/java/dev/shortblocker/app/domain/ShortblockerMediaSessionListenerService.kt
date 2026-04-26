package dev.shortblocker.app.domain

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.shortblocker.app.ShortblockerApplication
import kotlinx.coroutines.launch

class ShortblockerMediaSessionListenerService : NotificationListenerService() {
    private val application by lazy { applicationContext as ShortblockerApplication }
    private var mediaSessionManager: MediaSessionManager? = null
    private var componentName: ComponentName? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        componentName = ComponentName(this, ShortblockerMediaSessionListenerService::class.java)
    }

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

    /**
     * 指定したパッケージのメディアが現在「再生中」かどうかを判定する
     */
    fun isPlaybackActive(targetPackageName: String): Boolean {
        if (mediaSessionManager == null || componentName == null) return false

        return try {
            // NotificationListenerの権限を使ってアクティブなメディアセッションを取得
            val controllers = mediaSessionManager?.getActiveSessions(componentName) ?: return false

            val targetController = controllers.firstOrNull { it.packageName == targetPackageName }
            val playbackState = targetController?.playbackState

            // 状態が PLAYING または FAST_FORWARD などの動的な状態であるか確認
            playbackState?.state == PlaybackState.STATE_PLAYING ||
                    playbackState?.state == PlaybackState.STATE_BUFFERING
        } catch (e: SecurityException) {
            false
        }
    }

    private fun syncPermissions() {
        application.container.applicationScope.launch {
            application.container.store.updatePermissions(
                application.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java),
            )
        }
    }
}