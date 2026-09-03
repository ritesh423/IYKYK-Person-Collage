package com.ritesh.iykykcollage.ui

import com.ritesh.iykykcollage.model.SelectedVideo

sealed interface CollageUiState {
    data object AwaitingVideo : CollageUiState

    data class VideoReady(
        val video: SelectedVideo,
    ) : CollageUiState

    data class Processing(
        val video: SelectedVideo,
        val stage: String,
        val progress: Float,
    ) : CollageUiState

    data class Failure(
        val video: SelectedVideo?,
        val message: String,
    ) : CollageUiState
}

