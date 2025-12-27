package com.smartpresence.idukay.ai.quality

import android.graphics.Bitmap
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.util.ImageUtils

object QualityFilter {
    
    /**
     * Assess overall quality of detected face
     * @param bitmap Source image
     * @param bbox Face bounding box
     * @return Quality score between 0 and 1
     */
    fun assessQuality(bitmap: Bitmap, bbox: FloatArray): Float {
        var score = 1.0f
        
        // 1. Size check
        val width = bbox[2] - bbox[0]
        val height = bbox[3] - bbox[1]
        
        if (width < RecognitionConfig.MIN_FACE_SIZE_PX || height < RecognitionConfig.MIN_FACE_SIZE_PX) {
            score *= 0.3f  // Heavily penalize small faces
        } else if (width < RecognitionConfig.MIN_FACE_SIZE_PX * 1.5f) {
            score *= 0.7f  // Moderately penalize slightly small faces
        }
        
        // 2. Crop face region for detailed analysis
        val faceCrop = try {
            ImageUtils.cropBitmap(bitmap, bbox)
        } catch (e: Exception) {
            return 0f
        }
        
        // 3. Blur detection
        val blurScore = calculateBlurScore(faceCrop)
        score *= blurScore
        
        // 4. Brightness check
        val brightnessScore = calculateBrightnessScore(faceCrop)
        score *= brightnessScore
        
        faceCrop.recycle()
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Check if quality score meets threshold
     */
    fun isGoodQuality(score: Float): Boolean {
        return score >= RecognitionConfig.QUALITY_MIN
    }

    fun isGoodQuality(score: Float, min: Float): Boolean {
        return score >= min
    }
    
    /**
     * Calculate blur score using Laplacian variance
     * @return Score between 0 and 1 (higher is sharper)
     */
    private fun calculateBlurScore(bitmap: Bitmap): Float {
        val variance = ImageUtils.calculateLaplacianVariance(bitmap)
        
        // Normalize variance to 0-1 score
        // Typical sharp images have variance > 100
        // Blurry images have variance < 50
        val normalized = when {
            variance > 100f -> 1.0f
            variance > 50f -> (variance - 50f) / 50f
            else -> variance / 50f * 0.5f
        }
        
        return normalized.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate brightness score
     * @return Score between 0 and 1 (1 is optimal brightness)
     */
    private fun calculateBrightnessScore(bitmap: Bitmap): Float {
        val brightness = ImageUtils.calculateBrightness(bitmap)
        
        // Optimal brightness is between 80-180
        // Too dark < 50, too bright > 200
        val score = when {
            brightness < RecognitionConfig.MIN_BRIGHTNESS -> {
                brightness / RecognitionConfig.MIN_BRIGHTNESS * 0.5f
            }
            brightness > RecognitionConfig.MAX_BRIGHTNESS -> {
                (255f - brightness) / (255f - RecognitionConfig.MAX_BRIGHTNESS) * 0.5f
            }
            else -> {
                // Optimal range
                1.0f
            }
        }
        
        return score.coerceIn(0f, 1f)
    }
}
