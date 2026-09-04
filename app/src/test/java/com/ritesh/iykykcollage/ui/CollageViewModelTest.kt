package com.ritesh.iykykcollage.ui

import com.ritesh.iykykcollage.face.FaceAnalyzer
import com.ritesh.iykykcollage.face.FrameFaceDetection
import com.ritesh.iykykcollage.tracking.SceneBoundaryDetector
import com.ritesh.iykykcollage.tracking.SceneChange
import com.ritesh.iykykcollage.video.FrameSamplingProgress
import com.ritesh.iykykcollage.video.SampledVideoFrame
import com.ritesh.iykykcollage.video.VideoFrameSampler
import com.ritesh.iykykcollage.video.VideoMetadata
import com.ritesh.iykykcollage.video.VideoSamplingException
import com.ritesh.iykykcollage.video.VideoSamplingResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollageViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingVideo_exposesReadyState() {
        val viewModel = createViewModel()

        viewModel.onVideoSelected(
            uri = "content://video/sample-1",
            displayName = "sample-1.mp4",
        )

        val state = viewModel.uiState.value
        assertTrue(state is CollageUiState.VideoReady)
        assertEquals("sample-1.mp4", (state as CollageUiState.VideoReady).video.displayName)
    }

    @Test
    fun clearingSelection_returnsToAwaitingVideo() {
        val viewModel = createViewModel()
        viewModel.onVideoSelected("content://video/sample-1", "sample-1.mp4")

        viewModel.onSelectionCleared()

        assertEquals(CollageUiState.AwaitingVideo, viewModel.uiState.value)
    }

    @Test
    fun analyzingVideo_exposesFaceTrackletSummary() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.onVideoSelected("content://video/sample-1", "sample-1.mp4")

        viewModel.onAnalyzeVideo()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollageUiState.TrackletsBuilt)
        state as CollageUiState.TrackletsBuilt
        assertEquals(120, state.requestedFrames)
        assertEquals(120, state.decodedFrames)
        assertEquals(1080, state.metadata.displayWidth)
        assertEquals(1920, state.metadata.displayHeight)
        assertEquals(0, state.faceSummary.totalFaceObservations)
        assertEquals(0, state.trackletResult.summary.totalTracklets)
    }

    @Test
    fun cancelingAnalysis_returnsToReadyState() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            sampler = FakeVideoFrameSampler(suspendUntilCanceled = true),
        )
        viewModel.onVideoSelected("content://video/sample-1", "sample-1.mp4")

        viewModel.onAnalyzeVideo()
        runCurrent()
        assertTrue(viewModel.uiState.value is CollageUiState.Processing)

        viewModel.onCancelProcessing()
        runCurrent()

        assertTrue(viewModel.uiState.value is CollageUiState.VideoReady)
    }

    @Test
    fun samplingFailure_exposesUsefulErrorState() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            sampler = FakeVideoFrameSampler(
                failure = VideoSamplingException("Video is unreadable"),
            ),
        )
        viewModel.onVideoSelected("content://video/broken", "broken.mp4")

        viewModel.onAnalyzeVideo()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollageUiState.Failure)
        assertEquals("Video is unreadable", (state as CollageUiState.Failure).message)
    }

    private fun createViewModel(
        sampler: VideoFrameSampler = FakeVideoFrameSampler(),
    ) = CollageViewModel(
        frameSampler = sampler,
        faceAnalyzer = FakeFaceAnalyzer,
        sceneBoundaryDetector = FakeSceneBoundaryDetector,
    )

    private data object FakeFaceAnalyzer : FaceAnalyzer {
        override suspend fun analyze(frame: SampledVideoFrame) = FrameFaceDetection(
            frameIndex = frame.index,
            timestampUs = frame.timestampUs,
            faces = emptyList(),
        )

        override fun close() = Unit
    }

    private data object FakeSceneBoundaryDetector : SceneBoundaryDetector {
        override fun analyze(frame: SampledVideoFrame) = SceneChange(
            isSceneBoundary = false,
            differenceScore = 0f,
        )

        override fun reset() = Unit
    }

    private class FakeVideoFrameSampler(
        private val suspendUntilCanceled: Boolean = false,
        private val failure: Throwable? = null,
    ) : VideoFrameSampler {
        override suspend fun sample(
            uri: String,
            onFrame: suspend (SampledVideoFrame) -> Unit,
            onProgress: (FrameSamplingProgress) -> Unit,
        ): VideoSamplingResult {
            failure?.let { throw it }
            onProgress(
                FrameSamplingProgress(
                    processedFrames = 120,
                    totalFrames = 120,
                    decodedFrames = 120,
                ),
            )
            if (suspendUntilCanceled) awaitCancellation()

            return VideoSamplingResult(
                metadata = VideoMetadata(
                    durationMs = 30_000,
                    encodedWidth = 1080,
                    encodedHeight = 1920,
                    rotationDegrees = 0,
                ),
                requestedFrames = 120,
                decodedFrames = 120,
            )
        }
    }
}
