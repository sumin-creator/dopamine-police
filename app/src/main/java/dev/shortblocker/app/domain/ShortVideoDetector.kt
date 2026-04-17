package dev.shortblocker.app.domain

import android.view.accessibility.AccessibilityEvent
import dev.shortblocker.app.data.DetectionBreakdown
import dev.shortblocker.app.data.DetectionScenario
import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.MonitorSettings
import dev.shortblocker.app.data.PermissionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.TimeBand
import dev.shortblocker.app.data.UiFeature
import dev.shortblocker.app.data.WarningLevel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

data class DetectionDecision(
    val snapshot: DetectionSnapshot,
    val shouldTrigger: Boolean,
)

class ShortVideoDetector {
    private val lateNightDialogues = listOf(
        "この時間のShorts、ほぼ事故だよ。",
        "寝る前の1本が一番危ないよ。",
    )
    private val lightDialogues = listOf(
        "ねえ、それ今ほんとに開く必要あった？",
        "画面に吸われる前に戻っておいで。",
    )
    private val mediumDialogues = listOf(
        "またshort動画の沼に入ろうとしてる。",
        "1本だけって、今ので何本目？ ドバガキくんさぁ。",
    )
    private val strongDialogues = listOf(
        "今日はさすがに見すぎ。",
        "未来の自分に怒られるやつだよ。",
    )

    private var activeSession: ActiveSession? = null
    private val recentExitTimes = mutableMapOf<String, Long>()
    private val lastWarningTimes = mutableMapOf<String, Long>()

    fun evaluateScenario(
        scenario: DetectionScenario,
        settings: MonitorSettings,
        cooldownUntilEpochMillis: Long,
        requirePermissions: Boolean,
        permissions: PermissionSnapshot,
        now: Long = System.currentTimeMillis(),
    ): DetectionDecision {
        val target = ServiceTarget.fromPackage(scenario.packageName)
        val targetEnabled = settings.supportedApps.isEnabled(target)
        val targetAppContext = if (target != null && targetEnabled) {
            min(30, 18 + min(scenario.relaunchCount, 3) * 4)
        } else {
            0
        }
        val shortsLikeUi = min(
            35,
            scenario.keywords.size * 6 +
                (if (UiFeature.FULLSCREEN_VERTICAL in scenario.uiFeatures) 8 else 0) +
                (if (UiFeature.ACTION_RAIL in scenario.uiFeatures) 7 else 0) +
                (if (UiFeature.VIDEO_STRUCTURE in scenario.uiFeatures) 8 else 0) +
                (if (UiFeature.CONTINUOUS_TRANSITIONS in scenario.uiFeatures) 6 else 0),
        )
        val repeatedVerticalNavigation = min(
            20,
            scenario.swipeBurst * 4 + if (scenario.reentryAfterWarning) 4 else 0,
        )
        val sessionDuration = min(
            15,
            (scenario.sessionMinutes * 0.65 + min(scenario.dwellSeconds, 30) * 0.1).roundToInt(),
        )
        val riskyTime = when (scenario.timeBand) {
            TimeBand.LATE_NIGHT -> 10
            TimeBand.EVENING -> 4
            TimeBand.FOCUS -> 0
        }
        val total = min(
            100,
            targetAppContext + shortsLikeUi + repeatedVerticalNavigation + sessionDuration + riskyTime,
        )
        val warningLevel = warningLevelFromScore(total)
        val dialogue = dialogueFor(
            level = warningLevel,
            timeBand = scenario.timeBand,
            seed = scenario.sessionMinutes + scenario.swipeBurst + scenario.relaunchCount,
        )
        val breakdown = listOf(
            DetectionBreakdown(
                label = "Target App Context",
                value = targetAppContext,
                max = 30,
                detail = "${scenario.appName} / 再突入 ${scenario.relaunchCount}回",
            ),
            DetectionBreakdown(
                label = "Shorts-like UI",
                value = shortsLikeUi,
                max = 35,
                detail = "キーワード ${scenario.keywords.size}件 / UI特徴 ${scenario.uiFeatures.size}件",
            ),
            DetectionBreakdown(
                label = "Repeated Vertical Navigation",
                value = repeatedVerticalNavigation,
                max = 20,
                detail = "縦スワイプ ${scenario.swipeBurst}回 / 警告後再突入 ${if (scenario.reentryAfterWarning) "あり" else "なし"}",
            ),
            DetectionBreakdown(
                label = "Session Duration",
                value = sessionDuration,
                max = 15,
                detail = "${scenario.sessionMinutes}分継続 / 滞在 ${scenario.dwellSeconds}秒",
            ),
            DetectionBreakdown(
                label = "Risky Time Of Day",
                value = riskyTime,
                max = 10,
                detail = scenario.timeBand.label,
            ),
        )
        val snapshot = DetectionSnapshot(
            appName = scenario.appName,
            packageName = scenario.packageName,
            timeBand = scenario.timeBand,
            score = total,
            warningLevel = warningLevel,
            dialogue = dialogue,
            sessionMinutes = scenario.sessionMinutes,
            relaunchCount = scenario.relaunchCount,
            swipeBurst = scenario.swipeBurst,
            dwellSeconds = scenario.dwellSeconds,
            keywordHits = scenario.keywords,
            uiFeatures = scenario.uiFeatures,
            breakdown = breakdown,
            createdAtEpochMillis = now,
        )
        val permissionsReady = !requirePermissions || permissions.allRequiredGranted
        val shouldTrigger = settings.alertsEnabled &&
            targetEnabled &&
            permissionsReady &&
            now >= cooldownUntilEpochMillis &&
            total >= settings.threshold

        return DetectionDecision(snapshot = snapshot, shouldTrigger = shouldTrigger)
    }

