package com.ritesh.iykykcollage.ui

import com.ritesh.iykykcollage.collage.GeneratedCollage
import com.ritesh.iykykcollage.face.FaceDetectionSummary
import com.ritesh.iykykcollage.identity.AppearanceCountingResult
import com.ritesh.iykykcollage.identity.RepresentativeSelectionResult
import com.ritesh.iykykcollage.model.SelectedVideo
import com.ritesh.iykykcollage.tracking.FaceTrackletResult
import com.ritesh.iykykcollage.video.VideoMetadata

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

    data class PeopleCounted(
        val video: SelectedVideo,
        val metadata: VideoMetadata,
        val requestedFrames: Int,
        val decodedFrames: Int,
        val faceSummary: FaceDetectionSummary,
        val trackletResult: FaceTrackletResult,
        val appearanceResult: AppearanceCountingResult,
        val representativeResult: RepresentativeSelectionResult,
        val collage: GeneratedCollage?,
    ) : CollageUiState

    data class Failure(
        val video: SelectedVideo?,
        val message: String,
    ) : CollageUiState
}
