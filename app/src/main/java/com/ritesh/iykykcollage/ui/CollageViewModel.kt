package com.ritesh.iykykcollage.ui

import androidx.lifecycle.ViewModel
import com.ritesh.iykykcollage.model.SelectedVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CollageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CollageUiState>(CollageUiState.AwaitingVideo)
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: String, displayName: String) {
        _uiState.value = CollageUiState.VideoReady(
            SelectedVideo(
                uri = uri,
                displayName = displayName,
            ),
        )
    }

    fun onSelectionCleared() {
        _uiState.value = CollageUiState.AwaitingVideo
    }
}

