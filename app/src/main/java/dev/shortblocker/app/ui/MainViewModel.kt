package dev.shortblocker.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shortblocker.app.data.AppState
import dev.shortblocker.app.data.AppStateStore
import dev.shortblocker.app.data.DemoPreset
import dev.shortblocker.app.data.MonitorSettings
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.UiFeature
import dev.shortblocker.app.data.UserAction
import dev.shortblocker.app.domain.DetectionDecision
import dev.shortblocker.app.domain.ShortVideoAccessibilityService
import dev.shortblocker.app.domain.ShortVideoDetector
import dev.shortblocker.app.domain.ShortblockerNotificationController
import dev.shortblocker.app.domain.UsageStatsHelper
import dev.shortblocker.app.domain.buildPermissionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

class MainViewModel(
    private val store: AppStateStore,
    private val notificationController: ShortblockerNotificationController,
) : ViewModel() {
    private val demoDetector = ShortVideoDetector()
    private val selectedPreset = MutableStateFlow(DemoPreset.YOUTUBE)
    private val demoScenario = MutableStateFlow(DemoPreset.YOUTUBE.scenario)

    val appState: StateFlow<AppState> = store.state

    val activePreset: StateFlow<DemoPreset> = selectedPreset

    val demoDecision: StateFlow<DetectionDecision> = combine(appState, demoScenario) { state, scenario ->
        demoDetector.evaluateScenario(
            scenario = scenario,
            settings = state.settings,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
            requirePermissions = false,
            permissions = state.permissions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = demoDetector.evaluateScenario(
            scenario = DemoPreset.YOUTUBE.scenario,
            settings = AppState().settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = false,
            permissions = AppState().permissions,
        ),
    )

    fun refreshRuntimeState(context: Context) {
        viewModelScope.launch {
            store.updatePermissions(
                context.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java),
            )
            val foreground = UsageStatsHelper(context).queryForegroundApp()
            if (foreground != null) {
                store.updateForegroundApp(
                    appName = foreground.appName,
                    packageName = foreground.packageName,
                )
            }
        }
    }

    fun setPreset(preset: DemoPreset) {
        selectedPreset.value = preset
        demoScenario.value = preset.scenario
    }

    fun advanceSwipe() {
        demoScenario.update { current ->
            current.copy(
                sessionMinutes = min(30, current.sessionMinutes + 1),
                swipeBurst = min(6, current.swipeBurst + 1),
                dwellSeconds = min(45, current.dwellSeconds + 6),
                reentryAfterWarning = current.reentryAfterWarning || appState.value.pendingIntervention != null,
                uiFeatures = (current.uiFeatures + UiFeature.CONTINUOUS_TRANSITIONS).distinct(),
            )
        }
    }

    fun triggerDemo() {
        viewModelScope.launch {
            val decision = demoDecision.value
            store.applyEvaluation(
                snapshot = decision.snapshot,
                shouldTrigger = decision.shouldTrigger,
                source = "demo",
            )
        }
    }

    fun applyUserAction(action: UserAction) {
        viewModelScope.launch {
            store.applyUserAction(action, source = "app")
            notificationController.dismissIntervention()
        }
    }

    fun dismissIntervention() {
        viewModelScope.launch {
            store.clearPendingIntervention()
            notificationController.dismissIntervention()
        }
    }

    fun updateThreshold(value: Int) {
        updateSettings { copy(threshold = value) }
    }

    fun updateCooldownMinutes(value: Int) {
        updateSettings { copy(cooldownMinutes = value) }
    }

    fun updateDailyGoalMinutes(value: Int) {
        updateSettings { copy(dailyGoalMinutes = value) }
    }

    fun toggleAlerts() {
        updateSettings { copy(alertsEnabled = !alertsEnabled) }
    }

    fun toggleSupportedService(target: ServiceTarget) {
        updateSettings { copy(supportedApps = supportedApps.toggle(target)) }
    }

    fun resetCooldown() {
        viewModelScope.launch {
            store.resetCooldown()
        }
    }

    private fun updateSettings(transform: MonitorSettings.() -> MonitorSettings) {
        viewModelScope.launch {
            store.updateSettings { current -> current.transform() }
        }
    }

    companion object {
        fun factory(
            store: AppStateStore,
            notificationController: ShortblockerNotificationController,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(store, notificationController) as T
                }
            }
        }
    }
}
