package dev.shortblocker.app.domain

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.shortblocker.app.ShortblockerApplication
import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.PermissionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ShortVideoAccessibilityService : AccessibilityService() {
    private val shortblockerApplication by lazy { applicationContext as ShortblockerApplication }
    private var playbackTickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        shortblockerApplication.container.notificationController.createChannel()
        shortblockerApplication.container.applicationScope.launch {
            shortblockerApplication.container.store.updatePermissions(
                currentPermissions(),
            )
        }
        startPlaybackTicks()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        if (safeEvent.packageName?.toString() == packageName) {
            return
        }

        val store = shortblockerApplication.container.store
        val state = store.state.value
        val permissions = currentPermissions()
        val mediaPlaybackActive = if (safeEvent.packageName?.toString() == ServiceTarget.YOUTUBE.packageName) {
            shortblockerApplication.container.mediaPlaybackObserver.isPlaybackActive(ServiceTarget.YOUTUBE.packageName)
        } else {
            null
        }
        val decision = shortblockerApplication.container.detector.processEvent(
            event = safeEvent,
            settings = state.settings,
            permissions = permissions,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
            mediaPlaybackActive = mediaPlaybackActive,
        ) ?: return

        applyDecisionAsync(
            snapshot = decision.snapshot,
            shouldTrigger = decision.shouldTrigger,
            permissions = permissions,
            source = "service",
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        playbackTickJob?.cancel()
        playbackTickJob = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        playbackTickJob?.cancel()
        playbackTickJob = null
        super.onDestroy()
    }

    private fun startPlaybackTicks() {
        if (playbackTickJob?.isActive == true) {
            return
        }
        playbackTickJob = shortblockerApplication.container.applicationScope.launch {
            while (isActive) {
                delay(PLAYBACK_TICK_INTERVAL_MS)
                runPlaybackTick()
            }
        }
    }

    private fun runPlaybackTick() {
        val store = shortblockerApplication.container.store
        val state = store.state.value
        val permissions = currentPermissions()
        val mediaPlaybackActive = shortblockerApplication.container.mediaPlaybackObserver
            .isPlaybackActive(ServiceTarget.YOUTUBE.packageName)
        val decision = shortblockerApplication.container.detector.processPlaybackTick(
            settings = state.settings,
            permissions = permissions,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
            mediaPlaybackActive = mediaPlaybackActive,
        ) ?: return

        applyDecisionAsync(
            snapshot = decision.snapshot,
            shouldTrigger = decision.shouldTrigger,
            permissions = permissions,
            source = "playback-tick",
        )
    }

    private fun applyDecisionAsync(
        snapshot: DetectionSnapshot,
        shouldTrigger: Boolean,
        permissions: PermissionSnapshot,
        source: String,
    ) {
        val store = shortblockerApplication.container.store
        shortblockerApplication.container.applicationScope.launch {
            if (store.state.value.permissions != permissions) {
                store.updatePermissions(permissions)
            }
            store.applyEvaluation(
                snapshot = snapshot,
                shouldTrigger = shouldTrigger,
                source = source,
            )
            if (shouldTrigger) {
                shortblockerApplication.container.notificationController.showIntervention(
                    snapshot.toPendingIntervention(source = source),
                )
            }
        }
    }

    private fun currentPermissions(): PermissionSnapshot {
        return PermissionSnapshotBuilder.build(
            context = applicationContext,
            serviceClass = ShortVideoAccessibilityService::class.java,
        )
    }

    private companion object {
        const val PLAYBACK_TICK_INTERVAL_MS = 5_000L
    }
}
