package dev.shortblocker.app.domain

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.shortblocker.app.ShortblockerApplication
import kotlinx.coroutines.launch

class ShortVideoAccessibilityService : AccessibilityService() {
    private val application by lazy { applicationContext as ShortblockerApplication }

    override fun onServiceConnected() {
        super.onServiceConnected()
        application.container.notificationController.createChannel()
        application.container.applicationScope.launch {
            application.container.store.updatePermissions(
                application.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        if (safeEvent.packageName?.toString() == packageName) {
            return
        }

        val store = application.container.store
        val state = store.state.value
        val permissions = application.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java)
        val decision = application.container.detector.processEvent(
            event = safeEvent,
            settings = state.settings,
            permissions = permissions,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
        ) ?: return

        application.container.applicationScope.launch {
            if (state.permissions != permissions) {
                store.updatePermissions(permissions)
            }
            store.applyEvaluation(
                snapshot = decision.snapshot,
                shouldTrigger = decision.shouldTrigger,
                source = "service",
            )
            if (decision.shouldTrigger) {
                application.container.notificationController.showIntervention(
                    decision.snapshot.toPendingIntervention(source = "service"),
                )
            }
        }
    }

    override fun onInterrupt() = Unit
}
