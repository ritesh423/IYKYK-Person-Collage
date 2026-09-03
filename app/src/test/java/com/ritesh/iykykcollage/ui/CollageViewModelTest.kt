package com.ritesh.iykykcollage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageViewModelTest {
    @Test
    fun selectingVideo_exposesReadyState() {
        val viewModel = CollageViewModel()

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
        val viewModel = CollageViewModel()
        viewModel.onVideoSelected("content://video/sample-1", "sample-1.mp4")

        viewModel.onSelectionCleared()

        assertEquals(CollageUiState.AwaitingVideo, viewModel.uiState.value)
    }
}

