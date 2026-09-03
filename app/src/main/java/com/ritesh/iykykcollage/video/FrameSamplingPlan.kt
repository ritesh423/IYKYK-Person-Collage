package com.ritesh.iykykcollage.video

import kotlin.math.roundToInt

data class FrameSamplingPolicy(
    val framesPerSecond: Int = DEFAULT_FRAMES_PER_SECOND,
    val targetLongEdgePx: Int = DEFAULT_TARGET_LONG_EDGE_PX,
) {
    init {
        require(framesPerSecond in 1..30) { "framesPerSecond must be between 1 and 30" }
        require(targetLongEdgePx > 0) { "targetLongEdgePx must be positive" }
    }

    companion object {
        const val DEFAULT_FRAMES_PER_SECOND = 4
        const val DEFAULT_TARGET_LONG_EDGE_PX = 960
    }
}

data class ScaledFrameSize(
    val width: Int,
    val height: Int,
)

object FrameSamplingPlan {
    fun timestampsUs(durationMs: Long, framesPerSecond: Int): List<Long> {
        require(durationMs > 0) { "durationMs must be positive" }
        require(framesPerSecond in 1..30) { "framesPerSecond must be between 1 and 30" }

        val durationUs = durationMs * MICROSECONDS_PER_MILLISECOND
        val intervalUs = MICROSECONDS_PER_SECOND / framesPerSecond

        return generateSequence(0L) { previous -> previous + intervalUs }
            .takeWhile { timestampUs -> timestampUs < durationUs }
            .toList()
    }

    fun scaledSize(width: Int, height: Int, targetLongEdgePx: Int): ScaledFrameSize {
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
        require(targetLongEdgePx > 0) { "targetLongEdgePx must be positive" }

        val longestEdge = maxOf(width, height)
        if (longestEdge <= targetLongEdgePx) {
            return ScaledFrameSize(width, height)
        }

        val scale = targetLongEdgePx.toFloat() / longestEdge
        return ScaledFrameSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private const val MICROSECONDS_PER_MILLISECOND = 1_000L
    private const val MICROSECONDS_PER_SECOND = 1_000_000L
}
