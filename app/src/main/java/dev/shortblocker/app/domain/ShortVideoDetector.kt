package dev.shortblocker.app.domain

import android.graphics.Rect
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

data class DetectionDecision(
    val snapshot: DetectionSnapshot,
    val shouldTrigger: Boolean,
)

internal data class ScoreableShortsEvidence(
    val keywordHits: Set<String>,
    val actionHints: Set<String>,
    val swipeBurst: Int,
)

internal data class ShortsSwipeState(
    val swipeBurst: Int,
    val lastCountedSwipeAt: Long,
    val counted: Boolean,
)

internal enum class ObservedEventType {
    WINDOW_STATE_CHANGED,
    WINDOW_CONTENT_CHANGED,
    VIEW_SCROLLED,
    PLAYBACK_TICK,
    OTHER,
}

internal data class EventSignals(
    val texts: Set<String> = emptySet(),
    val viewIds: Set<String> = emptySet(),
    val classNames: Set<String> = emptySet(),
    val nodes: List<SignalNode> = emptyList(),
) {
    private val aggregatedTexts: Set<String> = (texts + nodes.mapNotNull { it.text }).toSet()
    private val aggregatedViewIds: Set<String> = (viewIds + nodes.mapNotNull { it.viewId }).toSet()

    val normalizedText: String = aggregatedTexts.joinToString(separator = " ").lowercase(Locale.US)
    val normalizedViewIds: String = aggregatedViewIds.joinToString(separator = " ").lowercase(Locale.US)

    fun isEmpty(): Boolean = aggregatedTexts.isEmpty() && aggregatedViewIds.isEmpty() && classNames.isEmpty()
}

internal data class SignalNode(
    val text: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val left: Int? = null,
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
) {
    val normalizedText: String = text?.lowercase(Locale.US).orEmpty()
    val normalizedViewId: String = viewId?.lowercase(Locale.US).orEmpty()
    val hasBounds: Boolean = left != null && top != null && right != null && bottom != null

    val centerX: Int?
        get() = if (hasBounds) ((left ?: 0) + (right ?: 0)) / 2 else null

    val centerY: Int?
        get() = if (hasBounds) ((top ?: 0) + (bottom ?: 0)) / 2 else null
}

internal data class ViewerSurfaceSignals(
    val keywordHits: Set<String> = emptySet(),
    val actionHints: Set<String> = emptySet(),
    val viewerEvidence: Boolean = false,
    val normalVideoUiDetected: Boolean = false,
)

