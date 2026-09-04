package com.ritesh.iykykcollage.tracking

import android.graphics.Bitmap
import com.ritesh.iykykcollage.video.SampledVideoFrame
import kotlin.math.abs

data class SceneChange(
    val isSceneBoundary: Boolean,
    val differenceScore: Float,
)

interface SceneBoundaryDetector {
    fun analyze(frame: SampledVideoFrame): SceneChange

    fun reset()
}

data class SceneBoundaryPolicy(
    val differenceThreshold: Float = 0.14f,
    val minimumFramesBetweenBoundaries: Int = 3,
    val signatureWidth: Int = 12,
    val signatureHeight: Int = 20,
) {
    init {
        require(differenceThreshold in 0f..1f) { "Difference threshold must be between 0 and 1" }
        require(minimumFramesBetweenBoundaries >= 0) { "Boundary cooldown cannot be negative" }
        require(signatureWidth > 0 && signatureHeight > 0) { "Signature dimensions must be positive" }
    }
}

class SceneBoundaryDecider(
    private val policy: SceneBoundaryPolicy = SceneBoundaryPolicy(),
) {
    private var previousSignature: IntArray? = null
    private var framesSinceBoundary: Int? = null

    fun observe(signature: IntArray): SceneChange {
        require(signature.isNotEmpty()) { "Scene signature cannot be empty" }
        val previous = previousSignature
        if (previous == null) {
            previousSignature = signature.copyOf()
            return SceneChange(isSceneBoundary = false, differenceScore = 0f)
        }

        framesSinceBoundary = framesSinceBoundary?.plus(1)
        val difference = meanAbsoluteRgbDifference(previous, signature)
        val outsideCooldown =
            framesSinceBoundary == null ||
                framesSinceBoundary!! >= policy.minimumFramesBetweenBoundaries
        val isBoundary = difference >= policy.differenceThreshold && outsideCooldown

        if (isBoundary) framesSinceBoundary = 0
        previousSignature = signature.copyOf()
        return SceneChange(
            isSceneBoundary = isBoundary,
            differenceScore = difference,
        )
    }

    fun reset() {
        previousSignature = null
        framesSinceBoundary = null
    }
}

class BitmapSceneBoundaryDetector(
    private val policy: SceneBoundaryPolicy = SceneBoundaryPolicy(),
    private val decider: SceneBoundaryDecider = SceneBoundaryDecider(policy),
) : SceneBoundaryDetector {
    override fun analyze(frame: SampledVideoFrame): SceneChange {
        val thumbnail = Bitmap.createScaledBitmap(
            frame.bitmap,
            policy.signatureWidth,
            policy.signatureHeight,
            true,
        )
        return try {
            val signature = IntArray(policy.signatureWidth * policy.signatureHeight)
            thumbnail.getPixels(
                signature,
                0,
                policy.signatureWidth,
                0,
                0,
                policy.signatureWidth,
                policy.signatureHeight,
            )
            decider.observe(signature)
        } finally {
            if (thumbnail !== frame.bitmap && !thumbnail.isRecycled) thumbnail.recycle()
        }
    }

    override fun reset() = decider.reset()
}

internal fun meanAbsoluteRgbDifference(first: IntArray, second: IntArray): Float {
    require(first.size == second.size) { "Scene signatures must have the same size" }
    require(first.isNotEmpty()) { "Scene signatures cannot be empty" }

    var differenceSum = 0L
    first.indices.forEach { index ->
        val firstColor = first[index]
        val secondColor = second[index]
        differenceSum += abs(((firstColor ushr 16) and 0xff) - ((secondColor ushr 16) and 0xff))
        differenceSum += abs(((firstColor ushr 8) and 0xff) - ((secondColor ushr 8) and 0xff))
        differenceSum += abs((firstColor and 0xff) - (secondColor and 0xff))
    }

    return differenceSum.toFloat() / (first.size * 3f * 255f)
}
