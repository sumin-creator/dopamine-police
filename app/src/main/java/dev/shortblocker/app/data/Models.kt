package dev.shortblocker.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class ServiceTarget(
    val appName: String,
    val label: String,
    val packageName: String,
    val keywords: List<String>,
) {
    YOUTUBE(
        appName = "YouTube",
        label = "YouTube Shorts",
        packageName = "com.google.android.youtube",
        keywords = listOf("shorts", "ショート"),
    ),
    INSTAGRAM(
        appName = "Instagram",
        label = "Instagram Reels",
        packageName = "com.instagram.android",
        keywords = listOf("reels", "リール"),
    ),
    TIKTOK(
        appName = "TikTok",
        label = "TikTok / For You",
        packageName = "com.zhiliaoapp.musically",
        keywords = listOf("for you", "foryou", "おすすめ"),
    );

    companion object {
        fun fromPackage(packageName: String?): ServiceTarget? =
            entries.firstOrNull { it.packageName == packageName }

        fun fromAppName(appName: String?): ServiceTarget? =
            entries.firstOrNull { it.appName == appName }
    }
}

enum class WarningLevel(val label: String) {
    WATCH("監視"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong");

    companion object {
        fun fromName(name: String?): WarningLevel =
            entries.firstOrNull { it.name == name } ?: WATCH
    }
}

enum class UserAction(val label: String) {
    STOP("今やめる"),
    EXTEND("あと1分だけ"),
    IGNORE("無視する");

    companion object {
        fun fromName(name: String?): UserAction =
            entries.firstOrNull { it.name == name } ?: STOP
    }
}

enum class TimeBand(val label: String) {
    FOCUS("07:00-09:00"),
    EVENING("19:00-21:00"),
    LATE_NIGHT("23:00-01:00");

    companion object {
        fun fromName(name: String?): TimeBand =
            entries.firstOrNull { it.name == name } ?: FOCUS

        fun fromHour(hour: Int): TimeBand = when (hour) {
            in 19..22 -> EVENING
            in 23..23, in 0..1 -> LATE_NIGHT
            else -> FOCUS
        }
    }
}

enum class UiFeature(val label: String) {
    FULLSCREEN_VERTICAL("全画面縦動画"),
    ACTION_RAIL("右側アクション列"),
    VIDEO_STRUCTURE("動画視聴画面構造"),
    CONTINUOUS_TRANSITIONS("連続遷移");

    companion object {
        fun fromNames(names: List<String>): List<UiFeature> =
            names.mapNotNull { raw -> entries.firstOrNull { it.name == raw } }
    }
}

enum class LauncherLogo(val label: String) {
    POLICE_VIDEO_SCAN("警察バッジ + 動画スキャン"),
    SIREN_PLAY_BLOCK("サイレン + 再生ブロック"),
    CAMERA_CHECKPOINT("検問カメラ + Shorts"),
    PATROL_ALERT("パトカー警戒 + 縦動画"),
    SHIELD_DETECT("シールド + 検知波形"),
    RADAR_REEL("レーダー + リール停止");

    companion object {
        fun fromName(name: String?): LauncherLogo =
            entries.firstOrNull { it.name == name } ?: POLICE_VIDEO_SCAN
    }
}

data class SupportedApps(
    val youtube: Boolean = true,
    val instagram: Boolean = true,
    val tiktok: Boolean = true,
) {
    fun isEnabled(target: ServiceTarget?): Boolean = when (target) {
        ServiceTarget.YOUTUBE -> youtube
        ServiceTarget.INSTAGRAM -> instagram
        ServiceTarget.TIKTOK -> tiktok
        null -> false
    }

    fun toggle(target: ServiceTarget): SupportedApps = when (target) {
        ServiceTarget.YOUTUBE -> copy(youtube = !youtube)
        ServiceTarget.INSTAGRAM -> copy(instagram = !instagram)
        ServiceTarget.TIKTOK -> copy(tiktok = !tiktok)
    }
}

data class MonitorSettings(
    val threshold: Int = 62,
    val cooldownMinutes: Int = 4,
    val dailyGoalMinutes: Int = 25,
    val alertsEnabled: Boolean = true,
    val supportedApps: SupportedApps = SupportedApps(),
    val launcherLogo: LauncherLogo = LauncherLogo.POLICE_VIDEO_SCAN,
)

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val usageStats: Boolean = false,
    val notifications: Boolean = false,
) {
    val allRequiredGranted: Boolean
        get() = accessibility && usageStats && notifications
}

