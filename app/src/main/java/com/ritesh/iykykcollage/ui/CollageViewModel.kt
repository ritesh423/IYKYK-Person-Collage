package com.ritesh.iykykcollage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            is CollageUiState.FramesSampled -> state.video
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
                val result = frameSampler.sample(
                    uri = video.uri,
                    onFrame = {
                        // Face detection will consume each temporary bitmap in Milestone 3.
                    },
                    onProgress = { progress ->
                        _uiState.value = CollageUiState.Processing(
                            video = video,
                            stage = "Sampling frame ${progress.processedFrames} of ${progress.totalFrames}",
                            progress = progress.fraction,
                        )
                    },
                )

                _uiState.value = CollageUiState.FramesSampled(
                    video = video,
                    metadata = result.metadata,
                    requestedFrames = result.requestedFrames,
                    decodedFrames = result.decodedFrames,
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
}
