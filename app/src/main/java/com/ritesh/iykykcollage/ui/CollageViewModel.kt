package com.ritesh.iykykcollage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.iykykcollage.face.FaceAnalyzer
import com.ritesh.iykykcollage.face.FaceDetectionAccumulator
import com.ritesh.iykykcollage.model.SelectedVideo
import com.ritesh.iykykcollage.video.VideoFrameSampler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CollageViewModel(
    private val frameSampler: VideoFrameSampler,
    private val faceAnalyzer: FaceAnalyzer,
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
            is CollageUiState.FacesDetected -> state.video
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
                val result = frameSampler.sample(
                    uri = video.uri,
                    onFrame = { frame ->
                        faceAccumulator.add(faceAnalyzer.analyze(frame))
                    },
                    onProgress = { progress ->
                        _uiState.value = CollageUiState.Processing(
                            video = video,
                            stage = "Detecting faces • frame ${progress.processedFrames} of ${progress.totalFrames}",
                            progress = progress.fraction,
                        )
                    },
                )

                _uiState.value = CollageUiState.FacesDetected(
                    video = video,
                    metadata = result.metadata,
                    requestedFrames = result.requestedFrames,
                    decodedFrames = result.decodedFrames,
                    faceSummary = faceAccumulator.snapshot(),
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
            faceAnalyzer.close()
        } else {
            activeJob.invokeOnCompletion { faceAnalyzer.close() }
        }
        super.onCleared()
    }
}
