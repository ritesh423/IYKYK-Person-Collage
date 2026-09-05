package com.ritesh.iykykcollage.face

import kotlin.math.hypot

data class FaceAlignmentPolicy(
    val modelInputSize: Int = 112,
    val eyeDistanceToCropRatio: Float = 1f / 2.5f,
    val eyeLineFromTopRatio: Float = 0.35f,
    val minimumEyeDistancePx: Float = 8f,
    val fallbackBoundsExpansion: Float = 1.25f,
) {
    init {
        require(modelInputSize > 0) { "Model input size must be positive" }
        require(eyeDistanceToCropRatio.isFinite() && eyeDistanceToCropRatio > 0f && eyeDistanceToCropRatio <= 1f) {
            "Eye-distance ratio must be greater than 0 and at most 1"
        }
        require(eyeLineFromTopRatio in 0f..1f) { "Eye-line ratio must be between 0 and 1" }
        require(minimumEyeDistancePx.isFinite() && minimumEyeDistancePx > 0f) {
            "Minimum eye distance must be finite and positive"
        }
        require(fallbackBoundsExpansion.isFinite() && fallbackBoundsExpansion >= 1f) {
            "Fallback crop expansion must be finite and cannot shrink the face bounds"
        }
    }
}

sealed interface FaceAlignmentPlan {
    data class EyeAligned(
        val sourceImageLeftEye: FacePoint,
        val sourceImageRightEye: FacePoint,
        val targetImageLeftEye: FacePoint,
        val targetImageRightEye: FacePoint,
    ) : FaceAlignmentPlan

    data class BoundsFallback(
        val sourceLeft: Float,
        val sourceTop: Float,
        val sourceRight: Float,
        val sourceBottom: Float,
    ) : FaceAlignmentPlan
}

class FaceAlignmentPlanner(
    private val policy: FaceAlignmentPolicy = FaceAlignmentPolicy(),
) {
    val outputSize: Int get() = policy.modelInputSize

    fun plan(face: FaceObservation): FaceAlignmentPlan {
        val firstEye = face.leftEyePosition
        val secondEye = face.rightEyePosition
        if (firstEye != null && secondEye != null) {
            val imageLeftEye = if (firstEye.x <= secondEye.x) firstEye else secondEye
            val imageRightEye = if (firstEye.x <= secondEye.x) secondEye else firstEye
            val eyeDistance = hypot(
                imageRightEye.x - imageLeftEye.x,
                imageRightEye.y - imageLeftEye.y,
            )
            if (eyeDistance >= policy.minimumEyeDistancePx) {
                val targetSize = policy.modelInputSize.toFloat()
                val targetEyeDistance = targetSize * policy.eyeDistanceToCropRatio
                val targetCenterX = targetSize / 2f
                val targetEyeY = targetSize * policy.eyeLineFromTopRatio
                return FaceAlignmentPlan.EyeAligned(
                    sourceImageLeftEye = imageLeftEye,
                    sourceImageRightEye = imageRightEye,
                    targetImageLeftEye = FacePoint(
                        x = targetCenterX - targetEyeDistance / 2f,
                        y = targetEyeY,
                    ),
                    targetImageRightEye = FacePoint(
                        x = targetCenterX + targetEyeDistance / 2f,
                        y = targetEyeY,
                    ),
                )
            }
        }

        val side = maxOf(face.bounds.width, face.bounds.height) * policy.fallbackBoundsExpansion
        val centerX = (face.bounds.left + face.bounds.right) / 2f
        val centerY = (face.bounds.top + face.bounds.bottom) / 2f
        return FaceAlignmentPlan.BoundsFallback(
            sourceLeft = centerX - side / 2f,
            sourceTop = centerY - side / 2f,
            sourceRight = centerX + side / 2f,
            sourceBottom = centerY + side / 2f,
        )
    }
}

internal fun Int.normalizedRightAngleRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270) {
        normalized
    } else {
        0
    }
}
