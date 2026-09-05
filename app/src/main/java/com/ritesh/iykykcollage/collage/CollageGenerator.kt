package com.ritesh.iykykcollage.collage

import android.graphics.Bitmap
import com.ritesh.iykykcollage.identity.AppearanceCountingResult
import com.ritesh.iykykcollage.identity.RepresentativeSelectionResult

data class GeneratedCollage(
    val bitmap: Bitmap,
    val peopleCount: Int,
    val appearanceCount: Int,
)

interface CollageGenerator {
    suspend fun generate(
        videoUri: String,
        representatives: RepresentativeSelectionResult,
        appearances: AppearanceCountingResult,
    ): GeneratedCollage
}

class CollageGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
