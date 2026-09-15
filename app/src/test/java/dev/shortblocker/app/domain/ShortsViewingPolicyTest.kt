package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.TimeBand
import dev.shortblocker.app.data.UiFeature
import dev.shortblocker.app.data.WarningLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortsViewingPolicyTest {
    @Test
    fun mediaSessionStateAloneDoesNotStopAVisibleShortsViewer() {
        val viewer = shortsSnapshot()

        assertTrue(ShortsViewingPolicy.shouldCount(viewer, mediaPlaybackActive = false))
        assertTrue(ShortsViewingPolicy.shouldCount(viewer, mediaPlaybackActive = true))
        assertTrue(ShortsViewingPolicy.shouldCount(viewer, mediaPlaybackActive = null))
    }

    @Test
    fun shortsPauseOverlayStopsCounting() {
        val pausedViewer = shortsSnapshot(
            uiFeatures = listOf(
                UiFeature.ACTION_RAIL,
                UiFeature.VIDEO_STRUCTURE,
                UiFeature.SHORTS_PAUSED,
            ),
        )

        assertFalse(ShortsViewingPolicy.shouldCount(pausedViewer, mediaPlaybackActive = false))
    }

    @Test
    fun commentsOpenedFromShortsCountEvenWhenPlaybackIsInactive() {
        val comments = shortsSnapshot(
            uiFeatures = listOf(
                UiFeature.ACTION_RAIL,
                UiFeature.VIDEO_STRUCTURE,
                UiFeature.SHORTS_COMMENTS,
            ),
        )

        assertTrue(ShortsViewingPolicy.shouldCount(comments, mediaPlaybackActive = false))
    }

    private fun shortsSnapshot(
        uiFeatures: List<UiFeature> = listOf(UiFeature.ACTION_RAIL, UiFeature.VIDEO_STRUCTURE),
    ): DetectionSnapshot = DetectionSnapshot(
        appName = "YouTube",
        packageName = ServiceTarget.YOUTUBE.packageName,
        timeBand = TimeBand.FOCUS,
        score = 75,
        warningLevel = WarningLevel.MEDIUM,
        dialogue = "",
        sessionMinutes = 1,
        relaunchCount = 0,
        swipeBurst = 0,
        dwellSeconds = 1,
        keywordHits = listOf("Shorts"),
        uiFeatures = uiFeatures,
        breakdown = emptyList(),
        createdAtEpochMillis = 1_000L,
    )
}
