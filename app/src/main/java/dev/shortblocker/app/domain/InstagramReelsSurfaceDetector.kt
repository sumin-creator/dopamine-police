package dev.shortblocker.app.domain

import java.util.Locale

/**
 * Instagram の通常フィードと Reels ビューアを、公開されている Accessibility 情報だけで分離する。
 * 文言よりも Reels 固有の view ID を優先し、ホーム上部の Reels トレイは検知対象にしない。
 */
internal class InstagramReelsSurfaceDetector {
    fun analyze(signals: EventSignals): ViewerSurfaceSignals {
        val hasViewerPager = signals.hasViewId(CLIPS_VIEWER_VIEW_PAGER)
        val hasMedia = signals.hasAnyViewId(CLIPS_MEDIA_COMPONENT, CLIPS_VIDEO_CONTAINER)
        val viewerEvidence = hasViewerPager && hasMedia
        val playbackControlLabels = signals.nodes
            .filter { node ->
                node.hasViewId(CLIPS_PAUSE_BUTTON) && node.visibleToUser != false
            }
            .map(SignalNode::normalizedText)
        val playbackPaused = playbackControlLabels.any { label ->
            PAUSED_CONTROL_LABELS.any(label::contains)
        }
        val playbackResumed = playbackControlLabels.any { label ->
            PLAYING_CONTROL_LABELS.any(label::contains)
        }
        val commentsSurface = signals.hasViewId(COMMENT_COMPOSER) ||
            (signals.hasViewId(LAYOUT_CONTAINER_BOTTOM_SHEET) &&
                signals.hasAnyViewId(MAIN_LIST_VIEW, COMMENTS_LIST))

        if (!viewerEvidence) {
            return ViewerSurfaceSignals(
                commentsSurface = commentsSurface,
                playbackPaused = playbackPaused,
                playbackResumed = playbackResumed,
                normalVideoUiDetected = signals.hasAnyViewId(*NON_REELS_SURFACE_IDS),
            )
        }

        val actionHints = buildSet {
            ACTION_VIEW_IDS.forEach { (viewId, label) ->
                if (signals.hasViewId(viewId)) add(label)
            }
            ACTION_LABELS.forEach { (label, hints) ->
                if (signals.nodes.any { node ->
                        hints.any { hint -> node.normalizedText.contains(hint) }
                    }
                ) {
                    add(label)
                }
            }
        }
        return ViewerSurfaceSignals(
            keywordHits = setOf("Reels", "ui:clips"),
            actionHints = actionHints,
            viewerEvidence = true,
            commentsSurface = commentsSurface,
            playbackPaused = playbackPaused,
            playbackResumed = playbackResumed,
        )
    }

    private fun EventSignals.hasViewId(expected: String): Boolean = allViewIds.any { viewId ->
        val normalizedViewId = viewId.lowercase(Locale.US)
        normalizedViewId.endsWith(":id/$expected") || normalizedViewId == expected
    }

    private fun EventSignals.hasAnyViewId(vararg expected: String): Boolean =
        expected.any { viewId -> hasViewId(viewId) }

    private fun SignalNode.hasViewId(expected: String): Boolean =
        normalizedViewId.endsWith(":id/$expected") || normalizedViewId == expected

    private companion object {
        const val CLIPS_VIEWER_VIEW_PAGER = "clips_viewer_view_pager"
        const val CLIPS_MEDIA_COMPONENT = "clips_media_component"
        const val CLIPS_VIDEO_CONTAINER = "clips_video_container"
        const val CLIPS_PAUSE_BUTTON = "clips_pause_button"
        const val COMMENT_COMPOSER = "comment_composer_parent_updated"
        const val LAYOUT_CONTAINER_BOTTOM_SHEET = "layout_container_bottom_sheet"
        const val MAIN_LIST_VIEW = "main_list_view"
        const val COMMENTS_LIST = "sticky_header_list"

        val ACTION_VIEW_IDS = mapOf(
            "like_button" to "like",
            "comment_button" to "comment",
            "direct_share_button" to "share",
            "save_button" to "save",
        )
        val ACTION_LABELS = mapOf(
            "like" to listOf("like", "いいね"),
            "comment" to listOf("comment", "コメント"),
            "share" to listOf("share", "シェア"),
            "save" to listOf("save", "保存"),
        )
        val NON_REELS_SURFACE_IDS = arrayOf(
            "reels_tray_container",
            "row_feed_photo_imageview",
            "row_feed_profile_header",
            "explore_grid",
            "explore_recycler_view",
            "list",
        )
        val PAUSED_CONTROL_LABELS = listOf("play", "再生")
        val PLAYING_CONTROL_LABELS = listOf("pause", "一時停止")
    }
}
