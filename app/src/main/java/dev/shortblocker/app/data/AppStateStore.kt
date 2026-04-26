package dev.shortblocker.app.data

import android.content.Context
import androidx.datastore.dataStore
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val Context.shortblockerDataStore: DataStore<AppState> by dataStore(
    fileName = "shortblocker_state.json",
    serializer = AppStateSerializer,
)

class AppStateStore(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val normalizedThreshold = MonitorSettings().threshold

    val state: StateFlow<AppState> = context.shortblockerDataStore.data.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppState(),
    )

    init {
        scope.launch {
            normalizePersistedState()
        }
    }

    suspend fun updateSettings(transform: (MonitorSettings) -> MonitorSettings) {
        context.shortblockerDataStore.updateDataCompat { current ->
            val next = current.copy(settings = transform(current.settings))
            next.copy(liveMonitor = next.liveMonitor.copy(statusLabel = deriveStatus(next)))
        }
    }

    suspend fun updatePermissions(snapshot: PermissionSnapshot) {
        context.shortblockerDataStore.updateDataCompat { current ->
            val next = current.copy(permissions = snapshot)
            next.copy(liveMonitor = next.liveMonitor.copy(statusLabel = deriveStatus(next)))
        }
    }

    suspend fun updateForegroundApp(appName: String, packageName: String) {
        context.shortblockerDataStore.updateDataCompat { current ->
            current.copy(
                foregroundAppName = appName,
                foregroundPackageName = packageName,
            )
        }
    }

    suspend fun applyEvaluation(
        snapshot: DetectionSnapshot,
        shouldTrigger: Boolean,
        source: String,
    ) {
        context.shortblockerDataStore.updateDataCompat { current ->
            val status = deriveStatus(
                state = current,
                packageName = snapshot.packageName,
                score = snapshot.score,
                now = snapshot.createdAtEpochMillis,
            )
            current.copy(
                liveMonitor = current.liveMonitor.applySnapshot(snapshot, status),
                pendingIntervention = null,
                characterState = if (shouldTrigger) {
                    current.characterState.reactToTrigger(snapshot.warningLevel)
                } else {
                    current.characterState
                },
            )
        }
    }

    suspend fun clearPendingIntervention() {
        context.shortblockerDataStore.updateDataCompat { current ->
            current.copy(pendingIntervention = null)
        }
    }

    suspend fun addWatchTime(seconds: Int) {
        context.shortblockerDataStore.updateDataCompat { current ->
            val currentDays = currentEpochDays()
            val baseHistory = if (current.dailyShortsWatchSeconds > 0L) {
                mergeShortsWatchHistory(
                    history = current.shortsWatchHistory,
                    epochDays = current.lastResetDateEpochDays,
                    seconds = current.dailyShortsWatchSeconds,
                )
            } else {
                current.shortsWatchHistory
            }
            val nextDailySeconds = if (current.lastResetDateEpochDays != currentDays) {
                seconds.toLong()
            } else {
                current.dailyShortsWatchSeconds + seconds
            }

            if (current.lastResetDateEpochDays != currentDays) {
                // 日付が変わっていれば0にリセットしてから加算
                current.copy(
                    dailyShortsWatchSeconds = nextDailySeconds,
                    lastResetDateEpochDays = currentDays,
                    shortsWatchHistory = mergeShortsWatchHistory(
                        history = baseHistory,
                        epochDays = currentDays,
                        seconds = nextDailySeconds,
                    ),
                )
            } else {
                // 同じ日であれば純粋に秒数を加算
                current.copy(
                    dailyShortsWatchSeconds = nextDailySeconds,
                    shortsWatchHistory = mergeShortsWatchHistory(
                        history = baseHistory,
                        epochDays = currentDays,
                        seconds = nextDailySeconds,
                    ),
                )
            }
        }
    }

    suspend fun applyUserAction(action: UserAction, source: String) {
        context.shortblockerDataStore.updateDataCompat { current ->
            val pending = current.pendingIntervention ?: return@updateDataCompat current
            val log = buildSessionLog(
                pending = pending,
                liveMonitor = current.liveMonitor,
                action = action,
                source = source,
            )
            val cooldownUntil = when (action) {
                UserAction.STOP -> 0L
                UserAction.EXTEND -> System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)
                UserAction.IGNORE -> System.currentTimeMillis() +
                    TimeUnit.MINUTES.toMillis(current.settings.cooldownMinutes.toLong())
            }
            val nextLiveMonitor = when (action) {
                UserAction.STOP -> current.liveMonitor.copy(
                    currentScore = 0,
                    warningLevel = WarningLevel.WATCH,
                    statusLabel = "監視中",
                    currentDialogue = "今止まるなら、まだ助かる。今日はここで切り上げよう。",
                )

                UserAction.EXTEND -> current.liveMonitor.copy(
                    statusLabel = "クールダウン中 (1分)",
                    currentDialogue = "あと1分だけ。次は本当に止まる前提で待ってるよ。",
                )

                UserAction.IGNORE -> current.liveMonitor.copy(
                    statusLabel = "クールダウン中 (${current.settings.cooldownMinutes}分)",
                    currentDialogue = "無視されたので少し引く。でも次はもう少し強めに止める。",
                )
            }

            current.copy(
                characterState = current.characterState.reactToAction(action, pending.warningLevel),
                liveMonitor = nextLiveMonitor,
                pendingIntervention = null,
                cooldownUntilEpochMillis = cooldownUntil,
                sessionLogs = listOf(log) + current.sessionLogs.take(39),
            )
        }
    }

    suspend fun resetCooldown() {
        context.shortblockerDataStore.updateDataCompat { current ->
            current.copy(
                cooldownUntilEpochMillis = 0L,
                liveMonitor = current.liveMonitor.copy(statusLabel = deriveStatus(current.copy(cooldownUntilEpochMillis = 0L))),
            )
        }
    }

    private suspend fun DataStore<AppState>.updateDataCompat(transform: suspend (AppState) -> AppState) {
        withContext(Dispatchers.IO) {
            updateData(transform)
        }
    }

    private suspend fun normalizePersistedState() {
        context.shortblockerDataStore.updateDataCompat { current ->
            val filteredSessionLogs = current.sessionLogs.filterNot { it.source == "seed" }
            val currentDays = currentEpochDays()
            val shouldResetDailyWatchTime = current.lastResetDateEpochDays != currentDays
            val normalizedShortsWatchHistory = normalizeShortsWatchHistory(
                state = current,
                currentDays = currentDays,
                resetDailyWatchTime = shouldResetDailyWatchTime,
            )
            if (
                current.settings.threshold == normalizedThreshold &&
                filteredSessionLogs.size == current.sessionLogs.size &&
                !shouldResetDailyWatchTime &&
                normalizedShortsWatchHistory == current.shortsWatchHistory
            ) {
                return@updateDataCompat current
            }
            val updated = current.copy(
                settings = current.settings.copy(threshold = normalizedThreshold),
                sessionLogs = filteredSessionLogs,
                pendingIntervention = null,
                dailyShortsWatchSeconds = if (shouldResetDailyWatchTime) 0L else current.dailyShortsWatchSeconds,
                lastResetDateEpochDays = if (shouldResetDailyWatchTime) currentDays else current.lastResetDateEpochDays,
                shortsWatchHistory = normalizedShortsWatchHistory,
            )
            updated.copy(
                liveMonitor = updated.liveMonitor.copy(statusLabel = deriveStatus(updated)),
            )
        }
    }

    private fun currentEpochDays(): Long = LocalDate.now().toEpochDay()

    private fun normalizeShortsWatchHistory(
        state: AppState,
        currentDays: Long,
        resetDailyWatchTime: Boolean,
    ): List<DailyShortsWatchTime> {
        val historyWithLastDailyValue = if (state.dailyShortsWatchSeconds > 0L) {
            mergeShortsWatchHistory(
                history = state.shortsWatchHistory,
                epochDays = state.lastResetDateEpochDays,
                seconds = state.dailyShortsWatchSeconds,
            )
        } else {
            state.shortsWatchHistory
        }
        val historyWithCurrentDay = if (!resetDailyWatchTime && state.dailyShortsWatchSeconds > 0L) {
            mergeShortsWatchHistory(
                history = historyWithLastDailyValue,
                epochDays = currentDays,
                seconds = state.dailyShortsWatchSeconds,
            )
        } else {
            historyWithLastDailyValue
        }
        return pruneShortsWatchHistory(historyWithCurrentDay, currentDays)
    }

    private fun mergeShortsWatchHistory(
        history: List<DailyShortsWatchTime>,
        epochDays: Long,
        seconds: Long,
    ): List<DailyShortsWatchTime> {
        if (seconds <= 0L) {
            return history
        }
        return pruneShortsWatchHistory(
            history = history
                .filterNot { it.epochDays == epochDays }
                .plus(DailyShortsWatchTime(epochDays = epochDays, seconds = seconds))
                .sortedBy { it.epochDays },
            currentDays = currentEpochDays(),
        )
    }

    private fun pruneShortsWatchHistory(
        history: List<DailyShortsWatchTime>,
        currentDays: Long,
    ): List<DailyShortsWatchTime> {
        val oldestKeptDay = currentDays - SHORTS_WATCH_HISTORY_DAYS + 1
        return history
            .filter { it.seconds > 0L && it.epochDays >= oldestKeptDay && it.epochDays <= currentDays }
            .distinctBy { it.epochDays }
            .sortedBy { it.epochDays }
    }

    private fun deriveStatus(
        state: AppState,
        packageName: String = state.liveMonitor.currentPackageName,
        score: Int = state.liveMonitor.currentScore,
        now: Long = System.currentTimeMillis(),
    ): String {
        if (!state.settings.alertsEnabled) {
            return "監視停止"
        }
        if (!state.permissions.canDetect) {
            return "権限待ち"
        }
        val target = ServiceTarget.fromPackage(packageName)
        if (target != null && !state.settings.supportedApps.isEnabled(target)) {
            return "対象外アプリ"
        }
        if (state.cooldownUntilEpochMillis > now) {
            val remaining = ((state.cooldownUntilEpochMillis - now) / 60_000L).toInt().coerceAtLeast(1)
            return "クールダウン中 (${remaining}分)"
        }
        return if (score >= state.settings.threshold) {
            "介入候補"
        } else {
            "監視中"
        }
    }

    private companion object {
        const val SHORTS_WATCH_HISTORY_DAYS = 30L
    }
}
