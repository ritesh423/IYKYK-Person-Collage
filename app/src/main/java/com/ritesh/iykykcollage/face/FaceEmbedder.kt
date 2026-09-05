package com.ritesh.iykykcollage.face

import com.ritesh.iykykcollage.video.SampledVideoFrame

interface FaceEmbedder : AutoCloseable {
    /**
     * Adds embeddings to matching-quality faces while the frame bitmap is still valid.
     */
    suspend fun embed(
        frame: SampledVideoFrame,
        detection: FrameFaceDetection,
    ): FrameFaceDetection

    override fun close()
}

class FaceEmbeddingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