internal data class ObservedEvent(
    val packageName: String,
    val type: ObservedEventType,
    val signals: EventSignals = EventSignals(),
    val scrollDeltaX: Int = 0,
    val scrollDeltaY: Int = 0,
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
    private val youtubeActionRailHints = listOf(
        "like",
        "liked",
        "dislike",
        "いいね",
        "高評価",
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
        "subscribe",
        "チャンネル登録",
    )
    private val youtubeShortsKeywords = listOf(
        "shorts",
        "ショート",
        "youtube shorts",
    )
    private val youtubeShortsViewIdHints = listOf(
        "short",
        "shorts",
        "reel",
    )
    private val youtubeShortsMetadataHints = listOf(
        "subscribe",
        "チャンネル登録",
        "save music",
        "music",
        "音楽",
        "♪",
        "#",
        "@",
    )
    private val youtubeStandardVideoHints = listOf(
        "play",
        "pause",
        "fullscreen",
        "full screen",
        "全画面",
        "字幕",
        "captions",
        "autoplay",
        "miniplayer",
        "chapter",
        "チャプター",
        "seek",
    )
    private val youtubeStandardVideoViewIdHints = listOf(
        "fullscreen",
        "player_control",
        "play_pause",
        "seek_bar",
        "miniplayer",
    )

    private var activeSession: ActiveSession? = null
    private val recentRelaunchStates = mutableMapOf<String, RelaunchState>()
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
        val youtubeRuntimeTarget = target == ServiceTarget.YOUTUBE && settings.supportedApps.isEnabled(target)
        val targetAppContext = if (youtubeRuntimeTarget) {
            val settledSessionBonus = if (scenario.sessionMinutes >= APP_CONTEXT_SETTLE_MINUTES) {
                APP_CONTEXT_SETTLE_BONUS
            } else {
                0
            }
            min(
                APP_CONTEXT_MAX,
                APP_CONTEXT_BASE +
                    min(scenario.relaunchCount, 2) * RELAUNCH_CONTEXT_BONUS +
                    settledSessionBonus,
            )
        } else {
            0
        }
        val keywordConfidence = min(KEYWORD_CONFIDENCE_MAX, scenario.keywords.size * KEYWORD_HIT_WEIGHT)
        val actionRailConfidence = if (UiFeature.ACTION_RAIL in scenario.uiFeatures) ACTION_RAIL_CONFIDENCE else 0
        val videoStructureConfidence = if (UiFeature.VIDEO_STRUCTURE in scenario.uiFeatures) VIDEO_STRUCTURE_CONFIDENCE else 0
        val fullscreenConfidence = if (UiFeature.FULLSCREEN_VERTICAL in scenario.uiFeatures) FULLSCREEN_CONFIDENCE else 0
        val shortsLikeUi = min(
            SHORTS_UI_MAX,
            keywordConfidence + actionRailConfidence + videoStructureConfidence + fullscreenConfidence,
        )
        val sessionMinutesScore = min(SESSION_MINUTES_MAX, scenario.sessionMinutes)
        val dwellScore = min(DWELL_SECONDS_MAX, scenario.dwellSeconds / 3)
        val sessionDuration = min(
            SESSION_DURATION_MAX,
            sessionMinutesScore + dwellScore,
        )
        val playbackMomentum = when {
            scenario.mediaPlaybackActive == true && hasReliableShortVideoEvidence(scenario) -> PLAYBACK_MOMENTUM_RELIABLE
            scenario.mediaPlaybackActive == true &&
                scenario.keywords.isNotEmpty() &&
                UiFeature.ACTION_RAIL in scenario.uiFeatures -> PLAYBACK_MOMENTUM_PARTIAL
            else -> 0
        }
        val total = min(
            100,
            targetAppContext + shortsLikeUi + sessionDuration + playbackMomentum,
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
                max = APP_CONTEXT_MAX,
                detail = "${scenario.appName} / 再突入 ${scenario.relaunchCount}回",
            ),
            DetectionBreakdown(
                label = "Viewer Surface Confidence",
                value = shortsLikeUi,
                max = SHORTS_UI_MAX,
                detail = "keyword ${keywordConfidence} / rail ${actionRailConfidence} / structure ${videoStructureConfidence} / vertical ${fullscreenConfidence}",
            ),
            DetectionBreakdown(
                label = "Persistence",
                value = sessionDuration,
                max = SESSION_DURATION_MAX,
                detail = "session ${sessionMinutesScore} / dwell ${dwellScore}",
            ),
            DetectionBreakdown(
                label = "Playback Momentum",
                value = playbackMomentum,
                max = PLAYBACK_MOMENTUM_RELIABLE,
                detail = mediaPlaybackLabel(scenario.mediaPlaybackActive),
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
        val permissionsReady = !requirePermissions || permissions.canIntervene
        val reliableShortVideoEvidence = hasReliableShortVideoEvidence(scenario)
        val shouldTrigger = settings.alertsEnabled &&
            youtubeRuntimeTarget &&
            permissionsReady &&
            now >= cooldownUntilEpochMillis &&
            reliableShortVideoEvidence &&
            total >= settings.threshold

        return DetectionDecision(snapshot = snapshot, shouldTrigger = shouldTrigger)
    }

    fun processEvent(
        event: AccessibilityEvent,
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
        mediaPlaybackActive: Boolean? = null,
    ): DetectionDecision? {
        val observedEvent = event.toObservedEvent() ?: return null
        return processObservedEvent(
            observedEvent = observedEvent,
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = cooldownUntilEpochMillis,
            mediaPlaybackActive = mediaPlaybackActive,
            now = System.currentTimeMillis(),
        )
    }

    fun processActiveWindowSnapshot(
        packageName: String,
        rootNode: AccessibilityNodeInfo?,
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
        mediaPlaybackActive: Boolean? = null,
        now: Long = System.currentTimeMillis(),
    ): DetectionDecision? {
        if (packageName.isBlank()) {
            runCatching { rootNode?.recycle() }
            return null
        }
        val signals = collectNodeSignals(rootNode)
        val observedEvent = ObservedEvent(
            packageName = packageName,
            type = ObservedEventType.WINDOW_CONTENT_CHANGED,
            signals = signals,
        )
        return processObservedEvent(
            observedEvent = observedEvent,
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = cooldownUntilEpochMillis,
            mediaPlaybackActive = mediaPlaybackActive,
            now = now,
        )
    }

    internal fun processObservedEvent(
        observedEvent: ObservedEvent,
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
        mediaPlaybackActive: Boolean? = null,
        now: Long,
    ): DetectionDecision? {
        val packageName = observedEvent.packageName
        val timeBand = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .let { TimeBand.fromHour(it.hour) }
        val target = ServiceTarget.fromPackage(packageName)

        if (target != ServiceTarget.YOUTUBE) {
            activeSession?.let { active ->
                recentRelaunchStates[active.packageName] = RelaunchState(
                    relaunchCount = active.relaunchCount,
                    lastExitAt = now,
                )
            }
            activeSession = null
            return DetectionDecision(
                snapshot = DetectionSnapshot(
                    appName = target?.appName ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
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
        val switchedApp = existing?.packageName != packageName
        val relaunchCount = if (switchedApp) {
            val relaunchState = recentRelaunchStates[packageName]
            if (relaunchState != null && now - relaunchState.lastExitAt <= RELAUNCH_WINDOW_MS) {
                relaunchState.relaunchCount + 1
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
                stage = DetectionStage.IDLE,
                lastKeywordEvidenceAt = 0L,
                lastActionHintEvidenceAt = 0L,
                lastReliableEvidenceAt = 0L,
            )
        } else {
            existing
        } ?: return null

        val signals = observedEvent.signals
        val rawKeywordHits = detectKeywords(target, signals).toSet()
        val rawActionHints = detectActionHints(signals).toSet()
        val surfaceSignals = analyzeYoutubeViewerSurface(
            signals = signals,
            rawKeywordHits = rawKeywordHits,
            rawActionHints = rawActionHints,
        )
        val currentKeywordHits = surfaceSignals.keywordHits
        val currentActionHints = surfaceSignals.actionHints
        val shouldResetEvidence = surfaceSignals.normalVideoUiDetected && !surfaceSignals.viewerEvidence
        val continuingShortsPlaybackCandidate = (
            mediaPlaybackActive == true ||
                observedEvent.type == ObservedEventType.PLAYBACK_TICK
            ) &&
            !shouldResetEvidence &&
            baseSession.stage != DetectionStage.IDLE &&
            baseSession.lastReliableEvidenceAt != 0L
        val keywordState = if (shouldResetEvidence) {
            RetainedHitsState(
                hits = emptySet(),
                lastSeenAt = 0L,
            )
        } else {
            retainFreshHits(
                currentHits = currentKeywordHits,
                retainedHits = baseSession.keywordHits,
                lastSeenAt = baseSession.lastKeywordEvidenceAt,
                now = now,
                ttlMs = SHORTS_KEYWORD_TTL_MS,
                keepAlive = continuingShortsPlaybackCandidate,
            )
        }
        val actionHintState = if (shouldResetEvidence) {
            RetainedHitsState(
                hits = emptySet(),
                lastSeenAt = 0L,
            )
        } else {
            retainFreshHits(
                currentHits = currentActionHints,
                retainedHits = baseSession.actionHints,
                lastSeenAt = baseSession.lastActionHintEvidenceAt,
                now = now,
                ttlMs = SHORTS_ACTION_HINT_TTL_MS,
                keepAlive = continuingShortsPlaybackCandidate,
            )
        }
        val keywordHits = keywordState.hits
        val actionHints = actionHintState.hits
        val currentViewerEvidence = surfaceSignals.viewerEvidence
        val retainedViewerEvidence = if (shouldResetEvidence) {
            false
        } else {
            hasYoutubeShortsViewerEvidence(
                keywordHits = keywordHits,
                actionHints = actionHints,
            )
        }
        val reliableEvidenceObservedNow = currentKeywordHits.isNotEmpty() && retainedViewerEvidence
        val continuingShortsPlayback = continuingShortsPlaybackCandidate && retainedViewerEvidence
        val freshReliableEvidence = if (shouldResetEvidence) {
            false
        } else {
            reliableEvidenceObservedNow ||
                continuingShortsPlayback ||
                isWithinWindow(baseSession.lastReliableEvidenceAt, now, SHORTS_EVIDENCE_TTL_MS)
        }
        val lastReliableEvidenceAt = when {
            shouldResetEvidence -> 0L
            reliableEvidenceObservedNow || continuingShortsPlayback -> now
            else -> baseSession.lastReliableEvidenceAt
        }
        val verticalScroll = isLikelyVerticalScroll(observedEvent)
        val continuingShortsScroll = verticalScroll && freshReliableEvidence
        val swipeState = resolveShortsSwipeState(
            currentSwipeBurst = baseSession.swipeBurst,
            lastCountedSwipeAt = baseSession.lastCountedSwipeAt,
            now = now,
            freshReliableEvidence = freshReliableEvidence,
            continuingShortsScroll = continuingShortsScroll,
        )
        val swipeBurst = swipeState.swipeBurst
        val lastTransitionAt = when {
            shouldResetEvidence -> now
            observedEvent.type == ObservedEventType.WINDOW_STATE_CHANGED -> now
            else -> baseSession.lastTransitionAt
        }
        val scoreableEvidence = resolveScoreableShortsEvidence(
            keywordHits = keywordHits,
            actionHints = actionHints,
            swipeBurst = swipeBurst,
            currentViewerEvidence = currentViewerEvidence,
            continuingShortsScroll = continuingShortsScroll,
            continuingShortsPlayback = continuingShortsPlayback,
        )
        val stage = resolveDetectionStage(
            scoreableEvidence = scoreableEvidence,
            freshReliableEvidence = freshReliableEvidence,
            keywordHits = keywordHits,
        )
        val updatedSession = baseSession.copy(
            swipeBurst = swipeBurst,
            keywordHits = keywordHits,
            actionHints = actionHints,
            stage = stage,
            lastKeywordEvidenceAt = keywordState.lastSeenAt,
            lastActionHintEvidenceAt = actionHintState.lastSeenAt,
            lastReliableEvidenceAt = lastReliableEvidenceAt,
            lastCountedSwipeAt = swipeState.lastCountedSwipeAt,
            lastTransitionAt = if (shouldResetEvidence || swipeState.counted) now else lastTransitionAt,
        )
        activeSession = updatedSession

        val uiFeatures = detectUiFeatures(
            target = target,
            keywordHits = scoreableEvidence.keywordHits,
            actionHints = scoreableEvidence.actionHints,
            swipeBurst = scoreableEvidence.swipeBurst,
        )

        val scenario = DetectionScenario(
            appName = target.appName,
            packageName = packageName,
            timeBand = timeBand,
            sessionMinutes = ((now - updatedSession.sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1),
            relaunchCount = updatedSession.relaunchCount,
            swipeBurst = scoreableEvidence.swipeBurst,
            dwellSeconds = ((now - updatedSession.lastTransitionAt) / 1000L).toInt().coerceAtLeast(1),
            reentryAfterWarning = updatedSession.relaunchCount > 0,
            keywords = scoreableEvidence.keywordHits.toList(),
            uiFeatures = uiFeatures,
            note = "YouTube Shorts stage=${stage.name} media=${mediaPlaybackLabel(mediaPlaybackActive)}",
            mediaPlaybackActive = mediaPlaybackActive,
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
        val rateLimitReady = now - recentWarning > WARNING_RATE_LIMIT_MS
        val shouldTrigger = decision.shouldTrigger && rateLimitReady
        logDebug(
            buildDebugLog(
                packageName = packageName,
                stage = stage,
                surfaceSignals = surfaceSignals,
                scoreableEvidence = scoreableEvidence,
                scenario = scenario,
                uiFeatures = uiFeatures,
                settings = settings,
                permissions = permissions,
                cooldownUntilEpochMillis = cooldownUntilEpochMillis,
                rateLimitReady = rateLimitReady,
                now = now,
                decision = decision,
                shouldTrigger = shouldTrigger,
            ),
        )
        if (shouldTrigger) {
            lastWarningTimes[packageName] = now
        }
        return decision.copy(shouldTrigger = shouldTrigger)
    }

    internal fun resolveShortsSwipeState(
        currentSwipeBurst: Int,
        lastCountedSwipeAt: Long,
        now: Long,
        freshReliableEvidence: Boolean,
        continuingShortsScroll: Boolean,
    ): ShortsSwipeState {
        if (!freshReliableEvidence) {
            return ShortsSwipeState(
                swipeBurst = 0,
                lastCountedSwipeAt = 0L,
                counted = false,
            )
        }

        val counted = continuingShortsScroll &&
            (lastCountedSwipeAt == 0L || now - lastCountedSwipeAt >= SHORTS_SWIPE_DEBOUNCE_MS)
        if (!counted) {
            return ShortsSwipeState(
                swipeBurst = currentSwipeBurst,
                lastCountedSwipeAt = lastCountedSwipeAt,
                counted = false,
            )
        }

        val nextSwipeBurst = if (lastCountedSwipeAt != 0L && now - lastCountedSwipeAt <= SCROLL_BURST_WINDOW_MS) {
            currentSwipeBurst + 1
        } else {
            1
        }
        return ShortsSwipeState(
            swipeBurst = nextSwipeBurst,
            lastCountedSwipeAt = now,
            counted = true,
        )
    }

    internal fun resolveScoreableShortsEvidence(
        keywordHits: Set<String>,
        actionHints: Set<String>,
        swipeBurst: Int,
        currentViewerEvidence: Boolean,
        continuingShortsScroll: Boolean,
        continuingShortsPlayback: Boolean = false,
    ): ScoreableShortsEvidence {
        val retainedShortsLikeEvidence = keywordHits.isNotEmpty() && actionHints.isNotEmpty()
        val canScoreAsShorts = currentViewerEvidence || continuingShortsScroll || retainedShortsLikeEvidence
        return if (canScoreAsShorts) {
            ScoreableShortsEvidence(
                keywordHits = keywordHits,
                actionHints = actionHints,
                swipeBurst = swipeBurst,
            )
        } else {
            ScoreableShortsEvidence(
                keywordHits = emptySet(),
                actionHints = emptySet(),
                swipeBurst = 0,
            )
        }
    }

    internal fun resolveDetectionStage(
        scoreableEvidence: ScoreableShortsEvidence,
        freshReliableEvidence: Boolean,
        keywordHits: Set<String>,
    ): DetectionStage = when {
        scoreableEvidence.swipeBurst >= REQUIRED_SHORTS_SWIPES -> DetectionStage.WATCHING_SHORTS
        freshReliableEvidence || keywordHits.isNotEmpty() -> DetectionStage.CANDIDATE
        else -> DetectionStage.IDLE
    }

    private fun detectKeywords(target: ServiceTarget, signals: EventSignals): List<String> {
        if (signals.isEmpty()) {
            return emptyList()
        }
        val keywords = if (target == ServiceTarget.YOUTUBE) {
            youtubeShortsKeywords
        } else {
            target.keywords
        }
        val textMatches = keywords.filter { keyword ->
            signals.normalizedText.contains(keyword.lowercase(Locale.US))
        }
        val viewIdTokens = tokenizeViewIdValue(signals.normalizedViewIds)
        val idMatches = youtubeShortsViewIdHints
            .filter { hint -> hint.lowercase(Locale.US) in viewIdTokens }
            .map { hint -> "ui:$hint" }
        return (textMatches + idMatches).distinct()
    }

    private fun detectActionHints(signals: EventSignals): List<String> {
        if (signals.isEmpty()) {
            return emptyList()
        }
        val viewIdTokens = tokenizeViewIdValue(signals.normalizedViewIds)
        return youtubeActionRailHints.filter { hint ->
            textMatchesHint(signals.normalizedText, hint) ||
                hint.lowercase(Locale.US) in viewIdTokens
        }.distinct()
    }

    private fun tokenizeViewIdValue(value: String): Set<String> {
        return value
            .split(VIEW_ID_TOKEN_SPLIT_REGEX)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
    }

    private fun analyzeYoutubeViewerSurface(
        signals: EventSignals,
        rawKeywordHits: Set<String>,
        rawActionHints: Set<String>,
    ): ViewerSurfaceSignals {
        val positionedNodes = signals.nodes.filter { it.hasBounds }
        if (positionedNodes.isEmpty()) {
            val normalVideoUiDetected = detectStandardVideoUi(
                signals = signals,
                positionedNodes = emptyList(),
                frameWidth = 0,
                frameHeight = 0,
            )
            val fallbackKeywordHits = rawKeywordHits
                .filterNot { it.startsWith("ui:") }
                .toSet()
            val fallbackViewerEvidence = !normalVideoUiDetected &&
                fallbackKeywordHits.isNotEmpty() &&
                rawActionHints.size >= FALLBACK_MIN_ACTION_HINTS
            return ViewerSurfaceSignals(
                keywordHits = fallbackKeywordHits,
                actionHints = if (fallbackViewerEvidence) rawActionHints else emptySet(),
                viewerEvidence = fallbackViewerEvidence,
                normalVideoUiDetected = normalVideoUiDetected,
            )
        }

        val frameWidth = positionedNodes.maxOf { it.right ?: 0 }.coerceAtLeast(1)
        val frameHeight = positionedNodes.maxOf { it.bottom ?: 0 }.coerceAtLeast(1)
        val normalVideoUiDetected = detectStandardVideoUi(
            signals = signals,
            positionedNodes = positionedNodes,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        val surfaceKeywordHits = positionedNodes
            .filter { node -> isLikelyShortsHeaderNode(node, frameWidth, frameHeight) }
            .flatMap { node -> detectKeywordsForNode(node) }
            .toSet()
        val surfaceActionHints = positionedNodes
            .filter { node -> isLikelyActionRailNode(node, frameWidth, frameHeight) }
            .flatMap { node -> detectActionHintsForNode(node) }
            .toSet()
        val bottomTabKeywordHits = positionedNodes
            .filter { node -> isLikelyBottomShortsTabNode(node, frameWidth, frameHeight) }
            .flatMap { node -> detectKeywordsForNode(node) }
            .toSet()
        val metadataHints = positionedNodes
            .filter { node -> isLikelyShortsMetadataNode(node, frameWidth, frameHeight) }
            .flatMap { node -> detectMetadataHintsForNode(node) }
            .toSet()
        val headerViewerEvidence = hasYoutubeShortsViewerEvidence(
            keywordHits = surfaceKeywordHits,
            actionHints = surfaceActionHints,
        )
        val bottomTabPlayerStructureEvidence = hasYoutubeShortsPlayerStructureEvidence(
            bottomTabKeywordHits = bottomTabKeywordHits,
            actionHints = surfaceActionHints,
            metadataHints = metadataHints,
        )
        val playbackStructureEvidence = hasYoutubeShortsPlaybackStructureEvidence(
            actionHints = surfaceActionHints,
            metadataHints = metadataHints,
        )
        val playerStructureEvidence = !normalVideoUiDetected &&
            (bottomTabPlayerStructureEvidence || playbackStructureEvidence)
        val viewerEvidence = headerViewerEvidence || playerStructureEvidence
        val playerStructureKeywordHits = when {
            bottomTabPlayerStructureEvidence -> bottomTabKeywordHits
            playbackStructureEvidence -> setOf(STRUCTURAL_SHORTS_PLAYER_HINT)
            else -> emptySet()
        }
        return ViewerSurfaceSignals(
            keywordHits = surfaceKeywordHits + if (playerStructureEvidence) playerStructureKeywordHits else emptySet(),
            actionHints = if (viewerEvidence) surfaceActionHints else emptySet(),
            viewerEvidence = viewerEvidence,
            normalVideoUiDetected = normalVideoUiDetected,
        )
    }

    private fun hasYoutubeShortsViewerEvidence(
        keywordHits: Set<String>,
        actionHints: Set<String>,
    ): Boolean = keywordHits.isNotEmpty() && actionHints.size >= MIN_ACTION_RAIL_HINTS

    private fun hasYoutubeShortsPlayerStructureEvidence(
        bottomTabKeywordHits: Set<String>,
        actionHints: Set<String>,
        metadataHints: Set<String>,
    ): Boolean = bottomTabKeywordHits.isNotEmpty() &&
        actionHints.size >= PLAYER_STRUCTURE_MIN_ACTION_HINTS &&
        metadataHints.size >= MIN_SHORTS_METADATA_HINTS

    private fun hasYoutubeShortsPlaybackStructureEvidence(
        actionHints: Set<String>,
        metadataHints: Set<String>,
    ): Boolean = actionHints.size >= PLAYER_STRUCTURE_MIN_ACTION_HINTS &&
        metadataHints.size >= MIN_STRONG_SHORTS_METADATA_HINTS

    private fun detectKeywordsForNode(node: SignalNode): List<String> {
        val textMatches = youtubeShortsKeywords.filter { keyword ->
            node.normalizedText.contains(keyword.lowercase(Locale.US))
        }
        val viewIdTokens = tokenizeViewIdValue(node.normalizedViewId)
        val idMatches = youtubeShortsViewIdHints
            .filter { hint -> hint.lowercase(Locale.US) in viewIdTokens }
            .map { hint -> "ui:$hint" }
        return (textMatches + idMatches).distinct()
    }

    private fun detectActionHintsForNode(node: SignalNode): List<String> {
        val viewIdTokens = tokenizeViewIdValue(node.normalizedViewId)
        return youtubeActionRailHints.filter { hint ->
            textMatchesHint(node.normalizedText, hint) ||
                hint.lowercase(Locale.US) in viewIdTokens
        }.distinct()
    }

    private fun textMatchesHint(normalizedText: String, hint: String): Boolean {
        if (normalizedText.isBlank()) {
            return false
        }
        val normalizedHint = hint.lowercase(Locale.US)
        if (!normalizedHint.isAsciiWordHint() || normalizedHint.contains(" ")) {
            return normalizedText.contains(normalizedHint)
        }

        val textTokens = tokenizeViewIdValue(normalizedText)
        return normalizedHint in textTokens || when (normalizedHint) {
            "like" -> "likes" in textTokens
            "comment" -> "comments" in textTokens
            else -> false
        }
    }

    private fun String.isAsciiWordHint(): Boolean {
        return all { char -> char in 'a'..'z' || char in '0'..'9' || char == ' ' }
    }

    private fun detectMetadataHintsForNode(node: SignalNode): List<String> {
        val viewIdTokens = tokenizeViewIdValue(node.normalizedViewId)
        return youtubeShortsMetadataHints.filter { hint ->
            val normalizedHint = hint.lowercase(Locale.US)
            node.normalizedText.contains(normalizedHint) ||
                normalizedHint in viewIdTokens
        }.distinct()
    }

    private fun isLikelyShortsHeaderNode(node: SignalNode, frameWidth: Int, frameHeight: Int): Boolean {
        val centerX = node.centerX ?: return false
        val centerY = node.centerY ?: return false
        return detectKeywordsForNode(node).isNotEmpty() &&
            centerY <= (frameHeight * 0.30).toInt() &&
            centerX <= (frameWidth * 0.72).toInt()
    }

    private fun isLikelyActionRailNode(node: SignalNode, frameWidth: Int, frameHeight: Int): Boolean {
        val centerX = node.centerX ?: return false
        val centerY = node.centerY ?: return false
        return detectActionHintsForNode(node).isNotEmpty() &&
            centerX >= (frameWidth * 0.68).toInt() &&
            centerY >= (frameHeight * 0.18).toInt() &&
            centerY <= (frameHeight * 0.92).toInt()
    }

    private fun isLikelyBottomShortsTabNode(node: SignalNode, frameWidth: Int, frameHeight: Int): Boolean {
        val centerX = node.centerX ?: return false
        val centerY = node.centerY ?: return false
        return detectKeywordsForNode(node).isNotEmpty() &&
            centerY >= (frameHeight * 0.88).toInt() &&
            centerX >= (frameWidth * 0.08).toInt() &&
            centerX <= (frameWidth * 0.82).toInt()
    }

    private fun isLikelyShortsMetadataNode(node: SignalNode, frameWidth: Int, frameHeight: Int): Boolean {
        val centerX = node.centerX ?: return false
        val centerY = node.centerY ?: return false
        return detectMetadataHintsForNode(node).isNotEmpty() &&
            centerX <= (frameWidth * 0.82).toInt() &&
            centerY >= (frameHeight * 0.60).toInt() &&
            centerY <= (frameHeight * 0.94).toInt()
    }

    private fun detectStandardVideoUi(
        signals: EventSignals,
        positionedNodes: List<SignalNode>,
        frameWidth: Int,
        frameHeight: Int,
    ): Boolean {
        val viewIdTokens = tokenizeViewIdValue(signals.normalizedViewIds)
        val hasExplicitPlayerControl = youtubeStandardVideoHints.any { hint ->
            signals.normalizedText.contains(hint.lowercase(Locale.US))
        } || youtubeStandardVideoViewIdHints.any { hint ->
            hint.lowercase(Locale.US) in viewIdTokens
        }
        if (hasExplicitPlayerControl) {
            return true
        }
        if (positionedNodes.isEmpty() || frameWidth == 0 || frameHeight == 0) {
            return false
        }

        val horizontalActionRow = positionedNodes
            .filter { node -> detectActionHintsForNode(node).isNotEmpty() }
            .filter { node ->
                val centerX = node.centerX ?: return@filter false
                val centerY = node.centerY ?: return@filter false
                centerX < (frameWidth * 0.72).toInt() &&
                    centerY >= (frameHeight * 0.42).toInt() &&
                    centerY <= (frameHeight * 0.82).toInt()
            }
        if (horizontalActionRow.size < MIN_ACTION_RAIL_HINTS) {
            return false
        }
        val xs = horizontalActionRow.mapNotNull { it.centerX }
        val ys = horizontalActionRow.mapNotNull { it.centerY }
        return (xs.maxOrNull() ?: 0) - (xs.minOrNull() ?: 0) >= (frameWidth * 0.20).toInt() &&
            (ys.maxOrNull() ?: 0) - (ys.minOrNull() ?: 0) <= (frameHeight * 0.14).toInt()
    }

    private fun detectUiFeatures(
        target: ServiceTarget,
        keywordHits: Set<String>,
        actionHints: Set<String>,
        swipeBurst: Int,
    ): List<UiFeature> {
        val hasShortSurfaceHint = keywordHits.isNotEmpty()
        val hasActionRail = actionHints.size >= MIN_ACTION_RAIL_HINTS
        val hasRepeatedVerticalNavigation = swipeBurst >= REQUIRED_SHORTS_SWIPES
        val hasShortVideoStructure = target == ServiceTarget.YOUTUBE &&
            hasShortSurfaceHint &&
            hasActionRail

        return buildList {
            if (hasShortVideoStructure) {
                add(UiFeature.VIDEO_STRUCTURE)
            }
            if (hasShortVideoStructure && hasActionRail) {
                add(UiFeature.FULLSCREEN_VERTICAL)
            }
            if (hasActionRail && hasShortSurfaceHint) {
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
        return target == ServiceTarget.YOUTUBE && (
            UiFeature.VIDEO_STRUCTURE in features ||
            (UiFeature.FULLSCREEN_VERTICAL in features && UiFeature.ACTION_RAIL in features) ||
            (scenario.keywords.isNotEmpty() && UiFeature.ACTION_RAIL in features)
            )
    }

    private fun isLikelyVerticalScroll(observedEvent: ObservedEvent): Boolean {
        if (observedEvent.type != ObservedEventType.VIEW_SCROLLED) {
            return false
        }
        val horizontalDelta = observedEvent.scrollDeltaX
        val verticalDelta = observedEvent.scrollDeltaY
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
            nodes = nodeSignals.nodes,
        )
    }

    private fun AccessibilityEvent.toObservedEvent(): ObservedEvent? {
        val safePackageName = packageName?.toString() ?: return null
        return ObservedEvent(
            packageName = safePackageName,
            type = when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ObservedEventType.WINDOW_STATE_CHANGED
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ObservedEventType.WINDOW_CONTENT_CHANGED
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> ObservedEventType.VIEW_SCROLLED
                else -> ObservedEventType.OTHER
            },
            signals = collectSignals(this),
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY,
        )
    }

    private fun retainFreshHits(
        currentHits: Set<String>,
        retainedHits: Set<String>,
        lastSeenAt: Long,
        now: Long,
        ttlMs: Long,
        keepAlive: Boolean = false,
    ): RetainedHitsState {
        if (keepAlive && retainedHits.isNotEmpty()) {
            return RetainedHitsState(
                hits = (retainedHits + currentHits).toSet(),
                lastSeenAt = now,
            )
        }
        val updatedLastSeenAt = if (currentHits.isNotEmpty()) now else lastSeenAt
        val fresh = currentHits.isNotEmpty() || isWithinWindow(lastSeenAt, now, ttlMs)
        return if (fresh) {
            RetainedHitsState(
                hits = (retainedHits + currentHits).toSet(),
                lastSeenAt = updatedLastSeenAt,
            )
        } else {
            RetainedHitsState(
                hits = emptySet(),
                lastSeenAt = 0L,
            )
        }
    }

    private fun isWithinWindow(lastSeenAt: Long, now: Long, ttlMs: Long): Boolean {
        return lastSeenAt != 0L && now - lastSeenAt <= ttlMs
    }

    private fun buildDebugLog(
        packageName: String,
        stage: DetectionStage,
        surfaceSignals: ViewerSurfaceSignals,
        scoreableEvidence: ScoreableShortsEvidence,
        scenario: DetectionScenario,
        uiFeatures: List<UiFeature>,
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
        rateLimitReady: Boolean,
        now: Long,
        decision: DetectionDecision,
        shouldTrigger: Boolean,
    ): String {
        val targetReady = settings.supportedApps.isEnabled(ServiceTarget.YOUTUBE)
        val alertsReady = settings.alertsEnabled
        val permissionsReady = permissions.canIntervene
        val cooldownReady = now >= cooldownUntilEpochMillis
        val evidenceReady = hasReliableShortVideoEvidence(scenario)
        val scoreReady = decision.snapshot.score >= settings.threshold
        val blockReasons = buildList {
            if (!alertsReady) add("alerts")
            if (!targetReady) add("target")
            if (!permissionsReady) add("permissions")
            if (!cooldownReady) add("cooldown")
            if (!evidenceReady) add("evidence")
            if (!scoreReady) add("threshold")
            if (!rateLimitReady) add("rate-limit")
        }
        val triggerLabel = if (shouldTrigger) {
            "YES"
        } else {
            "NO[${blockReasons.joinToString(",").ifEmpty { "blocked" }}]"
        }
        val surfaceLabel = when {
            surfaceSignals.viewerEvidence -> "viewer"
            surfaceSignals.normalVideoUiDetected -> "normal-video"
            else -> "unknown"
        }
        return buildString {
            append("pkg=").append(packageName)
            append(" | stage=").append(stage.name)
            append(" | surface=").append(surfaceLabel)
            append(" | score=").append(decision.snapshot.score).append('/').append(settings.threshold)
            append(" | trigger=").append(triggerLabel)
            append(" | gates[a=").append(flag(alertsReady))
            append(" t=").append(flag(targetReady))
            append(" p=").append(flag(permissionsReady))
            append(" c=").append(flag(cooldownReady))
            append(" e=").append(flag(evidenceReady))
            append(" s=").append(flag(scoreReady))
            append(" r=").append(flag(rateLimitReady)).append(']')
            append(" | kw=").append(formatHits(scoreableEvidence.keywordHits))
            append(" | act=").append(formatHits(scoreableEvidence.actionHints))
            append(" | ui=").append(formatUiFeatures(uiFeatures))
            append(" | session=").append(scenario.sessionMinutes).append("m")
            append(" dwell=").append(scenario.dwellSeconds).append("s")
            append(" relaunch=").append(scenario.relaunchCount)
            append(" swipes=").append(scoreableEvidence.swipeBurst)
            append(" | media=").append(mediaPlaybackLabel(scenario.mediaPlaybackActive))
        }
    }

    private fun flag(value: Boolean): String = if (value) "Y" else "N"

    private fun formatHits(values: Set<String>): String {
        if (values.isEmpty()) {
            return "-"
        }
        return values.sorted().joinToString(",")
    }

    private fun formatUiFeatures(values: List<UiFeature>): String {
        if (values.isEmpty()) {
            return "-"
        }
        return values.map(UiFeature::name).sorted().joinToString(",")
    }

    private fun logDebug(message: String) {
        runCatching { android.util.Log.d(TAG, message) }
    }

    @Suppress("DEPRECATION")
    private fun collectNodeSignals(root: AccessibilityNodeInfo?): EventSignals {
        if (root == null) {
            return EventSignals()
        }
        val texts = linkedSetOf<String>()
        val viewIds = linkedSetOf<String>()
        val classNames = linkedSetOf<String>()
        val nodes = mutableListOf<SignalNode>()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_NODE_DEPTH || visited >= MAX_NODE_COUNT) {
                return
            }
            visited += 1
            val nodeText = buildList {
                node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }.distinct().joinToString(separator = " ").trim().ifBlank { null }
            val viewId = node.viewIdResourceName?.trim()?.takeIf { it.isNotBlank() }
            val className = node.className?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val bounds = Rect().also(node::getBoundsInScreen)

            nodeText?.let(texts::add)
            viewId?.let(viewIds::add)
            className?.let(classNames::add)
            nodes += SignalNode(
                text = nodeText,
                viewId = viewId,
                className = className,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            )

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
            EventSignals(texts = texts, viewIds = viewIds, classNames = classNames, nodes = nodes)
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

    private fun mediaPlaybackLabel(mediaPlaybackActive: Boolean?): String = when (mediaPlaybackActive) {
        true -> "playing"
        false -> "inactive"
        null -> "unknown"
    }

    private data class ActiveSession(
        val packageName: String,
        val appName: String,
        val sessionStartedAt: Long,
        val relaunchCount: Int,
        val swipeBurst: Int,
        val lastTransitionAt: Long,
        val lastCountedSwipeAt: Long = 0L,
        val keywordHits: Set<String>,
        val actionHints: Set<String>,
        val stage: DetectionStage,
        val lastKeywordEvidenceAt: Long,
        val lastActionHintEvidenceAt: Long,
        val lastReliableEvidenceAt: Long,
    )

    private data class RelaunchState(
        val relaunchCount: Int,
        val lastExitAt: Long,
    )

    private data class RetainedHitsState(
        val hits: Set<String>,
        val lastSeenAt: Long,
    )

    internal enum class DetectionStage {
        IDLE,
        CANDIDATE,
        WATCHING_SHORTS,
    }

    private companion object {
        const val TAG = "ShortDetector"
        const val APP_CONTEXT_MAX = 10
        const val APP_CONTEXT_BASE = 2
        const val RELAUNCH_CONTEXT_BONUS = 2
        const val APP_CONTEXT_SETTLE_BONUS = 2
        const val APP_CONTEXT_SETTLE_MINUTES = 3
        const val SHORTS_UI_MAX = 70
        const val KEYWORD_CONFIDENCE_MAX = 22
        const val KEYWORD_HIT_WEIGHT = 11
        const val ACTION_RAIL_CONFIDENCE = 20
        const val VIDEO_STRUCTURE_CONFIDENCE = 24
        const val FULLSCREEN_CONFIDENCE = 12
        const val SESSION_DURATION_MAX = 20
        const val SESSION_MINUTES_MAX = 12
        const val DWELL_SECONDS_MAX = 8
        const val PLAYBACK_MOMENTUM_RELIABLE = 18
        const val PLAYBACK_MOMENTUM_PARTIAL = 12
        const val SCROLL_BURST_WINDOW_MS = 20_000L
        const val SHORTS_SWIPE_DEBOUNCE_MS = 900L
        const val SHORTS_KEYWORD_TTL_MS = 90_000L
        const val SHORTS_ACTION_HINT_TTL_MS = 90_000L
        const val SHORTS_EVIDENCE_TTL_MS = 90_000L
        const val WARNING_RATE_LIMIT_MS = 30_000L
        const val RELAUNCH_WINDOW_MS = 5 * 60_000L
        const val FALLBACK_MIN_ACTION_HINTS = 3
        const val REQUIRED_SHORTS_SWIPES = 2
        const val MIN_ACTION_RAIL_HINTS = 2
        const val PLAYER_STRUCTURE_MIN_ACTION_HINTS = 3
        const val MIN_SHORTS_METADATA_HINTS = 2
        const val MIN_STRONG_SHORTS_METADATA_HINTS = 3
        const val MAX_NODE_DEPTH = 5
        const val MAX_NODE_COUNT = 80
        const val STRUCTURAL_SHORTS_PLAYER_HINT = "ui:shorts-player"
        val VIEW_ID_TOKEN_SPLIT_REGEX = Regex("[^a-z0-9]+")
    }

    fun evaluateCurrentSession(
        settings: MonitorSettings,
        permissions: PermissionSnapshot,
        cooldownUntilEpochMillis: Long,
        mediaPlaybackActive: Boolean? = null,
        now: Long = System.currentTimeMillis()
    ): DetectionDecision? {
        // 現在アクティブなセッションがなければ何もしない
        val session = activeSession ?: return null

        // 最新の時刻を使って滞在時間を再計算
        val sessionMinutes = ((now - session.sessionStartedAt) / 60_000L).toInt().coerceAtLeast(1)
        val dwellSeconds = ((now - session.lastTransitionAt) / 1000L).toInt().coerceAtLeast(1)
        val timeBand = TimeBand.fromHour(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour)

        val target = ServiceTarget.fromPackage(session.packageName) ?: ServiceTarget.YOUTUBE
        val currentUiFeatures = detectUiFeatures(
            target = target,
            keywordHits = session.keywordHits,
            actionHints = session.actionHints,
            swipeBurst = session.swipeBurst
        )

        // 現在のセッション情報から最新のシナリオを構築
        val scenario = DetectionScenario(
            appName = session.appName,
            packageName = session.packageName,
            timeBand = timeBand,
            sessionMinutes = sessionMinutes,
            relaunchCount = session.relaunchCount,
            swipeBurst = session.swipeBurst,
            dwellSeconds = dwellSeconds,
            reentryAfterWarning = session.relaunchCount > 0,
            keywords = session.keywordHits.toList(),
            // ▼修正：固定リストではなく、動的に再計算したものを渡す
            uiFeatures = currentUiFeatures,
            note = "Timer driven detection",
            mediaPlaybackActive = mediaPlaybackActive,
        )

        val decision = evaluateScenario(
            scenario = scenario,
            settings = settings,
            cooldownUntilEpochMillis = cooldownUntilEpochMillis,
            requirePermissions = true,
            permissions = permissions,
            now = now
        )

        // クールダウンや直近の警告時間を考慮してトリガーすべきか判定
        val recentWarning = lastWarningTimes[session.packageName] ?: 0L
        val shouldTrigger = decision.shouldTrigger && now - recentWarning > 30_000L

        if (shouldTrigger) {
            lastWarningTimes[session.packageName] = now
        }

        return decision.copy(shouldTrigger = shouldTrigger)
    }

    private fun analyzeTextLayoutStructure(
        positionedNodes: List<SignalNode>,
        frameWidth: Int,
        frameHeight: Int
    ): Boolean {
        // 画面下部（例：下から30%の領域）にあるテキストノードを抽出
        val bottomTextNodes = positionedNodes.filter { node ->
            val centerY = node.centerY ?: return@filter false
            val hasText = node.normalizedText.isNotBlank()

            hasText && centerY >= (frameHeight * 0.70).toInt()
        }

        // 下部領域にあるテキストの中に、ショート特有のキーワードが含まれているか
        val hasShortsKeywordInBottom = bottomTextNodes.any { node ->
            youtubeShortsKeywords.any { keyword ->
                node.normalizedText.contains(keyword)
            }
        }

        // テキストが左寄り（X座標の平均が画面中央より左）に配置されているかなど、
        // ショート特有のテキストブロックの配置条件をここで判定可能

        return bottomTextNodes.isNotEmpty() && hasShortsKeywordInBottom
    }

}
