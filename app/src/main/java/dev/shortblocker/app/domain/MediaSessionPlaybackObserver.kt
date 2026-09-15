package dev.shortblocker.app.domain

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

class MediaSessionPlaybackObserver(
    private val context: Context,
) {
    private val mediaSessionManager by lazy {
        context.getSystemService(MediaSessionManager::class.java)
    }

    fun isPlaybackActive(packageName: String): Boolean? {
        if (!context.hasNotificationListenerAccess(ShortblockerMediaSessionListenerService::class.java)) {
            return null
        }
        val manager = mediaSessionManager ?: return null
        val listenerComponent = ComponentName(context, ShortblockerMediaSessionListenerService::class.java)
        val activeSessions = runCatching { manager.getActiveSessions(listenerComponent) }
            .getOrNull() ?: return null
        val matchingSessions = activeSessions.filter { it.packageName == packageName }
        if (matchingSessions.isEmpty()) {
            return null
        }
        val observedStates = matchingSessions.mapNotNull { controller ->
            controller.activePlaybackState()
        }
        if (observedStates.isEmpty()) {
            return null
        }
        return observedStates.any { it }
    }

    private fun MediaController.activePlaybackState(): Boolean? {
        val state = playbackState?.state ?: return null
        return when (state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
            -> true

            else -> false
        }
    }
}
