package dev.shortblocker.app.domain

import dev.shortblocker.app.data.DetectionSnapshot
import dev.shortblocker.app.data.ServiceTarget

internal object ShortVideoViewingPolicy {
    fun shouldCount(
        snapshot: DetectionSnapshot,
        mediaPlaybackActive: Boolean?,
    ): Boolean = when (ServiceTarget.fromPackage(snapshot.packageName)) {
        ServiceTarget.YOUTUBE -> ShortsViewingPolicy.shouldCount(snapshot, mediaPlaybackActive)
        ServiceTarget.INSTAGRAM -> InstagramReelsViewingPolicy.shouldCount(snapshot, mediaPlaybackActive)
        else -> false
    }
}
