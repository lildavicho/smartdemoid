package com.smartpresence.idukay.ai.detector

import android.graphics.Bitmap
import com.smartpresence.idukay.ai.ModelLoadingState

/**
 * Represents a detected face with bounding box, confidence, and facial landmarks
 */
data class Detection(
    val bbox: FloatArray,        // [x1, y1, x2, y2] - bounding box coordinates
    val confidence: Float,        // Detection confidence score
    val landmarks: FloatArray = floatArrayOf()  // [x1,y1, x2,y2, x3,y3, x4,y4, x5,y5] - 5 facial keypoints (optional)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Detection
        if (!bbox.contentEquals(other.bbox)) return false
        if (confidence != other.confidence) return false
        if (!landmarks.contentEquals(other.landmarks)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bbox.contentHashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
    
    fun getWidth(): Float = bbox[2] - bbox[0]
    fun getHeight(): Float = bbox[3] - bbox[1]
    fun getArea(): Float = getWidth() * getHeight()
}

/**
 * Interface for face detection models
 */
interface FaceDetector {
    /**
     * Model loading state
     */
    val loadingState: ModelLoadingState
    
    /**
     * Detect faces in the given bitmap
     * @param bitmap Input image
     * @return List of detected faces with bounding boxes and landmarks
     */
    suspend fun detect(bitmap: Bitmap): List<Detection>
    
    /**
     * Release model resources
     */
    fun close()
}

