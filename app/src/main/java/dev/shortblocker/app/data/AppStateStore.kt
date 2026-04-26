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
            normalizePersistedSettings()
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
                pendingIntervention = if (shouldTrigger) {
                    snapshot.toPendingIntervention(source)
                } else {
                    current.pendingIntervention
                },
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

    private suspend fun normalizePersistedSettings() {
        context.shortblockerDataStore.updateDataCompat { current ->
            val filteredLogs = current.sessionLogs.filterNot { it.source == "seed" }
            if (current.settings.threshold == normalizedThreshold && filteredLogs.size == current.sessionLogs.size) {
                return@updateDataCompat current
            }
            val updated = current.copy(
                settings = current.settings.copy(threshold = normalizedThreshold),
                sessionLogs = filteredLogs,
            )
            updated.copy(
                liveMonitor = updated.liveMonitor.copy(statusLabel = deriveStatus(updated)),
            )
        }
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
}
