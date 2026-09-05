package com.ritesh.iykykcollage.collage

import com.ritesh.iykykcollage.face.FaceBounds
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PortraitCrop(
    val left: Int,
    val top: Int,
    val size: Int,
)

class PortraitCropPlanner {
    fun plan(
        faceBounds: FaceBounds,
        coordinateWidth: Int,
        coordinateHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): PortraitCrop {
        require(coordinateWidth > 0 && coordinateHeight > 0) {
            "Face-coordinate dimensions must be positive"
        }
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }
        require(faceBounds.width > 0 && faceBounds.height > 0) { "Face bounds must not be empty" }

        val scaleX = bitmapWidth.toFloat() / coordinateWidth
        val scaleY = bitmapHeight.toFloat() / coordinateHeight
        val faceWidth = faceBounds.width * scaleX
        val faceHeight = faceBounds.height * scaleY
        val faceCenterX = (faceBounds.left + faceBounds.right) * 0.5f * scaleX
        val faceCenterY = (faceBounds.top + faceBounds.bottom) * 0.5f * scaleY

        val maximumSize = min(bitmapWidth, bitmapHeight)
        val cropSize = max(faceWidth, faceHeight)
            .times(FACE_TO_CROP_SCALE)
            .roundToInt()
            .coerceIn(1, maximumSize)
        val desiredLeft = (faceCenterX - cropSize / 2f).roundToInt()
        val desiredTop = (faceCenterY + faceHeight * VERTICAL_FACE_OFFSET - cropSize / 2f)
            .roundToInt()

        return PortraitCrop(
            left = desiredLeft.coerceIn(0, bitmapWidth - cropSize),
            top = desiredTop.coerceIn(0, bitmapHeight - cropSize),
            size = cropSize,
        )
    }

    private companion object {
        const val FACE_TO_CROP_SCALE = 2.15f
        const val VERTICAL_FACE_OFFSET = 0.18f
    }
}
