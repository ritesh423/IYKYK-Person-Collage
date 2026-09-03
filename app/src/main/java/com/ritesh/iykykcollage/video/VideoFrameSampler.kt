package com.ritesh.iykykcollage.video

import android.graphics.Bitmap

data class SampledVideoFrame(
    val bitmap: Bitmap,
    val index: Int,
    val timestampUs: Long,
)

data class FrameSamplingProgress(
    val processedFrames: Int,
    val totalFrames: Int,
    val decodedFrames: Int,
) {
    val fraction: Float
        get() = if (totalFrames == 0) 0f else processedFrames.toFloat() / totalFrames
}

data class VideoSamplingResult(
    val metadata: VideoMetadata,
    val requestedFrames: Int,
    val decodedFrames: Int,
)

interface VideoFrameSampler {
    /**
     * Decodes frames sequentially. A frame bitmap is valid only while [onFrame] is running;
     * the sampler recycles it immediately after the callback returns.
     */
    suspend fun sample(
        uri: String,
        onFrame: suspend (SampledVideoFrame) -> Unit,
        onProgress: (FrameSamplingProgress) -> Unit,
    ): VideoSamplingResult
}

class VideoSamplingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
