package dev.shortblocker.app.domain

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import kotlin.math.abs
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
    private val actionRailHints = listOf(
        "like",
        "liked",
        "いいね",
        "comment",
        "コメント",
        "share",
        "共有",
        "send",
        "送信",
        "save",
        "保存",
        "sound",
        "音源",
        "remix",
        "フォロー",
        "follow",
    )
    private val structuralIdHints = mapOf(
        ServiceTarget.YOUTUBE to listOf("short", "shorts", "reel"),
        ServiceTarget.INSTAGRAM to listOf("reel", "reels", "clips"),
        ServiceTarget.TIKTOK to listOf("for_you", "foryou", "aweme", "feed_video"),
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
        val reliableShortVideoEvidence = hasReliableShortVideoEvidence(scenario)
        val interactionEvidence = scenario.swipeBurst >= 2 ||
            scenario.sessionMinutes >= 3 ||
            scenario.dwellSeconds >= 12
        val shouldTrigger = settings.alertsEnabled &&
            targetEnabled &&
            permissionsReady &&
            now >= cooldownUntilEpochMillis &&
            reliableShortVideoEvidence &&
            interactionEvidence &&
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
                actionHints = emptySet(),
            )
        } else {
            existing
        } ?: return null

        val signals = collectSignals(event)
        val keywordHits = (baseSession.keywordHits + detectKeywords(target, signals)).toSet()
        val actionHints = (baseSession.actionHints + detectActionHints(signals)).toSet()
        val verticalScroll = isLikelyVerticalScroll(event)
        val swipeBurst = when {
            verticalScroll && now - baseSession.lastScrollAt <= SCROLL_BURST_WINDOW_MS -> baseSession.swipeBurst + 1

            verticalScroll -> 1
            else -> baseSession.swipeBurst
        }
        val lastTransitionAt = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> now
            else -> baseSession.lastTransitionAt
        }
        val updatedSession = baseSession.copy(
            swipeBurst = swipeBurst,
            keywordHits = keywordHits,
            actionHints = actionHints,
            lastScrollAt = if (verticalScroll) now else baseSession.lastScrollAt,
            lastTransitionAt = if (verticalScroll) now else lastTransitionAt,
        )
        activeSession = updatedSession

        val uiFeatures = detectUiFeatures(
            target = target,
            keywordHits = keywordHits,
            actionHints = actionHints,
            swipeBurst = swipeBurst,
        )

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
        android.util.Log.d("ShortDetector", "pkg=$packageName score=${decision.snapshot.score} trigger=${decision.shouldTrigger}")
        val recentWarning = lastWarningTimes[packageName] ?: 0L
        val shouldTrigger = decision.shouldTrigger && now - recentWarning > 30_000L
        if (shouldTrigger) {
            lastWarningTimes[packageName] = now
        }
        return decision.copy(shouldTrigger = shouldTrigger)
    }

    private fun detectKeywords(target: ServiceTarget, signals: EventSignals): List<String> {
        if (signals.isEmpty()) {
            return emptyList()
        }
        val textMatches = target.keywords.filter { keyword ->
            signals.normalizedText.contains(keyword.lowercase(Locale.US))
        }
        val idMatches = structuralIdHints[target].orEmpty()
            .filter { hint -> signals.normalizedViewIds.contains(hint) }
            .map { hint -> "ui:$hint" }
        return (textMatches + idMatches).distinct()
    }

    private fun detectActionHints(signals: EventSignals): List<String> {
        if (signals.isEmpty()) {
            return emptyList()
        }
        val searchable = "${signals.normalizedText} ${signals.normalizedViewIds}"
        return actionRailHints.filter { hint ->
            searchable.contains(hint.lowercase(Locale.US))
        }.distinct()
    }

    private fun detectUiFeatures(
        target: ServiceTarget,
        keywordHits: Set<String>,
        actionHints: Set<String>,
        swipeBurst: Int,
    ): List<UiFeature> {
        val hasShortSurfaceHint = keywordHits.isNotEmpty()
        val hasActionRail = actionHints.size >= 2
        val hasRepeatedVerticalNavigation = swipeBurst >= 2
        val hasShortVideoStructure = (hasShortSurfaceHint && (hasActionRail || hasRepeatedVerticalNavigation)) ||
            (target == ServiceTarget.TIKTOK && hasActionRail && hasRepeatedVerticalNavigation)

        return buildList {
            if (hasShortVideoStructure) {
                add(UiFeature.VIDEO_STRUCTURE)
            }
            if (hasShortVideoStructure && hasActionRail) {
                add(UiFeature.FULLSCREEN_VERTICAL)
            }
            if (hasActionRail && (hasShortSurfaceHint || hasRepeatedVerticalNavigation)) {
                add(UiFeature.ACTION_RAIL)
            }
            if (hasRepeatedVerticalNavigation && (hasShortSurfaceHint || hasActionRail)) {
                add(UiFeature.CONTINUOUS_TRANSITIONS)
            }
        }.distinct()
    }

    private fun hasReliableShortVideoEvidence(scenario: DetectionScenario): Boolean {
        val features = scenario.uiFeatures.toSet()
        val target = ServiceTarget.fromPackage(scenario.packageName)
        return UiFeature.VIDEO_STRUCTURE in features ||
            (UiFeature.FULLSCREEN_VERTICAL in features && UiFeature.ACTION_RAIL in features) ||
            scenario.keywords.size >= 2 ||
            (
                target == ServiceTarget.TIKTOK &&
                    UiFeature.ACTION_RAIL in features &&
                    UiFeature.CONTINUOUS_TRANSITIONS in features
                )
    }

    private fun isLikelyVerticalScroll(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return false
        }
        val horizontalDelta = event.scrollDeltaX
        val verticalDelta = event.scrollDeltaY
        if (horizontalDelta == 0 && verticalDelta == 0) {
            return true
        }
        return abs(verticalDelta) >= abs(horizontalDelta)
    }

    private fun collectSignals(event: AccessibilityEvent): EventSignals {
        val eventTexts = linkedSetOf<String>()
        val eventClassNames = linkedSetOf<String>()
        event.text?.forEach { item ->
            item?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(eventTexts::add)
        }
        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(eventTexts::add)
        event.className?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(eventClassNames::add)

        val nodeSignals = runCatching { collectNodeSignals(event.source) }
            .getOrDefault(EventSignals())
        return EventSignals(
            texts = eventTexts + nodeSignals.texts,
            viewIds = nodeSignals.viewIds,
            classNames = eventClassNames + nodeSignals.classNames,
        )
    }

    @Suppress("DEPRECATION")
    private fun collectNodeSignals(root: AccessibilityNodeInfo?): EventSignals {
        if (root == null) {
            return EventSignals()
        }
        val texts = linkedSetOf<String>()
        val viewIds = linkedSetOf<String>()
        val classNames = linkedSetOf<String>()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_NODE_DEPTH || visited >= MAX_NODE_COUNT) {
                return
            }
            visited += 1
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(texts::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(texts::add)
            node.viewIdResourceName?.trim()?.takeIf { it.isNotBlank() }?.let(viewIds::add)
            node.className?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(classNames::add)

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        return try {
            visit(root, depth = 0)
            EventSignals(texts = texts, viewIds = viewIds, classNames = classNames)
        } finally {
            root.recycle()
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
        val actionHints: Set<String>,
    )

    private data class EventSignals(
        val texts: Set<String> = emptySet(),
        val viewIds: Set<String> = emptySet(),
        val classNames: Set<String> = emptySet(),
    ) {
        val normalizedText: String = texts.joinToString(separator = " ").lowercase(Locale.US)
        val normalizedViewIds: String = viewIds.joinToString(separator = " ").lowercase(Locale.US)

        fun isEmpty(): Boolean = texts.isEmpty() && viewIds.isEmpty() && classNames.isEmpty()
    }

    private companion object {
        const val SCROLL_BURST_WINDOW_MS = 20_000L
        const val MAX_NODE_DEPTH = 5
        const val MAX_NODE_COUNT = 80
    }
}
