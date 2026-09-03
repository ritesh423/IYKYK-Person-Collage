package com.ritesh.iykykcollage.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AndroidVideoFrameSampler(
    context: Context,
    private val policy: FrameSamplingPolicy = FrameSamplingPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VideoFrameSampler {
    private val appContext = context.applicationContext

    override suspend fun sample(
        uri: String,
        onFrame: suspend (SampledVideoFrame) -> Unit,
        onProgress: (FrameSamplingProgress) -> Unit,
    ): VideoSamplingResult = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(appContext, uri.toUri())
            val metadata = retriever.readMetadata()
            val timestampsUs = FrameSamplingPlan.timestampsUs(
                durationMs = metadata.durationMs,
                framesPerSecond = policy.framesPerSecond,
            )
            val targetSize = FrameSamplingPlan.scaledSize(
                width = metadata.encodedWidth,
                height = metadata.encodedHeight,
                targetLongEdgePx = policy.targetLongEdgePx,
            )

            var decodedFrames = 0
            timestampsUs.forEachIndexed { index, timestampUs ->
                coroutineContext.ensureActive()

                val bitmap = retriever.frameAt(
                    timestampUs = timestampUs,
                    targetSize = targetSize,
                )

                if (bitmap != null) {
                    try {
                        onFrame(
                            SampledVideoFrame(
                                bitmap = bitmap,
                                index = index,
                                timestampUs = timestampUs,
                            ),
                        )
                        decodedFrames += 1
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }

                onProgress(
                    FrameSamplingProgress(
                        processedFrames = index + 1,
                        totalFrames = timestampsUs.size,
                        decodedFrames = decodedFrames,
                    ),
                )
            }

            if (decodedFrames == 0) {
                throw VideoSamplingException("Android could not decode any frames from this video.")
            }

            VideoSamplingResult(
                metadata = metadata,
                requestedFrames = timestampsUs.size,
                decodedFrames = decodedFrames,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: VideoSamplingException) {
            throw error
        } catch (error: Exception) {
            throw VideoSamplingException(
                message = "This video could not be read. Try another MP4 video.",
                cause = error,
            )
        } finally {
            retriever.release()
        }
    }
}

private fun MediaMetadataRetriever.readMetadata(): VideoMetadata {
    val durationMs = requiredLongMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION, "duration")
    val width = requiredIntMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "width")
    val height = requiredIntMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "height")
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?: 0

    return VideoMetadata(
        durationMs = durationMs,
        encodedWidth = width,
        encodedHeight = height,
        rotationDegrees = rotation,
    )
}

private fun MediaMetadataRetriever.requiredLongMetadata(key: Int, label: String): Long {
    return extractMetadata(key)?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw VideoSamplingException("The video has no valid $label metadata.")
}

private fun MediaMetadataRetriever.requiredIntMetadata(key: Int, label: String): Int {
    return extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw VideoSamplingException("The video has no valid $label metadata.")
}

private fun MediaMetadataRetriever.frameAt(
    timestampUs: Long,
    targetSize: ScaledFrameSize,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return getScaledFrameAtTime(
            timestampUs,
            MediaMetadataRetriever.OPTION_CLOSEST,
            targetSize.width,
            targetSize.height,
        )
    }

    val original = getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
    if (original.width == targetSize.width && original.height == targetSize.height) return original

    return original.scale(targetSize.width, targetSize.height).also {
        if (it !== original) original.recycle()
    }
}