data class CharacterState(
    val characterId: String = "guardian_teto",
    val mood: String = "watchful",
    val trustLevel: Int = 74,
    val lastDialogueType: String = "medium",
) {
    fun reactToTrigger(level: WarningLevel): CharacterState = copy(
        mood = when (level) {
            WarningLevel.STRONG -> "serious"
            WarningLevel.MEDIUM -> "stern"
            WarningLevel.LIGHT -> "curious"
            WarningLevel.WATCH -> "watchful"
        },
        lastDialogueType = level.name.lowercase(Locale.US),
    )

    fun reactToAction(action: UserAction, level: WarningLevel): CharacterState = when (action) {
        UserAction.STOP -> copy(
            mood = "relieved",
            trustLevel = min(100, trustLevel + 8),
            lastDialogueType = level.name.lowercase(Locale.US),
        )

        UserAction.EXTEND -> copy(
            mood = "watchful",
            trustLevel = max(0, trustLevel - 1),
            lastDialogueType = "extend",
        )

        UserAction.IGNORE -> copy(
            mood = "annoyed",
            trustLevel = max(0, trustLevel - 6),
            lastDialogueType = "ignore",
        )
    }
}

data class DetectionScenario(
    val appName: String,
    val packageName: String,
    val timeBand: TimeBand,
    val sessionMinutes: Int,
    val relaunchCount: Int,
    val swipeBurst: Int,
    val dwellSeconds: Int,
    val reentryAfterWarning: Boolean,
    val keywords: List<String>,
    val uiFeatures: List<UiFeature>,
    val note: String,
)

data class DetectionBreakdown(
    val label: String,
    val value: Int,
    val max: Int,
    val detail: String,
)

