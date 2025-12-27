package com.smartpresence.idukay.ai.alignment

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object FaceAligner {
    
    // Template landmarks for 112x112 aligned face (ArcFace standard)
    private val TEMPLATE_LANDMARKS = floatArrayOf(
        38.2946f, 51.6963f,  // left eye
        73.5318f, 51.5014f,  // right eye
        56.0252f, 71.7366f,  // nose
        41.5493f, 92.3655f,  // left mouth corner
        70.7299f, 92.2041f   // right mouth corner
    )
    
    /**
     * Align face using similarity transform based on 5 facial landmarks
     * @param bitmap Source image containing the face
     * @param landmarks 5 facial keypoints [x1,y1, x2,y2, x3,y3, x4,y4, x5,y5]
     * @param outputSize Output image size (default 112x112)
     * @return Aligned face bitmap
     */
    fun alignFace(
        bitmap: Bitmap,
        landmarks: FloatArray,
        outputSize: Int = 112
    ): Bitmap {
        require(landmarks.size == 10) { "Expected 5 landmarks (10 coordinates)" }
        
        // Extract eye positions
        val leftEye = floatArrayOf(landmarks[0], landmarks[1])
        val rightEye = floatArrayOf(landmarks[2], landmarks[3])
        
        // Calculate transformation matrix using similarity transform
        val matrix = calculateSimilarityTransform(
            srcPoints = landmarks,
            dstPoints = TEMPLATE_LANDMARKS,
            outputSize = outputSize
        )
        
        // Apply transformation
        val aligned = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(aligned)
        canvas.drawBitmap(bitmap, matrix, null)
        
        return aligned
    }
    
    /**
     * Calculate similarity transformation matrix
     * Uses least squares to find best fit transformation
     */
    private fun calculateSimilarityTransform(
        srcPoints: FloatArray,
        dstPoints: FloatArray,
        outputSize: Int
    ): Matrix {
        // Use first 3 points (eyes and nose) for transformation
        val src = FloatArray(6)
        val dst = FloatArray(6)
        
        for (i in 0 until 3) {
            src[i * 2] = srcPoints[i * 2]
            src[i * 2 + 1] = srcPoints[i * 2 + 1]
            dst[i * 2] = dstPoints[i * 2]
            dst[i * 2 + 1] = dstPoints[i * 2 + 1]
        }
        
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 3)
        
        return matrix
    }
    
    /**
     * Alternative alignment using affine transform (more robust)
     */
    fun alignFaceAffine(
        bitmap: Bitmap,
        landmarks: FloatArray,
        outputSize: Int = 112
    ): Bitmap {
        // Extract eye centers
        val leftEye = floatArrayOf(landmarks[0], landmarks[1])
        val rightEye = floatArrayOf(landmarks[2], landmarks[3])
        
        // Calculate angle between eyes
        val dY = rightEye[1] - leftEye[1]
        val dX = rightEye[0] - leftEye[0]
        val angle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()
        
        // Calculate eye center
        val eyesCenterX = (leftEye[0] + rightEye[0]) / 2f
        val eyesCenterY = (leftEye[1] + rightEye[1]) / 2f
        
        // Calculate scale
        val eyeDistance = sqrt((dX * dX + dY * dY).toDouble()).toFloat()
        val desiredEyeDistance = (TEMPLATE_LANDMARKS[2] - TEMPLATE_LANDMARKS[0])
        val scale = desiredEyeDistance / eyeDistance
        
        // Create transformation matrix
        val matrix = Matrix()
        matrix.postTranslate(-eyesCenterX, -eyesCenterY)
        matrix.postRotate(-angle)
        matrix.postScale(scale, scale)
        matrix.postTranslate(outputSize / 2f, outputSize / 2f)
        
        // Apply transformation
        val aligned = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(aligned)
        canvas.drawBitmap(bitmap, matrix, null)
        
        return aligned
    }
}
