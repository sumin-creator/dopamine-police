package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionScenario
import dev.shortblocker.app.data.MonitorSettings
import dev.shortblocker.app.data.PermissionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.TimeBand
import dev.shortblocker.app.data.UiFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortVideoDetectorTest {
    private val detector = ShortVideoDetector()
    private val settings = MonitorSettings()
    private val permissions = PermissionSnapshot(
        accessibility = true,
        usageStats = true,
        notifications = true,
    )

    @Test
    fun targetAppScrollBurstWithoutShortUiEvidenceDoesNotTrigger() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 20,
                relaunchCount = 3,
                swipeBurst = 6,
                dwellSeconds = 30,
                reentryAfterWarning = false,
                keywords = emptyList(),
                uiFeatures = emptyList(),
                note = "Long normal feed scrolling in a target app",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.swipeBurst >= 2)
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun keywordOnlyShortsTabDoesNotTriggerWithoutStructuralEvidence() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 20,
                relaunchCount = 1,
                swipeBurst = 6,
                dwellSeconds = 30,
                reentryAfterWarning = false,
                keywords = listOf("Shorts"),
                uiFeatures = emptyList(),
                note = "The Shorts tab label is visible outside the Shorts viewer",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.keywordHits.isNotEmpty())
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun shortsLabelAndActionRailMustAppearOnSameViewerSurface() {
        val decision = detector.processObservedEvent(
            observedEvent(
                nodes = listOf(
                    signalNode(text = "Shorts", left = 120, top = 1_500, right = 320, bottom = 1_580),
                    signalNode(text = "Like", left = 940, top = 620, right = 1_020, bottom = 700),
                    signalNode(text = "Share", left = 940, top = 860, right = 1_020, bottom = 940),
                ),
            ),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = 1_000L,
        )!!

        assertTrue(decision.snapshot.keywordHits.isEmpty())
        assertTrue(decision.snapshot.score < settings.threshold)
        assertTrue(decision.snapshot.uiFeatures.isEmpty())
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun shortVideoStructureCanTriggerWithoutShortsSwipeRequirement() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 20,
                relaunchCount = 0,
                swipeBurst = 0,
                dwellSeconds = 30,
                reentryAfterWarning = false,
                keywords = listOf("Shorts"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                ),
                note = "Shorts viewer with structural evidence but no swipe dependency",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun shortVideoStructureAndRelaunchStillTriggerWithoutSwipeScore() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 9,
                relaunchCount = 2,
                swipeBurst = 2,
                dwellSeconds = 12,
                reentryAfterWarning = true,
                keywords = listOf("Shorts"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                    UiFeature.CONTINUOUS_TRANSITIONS,
                ),
                note = "Shorts viewer with action rail and relaunch context",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun nonYoutubeShortVideoScenarioDoesNotTriggerForNow() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "Instagram",
                packageName = ServiceTarget.INSTAGRAM.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 14,
                relaunchCount = 2,
                swipeBurst = 4,
                dwellSeconds = 26,
                reentryAfterWarning = true,
                keywords = listOf("Reels"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                    UiFeature.CONTINUOUS_TRANSITIONS,
                ),
                note = "Instagram is intentionally out of scope while tuning YouTube Shorts",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun retainedShortsEvidenceDoesNotScoreAfterLeavingViewer() {
        val evidence = detector.resolveScoreableShortsEvidence(
            keywordHits = setOf("Shorts"),
            actionHints = setOf("like", "share"),
            swipeBurst = 2,
            currentViewerEvidence = false,
            continuingShortsScroll = false,
        )

        assertTrue(evidence.keywordHits.isEmpty())
        assertTrue(evidence.actionHints.isEmpty())
        assertEquals(0, evidence.swipeBurst)
    }

    @Test
    fun retainedShortsEvidenceCanScoreDuringContinuousShortsScroll() {
        val evidence = detector.resolveScoreableShortsEvidence(
            keywordHits = setOf("Shorts"),
            actionHints = setOf("like", "share"),
            swipeBurst = 2,
            currentViewerEvidence = false,
            continuingShortsScroll = true,
        )

        assertEquals(setOf("Shorts"), evidence.keywordHits)
        assertEquals(setOf("like", "share"), evidence.actionHints)
        assertEquals(2, evidence.swipeBurst)
    }

    @Test
    fun stageDropsFromWatchingShortsAfterLeavingViewer() {
        val stage = detector.resolveDetectionStage(
            scoreableEvidence = ScoreableShortsEvidence(
                keywordHits = emptySet(),
                actionHints = emptySet(),
                swipeBurst = 0,
            ),
            freshReliableEvidence = true,
            keywordHits = setOf("Shorts"),
        )

        assertEquals(ShortVideoDetector.DetectionStage.CANDIDATE, stage)
    }

    @Test
    fun repeatedScrollEventsWithinDebounceDoNotInflateShortsSwipes() {
        val first = detector.resolveShortsSwipeState(
            currentSwipeBurst = 0,
            lastCountedSwipeAt = 0L,
            now = 10_000L,
            freshReliableEvidence = true,
            continuingShortsScroll = true,
        )
        val duplicate = detector.resolveShortsSwipeState(
            currentSwipeBurst = first.swipeBurst,
            lastCountedSwipeAt = first.lastCountedSwipeAt,
            now = 10_300L,
            freshReliableEvidence = true,
            continuingShortsScroll = true,
        )

        assertTrue(first.counted)
        assertEquals(1, first.swipeBurst)
        assertFalse(duplicate.counted)
        assertEquals(1, duplicate.swipeBurst)
    }

    @Test
    fun separatedScrollEventsIncrementShortsSwipes() {
        val next = detector.resolveShortsSwipeState(
            currentSwipeBurst = 1,
            lastCountedSwipeAt = 10_000L,
            now = 12_000L,
            freshReliableEvidence = true,
            continuingShortsScroll = true,
        )

        assertTrue(next.counted)
        assertEquals(2, next.swipeBurst)
    }

    @Test
    fun shortsSwipeBurstResetsWhenReliableEvidenceExpires() {
        val next = detector.resolveShortsSwipeState(
            currentSwipeBurst = 4,
            lastCountedSwipeAt = 10_000L,
            now = 12_000L,
            freshReliableEvidence = false,
            continuingShortsScroll = false,
        )

        assertFalse(next.counted)
        assertEquals(0, next.swipeBurst)
        assertEquals(0L, next.lastCountedSwipeAt)
    }

    @Test
    fun actionHintsAloneDoNotRefreshReliableShortsEvidence() {
        val base = 100_000L
        detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base,
        )

        detector.processObservedEvent(
            observedEvent(texts = setOf("Like", "Share", "Save")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 13_000L,
        )

        val decision = detector.processObservedEvent(
            observedScrollEvent(now = base + 13_100L),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 13_100L,
        )!!

        assertEquals(0, decision.snapshot.swipeBurst)
        assertTrue(decision.snapshot.keywordHits.isEmpty())
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun normalVideoUiClearsRetainedShortsEvidenceImmediately() {
        val base = 100_000L
        detector.processObservedEvent(
            observedEvent(nodes = shortsViewerNodes()),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base,
        )

        val normalVideoDecision = detector.processObservedEvent(
            observedEvent(nodes = standardVideoNodes()),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 5_000L,
        )!!
        val scrollDecision = detector.processObservedEvent(
            observedScrollEvent(now = base + 5_100L),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 5_100L,
        )!!

        assertTrue(normalVideoDecision.snapshot.keywordHits.isEmpty())
        assertTrue(normalVideoDecision.snapshot.uiFeatures.isEmpty())
        assertTrue(normalVideoDecision.snapshot.score < settings.threshold)
        assertEquals(0, scrollDecision.snapshot.swipeBurst)
        assertTrue(scrollDecision.snapshot.keywordHits.isEmpty())
        assertFalse(scrollDecision.shouldTrigger)
    }

    @Test
    fun relaunchCountAccumulatesAcrossQuickReentries() {
        val base = 100_000L
        val firstEntry = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base,
        )!!
        detector.processObservedEvent(
            observedEvent(packageName = "com.example.maps"),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 1_000L,
        )
        val secondEntry = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 2_000L,
        )!!
        detector.processObservedEvent(
            observedEvent(packageName = "com.example.maps"),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 3_000L,
        )
        val thirdEntry = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 4_000L,
        )!!

        assertEquals(0, firstEntry.snapshot.relaunchCount)
        assertEquals(1, secondEntry.snapshot.relaunchCount)
        assertEquals(2, thirdEntry.snapshot.relaunchCount)
    }

    @Test
    fun cooldownSuppressesTriggerEvenWhenShortsSequenceScoresHigh() {
        val base = 100_000L
        detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = base + 30 * 60_000L,
            now = base,
        )
        val decision = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = base + 30 * 60_000L,
            now = base + 20 * 60_000L,
        )!!

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun rateLimitBlocksImmediateRepeatTrigger() {
        val base = 100_000L
        detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base,
        )
        val firstTrigger = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 20 * 60_000L,
        )!!
        val secondDecision = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = permissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 20 * 60_000L + 1_000L,
        )!!

        assertTrue(firstTrigger.shouldTrigger)
        assertTrue(secondDecision.snapshot.score >= settings.threshold)
        assertFalse(secondDecision.shouldTrigger)
    }

    @Test
    fun missingNotificationAndUsageStatsPermissionsDoNotBlockTrigger() {
        val base = 100_000L
        val limitedPermissions = PermissionSnapshot(
            accessibility = true,
            usageStats = false,
            notifications = false,
        )

        detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = limitedPermissions,
            cooldownUntilEpochMillis = 0L,
            now = base,
        )
        val decision = detector.processObservedEvent(
            observedEvent(texts = setOf("Shorts", "Like", "Share")),
            settings = settings,
            permissions = limitedPermissions,
            cooldownUntilEpochMillis = 0L,
            now = base + 20 * 60_000L,
        )!!

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun inactiveMediaPlaybackDoesNotBlockTriggerWhenUiEvidenceIsStrong() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 18,
                relaunchCount = 1,
                swipeBurst = 0,
                dwellSeconds = 20,
                reentryAfterWarning = false,
                keywords = listOf("Shorts"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                ),
                note = "Shorts-like UI while playback is inactive",
                mediaPlaybackActive = false,
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun unknownMediaPlaybackDoesNotBlockTrigger() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 18,
                relaunchCount = 1,
                swipeBurst = 0,
                dwellSeconds = 20,
                reentryAfterWarning = false,
                keywords = listOf("Shorts"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                ),
                note = "Shorts-like UI while MediaSession is unavailable",
                mediaPlaybackActive = null,
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.shouldTrigger)
    }

    private fun observedEvent(
        packageName: String = ServiceTarget.YOUTUBE.packageName,
        type: ObservedEventType = ObservedEventType.WINDOW_CONTENT_CHANGED,
        texts: Set<String> = emptySet(),
        viewIds: Set<String> = emptySet(),
        classNames: Set<String> = emptySet(),
        nodes: List<SignalNode> = emptyList(),
        scrollDeltaX: Int = 0,
        scrollDeltaY: Int = 0,
    ): ObservedEvent {
        return ObservedEvent(
            packageName = packageName,
            type = type,
            signals = EventSignals(
                texts = texts,
                viewIds = viewIds,
                classNames = classNames,
                nodes = nodes,
            ),
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY,
        )
    }

    private fun observedScrollEvent(
        now: Long,
        packageName: String = ServiceTarget.YOUTUBE.packageName,
    ): ObservedEvent {
        return observedEvent(
            packageName = packageName,
            type = ObservedEventType.VIEW_SCROLLED,
            scrollDeltaY = 160,
        )
    }

    private fun shortsViewerNodes(): List<SignalNode> = listOf(
        signalNode(text = "Shorts", left = 110, top = 90, right = 320, bottom = 180),
        signalNode(text = "Like", left = 950, top = 760, right = 1_030, bottom = 840),
        signalNode(text = "Share", left = 950, top = 1_020, right = 1_030, bottom = 1_100),
        signalNode(text = "Comment", left = 950, top = 890, right = 1_030, bottom = 970),
    )

    private fun standardVideoNodes(): List<SignalNode> = listOf(
        signalNode(text = "Pause", left = 470, top = 260, right = 610, bottom = 340),
        signalNode(text = "Fullscreen", left = 900, top = 420, right = 1_040, bottom = 500),
        signalNode(text = "Like", left = 190, top = 1_050, right = 280, bottom = 1_120),
        signalNode(text = "Share", left = 420, top = 1_050, right = 520, bottom = 1_120),
        signalNode(text = "Save", left = 660, top = 1_050, right = 760, bottom = 1_120),
    )

    private fun signalNode(
        text: String? = null,
        viewId: String? = null,
        className: String? = null,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): SignalNode = SignalNode(
        text = text,
        viewId = viewId,
        className = className,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
}
