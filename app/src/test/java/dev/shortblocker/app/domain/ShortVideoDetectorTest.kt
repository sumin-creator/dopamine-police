package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionScenario
import dev.shortblocker.app.data.MonitorSettings
import dev.shortblocker.app.data.PermissionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.TimeBand
import dev.shortblocker.app.data.UiFeature
import kotlin.test.Test
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
}
