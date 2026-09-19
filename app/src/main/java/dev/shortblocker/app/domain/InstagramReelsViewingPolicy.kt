package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.UiFeature

internal object InstagramReelsViewingPolicy {
    fun shouldCount(
        snapshot: DetectionSnapshot,
        @Suppress("UNUSED_PARAMETER") mediaPlaybackActive: Boolean?,
    ): Boolean {
        if (ServiceTarget.fromPackage(snapshot.packageName) != ServiceTarget.INSTAGRAM) {
            return false
        }
        val features = snapshot.uiFeatures.toSet()
        val commentsSurface = UiFeature.REELS_COMMENTS in features
        val playbackPaused = UiFeature.REELS_PAUSED in features
        val hasReelsEvidence = commentsSurface ||
            UiFeature.VIDEO_STRUCTURE in features ||
            (snapshot.keywordHits.isNotEmpty() && UiFeature.ACTION_RAIL in features)
        if (!hasReelsEvidence || snapshot.score <= 0) {
            return false
        }
        if (commentsSurface) {
            return true
        }

        // Instagram は MediaSession を公開しないことがあるため、画面内の Play 表示を停止の主証拠にする。
        return !playbackPaused
    }
}
