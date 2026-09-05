package com.ritesh.iykykcollage.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.ritesh.iykykcollage.video.SampledVideoFrame

class AlignedFaceCropper(
    private val planner: FaceAlignmentPlanner = FaceAlignmentPlanner(),
) {
    fun crop(
        frame: SampledVideoFrame,
        face: FaceObservation,
        correctSideBySideCompression: Boolean = false,
    ): Bitmap {
        val uprightBitmap = frame.bitmap.rotatedUpright(frame.rotationDegrees)
        return try {
            require(uprightBitmap.width == face.frameWidth && uprightBitmap.height == face.frameHeight) {
                "Face coordinates do not match the upright frame dimensions"
            }

            val output = Bitmap.createBitmap(
                planner.outputSize,
                planner.outputSize,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)
            val transform = Matrix()
            when (val plan = planner.plan(face)) {
                is FaceAlignmentPlan.EyeAligned -> {
                    val sourcePoints = mutableListOf(
                            plan.sourceImageLeftEye.x,
                            plan.sourceImageLeftEye.y,
                            plan.sourceImageRightEye.x,
                            plan.sourceImageRightEye.y,
                    )
                    val targetPoints = mutableListOf(
                            plan.targetImageLeftEye.x,
                            plan.targetImageLeftEye.y,
                            plan.targetImageRightEye.x,
                            plan.targetImageRightEye.y,
                    )
                    val pointCount = if (correctSideBySideCompression) {
                        val sourceDx = plan.sourceImageRightEye.x - plan.sourceImageLeftEye.x
                        val sourceDy = plan.sourceImageRightEye.y - plan.sourceImageLeftEye.y
                        sourcePoints += (plan.sourceImageLeftEye.x + plan.sourceImageRightEye.x) / 2f - sourceDy
                        sourcePoints += (plan.sourceImageLeftEye.y + plan.sourceImageRightEye.y) / 2f + sourceDx

                        val targetEyeDistance =
                            plan.targetImageRightEye.x - plan.targetImageLeftEye.x
                        targetPoints += (plan.targetImageLeftEye.x + plan.targetImageRightEye.x) / 2f
                        targetPoints += plan.targetImageLeftEye.y + targetEyeDistance / 2f
                        3
                    } else {
                        2
                    }
                    val mapped = transform.setPolyToPoly(
                        sourcePoints.toFloatArray(),
                        0,
                        targetPoints.toFloatArray(),
                        0,
                        pointCount,
                    )
                    check(mapped) { "Eye landmarks could not produce an alignment transform" }
                }

                is FaceAlignmentPlan.BoundsFallback -> {
                    transform.setRectToRect(
                        RectF(
                            plan.sourceLeft,
                            plan.sourceTop,
                            plan.sourceRight,
                            plan.sourceBottom,
                        ),
                        RectF(0f, 0f, planner.outputSize.toFloat(), planner.outputSize.toFloat()),
                        Matrix.ScaleToFit.FILL,
                    )
                }
            }

            canvas.drawBitmap(
                uprightBitmap,
                transform,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
            )
            output
        } finally {
            if (uprightBitmap !== frame.bitmap && !uprightBitmap.isRecycled) uprightBitmap.recycle()
        }
    }
}

private fun Bitmap.rotatedUpright(rotationDegrees: Int): Bitmap {
    val normalizedRotation = rotationDegrees.normalizedRightAngleRotation()
    if (normalizedRotation == 0) return this

    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(normalizedRotation.toFloat()) },
        true,
    )
}