    fun processEvent(
        event: AccessibilityEvent,
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
    ): DetectionDecision? {
        val packageName = event.packageName?.toString() ?: return null
        val now = System.currentTimeMillis()
        val timeBand = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .let { TimeBand.fromHour(it.hour) }
        val target = ServiceTarget.fromPackage(packageName)
        val texts = buildList {
            event.text?.forEach { item ->
                item?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            }
            event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
        }

        if (target == null) {
            activeSession?.let { active ->
                recentExitTimes[active.packageName] = now
            }
            activeSession = null
            return DetectionDecision(
                snapshot = DetectionSnapshot(
                    appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                    packageName = packageName,
                    timeBand = timeBand,
                    score = 0,
                    warningLevel = WarningLevel.WATCH,
                    dialogue = "通常利用の範囲。必要以上に邪魔せず見守ります。",
                    sessionMinutes = 0,
                    relaunchCount = 0,
                    swipeBurst = 0,
                    dwellSeconds = 0,
                    keywordHits = emptyList(),
                    uiFeatures = emptyList(),
                    breakdown = emptyList(),
                    createdAtEpochMillis = now,
                ),
                shouldTrigger = false,
            )
        }

        val existing = activeSession
        val switchedApp = existing == null || existing.packageName != packageName
        val relaunchCount = if (switchedApp) {
            val lastExit = recentExitTimes[packageName] ?: 0L
            if (now - lastExit <= 5 * 60_000L) {
                (existing?.relaunchCount ?: 0) + 1
            } else {
                0
            }
        } else {
            existing.relaunchCount
        }
        val baseSession = if (switchedApp) {
            ActiveSession(
                packageName = packageName,
                appName = target.appName,
                sessionStartedAt = now,
                relaunchCount = relaunchCount,
                swipeBurst = 0,
                lastTransitionAt = now,
                keywordHits = emptySet(),
            )
        } else {
            existing
        } ?: return null

        val keywordHits = (baseSession.keywordHits + detectKeywords(target, texts)).toSet()
        val swipeBurst = when {
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                now - baseSession.lastScrollAt <= 25_000L -> baseSession.swipeBurst + 1

            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED -> 1
            else -> baseSession.swipeBurst
        }
        val lastTransitionAt = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> now
            else -> baseSession.lastTransitionAt
        }
        val updatedSession = baseSession.copy(
            swipeBurst = swipeBurst,
            keywordHits = keywordHits,
            lastScrollAt = if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) now else baseSession.lastScrollAt,
            lastTransitionAt = lastTransitionAt,
        )
        activeSession = updatedSession

        val uiFeatures = buildList {
            add(UiFeature.VIDEO_STRUCTURE)
            add(UiFeature.FULLSCREEN_VERTICAL)
            if (keywordHits.isNotEmpty() || swipeBurst > 0) {
                add(UiFeature.ACTION_RAIL)
            }
            if (swipeBurst >= 2 || event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                add(UiFeature.CONTINUOUS_TRANSITIONS)
            }
        }.distinct()

        val scenario = DetectionScenario(
            appName = target.appName,
            packageName = packageName,
            timeBand = timeBand,
            sessionMinutes = ((now - updatedSession.sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1),
            relaunchCount = updatedSession.relaunchCount,
            swipeBurst = updatedSession.swipeBurst,
            dwellSeconds = ((now - updatedSession.lastTransitionAt) / 1000L).toInt().coerceAtLeast(1),
            reentryAfterWarning = updatedSession.relaunchCount > 0,
            keywords = keywordHits.toList(),
            uiFeatures = uiFeatures,
            note = "Accessibility event driven detection",
        )
        val decision = evaluateScenario(
            scenario = scenario,
            settings = settings,
            cooldownUntilEpochMillis = cooldownUntilEpochMillis,
            requirePermissions = true,
            permissions = permissions,
            now = now,
        )
        val recentWarning = lastWarningTimes[packageName] ?: 0L
        val shouldTrigger = decision.shouldTrigger && now - recentWarning > 30_000L
        if (shouldTrigger) {
            lastWarningTimes[packageName] = now
        }
        return decision.copy(shouldTrigger = shouldTrigger)
    }

    private fun detectKeywords(target: ServiceTarget, texts: List<String>): List<String> {
        if (texts.isEmpty()) {
            return emptyList()
        }
        val normalized = texts.joinToString(separator = " ").lowercase(Locale.US)
        return target.keywords.filter { keyword ->
            normalized.contains(keyword.lowercase(Locale.US))
        }
    }

    private fun warningLevelFromScore(score: Int): WarningLevel = when {
        score >= 85 -> WarningLevel.STRONG
        score >= 65 -> WarningLevel.MEDIUM
        score >= 45 -> WarningLevel.LIGHT
        else -> WarningLevel.WATCH
    }

    private fun dialogueFor(level: WarningLevel, timeBand: TimeBand, seed: Int): String {
        if (level == WarningLevel.WATCH) {
            return "通常利用の範囲。必要以上に邪魔せず見守ります。"
        }
        val pool = if (timeBand == TimeBand.LATE_NIGHT) {
            lateNightDialogues
        } else {
            when (level) {
                WarningLevel.LIGHT -> lightDialogues
                WarningLevel.MEDIUM -> mediumDialogues
                WarningLevel.STRONG -> strongDialogues
                WarningLevel.WATCH -> lightDialogues
            }
        }
        return pool[seed.mod(pool.size)]
    }

    private data class ActiveSession(
        val packageName: String,
        val appName: String,
        val sessionStartedAt: Long,
        val relaunchCount: Int,
        val swipeBurst: Int,
        val lastTransitionAt: Long,
        val lastScrollAt: Long = 0L,
        val keywordHits: Set<String>,
    )
}
