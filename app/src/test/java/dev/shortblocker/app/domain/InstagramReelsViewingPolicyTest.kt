package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.TimeBand
import dev.shortblocker.app.data.UiFeature
import dev.shortblocker.app.data.WarningLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramReelsViewingPolicyTest {
    @Test
    fun pauseStopsCountingButCommentsContinueCounting() {
        val viewer = reelsSnapshot()
        val paused = reelsSnapshot(extraFeature = UiFeature.REELS_PAUSED)
        val comments = reelsSnapshot(extraFeature = UiFeature.REELS_COMMENTS)

        assertTrue(ShortVideoViewingPolicy.shouldCount(viewer, mediaPlaybackActive = false))
        assertFalse(ShortVideoViewingPolicy.shouldCount(paused, mediaPlaybackActive = false))
        assertTrue(ShortVideoViewingPolicy.shouldCount(comments, mediaPlaybackActive = false))
    }

    private fun reelsSnapshot(extraFeature: UiFeature? = null): DetectionSnapshot = DetectionSnapshot(
        appName = "Instagram",
        packageName = ServiceTarget.INSTAGRAM.packageName,
        timeBand = TimeBand.FOCUS,
        score = 75,
        warningLevel = WarningLevel.MEDIUM,
        dialogue = "",
        sessionMinutes = 1,
        relaunchCount = 0,
        swipeBurst = 0,
        dwellSeconds = 1,
        keywordHits = listOf("Reels"),
        uiFeatures = listOfNotNull(
            UiFeature.ACTION_RAIL,
            UiFeature.VIDEO_STRUCTURE,
            extraFeature,
        ),
        breakdown = emptyList(),
        createdAtEpochMillis = 1_000L,
    )
}
