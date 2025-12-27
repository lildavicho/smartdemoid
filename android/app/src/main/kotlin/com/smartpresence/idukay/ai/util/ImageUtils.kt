package com.smartpresence.idukay.ai.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    
    /**
     * Resize bitmap to target size maintaining aspect ratio
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    /**
     * Resize bitmap to square maintaining aspect ratio with padding
     */
    fun resizeToSquare(bitmap: Bitmap, size: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        val scale = size.toFloat() / maxDim
        
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // Create square bitmap with padding
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        
        val left = (size - newWidth) / 2f
        val top = (size - newHeight) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        
        scaled.recycle()
        return result
    }
    
    /**
     * Convert bitmap to float buffer with normalization
     * @param normalize If true, normalize to [-1, 1], else [0, 255]
     * @param channelsFirst If true, output CHW format, else HWC
     */
    fun bitmapToFloatBuffer(
        bitmap: Bitmap,
        normalize: Boolean = true,
        channelsFirst: Boolean = true
    ): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val floatArray = FloatArray(width * height * 3)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            
            val normalizedR = if (normalize) (r - 127.5f) / 127.5f else r
            val normalizedG = if (normalize) (g - 127.5f) / 127.5f else g
            val normalizedB = if (normalize) (b - 127.5f) / 127.5f else b
            
            if (channelsFirst) {
                // CHW format
                floatArray[i] = normalizedR
                floatArray[width * height + i] = normalizedG
                floatArray[width * height * 2 + i] = normalizedB
            } else {
                // HWC format
                floatArray[i * 3] = normalizedR
                floatArray[i * 3 + 1] = normalizedG
                floatArray[i * 3 + 2] = normalizedB
            }
        }
        
        return floatArray
    }
    
    /**
     * Crop bitmap using bounding box
     */
    fun cropBitmap(bitmap: Bitmap, bbox: FloatArray): Bitmap {
        val x1 = max(0f, bbox[0]).toInt()
        val y1 = max(0f, bbox[1]).toInt()
        val x2 = min(bitmap.width.toFloat(), bbox[2]).toInt()
        val y2 = min(bitmap.height.toFloat(), bbox[3]).toInt()
        
        val width = x2 - x1
        val height = y2 - y1
        
        if (width <= 0 || height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        
        return Bitmap.createBitmap(bitmap, x1, y1, width, height)
    }
    
    /**
     * Calculate Laplacian variance for blur detection
     */
    fun calculateLaplacianVariance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale
        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b)
        }
        
        // Apply Laplacian kernel
        val laplacian = FloatArray((width - 2) * (height - 2))
        var idx = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val value = (
                    -1 * gray[(y - 1) * width + (x - 1)] +
                    -1 * gray[(y - 1) * width + x] +
                    -1 * gray[(y - 1) * width + (x + 1)] +
                    -1 * gray[y * width + (x - 1)] +
                    8 * gray[y * width + x] +
                    -1 * gray[y * width + (x + 1)] +
                    -1 * gray[(y + 1) * width + (x - 1)] +
                    -1 * gray[(y + 1) * width + x] +
                    -1 * gray[(y + 1) * width + (x + 1)]
                )
                laplacian[idx++] = value
            }
        }
        
        // Calculate variance
        val mean = laplacian.average().toFloat()
        var variance = 0f
        for (value in laplacian) {
            val diff = value - mean
            variance += diff * diff
        }
        variance /= laplacian.size
        
        return variance
    }
    
    /**
     * Calculate average brightness
     */
    fun calculateBrightness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalBrightness = 0f
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            totalBrightness += (0.299f * r + 0.587f * g + 0.114f * b)
        }
        
        return totalBrightness / pixels.size
    }
}
