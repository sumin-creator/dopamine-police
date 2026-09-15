package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.UiFeature

internal object ShortsViewingPolicy {
    fun shouldCount(
        snapshot: DetectionSnapshot,
        mediaPlaybackActive: Boolean?,
    ): Boolean {
        if (ServiceTarget.fromPackage(snapshot.packageName) != ServiceTarget.YOUTUBE) {
            return false
        }
        val features = snapshot.uiFeatures.toSet()
        val commentsSurface = UiFeature.SHORTS_COMMENTS in features
        val playbackPaused = UiFeature.SHORTS_PAUSED in features
        val hasShortsEvidence = commentsSurface ||
            snapshot.keywordHits.isNotEmpty() ||
            UiFeature.ACTION_RAIL in features ||
            UiFeature.VIDEO_STRUCTURE in features
        if (!hasShortsEvidence || snapshot.score <= 0) {
            return false
        }
        if (commentsSurface) {
            return true
        }
        if (playbackPaused) {
            return false
        }

        // YouTube Shorts reports a stopped MediaSession while the video is visibly
        // playing on current app versions. Treat it as supporting evidence only;
        // the Shorts pause overlay is the authoritative paused signal.
        return mediaPlaybackActive != false || UiFeature.VIDEO_STRUCTURE in features
    }
}
