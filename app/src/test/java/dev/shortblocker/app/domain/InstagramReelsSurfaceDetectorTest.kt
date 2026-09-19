package dev.shortblocker.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramReelsSurfaceDetectorTest {
    private val detector = InstagramReelsSurfaceDetector()

    @Test
    fun normalFeedReelsTrayIsNotAReelsViewer() {
        val result = detector.analyze(
            EventSignals(
                viewIds = setOf(
                    instagramId("reels_tray_container"),
                    instagramId("row_feed_photo_imageview"),
                    instagramId("like_button"),
                    instagramId("comment_button"),
                ),
            ),
        )

        assertFalse(result.viewerEvidence)
        assertTrue(result.normalVideoUiDetected)
    }

    @Test
    fun exactClipsStructureDetectsViewerAndPauseState() {
        val result = detector.analyze(
            reelsSignals(
                SignalNode(
                    text = "Play",
                    viewId = instagramId("clips_pause_button"),
                ),
            ),
        )

        assertTrue(result.viewerEvidence)
        assertTrue(result.actionHints.containsAll(setOf("like", "comment", "share")))
        assertTrue(result.playbackPaused)
    }

    @Test
    fun pauseControlLabelMarksPlaybackAsResumed() {
        val result = detector.analyze(
            reelsSignals(
                SignalNode(
                    text = "Pause",
                    viewId = instagramId("clips_pause_button"),
                ),
            ),
        )

        assertTrue(result.playbackResumed)
        assertFalse(result.playbackPaused)
    }

    @Test
    fun invisiblePlayControlDoesNotMarkPlaybackAsPaused() {
        val result = detector.analyze(
            reelsSignals(
                SignalNode(
                    text = "Play",
                    viewId = instagramId("clips_pause_button"),
                    visibleToUser = false,
                ),
            ),
        )

        assertTrue(result.viewerEvidence)
        assertFalse(result.playbackPaused)
    }

    @Test
    fun commentsSheetRemainsPartOfTheReelsViewer() {
        val result = detector.analyze(
            reelsSignals(
                SignalNode(viewId = instagramId("layout_container_bottom_sheet")),
                SignalNode(viewId = instagramId("main_list_view")),
                SignalNode(viewId = instagramId("comment_composer_parent_updated")),
            ),
        )

        assertTrue(result.viewerEvidence)
        assertTrue(result.commentsSurface)
    }

    @Test
    fun commentsSheetCanBeRecognizedWhenItHidesTheViewerStructure() {
        val result = detector.analyze(
            EventSignals(
                nodes = listOf(
                    SignalNode(viewId = instagramId("layout_container_bottom_sheet")),
                    SignalNode(viewId = instagramId("main_list_view")),
                ),
            ),
        )

        assertFalse(result.viewerEvidence)
        assertTrue(result.commentsSurface)
    }

    private fun reelsSignals(vararg extraNodes: SignalNode): EventSignals = EventSignals(
        nodes = listOf(
            SignalNode(viewId = instagramId("clips_viewer_view_pager")),
            SignalNode(viewId = instagramId("clips_media_component")),
            SignalNode(text = "Like number is 20486. View likes"),
            SignalNode(text = "Comment number is 333. View comments"),
            SignalNode(text = "Share"),
        ) + extraNodes,
    )

    private fun instagramId(id: String): String = "com.instagram.android:id/$id"
}
