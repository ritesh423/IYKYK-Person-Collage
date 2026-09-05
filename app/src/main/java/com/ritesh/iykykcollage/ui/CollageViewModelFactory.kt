package com.ritesh.iykykcollage.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ritesh.iykykcollage.face.LiteRtFaceEmbedder
import com.ritesh.iykykcollage.face.MlKitFaceAnalyzer
import com.ritesh.iykykcollage.tracking.BitmapSceneBoundaryDetector
import com.ritesh.iykykcollage.video.AndroidVideoFrameSampler

class CollageViewModelFactory(
    context: Context,
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollageViewModel::class.java)) {
            return CollageViewModel(
                frameSampler = AndroidVideoFrameSampler(appContext),
                faceAnalyzer = MlKitFaceAnalyzer(),
                faceEmbedder = LiteRtFaceEmbedder(appContext),
                sceneBoundaryDetector = BitmapSceneBoundaryDetector(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
