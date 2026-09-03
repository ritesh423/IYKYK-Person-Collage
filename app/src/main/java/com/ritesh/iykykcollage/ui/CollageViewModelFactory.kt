package com.ritesh.iykykcollage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ritesh.iykykcollage.video.VideoFrameSampler

class CollageViewModelFactory(
    private val frameSampler: VideoFrameSampler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollageViewModel::class.java)) {
            return CollageViewModel(frameSampler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
