package com.ritesh.iykykcollage.face

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ritesh.iykykcollage.video.SampledVideoFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MlKitFaceAnalyzer(
    private val qualityAssessor: FaceQualityAssessor = FaceQualityAssessor(),
    private val detector: FaceDetector = FaceDetection.getClient(defaultDetectorOptions()),
) : FaceAnalyzer {
    private val detectorMutex = Mutex()

    override suspend fun analyze(frame: SampledVideoFrame): FrameFaceDetection {
        val normalizedRotation = frame.rotationDegrees.normalizedImageRotation()
        val image = InputImage.fromBitmap(frame.bitmap, normalizedRotation)

        return try {
            val faces = detectorMutex.withLock {
                // Keep this one ML Kit task alive until it releases the input bitmap.
                withContext(NonCancellable) {
                    detector.process(image).await()
                }
            }
            val analysisWidth = if (normalizedRotation == 90 || normalizedRotation == 270) {
                frame.bitmap.height
            } else {
                frame.bitmap.width
            }
            val analysisHeight = if (normalizedRotation == 90 || normalizedRotation == 270) {
                frame.bitmap.width
            } else {
                frame.bitmap.height
            }

            FrameFaceDetection(
                frameIndex = frame.index,
                timestampUs = frame.timestampUs,
                faces = faces.map { face ->
                    face.toObservation(
                        frame = frame,
                        frameWidth = analysisWidth,
                        frameHeight = analysisHeight,
                    )
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw FaceAnalysisException(
                message = "On-device face detection failed for this video.",
                cause = error,
            )
        }
    }

    override fun close() {
        detector.close()
    }

    private fun Face.toObservation(
        frame: SampledVideoFrame,
        frameWidth: Int,
        frameHeight: Int,
    ): FaceObservation {
        val faceBounds = FaceBounds(
            left = boundingBox.left,
            top = boundingBox.top,
            right = boundingBox.right,
            bottom = boundingBox.bottom,
        )
        val quality = qualityAssessor.assess(
            bounds = faceBounds,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
        )

        return FaceObservation(
            frameIndex = frame.index,
            timestampUs = frame.timestampUs,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            bounds = faceBounds,
            trackingId = trackingId,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            smilingProbability = smilingProbability,
            quality = quality,
        )
    }

    companion object {
        fun defaultDetectorOptions(): FaceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    }
}

private fun Int.normalizedImageRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270) {
        normalized
    } else {
        0
    }
}
