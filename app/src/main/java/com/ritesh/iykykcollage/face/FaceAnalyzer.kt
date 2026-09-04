package com.ritesh.iykykcollage.face

import com.ritesh.iykykcollage.video.SampledVideoFrame

interface FaceAnalyzer : AutoCloseable {
    suspend fun analyze(frame: SampledVideoFrame): FrameFaceDetection

    override fun close()
}

class FaceAnalysisException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
