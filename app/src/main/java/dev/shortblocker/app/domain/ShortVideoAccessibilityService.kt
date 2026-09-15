package dev.shortblocker.app.domain

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.shortblocker.app.ShortblockerApplication
import dev.shortblocker.app.data.AppState
import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ShortVideoAccessibilityService : AccessibilityService() {
    private val application by lazy { applicationContext as ShortblockerApplication }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detectionTimingGate = DetectionTimingGate(repeatAfterTrigger = true)
    private var shortsWatchPackageName: String? = null
    private var lastShortsWatchSampleElapsedRealtime: Long? = null
    private var pendingShortsWatchMillis: Long = 0L

    // 監視タイマー用のJobを保持
    private var monitorJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            application.container.store.updatePermissions(
                application.buildPermissionSnapshot(ShortVideoAccessibilityService::class.java),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        val currentPackageName = safeEvent.packageName?.toString() ?: return

        // 自身からのイベントは無視
        if (currentPackageName == packageName) return

        // システムUIやキーボードからのイベントを完全に無視する
        if (currentPackageName == "com.android.systemui" ||
            currentPackageName.contains("inputmethod")) {
            return
        }

        val store = application.container.store
        val state = store.state.value
        val mediaPlaybackActive = if (isDetectionTimingTarget(currentPackageName)) {
            application.container.mediaPlaybackObserver.isPlaybackActive(currentPackageName)
        } else {
            null
        }

        // 既存のイベント駆動の処理
        val decision = application.container.detector.processEvent(
            event = safeEvent,
            settings = state.settings,
            permissions = state.permissions,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
            mediaPlaybackActive = mediaPlaybackActive,
        )

        // 対象アプリならタイマーを開始、それ以外なら停止
        val targetPackageName = safeEvent.packageName?.toString()
        if (isDetectionTimingTarget(targetPackageName)) {
            startMonitoringTimer()
        } else {
            pauseMonitoringTimer()
        }

        if (decision != null) {
            handleDecision(
                decision = decision,
                shouldTrigger = shouldTriggerAfterDetectionTiming(
                    decision = decision,
                    state = state,
                    mediaPlaybackActive = mediaPlaybackActive,
                ),
            )
        }
    }

    // 新規追加：タイマー処理
    private fun startMonitoringTimer() {
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(3000L) // 3秒ごとにチェック

                val store = application.container.store
                val state = store.state.value

                val rootNode = rootInActiveWindow
                val activePackage = rootNode?.packageName?.toString().orEmpty()
                if (activePackage.isBlank()) {
                    pauseDetectionTiming(
                        reason = "active-window-unavailable",
                        elapsedRealtime = SystemClock.elapsedRealtime(),
                    )
                    runCatching { rootNode?.recycle() }
                    continue
                }
                if (!isDetectionTimingTarget(activePackage)) {
                    val elapsedRealtime = SystemClock.elapsedRealtime()
                    runCatching { rootNode?.recycle() }
                    pauseMonitoringTimer(
                        reason = "non-target-app pkg=$activePackage",
                        elapsedRealtime = elapsedRealtime,
                    )
                    return@launch
                }
                val mediaPlaybackActive = application.container.mediaPlaybackObserver
                    .isPlaybackActive(activePackage)
                val detector = application.container.detector

                // 1. 視聴中の画面そのものを定期スキャンして検知を更新
                val decision = detector.processActiveWindowSnapshot(
                    packageName = activePackage,
                    rootNode = rootNode,
                    settings = state.settings,
                    permissions = state.permissions,
                    cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
                    mediaPlaybackActive = mediaPlaybackActive,
                ) ?: detector.evaluateCurrentSession(
                    settings = state.settings,
                    permissions = state.permissions,
                    cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
                    mediaPlaybackActive = mediaPlaybackActive,
                )

                if (decision != null) {
                    // 2. 閾値超えの累積時間が設定値を超えた場合のみ介入
                    handleDecision(
                        decision = decision,
                        shouldTrigger = shouldTriggerAfterDetectionTiming(
                            decision = decision,
                            state = state,
                            mediaPlaybackActive = mediaPlaybackActive,
                        ),
                    )
                }
            }
        }
    }

    private fun stopMonitoringTimer() {
        monitorJob?.cancel()
        monitorJob = null
        detectionTimingGate.reset()
        resetShortsWatchTime()
    }

    private fun pauseMonitoringTimer(
        reason: String = "monitor-paused",
        elapsedRealtime: Long = SystemClock.elapsedRealtime(),
    ) {
        monitorJob?.cancel()
        monitorJob = null
        pauseDetectionTiming(reason, elapsedRealtime)
    }

    private fun handleDecision(
        decision: DetectionDecision,
        shouldTrigger: Boolean,
    ) {
        application.container.applicationScope.launch {
            application.container.store.applyEvaluation(
                snapshot = decision.snapshot,
                shouldTrigger = shouldTrigger,
                source = "service"
            )
            if (shouldTrigger) {
                application.container.notificationController.showIntervention(
                    decision.snapshot.toPendingIntervention(source = "service")
                )
            }
        }
    }

    override fun onInterrupt() {
        stopMonitoringTimer()
    }

    override fun onDestroy() {
        stopMonitoringTimer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldTriggerAfterDetectionTiming(
        decision: DetectionDecision,
        state: AppState,
        mediaPlaybackActive: Boolean? = null,
        elapsedRealtime: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        val snapshot = decision.snapshot
        val target = ServiceTarget.fromPackage(snapshot.packageName)
        if (!isDetectionTimingTarget(snapshot.packageName)) {
            pauseDetectionTiming(
                reason = "non-target-decision pkg=${snapshot.packageName}",
                elapsedRealtime = elapsedRealtime,
            )
            return false
        }
        val shouldCount = ShortsViewingPolicy.shouldCount(
            snapshot = snapshot,
            mediaPlaybackActive = mediaPlaybackActive,
        )
        if (!shouldCount) {
            pauseDetectionTiming(
                reason = "shorts-not-countable playback=${playbackLabel(mediaPlaybackActive)}",
                elapsedRealtime = elapsedRealtime,
            )
            return false
        }
        updateShortsWatchTime(snapshot, elapsedRealtime)

        val timingCandidate = decision.snapshot.score >= state.settings.threshold
        val timing = detectionTimingGate.update(
            packageName = snapshot.packageName,
            overThreshold = timingCandidate,
            now = elapsedRealtime,
            requiredMillis = detectionDelayMillis(state),
        )
        logDetectionTiming(
            decision = decision,
            timing = timing,
            mediaPlaybackActive = mediaPlaybackActive,
            timingCandidate = timingCandidate,
            threshold = state.settings.threshold,
        )
        val canIntervene = state.settings.alertsEnabled &&
            state.settings.supportedApps.isEnabled(target) &&
            state.permissions.canIntervene &&
            snapshot.createdAtEpochMillis >= state.cooldownUntilEpochMillis

        return timing.readyToTrigger && decision.triggerCandidate && canIntervene
    }

    private fun detectionDelayMillis(state: AppState): Long {
        return TimeUnit.MINUTES.toMillis(state.settings.cooldownMinutes.coerceAtLeast(1).toLong())
    }

    private fun isDetectionTimingTarget(packageName: String?): Boolean {
        return ServiceTarget.fromPackage(packageName) == ServiceTarget.YOUTUBE
    }

    private fun logDetectionTiming(
        decision: DetectionDecision,
        timing: DetectionTimingResult,
        mediaPlaybackActive: Boolean?,
        timingCandidate: Boolean,
        threshold: Int,
    ) {
        val accumulatedSeconds = timing.accumulatedMillis / 1000.0
        val requiredSeconds = timing.requiredMillis / 1000.0
        Log.d(
            TAG,
            "timing pkg=${decision.snapshot.packageName}" +
                " score=${decision.snapshot.score}" +
                " threshold=$threshold" +
                " candidate=${flag(timingCandidate)}" +
                " triggerable=${flag(decision.triggerCandidate)}" +
                " playback=${playbackLabel(mediaPlaybackActive)}" +
                " accumulated=${"%.1f".format(accumulatedSeconds)}s/${"%.1f".format(requiredSeconds)}s" +
                " ready=${flag(timing.readyToTrigger)}",
        )
    }

    private fun pauseDetectionTiming(reason: String, elapsedRealtime: Long) {
        val timing = detectionTimingGate.pause(elapsedRealtime)
        pauseShortsWatchTime(elapsedRealtime)
        Log.d(
            TAG,
            "timing paused reason=$reason accumulated=${"%.1f".format(timing.accumulatedMillis / 1000.0)}s",
        )
    }

    private fun updateShortsWatchTime(snapshot: DetectionSnapshot, elapsedRealtime: Long) {
        val lastSampleAt = lastShortsWatchSampleElapsedRealtime
        val samePackage = shortsWatchPackageName == snapshot.packageName
        if (lastSampleAt != null && samePackage) {
            recordDetectedShortsTime(elapsedRealtime - lastSampleAt)
        }
        shortsWatchPackageName = snapshot.packageName
        lastShortsWatchSampleElapsedRealtime = elapsedRealtime
    }

    private fun pauseShortsWatchTime(elapsedRealtime: Long) {
        val lastSampleAt = lastShortsWatchSampleElapsedRealtime
        if (lastSampleAt != null) {
            recordDetectedShortsTime(elapsedRealtime - lastSampleAt)
        }
        resetShortsWatchTime()
    }

    private fun resetShortsWatchTime() {
        shortsWatchPackageName = null
        lastShortsWatchSampleElapsedRealtime = null
    }

    private fun recordDetectedShortsTime(addedMillis: Long) {
        pendingShortsWatchMillis += addedMillis.coerceIn(0L, MAX_WATCH_TIME_SAMPLE_GAP_MS)
        val addedSeconds = (pendingShortsWatchMillis / 1000L).toInt()
        if (addedSeconds <= 0) return
        pendingShortsWatchMillis %= 1000L
        application.container.applicationScope.launch {
            application.container.store.addWatchTime(addedSeconds)
        }
    }

    private fun flag(value: Boolean): String = if (value) "Y" else "N"

    private fun playbackLabel(value: Boolean?): String = when (value) {
        true -> "playing"
        false -> "inactive"
        null -> "unknown"
    }

    private companion object {
        const val TAG = "ShortDetectionTiming"
        const val MAX_WATCH_TIME_SAMPLE_GAP_MS = 10_000L
    }
}