data class DetectionSnapshot(
    val appName: String,
    val packageName: String,
    val timeBand: TimeBand,
    val score: Int,
    val warningLevel: WarningLevel,
    val dialogue: String,
    val sessionMinutes: Int,
    val relaunchCount: Int,
    val swipeBurst: Int,
    val dwellSeconds: Int,
    val keywordHits: List<String>,
    val uiFeatures: List<UiFeature>,
    val breakdown: List<DetectionBreakdown>,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toPendingIntervention(source: String): PendingIntervention = PendingIntervention(
        id = UUID.randomUUID().toString(),
        appName = appName,
        packageName = packageName,
        score = score,
        warningLevel = warningLevel,
        dialogue = dialogue,
        timeBand = timeBand,
        source = source,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

data class PendingIntervention(
    val id: String,
    val appName: String,
    val packageName: String,
    val score: Int,
    val warningLevel: WarningLevel,
    val dialogue: String,
    val timeBand: TimeBand,
    val source: String,
    val createdAtEpochMillis: Long,
)

data class LiveMonitorState(
    val currentAppName: String = "待機中",
    val currentPackageName: String = "",
    val currentScore: Int = 0,
    val warningLevel: WarningLevel = WarningLevel.WATCH,
    val statusLabel: String = "権限待ち",
    val currentDialogue: String = "Accessibility Service と Usage Stats を有効化すると監視が始まります。",
    val sessionMinutes: Int = 0,
    val relaunchCount: Int = 0,
    val swipeBurst: Int = 0,
    val dwellSeconds: Int = 0,
    val keywordHits: List<String> = emptyList(),
    val uiFeatures: List<UiFeature> = emptyList(),
    val timeBand: TimeBand = TimeBand.FOCUS,
    val lastUpdatedAtEpochMillis: Long = 0L,
) {
    fun applySnapshot(snapshot: DetectionSnapshot, statusLabel: String): LiveMonitorState = copy(
        currentAppName = snapshot.appName,
        currentPackageName = snapshot.packageName,
        currentScore = snapshot.score,
        warningLevel = snapshot.warningLevel,
        statusLabel = statusLabel,
        currentDialogue = snapshot.dialogue,
        sessionMinutes = snapshot.sessionMinutes,
        relaunchCount = snapshot.relaunchCount,
        swipeBurst = snapshot.swipeBurst,
        dwellSeconds = snapshot.dwellSeconds,
        keywordHits = snapshot.keywordHits,
        uiFeatures = snapshot.uiFeatures,
        timeBand = snapshot.timeBand,
        lastUpdatedAtEpochMillis = snapshot.createdAtEpochMillis,
    )
}

data class SessionLog(
    val id: String,
    val timestampStartEpochMillis: Long,
    val timestampEndEpochMillis: Long,
    val appName: String,
    val uiScore: Int,
    val triggeredWarning: Boolean,
    val warningLevel: WarningLevel,
    val userAction: UserAction,
    val timeBand: TimeBand,
    val savedMinutes: Int,
    val source: String,
) {
    fun formattedRange(zoneId: ZoneId): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        return "${Instant.ofEpochMilli(timestampStartEpochMillis).atZone(zoneId).format(formatter)} - " +
            Instant.ofEpochMilli(timestampEndEpochMillis).atZone(zoneId).format(formatter)
    }
}

data class DailyStats(
    val warningCount: Int,
    val stopCount: Int,
    val ignoreCount: Int,
    val estimatedSavedMinutes: Int,
    val mostRiskyTimeBand: TimeBand,
    val goalProgressPercent: Int,
)

data class AppState(
    val settings: MonitorSettings = MonitorSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val characterState: CharacterState = CharacterState(),
    val liveMonitor: LiveMonitorState = LiveMonitorState(),
    val pendingIntervention: PendingIntervention? = null,
    val cooldownUntilEpochMillis: Long = 0L,
    val foregroundAppName: String = "未取得",
    val foregroundPackageName: String = "",
    val sessionLogs: List<SessionLog> = seedLogs(),
) {
    fun dailyStats(): DailyStats {
        val warningCount = sessionLogs.count { it.triggeredWarning }
        val stopCount = sessionLogs.count { it.userAction == UserAction.STOP }
        val ignoreCount = sessionLogs.count { it.userAction == UserAction.IGNORE }
        val estimatedSavedMinutes = sessionLogs.sumOf { it.savedMinutes }
        val riskyBand = sessionLogs
            .groupingBy { it.timeBand }
            .eachCount()
            .maxByOrNull { (_, count) -> count }
            ?.key ?: TimeBand.FOCUS
        val goalProgressPercent = if (settings.dailyGoalMinutes <= 0) {
            0
        } else {
            min(100, (estimatedSavedMinutes * 100) / settings.dailyGoalMinutes)
        }

        return DailyStats(
            warningCount = warningCount,
            stopCount = stopCount,
            ignoreCount = ignoreCount,
            estimatedSavedMinutes = estimatedSavedMinutes,
            mostRiskyTimeBand = riskyBand,
            goalProgressPercent = goalProgressPercent,
        )
    }

    companion object {
        fun seedLogs(now: Long = System.currentTimeMillis()): List<SessionLog> {
            val hour = 60 * 60 * 1000L
            return listOf(
                SessionLog(
                    id = "seed-1",
                    timestampStartEpochMillis = now - 2 * hour,
                    timestampEndEpochMillis = now - 2 * hour + 13 * 60 * 1000L,
                    appName = "TikTok",
                    uiScore = 92,
                    triggeredWarning = true,
                    warningLevel = WarningLevel.STRONG,
                    userAction = UserAction.STOP,
                    timeBand = TimeBand.LATE_NIGHT,
                    savedMinutes = 14,
                    source = "seed",
                ),
                SessionLog(
                    id = "seed-2",
                    timestampStartEpochMillis = now - 6 * hour,
                    timestampEndEpochMillis = now - 6 * hour + 8 * 60 * 1000L,
                    appName = "Instagram",
                    uiScore = 67,
                    triggeredWarning = true,
                    warningLevel = WarningLevel.MEDIUM,
                    userAction = UserAction.EXTEND,
                    timeBand = TimeBand.EVENING,
                    savedMinutes = 2,
                    source = "seed",
                ),
                SessionLog(
                    id = "seed-3",
                    timestampStartEpochMillis = now - 13 * hour,
                    timestampEndEpochMillis = now - 13 * hour + 5 * 60 * 1000L,
                    appName = "YouTube",
                    uiScore = 58,
                    triggeredWarning = true,
                    warningLevel = WarningLevel.LIGHT,
                    userAction = UserAction.IGNORE,
                    timeBand = TimeBand.FOCUS,
                    savedMinutes = 0,
                    source = "seed",
                ),
            )
        }
    }
}

enum class DemoPreset(
    val title: String,
    val note: String,
    val scenario: DetectionScenario,
) {
    YOUTUBE(
        title = "YouTube Shorts",
        note = "通常動画ではなく Shorts 体験を検知するケース",
        scenario = DetectionScenario(
            appName = "YouTube",
            packageName = ServiceTarget.YOUTUBE.packageName,
            timeBand = TimeBand.LATE_NIGHT,
            sessionMinutes = 14,
            relaunchCount = 2,
            swipeBurst = 4,
            dwellSeconds = 26,
            reentryAfterWarning = true,
            keywords = listOf("Shorts"),
            uiFeatures = listOf(
                UiFeature.FULLSCREEN_VERTICAL,
                UiFeature.ACTION_RAIL,
                UiFeature.VIDEO_STRUCTURE,
                UiFeature.CONTINUOUS_TRANSITIONS,
            ),
            note = "深夜帯で Shorts テキストと連続縦スワイプが揃ったパターン",
        ),
    ),
    INSTAGRAM(
        title = "Instagram Reels",
        note = "縦動画 UI と右側アクション列が揃ったケース",
        scenario = DetectionScenario(
            appName = "Instagram",
            packageName = ServiceTarget.INSTAGRAM.packageName,
            timeBand = TimeBand.EVENING,
            sessionMinutes = 9,
            relaunchCount = 1,
            swipeBurst = 3,
            dwellSeconds = 18,
            reentryAfterWarning = false,
            keywords = listOf("Reels"),
            uiFeatures = listOf(
                UiFeature.FULLSCREEN_VERTICAL,
                UiFeature.ACTION_RAIL,
                UiFeature.VIDEO_STRUCTURE,
                UiFeature.CONTINUOUS_TRANSITIONS,
            ),
            note = "Reels ラベルと連続遷移を伴う標準ケース",
        ),
    ),
    TIKTOK(
        title = "TikTok For You",
        note = "深夜帯に再突入を繰り返した強介入ケース",
        scenario = DetectionScenario(
            appName = "TikTok",
            packageName = ServiceTarget.TIKTOK.packageName,
            timeBand = TimeBand.LATE_NIGHT,
            sessionMinutes = 18,
            relaunchCount = 3,
            swipeBurst = 5,
            dwellSeconds = 34,
            reentryAfterWarning = true,
            keywords = listOf("For You"),
            uiFeatures = listOf(
                UiFeature.FULLSCREEN_VERTICAL,
                UiFeature.ACTION_RAIL,
                UiFeature.VIDEO_STRUCTURE,
                UiFeature.CONTINUOUS_TRANSITIONS,
            ),
            note = "For You と再突入が重なった強めの検知ケース",
        ),
    ),
    NORMAL(
        title = "通常利用",
        note = "ショート動画 UI ではない通常アプリ利用",
        scenario = DetectionScenario(
            appName = "Study Notes",
            packageName = "dev.shortblocker.app.notes",
            timeBand = TimeBand.FOCUS,
            sessionMinutes = 6,
            relaunchCount = 0,
            swipeBurst = 0,
            dwellSeconds = 6,
            reentryAfterWarning = false,
            keywords = emptyList(),
            uiFeatures = emptyList(),
            note = "対象外アプリで通常利用しているケース",
        ),
    );
}

fun buildSessionLog(
    pending: PendingIntervention,
    liveMonitor: LiveMonitorState,
    action: UserAction,
    source: String,
): SessionLog {
    val end = System.currentTimeMillis()
    val duration = max(2, liveMonitor.sessionMinutes)
    val start = end - duration * 60 * 1000L
    val savedMinutes = when (action) {
        UserAction.STOP -> max(8, (liveMonitor.sessionMinutes * 0.8).toInt())
        UserAction.EXTEND -> 2
        UserAction.IGNORE -> 0
    }

    return SessionLog(
        id = UUID.randomUUID().toString(),
        timestampStartEpochMillis = start,
        timestampEndEpochMillis = end,
        appName = pending.appName,
        uiScore = pending.score,
        triggeredWarning = true,
        warningLevel = pending.warningLevel,
        userAction = action,
        timeBand = pending.timeBand,
        savedMinutes = savedMinutes,
        source = source,
    )
}
