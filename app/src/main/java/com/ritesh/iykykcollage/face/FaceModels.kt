package com.ritesh.iykykcollage.face

data class FaceBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height
}

data class FacePoint(
    val x: Float,
    val y: Float,
)

enum class FaceQualityIssue {
    TOO_SMALL_FOR_MATCHING,
    TOO_SMALL_FOR_REPRESENTATIVE,
    PARTIALLY_OUTSIDE_FRAME,
    CLOSE_TO_FRAME_EDGE,
    EXTREME_POSE,
    NOT_FRONTAL,
    EYES_NOT_CONFIDENTLY_OPEN,
}

data class FaceQualityAssessment(
    val usableForMatching: Boolean,
    val eligibleAsRepresentative: Boolean,
    val issues: Set<FaceQualityIssue>,
)

data class FaceObservation(
    val frameIndex: Int,
    val timestampUs: Long,
    val frameWidth: Int,
    val frameHeight: Int,
    val bounds: FaceBounds,
    val trackingId: Int?,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val quality: FaceQualityAssessment,
    val leftEyePosition: FacePoint? = null,
    val rightEyePosition: FacePoint? = null,
    val embedding: FaceEmbedding? = null,
    val sharpnessScore: Float? = null,
    val fromSideBySideLayout: Boolean = false,
)

data class FrameFaceDetection(
    val frameIndex: Int,
    val timestampUs: Long,
    val faces: List<FaceObservation>,
)

data class FaceDetectionSummary(
    val analyzedFrames: Int,
    val framesWithFaces: Int,
    val totalFaceObservations: Int,
    val matchingCandidates: Int,
    val representativeCandidates: Int,
    val maxFacesInOneFrame: Int,
)

class FaceDetectionAccumulator {
    private var analyzedFrames = 0
    private var framesWithFaces = 0
    private var totalFaceObservations = 0
    private var matchingCandidates = 0
    private var representativeCandidates = 0
    private var maxFacesInOneFrame = 0

    fun add(result: FrameFaceDetection) {
        analyzedFrames += 1
        if (result.faces.isNotEmpty()) framesWithFaces += 1
        totalFaceObservations += result.faces.size
        matchingCandidates += result.faces.count { it.quality.usableForMatching }
        representativeCandidates += result.faces.count { it.quality.eligibleAsRepresentative }
        maxFacesInOneFrame = maxOf(maxFacesInOneFrame, result.faces.size)
    }

    fun snapshot(): FaceDetectionSummary = FaceDetectionSummary(
        analyzedFrames = analyzedFrames,
        framesWithFaces = framesWithFaces,
        totalFaceObservations = totalFaceObservations,
        matchingCandidates = matchingCandidates,
        representativeCandidates = representativeCandidates,
        maxFacesInOneFrame = maxFacesInOneFrame,
    )
}
