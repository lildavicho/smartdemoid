package com.smartpresence.idukay.presentation.attendance

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import timber.log.Timber
import java.nio.ByteBuffer

class FaceRecognitionAnalyzer(
    private val onFrameAvailable: (CameraFrame) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastProcessedTime = 0L

    @Volatile
    var minAnalyzeIntervalMs: Long = 0L

    private val bitmapPool = BitmapPool(maxPoolSize = 3)
    private var rowBuffer: ByteArray? = null
    private var contiguousRgbaBuffer: ByteBuffer? = null
    private var yuvConversionBuffer: ByteArray? = null
    
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val intervalMs = minAnalyzeIntervalMs
        if (intervalMs > 0 && currentTime - lastProcessedTime < intervalMs) {
            image.close()
            return
        }

        try {
            val bitmap = imageToBitmapRgba(image)
            if (bitmap == null) {
                image.close()
                return
            }
            
            val frame = CameraFrame(
                bitmap = bitmap,
                timestampMs = currentTime,
                release = { bitmapPool.release(bitmap) }
            )

            onFrameAvailable(frame)
            lastProcessedTime = currentTime
        } catch (e: Exception) {
            Timber.e(e, "Frame analysis failed")
            image.close()
        }
    }

    private fun imageToBitmapRgba(image: ImageProxy): android.graphics.Bitmap? {
        return when (image.format) {
            PixelFormat.RGBA_8888 -> convertRgba8888(image)
            ImageFormat.YUV_420_888 -> convertYuv420888(image)
            else -> {
                Timber.w("Unsupported ImageProxy format: ${image.format}")
                null
            }
        }
    }

    private fun convertRgba8888(image: ImageProxy): android.graphics.Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null

        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val bitmap = bitmapPool.acquire(width, height) ?: return null

        try {
            buffer.rewind()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val expectedPixelStride = 4
            val rowBytes = width * expectedPixelStride

            if (pixelStride != expectedPixelStride) {
                Timber.w("Unexpected pixelStride=$pixelStride (expected=$expectedPixelStride)")
                bitmapPool.release(bitmap)
                return null
            }

            if (rowStride == rowBytes) {
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }

            val needed = rowBytes * height
            val rgbaBuffer = contiguousRgbaBuffer?.takeIf { it.capacity() >= needed } ?: ByteBuffer.allocateDirect(needed)
            contiguousRgbaBuffer = rgbaBuffer
            rgbaBuffer.rewind()

            val row = rowBuffer?.takeIf { it.size >= rowBytes } ?: ByteArray(rowBytes).also { rowBuffer = it }
            val basePos = buffer.position()

            for (y in 0 until height) {
                buffer.position(basePos + y * rowStride)
                buffer.get(row, 0, rowBytes)
                rgbaBuffer.put(row, 0, rowBytes)
            }

            rgbaBuffer.rewind()
            bitmap.copyPixelsFromBuffer(rgbaBuffer)
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed RGBA_8888 -> Bitmap conversion")
            bitmapPool.release(bitmap)
            return null
        }
    }

    private fun convertYuv420888(image: ImageProxy): android.graphics.Bitmap? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val bitmap = bitmapPool.acquire(width, height) ?: return null

        try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val totalSize = ySize + uSize + vSize
            val nv21 = yuvConversionBuffer?.takeIf { it.size >= totalSize } ?: ByteArray(totalSize).also { yuvConversionBuffer = it }

            yBuffer.get(nv21, 0, ySize)
            
            val pixelStride = vPlane.pixelStride
            if (pixelStride == 1) {
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
            } else {
                val uvPixelStride = uPlane.pixelStride
                val uvRowStride = uPlane.rowStride
                val uvWidth = width / 2
                val uvHeight = height / 2

                var pos = ySize
                for (row in 0 until uvHeight) {
                    for (col in 0 until uvWidth) {
                        val vIndex = row * uvRowStride + col * uvPixelStride
                        val uIndex = row * uvRowStride + col * uvPixelStride
                        nv21[pos++] = vBuffer.get(vIndex)
                        nv21[pos++] = uBuffer.get(uIndex)
                    }
                }
            }

            yuv420ToRgba(nv21, width, height, bitmap)
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed YUV_420_888 -> Bitmap conversion")
            bitmapPool.release(bitmap)
            return null
        }
    }

    private fun yuv420ToRgba(yuv: ByteArray, width: Int, height: Int, bitmap: android.graphics.Bitmap) {
        val frameSize = width * height
        val rgba = IntArray(frameSize)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val yIndex = j * width + i
                val uvIndex = frameSize + (j / 2) * width + (i and 1.inv())

                val y = yuv[yIndex].toInt() and 0xFF
                val v = yuv[uvIndex].toInt() and 0xFF
                val u = yuv[uvIndex + 1].toInt() and 0xFF

                val r = (y + 1.370705f * (v - 128)).toInt().coerceIn(0, 255)
                val g = (y - 0.337633f * (u - 128) - 0.698001f * (v - 128)).toInt().coerceIn(0, 255)
                val b = (y + 1.732446f * (u - 128)).toInt().coerceIn(0, 255)

                rgba[yIndex] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(rgba, 0, width, 0, 0, width, height)
    }
}

private class BitmapPool(
    private val maxPoolSize: Int
) {
    private var width: Int = 0
    private var height: Int = 0
    private var allocatedCount: Int = 0
    private val pool = ArrayDeque<android.graphics.Bitmap>()

    @Synchronized
    fun acquire(width: Int, height: Int): android.graphics.Bitmap? {
        if (this.width != width || this.height != height) {
            pool.forEach { it.recycle() }
            pool.clear()
            this.width = width
            this.height = height
            allocatedCount = 0
        }

        if (pool.isNotEmpty()) {
            return pool.removeFirst()
        }

        if (allocatedCount >= maxPoolSize) return null

        return try {
            allocatedCount += 1
            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            allocatedCount = (allocatedCount - 1).coerceAtLeast(0)
            null
        }
    }

    @Synchronized
    fun release(bitmap: android.graphics.Bitmap) {
        if (bitmap.isRecycled) return
        if (width == 0 || height == 0) {
            bitmap.recycle()
            return
        }
        if (bitmap.width != width || bitmap.height != height) {
            bitmap.recycle()
            return
        }
        if (pool.size >= maxPoolSize) {
            bitmap.recycle()
            allocatedCount = (allocatedCount - 1).coerceAtLeast(0)
            return
        }
        pool.addLast(bitmap)
    }
}
