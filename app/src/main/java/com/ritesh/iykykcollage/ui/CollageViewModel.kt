package com.ritesh.iykykcollage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.iykykcollage.face.FaceAnalyzer
import com.ritesh.iykykcollage.face.FaceDetectionAccumulator
import com.ritesh.iykykcollage.face.FaceEmbedder
import com.ritesh.iykykcollage.identity.FaceAppearanceCounter
import com.ritesh.iykykcollage.identity.FaceIdentityClusterer
import com.ritesh.iykykcollage.model.SelectedVideo
import com.ritesh.iykykcollage.tracking.FaceTrackletTracker
import com.ritesh.iykykcollage.tracking.SceneBoundaryDetector
import com.ritesh.iykykcollage.video.VideoFrameSampler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollageViewModel(
    private val frameSampler: VideoFrameSampler,
    private val faceAnalyzer: FaceAnalyzer,
    private val faceEmbedder: FaceEmbedder,
    private val sceneBoundaryDetector: SceneBoundaryDetector,
    private val identityClusterer: FaceIdentityClusterer,
    private val appearanceCounter: FaceAppearanceCounter,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CollageUiState>(CollageUiState.AwaitingVideo)
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()
    private var processingJob: Job? = null

    fun onVideoSelected(uri: String, displayName: String) {
        processingJob?.cancel()
        _uiState.value = CollageUiState.VideoReady(
            SelectedVideo(
                uri = uri,
                displayName = displayName,
            ),
        )
    }

    fun onSelectionCleared() {
        processingJob?.cancel()
        _uiState.value = CollageUiState.AwaitingVideo
    }

    fun onAnalyzeVideo() {
        val video = when (val state = _uiState.value) {
            is CollageUiState.VideoReady -> state.video
            is CollageUiState.PeopleCounted -> state.video
            else -> return
        }

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.value = CollageUiState.Processing(
                video = video,
                stage = "Reading video metadata",
                progress = 0f,
            )

            try {
                val faceAccumulator = FaceDetectionAccumulator()
                val trackletTracker = FaceTrackletTracker()
                sceneBoundaryDetector.reset()
                val result = frameSampler.sample(
                    uri = video.uri,
                    onFrame = { frame ->
                        val sceneChange = sceneBoundaryDetector.analyze(frame)
                        val detection = faceAnalyzer.analyze(frame)
                        faceAccumulator.add(detection)
                        val embeddedDetection = if (sceneChange.isSceneBoundary) {
                            detection
                        } else {
                            faceEmbedder.embed(frame, detection)
                        }
                        trackletTracker.add(
                            frame = embeddedDetection,
                            isSceneTransitionFrame = sceneChange.isSceneBoundary,
                        )
                    },
                    onProgress = { progress ->
                        _uiState.value = CollageUiState.Processing(
                            video = video,
                            stage = "Generating face embeddings • frame ${progress.processedFrames} of ${progress.totalFrames}",
                            progress = progress.fraction,
                        )
                    },
                )

                val trackletResult = trackletTracker.finish()
                _uiState.value = CollageUiState.Processing(
                    video = video,
                    stage = "Grouping appearances by person",
                    progress = 1f,
                )
                val appearanceResult = withContext(processingDispatcher) {
                    appearanceCounter.count(
                        identityClusterer.cluster(trackletResult.tracklets),
                    )
                }
                _uiState.value = CollageUiState.PeopleCounted(
                    video = video,
                    metadata = result.metadata,
                    requestedFrames = result.requestedFrames,
                    decodedFrames = result.decodedFrames,
                    faceSummary = faceAccumulator.snapshot(),
                    trackletResult = trackletResult,
                    appearanceResult = appearanceResult,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = CollageUiState.Failure(
                    video = video,
                    message = error.message ?: "An unexpected video error occurred.",
                )
            }
        }
    }

    fun onCancelProcessing() {
        val video = (_uiState.value as? CollageUiState.Processing)?.video ?: return
        processingJob?.cancel()
        _uiState.value = CollageUiState.VideoReady(video)
    }

    override fun onCleared() {
        val activeJob = processingJob
        activeJob?.cancel()
        if (activeJob == null || activeJob.isCompleted) {
            closeVisionProcessors()
        } else {
            activeJob.invokeOnCompletion { closeVisionProcessors() }
        }
        super.onCleared()
    }

    private fun closeVisionProcessors() {
        try {
            faceAnalyzer.close()
        } finally {
            faceEmbedder.close()
        }
    }
}
