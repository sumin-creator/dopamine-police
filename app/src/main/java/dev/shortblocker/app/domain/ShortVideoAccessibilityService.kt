package dev.shortblocker.app.domain

import android.accessibilityservice.AccessibilityService
import android.media.session.PlaybackState
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.shortblocker.app.ShortblockerApplication
import dev.shortblocker.app.data.AppState
import dev.shortblocker.app.data.ServiceTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ShortVideoAccessibilityService : AccessibilityService() {
    private val application by lazy { applicationContext as ShortblockerApplication }
    private val detectionTimingGate = DetectionTimingGate()

    // 監視タイマー用のJobを保持
    private var monitorJob: Job? = null

    // ... (既存の onServiceConnected などはそのまま) ...

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

        // 既存のイベント駆動の処理
        val decision = application.container.detector.processEvent(
            event = safeEvent,
            settings = state.settings,
            permissions = state.permissions,
            cooldownUntilEpochMillis = state.cooldownUntilEpochMillis
        )

        // 対象アプリならタイマーを開始、それ以外なら停止
        val targetPackageName = safeEvent.packageName?.toString()
        if (isDetectionTimingTarget(targetPackageName)) {
            startMonitoringTimer()
        } else {
            pauseMonitoringTimer(now = System.currentTimeMillis())
        }

        if (decision != null) {
            handleDecision(
                decision = decision,
                shouldTrigger = shouldTriggerAfterDetectionTiming(
                    decision = decision,
                    state = state,
                ),
            )
        }
    }

    // 新規追加：タイマー処理
    private fun startMonitoringTimer() {
        if (monitorJob?.isActive == true) return

        monitorJob = application.container.applicationScope.launch {
            while (isActive) {
                delay(3000L) // 3秒ごとにチェック

                val store = application.container.store
                val state = store.state.value

                val rootNode = rootInActiveWindow
                val activePackage = rootNode?.packageName?.toString().orEmpty()
                if (activePackage.isBlank()) {
                    logDetectionTimingSkipped("active-window-unavailable")
                    runCatching { rootNode?.recycle() }
                    continue
                }
                if (!isDetectionTimingTarget(activePackage)) {
                    val now = System.currentTimeMillis()
                    pauseDetectionTiming("non-target-app pkg=$activePackage", now)
                    runCatching { rootNode?.recycle() }
                    pauseMonitoringTimer(now)
                    return@launch
                }
                val isPlaying = checkPlaybackActive(activePackage)
                val detector = application.container.detector

                // 1. 視聴中の画面そのものを定期スキャンして検知を更新
                val decision = detector.processActiveWindowSnapshot(
                    packageName = activePackage,
                    rootNode = rootNode,
                    settings = state.settings,
                    permissions = state.permissions,
                    cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
                    mediaPlaybackActive = isPlaying,
                ) ?: detector.evaluateCurrentSession(
                    settings = state.settings,
                    permissions = state.permissions,
                    cooldownUntilEpochMillis = state.cooldownUntilEpochMillis,
                    mediaPlaybackActive = isPlaying,
                )

                if (decision != null) {
                    // 2. 閾値超えの累積時間が設定値を超えた場合のみ介入
                    handleDecision(
                        decision = decision,
                        shouldTrigger = shouldTriggerAfterDetectionTiming(
                            decision = decision,
                            state = state,
                            isPlaying = isPlaying,
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
    }

    private fun pauseMonitoringTimer(now: Long = System.currentTimeMillis()) {
        monitorJob?.cancel()
        monitorJob = null
        pauseDetectionTiming("monitor-paused", now)
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

    private fun shouldTriggerAfterDetectionTiming(
        decision: DetectionDecision,
        state: AppState,
        isPlaying: Boolean = true,
    ): Boolean {
        val snapshot = decision.snapshot
        if (!isDetectionTimingTarget(snapshot.packageName)) {
            pauseDetectionTiming("non-target-decision pkg=${snapshot.packageName}", snapshot.createdAtEpochMillis)
            return false
        }
        val timingCandidate = decision.snapshot.score >= state.settings.threshold
        val timing = detectionTimingGate.update(
            packageName = snapshot.packageName,
            overThreshold = timingCandidate,
            now = snapshot.createdAtEpochMillis,
            requiredMillis = detectionDelayMillis(state),
        )
        recordDetectedShortsTime(timing.addedMillis)
        logDetectionTiming(
            decision = decision,
            timing = timing,
            isPlaying = isPlaying,
            timingCandidate = timingCandidate,
            threshold = state.settings.threshold,
        )
        return timing.readyToTrigger &&
            decision.triggerCandidate &&
            state.pendingIntervention == null
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
        isPlaying: Boolean,
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
                " playing=${flag(isPlaying)}" +
                " accumulated=${"%.1f".format(accumulatedSeconds)}s/${"%.1f".format(requiredSeconds)}s" +
                " ready=${flag(timing.readyToTrigger)}",
        )
    }

    private fun pauseDetectionTiming(reason: String, now: Long) {
        val timing = detectionTimingGate.pause(now)
        recordDetectedShortsTime(timing.addedMillis)
        Log.d(
            TAG,
            "timing paused reason=$reason accumulated=${"%.1f".format(timing.accumulatedMillis / 1000.0)}s",
        )
    }

    private fun recordDetectedShortsTime(addedMillis: Long) {
        val addedSeconds = (addedMillis / 1000L).toInt()
        if (addedSeconds <= 0) return
        application.container.applicationScope.launch {
            application.container.store.addWatchTime(addedSeconds)
        }
    }

    private fun logDetectionTimingSkipped(reason: String) {
        Log.d(TAG, "timing skip reason=$reason")
    }

    private fun flag(value: Boolean): String = if (value) "Y" else "N"

    private fun checkPlaybackActive(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val mediaSessionManager = getSystemService(android.media.session.MediaSessionManager::class.java)
            // すでに実装されている NotificationListenerService のコンポーネント名を指定
            val componentName = android.content.ComponentName(this, ShortblockerMediaSessionListenerService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)

            val targetController = controllers.firstOrNull { it.packageName == packageName }
            val state = targetController?.playbackState?.state

            // 再生中またはバッファリング中であれば true
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        } catch (e: SecurityException) {
            // 権限がない場合は安全のため false を返す
            false
        }
    }

    private companion object {
        const val TAG = "ShortDetectionTiming"
    }
}
