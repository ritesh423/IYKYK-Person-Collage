package com.ritesh.iykykcollage.face

import android.content.Context
import android.graphics.Bitmap
import com.ritesh.iykykcollage.video.SampledVideoFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LiteRtFaceEmbedder(
    context: Context,
    private val cropper: AlignedFaceCropper = AlignedFaceCropper(),
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FaceEmbedder {
    private val appContext = context.applicationContext
    private val inferenceLock = ReentrantLock()
    private val inputBuffer = ByteBuffer.allocateDirect(INPUT_FLOAT_COUNT * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val inputPixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
    private val outputBuffer = Array(1) { FloatArray(EMBEDDING_DIMENSION) }
    private var framePixels = IntArray(0)
    private var interpreter: Interpreter? = null
    private var closed = false

    override suspend fun embed(
        frame: SampledVideoFrame,
        detection: FrameFaceDetection,
    ): FrameFaceDetection = withContext(inferenceDispatcher) {
        try {
            inferenceLock.withLock {
                check(!closed) { "Face embedder has already been closed" }
                val runtime = interpreter ?: createInterpreter().also { interpreter = it }
                val correctSideBySideCompression = detection.looksLikeSideBySideLayout()
                val frameIsSharpEnough =
                    frame.bitmap.sharpnessScore() >= MINIMUM_FRAME_LAPLACIAN_VARIANCE
                detection.copy(
                    faces = detection.faces.map { face ->
                        val usableSideBySideFace = correctSideBySideCompression &&
                            FaceQualityIssue.TOO_SMALL_FOR_MATCHING !in face.quality.issues &&
                            FaceQualityIssue.EXTREME_POSE !in face.quality.issues
                        if (
                            (!face.quality.usableForMatching && !usableSideBySideFace) ||
                            !frameIsSharpEnough
                        ) {
                            face
                        } else {
                            val alignedFace = cropper.crop(
                                frame = frame,
                                face = face,
                                correctSideBySideCompression = correctSideBySideCompression,
                            )
                            try {
                                face.copy(
                                    embedding = runtime.embeddingFor(alignedFace),
                                    fromSideBySideLayout = correctSideBySideCompression,
                                )
                            } finally {
                                if (!alignedFace.isRecycled) alignedFace.recycle()
                            }
                        }
                    },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: FaceEmbeddingException) {
            throw error
        } catch (error: Exception) {
            throw FaceEmbeddingException(
                message = "On-device face embedding failed for this video.",
                cause = error,
            )
        }
    }

    override fun close() {
        inferenceLock.withLock {
            if (!closed) {
                interpreter?.close()
                interpreter = null
                closed = true
            }
        }
    }

    private fun createInterpreter(): Interpreter {
        val modelBuffer = try {
            appContext.assets.openFd(MODEL_ASSET_PATH).use { asset ->
                FileInputStream(asset.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        asset.startOffset,
                        asset.declaredLength,
                    )
                }
            }
        } catch (error: Exception) {
            throw FaceEmbeddingException("The bundled face-embedding model could not be loaded.", error)
        }

        val runtime = Interpreter(
            modelBuffer,
            Interpreter.Options()
                .setNumThreads(INFERENCE_THREADS)
                .setUseXNNPACK(true),
        )
        try {
            runtime.requireExpectedTensorContract()
            return runtime
        } catch (error: Exception) {
            runtime.close()
            throw error
        }
    }

    private fun Interpreter.embeddingFor(bitmap: Bitmap): FaceEmbedding {
        require(bitmap.width == INPUT_WIDTH && bitmap.height == INPUT_HEIGHT) {
            "Aligned face must be $INPUT_WIDTH × $INPUT_HEIGHT pixels"
        }

        bitmap.getPixels(
            inputPixels,
            0,
            INPUT_WIDTH,
            0,
            0,
            INPUT_WIDTH,
            INPUT_HEIGHT,
        )
        inputBuffer.clear()
        inputPixels.forEach { color ->
            inputBuffer.putFloat((((color ushr 16) and 0xff) / 127.5f) - 1f)
            inputBuffer.putFloat((((color ushr 8) and 0xff) / 127.5f) - 1f)
            inputBuffer.putFloat(((color and 0xff) / 127.5f) - 1f)
        }
        inputBuffer.rewind()
        outputBuffer[0].fill(0f)
        run(inputBuffer, outputBuffer)
        return FaceEmbedding.from(outputBuffer[0])
    }

    private fun Bitmap.sharpnessScore(): Double {
        val requiredSize = width * height
        if (framePixels.size < requiredSize) framePixels = IntArray(requiredSize)
        getPixels(framePixels, 0, width, 0, 0, width, height)

        var sampleCount = 0L
        var sum = 0L
        var squaredSum = 0L
        for (y in 1 until height - 1 step SHARPNESS_SAMPLE_STRIDE) {
            for (x in 1 until width - 1 step SHARPNESS_SAMPLE_STRIDE) {
                val center = framePixels[y * width + x].luminance()
                val laplacian =
                    framePixels[(y - 1) * width + x].luminance() +
                        framePixels[(y + 1) * width + x].luminance() +
                        framePixels[y * width + x - 1].luminance() +
                        framePixels[y * width + x + 1].luminance() -
                        4 * center
                sampleCount += 1
                sum += laplacian
                squaredSum += laplacian.toLong() * laplacian
            }
        }
        if (sampleCount == 0L) return 0.0
        val mean = sum.toDouble() / sampleCount
        return squaredSum.toDouble() / sampleCount - mean * mean
    }

    private fun Interpreter.requireExpectedTensorContract() {
        val input = getInputTensor(0)
        val output = getOutputTensor(0)
        val valid = inputTensorCount == 1 &&
            outputTensorCount == 1 &&
            input.dataType() == DataType.FLOAT32 &&
            output.dataType() == DataType.FLOAT32 &&
            input.shape().contentEquals(intArrayOf(1, INPUT_HEIGHT, INPUT_WIDTH, RGB_CHANNELS)) &&
            output.shape().contentEquals(intArrayOf(1, EMBEDDING_DIMENSION))
        if (!valid) {
            throw FaceEmbeddingException(
                "Unexpected MobileFaceNet tensor contract: " +
                    "input=${input.dataType()} ${input.shape().contentToString()}, " +
                    "output=${output.dataType()} ${output.shape().contentToString()}.",
            )
        }
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/mobilefacenet.tflite"
        const val INPUT_WIDTH = 112
        const val INPUT_HEIGHT = 112
        const val EMBEDDING_DIMENSION = 192
        private const val RGB_CHANNELS = 3
        private const val INPUT_FLOAT_COUNT = INPUT_WIDTH * INPUT_HEIGHT * RGB_CHANNELS
        private const val INFERENCE_THREADS = 4
        private const val SHARPNESS_SAMPLE_STRIDE = 4
        private const val MINIMUM_FRAME_LAPLACIAN_VARIANCE = 3.0
    }
}

private fun Int.luminance(): Int {
    val red = (this ushr 16) and 0xff
    val green = (this ushr 8) and 0xff
    val blue = this and 0xff
    return (77 * red + 150 * green + 29 * blue) ushr 8
}

private fun FrameFaceDetection.looksLikeSideBySideLayout(): Boolean {
    if (faces.size != 2) return false
    val ordered = faces.sortedBy { (it.bounds.left + it.bounds.right) / 2f }
    val middle = ordered.first().frameWidth / 2f
    val leftCenter = (ordered[0].bounds.left + ordered[0].bounds.right) / 2f
    val rightCenter = (ordered[1].bounds.left + ordered[1].bounds.right) / 2f
    val reachesBothEdges =
        ordered[0].bounds.left <= ordered[0].frameWidth * 0.12f &&
            ordered[1].bounds.right >= ordered[1].frameWidth * 0.88f
    return leftCenter < middle && rightCenter > middle && reachesBothEdges
}
