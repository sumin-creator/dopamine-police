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
                relaunchCount = 0,
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

        assertTrue(decision.snapshot.score >= settings.threshold)
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
                relaunchCount = 0,
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

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun shortVideoStructureDoesNotTriggerBeforeSecondShortsSwipe() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 4,
                relaunchCount = 0,
                swipeBurst = 1,
                dwellSeconds = 12,
                reentryAfterWarning = false,
                keywords = listOf("Shorts"),
                uiFeatures = listOf(
                    UiFeature.FULLSCREEN_VERTICAL,
                    UiFeature.ACTION_RAIL,
                    UiFeature.VIDEO_STRUCTURE,
                    UiFeature.CONTINUOUS_TRANSITIONS,
                ),
                note = "Shorts viewer before enough page swipes",
            ),
            settings = settings,
            cooldownUntilEpochMillis = 0L,
            requirePermissions = true,
            permissions = permissions,
            now = 1_000L,
        )

        assertTrue(decision.snapshot.score >= settings.threshold)
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun shortVideoStructureTriggersAfterSecondShortsSwipe() {
        val decision = detector.evaluateScenario(
            scenario = DetectionScenario(
                appName = "YouTube",
                packageName = ServiceTarget.YOUTUBE.packageName,
                timeBand = TimeBand.LATE_NIGHT,
                sessionMinutes = 4,
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
                note = "Shorts viewer with action rail and repeated vertical transitions",
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
}
