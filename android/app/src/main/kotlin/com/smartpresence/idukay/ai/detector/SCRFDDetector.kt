package com.smartpresence.idukay.ai.detector

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.smartpresence.idukay.ai.ModelLoadingState
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.onnx.OnnxSessionOptionsFactory
import timber.log.Timber
import java.io.FileNotFoundException
import java.nio.FloatBuffer

/**
 * SCRFD (Sample and Computation Redistribution for Face Detection) Detector
 * Lightweight face detection model optimized for mobile devices
 */
class SCRFDDetector(
    private val context: Context,
    private val ortEnv: OrtEnvironment
) : FaceDetector {
    
    private var session: OrtSession? = null
    private var inputName: String? = null
    
    override val loadingState: ModelLoadingState
    
    init {
        loadingState = run {
            val modelBytes = try {
                context.assets.open(RecognitionConfig.DETECTOR_MODEL_PATH).use {
                    it.readBytes()
                }
            } catch (_: FileNotFoundException) {
                Timber.e("Model asset not found: ${RecognitionConfig.DETECTOR_MODEL_PATH}")
                return@run ModelLoadingState.AssetNotFound(RecognitionConfig.DETECTOR_MODEL_PATH)
            }
            
            try {
                val sessionOptions = OnnxSessionOptionsFactory.create(tag = "SCRFDDetector")
                session = ortEnv.createSession(modelBytes, sessionOptions)
                try {
                    sessionOptions.close()
                } catch (_: Throwable) {
                }
                inputName = session?.inputNames?.iterator()?.next()
                
                Timber.d("SCRFD Detector loaded successfully")
                Timber.d("Input name: $inputName")
                Timber.d("Input shape: ${session?.inputInfo?.get(inputName)?.info}")
                
                ModelLoadingState.Success
            } catch (e: Exception) {
                Timber.e(e, "Failed to load SCRFD detector")
                ModelLoadingState.Error("Failed to initialize SCRFD detector: ${e.message}", e)
            }
        }
    }
    
    override suspend fun detect(bitmap: Bitmap): List<Detection> {
        if (!loadingState.isSuccess() || session == null || inputName == null) {
            Timber.w("Detector not loaded, skipping detection")
            return emptyList()
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Preprocess image
            val preprocessed = preprocessImage(bitmap)
            
            // Run inference
            val outputs = session!!.run(
                mapOf(inputName!! to preprocessed.tensor)
            )
            
            // Post-process results
            val detections = postprocess(outputs, preprocessed.scale, preprocessed.padX, preprocessed.padY, bitmap.width, bitmap.height)
            
            // Close tensor after use
            preprocessed.tensor.close()
            
            val inferenceTime = System.currentTimeMillis() - startTime
            Timber.d("SCRFD detection completed in ${inferenceTime}ms, found ${detections.size} faces")
            
            detections
        } catch (e: Exception) {
            Timber.e(e, "Error during face detection")
            emptyList()
        }
    }
    
    private data class PreprocessResult(
        val tensor: OnnxTensor,
        val scale: Float,
        val padX: Int,
        val padY: Int
    )
    
    private fun preprocessImage(bitmap: Bitmap): PreprocessResult {
        val targetWidth = RecognitionConfig.DETECTION_INPUT_WIDTH
        val targetHeight = RecognitionConfig.DETECTION_INPUT_HEIGHT
        
        // Calculate scale to fit image into target size while maintaining aspect ratio
        val scaleW = targetWidth.toFloat() / bitmap.width
        val scaleH = targetHeight.toFloat() / bitmap.height
        val scale = minOf(scaleW, scaleH)
        
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        
        // Calculate padding to center the image
        val padX = (targetWidth - scaledWidth) / 2
        val padY = (targetHeight - scaledHeight) / 2
        
        // Create scaled bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // Create padded bitmap with black background
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(paddedBitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(scaledBitmap, padX.toFloat(), padY.toFloat(), null)
        
        // Convert to tensor
        val pixels = IntArray(targetWidth * targetHeight)
        paddedBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        
        // Normalize and convert to NCHW format [1, 3, H, W]
        val floatArray = FloatArray(3 * targetHeight * targetWidth)
        val mean = floatArrayOf(127.5f, 127.5f, 127.5f)
        val std = floatArrayOf(128.0f, 128.0f, 128.0f)
        
        val planeSize = targetHeight * targetWidth
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val i = y * targetWidth + x
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                
                // NCHW format: [batch, channel, height, width]
                floatArray[i] = (r - mean[0]) / std[0]                    // R channel
                floatArray[planeSize + i] = (g - mean[1]) / std[1]        // G channel
                floatArray[2 * planeSize + i] = (b - mean[2]) / std[2]    // B channel
            }
        }
        
        val tensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(floatArray),
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())
        )
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        paddedBitmap.recycle()
        
        return PreprocessResult(tensor, scale, padX, padY)
    }
    
    private fun postprocess(
        outputs: OrtSession.Result,
        scale: Float,
        padX: Int,
        padY: Int,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        try {
            // Get output tensors
            val outputNames = mutableListOf<String>()
            val outputValues = mutableListOf<Any?>()
            
            for (entry in outputs) {
                outputNames.add(entry.key)
                outputValues.add(entry.value.value)
            }
            
            Timber.d("SCRFD outputs: $outputNames")
            
            // Try to get scores and bboxes by name
            var scoresArray: FloatArray? = null
            var bboxesArray: FloatArray? = null
            
            for (i in outputNames.indices) {
                val name = outputNames[i]
                val value = outputValues[i]
                
                when {
                    name.contains("score", ignoreCase = true) || name.contains("conf", ignoreCase = true) -> {
                        scoresArray = extractFloatArray(value)
                    }
                    name.contains("box", ignoreCase = true) || name.contains("bbox", ignoreCase = true) -> {
                        bboxesArray = extractFloatArray(value)
                    }
                }
            }
            
            // If not found by name, use positional (first = scores, second = bboxes)
            if (scoresArray == null || bboxesArray == null) {
                if (outputValues.size >= 2) {
                    scoresArray = extractFloatArray(outputValues[0])
                    bboxesArray = extractFloatArray(outputValues[1])
                }
            }
            
            if (scoresArray == null || bboxesArray == null) {
                Timber.w("Could not extract scores/bboxes from SCRFD output")
                return emptyList()
            }
            
            // Process detections - assume 4 values per bbox
            val numDetections = scoresArray.size
            val bboxStride = if (bboxesArray.size >= numDetections * 4) 4 else bboxesArray.size / maxOf(numDetections, 1)
            
            for (i in 0 until numDetections) {
                val score = scoresArray[i]
                if (score >= RecognitionConfig.DETECTION_THRESHOLD) {
                    val bboxOffset = i * bboxStride
                    if (bboxOffset + 3 < bboxesArray.size) {
                        // Get bbox coordinates
                        val x1Raw = bboxesArray[bboxOffset]
                        val y1Raw = bboxesArray[bboxOffset + 1]
                        val x2Raw = bboxesArray[bboxOffset + 2]
                        val y2Raw = bboxesArray[bboxOffset + 3]
                        
                        // Adjust for padding and scaling back to original image size
                        val x1 = ((x1Raw - padX) / scale).coerceIn(0f, originalWidth.toFloat())
                        val y1 = ((y1Raw - padY) / scale).coerceIn(0f, originalHeight.toFloat())
                        val x2 = ((x2Raw - padX) / scale).coerceIn(0f, originalWidth.toFloat())
                        val y2 = ((y2Raw - padY) / scale).coerceIn(0f, originalHeight.toFloat())
                        
                        // Skip invalid boxes
                        if (x2 > x1 && y2 > y1) {
                            detections.add(
                                Detection(
                                    bbox = floatArrayOf(x1, y1, x2, y2),
                                    confidence = score
                                )
                            )
                        }
                    }
                }
            }
            
            // Apply NMS
            return nonMaximumSuppression(detections, RecognitionConfig.NMS_IOU_THRESHOLD)
        } catch (e: Exception) {
            Timber.e(e, "Error in postprocessing")
            return emptyList()
        } finally {
            outputs.close()
        }
    }
    
    private fun extractFloatArray(value: Any?): FloatArray? {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                // Handle nested arrays like Array<FloatArray>
                if (value.isNotEmpty() && value[0] is FloatArray) {
                    value[0] as FloatArray
                } else if (value.isNotEmpty() && value[0] is Array<*>) {
                    val inner = value[0] as Array<*>
                    if (inner.isNotEmpty() && inner[0] is FloatArray) {
                        inner[0] as FloatArray
                    } else null
                } else null
            }
            else -> null
        }
    }
    
    private fun nonMaximumSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence descending
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        
        for (detection in sorted) {
            var shouldKeep = true
            for (kept in keep) {
                if (calculateIoU(detection.bbox, kept.bbox) > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }
            if (shouldKeep) {
                keep.add(detection)
            }
        }
        
        return keep
    }
    
    private fun calculateIoU(box1: FloatArray, box2: FloatArray): Float {
        val x1 = maxOf(box1[0], box2[0])
        val y1 = maxOf(box1[1], box2[1])
        val x2 = minOf(box1[2], box2[2])
        val y2 = minOf(box1[3], box2[3])
        
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
        val area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }
    
    override fun close() {
        session?.close()
        session = null
        Timber.d("SCRFD Detector closed")
    }
}
