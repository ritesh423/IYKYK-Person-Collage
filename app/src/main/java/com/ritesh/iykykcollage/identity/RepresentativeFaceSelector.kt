package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.face.FaceBounds
import com.ritesh.iykykcollage.face.FaceObservation
import kotlin.math.abs

data class PersonRepresentative(
    val personId: Int,
    val observation: FaceObservation,
    val qualityScore: Float,
)

data class RepresentativeSelectionResult(
    val representatives: List<PersonRepresentative>,
) {
    val selectedCount: Int get() = representatives.size
}

class RepresentativeFaceSelector {
    fun select(result: AppearanceCountingResult): RepresentativeSelectionResult {
        val representatives = result.people.mapNotNull { person ->
            val embeddedFaces = person.identity.tracklets
                .flatMap { it.observations }
                .filter { it.embedding != null }
            val preferredFaces = embeddedFaces
                .filter { it.quality.eligibleAsRepresentative }
                .ifEmpty { embeddedFaces }
            val selected = preferredFaces.maxByOrNull(::qualityScore) ?: return@mapNotNull null

            PersonRepresentative(
                personId = person.id,
                observation = selected,
                qualityScore = qualityScore(selected),
            )
        }

        return RepresentativeSelectionResult(representatives)
    }

    private fun qualityScore(face: FaceObservation): Float {
        val frontality = listOf(
            angleScore(face.headEulerAngleX, MAXIMUM_PITCH_DEGREES),
            angleScore(face.headEulerAngleY, MAXIMUM_YAW_DEGREES),
            angleScore(face.headEulerAngleZ, MAXIMUM_ROLL_DEGREES),
        ).average().toFloat()
        val openEyes = listOfNotNull(
            face.leftEyeOpenProbability,
            face.rightEyeOpenProbability,
        ).averageOrZero()
        val smile = face.smilingProbability?.coerceIn(0f, 1f) ?: 0f
        val visibility = face.bounds.visibleFraction(face.frameWidth, face.frameHeight)
        val sharpness = face.sharpnessScore
            ?.coerceAtLeast(0f)
            ?.let { it / (it + SHARPNESS_HALF_SCORE) }
            ?: 0f

        return sharpness * 0.30f +
            frontality * 0.25f +
            openEyes * 0.20f +
            visibility * 0.15f +
            smile * 0.10f
    }

    private fun angleScore(angle: Float, maximum: Float): Float =
        (1f - abs(angle) / maximum).coerceIn(0f, 1f)

    private fun List<Float>.averageOrZero(): Float =
        if (isEmpty()) 0f else average().toFloat().coerceIn(0f, 1f)

    private fun FaceBounds.visibleFraction(frameWidth: Int, frameHeight: Int): Float {
        if (area == 0L) return 0f
        val visibleWidth = (right.coerceIn(0, frameWidth) - left.coerceIn(0, frameWidth))
            .coerceAtLeast(0)
        val visibleHeight = (bottom.coerceIn(0, frameHeight) - top.coerceIn(0, frameHeight))
            .coerceAtLeast(0)
        return (visibleWidth.toLong() * visibleHeight / area.toFloat()).coerceIn(0f, 1f)
    }

    private companion object {
        const val MAXIMUM_PITCH_DEGREES = 20f
        const val MAXIMUM_YAW_DEGREES = 18f
        const val MAXIMUM_ROLL_DEGREES = 20f
        const val SHARPNESS_HALF_SCORE = 100f
    }
}
