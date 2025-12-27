package com.smartpresence.idukay.ai.recognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.smartpresence.idukay.ai.ModelLoadingState
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.onnx.OnnxSessionOptionsFactory
import timber.log.Timber
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import kotlin.math.sqrt

class ArcFaceRecognizer(
    context: Context,
    private val ortEnv: OrtEnvironment
) : FaceRecognizer {
    
    private var session: OrtSession? = null
    private var inputName: String? = null
    
    val loadingState: ModelLoadingState

    private val targetSize = RecognitionConfig.RECOGNITION_INPUT_SIZE
    private val inputShape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
    private val pixels = IntArray(targetSize * targetSize)
    private val inputFloats = FloatArray(targetSize * targetSize * 3)
    private val inputFloatBuffer = FloatBuffer.wrap(inputFloats)
    private val inputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    private val inputCanvas = Canvas(inputBitmap)
    
    init {
        loadingState = run {
            val modelBytes = try {
                context.assets.open(RecognitionConfig.RECOGNIZER_MODEL_PATH).use {
                    it.readBytes()
                }
            } catch (e: FileNotFoundException) {
                Timber.e("Model asset not found: ${RecognitionConfig.RECOGNIZER_MODEL_PATH}")
                return@run ModelLoadingState.AssetNotFound(RecognitionConfig.RECOGNIZER_MODEL_PATH)
            }
            
            try {
                val sessionOptions = OnnxSessionOptionsFactory.create(tag = "ArcFaceRecognizer")
                session = ortEnv.createSession(modelBytes, sessionOptions)
                try {
                    sessionOptions.close()
                } catch (_: Throwable) {
                }
                inputName = session?.inputNames?.iterator()?.next()
                
                Timber.d("ArcFace Recognizer loaded successfully")
                Timber.d("Input name: $inputName")
                Timber.d("Input shape: ${session?.inputInfo?.get(inputName)?.info}")
                
                ModelLoadingState.Success
            } catch (e: Exception) {
                Timber.e(e, "Failed to load ArcFace recognizer")
                ModelLoadingState.Error("Failed to initialize ArcFace recognizer: ${e.message}", e)
            }
        }
    }
    
    override suspend fun recognize(alignedFace: Bitmap): FloatArray {
        if (!loadingState.isSuccess() || session == null || inputName == null) {
            Timber.w("Recognizer not loaded, returning zero embedding")
            return FloatArray(512)
        }
        
        return try {
            val inputTensor = preprocessImage(alignedFace)
            
            val outputs = session!!.run(mapOf(inputName!! to inputTensor))
            
            val embedding = extractEmbedding(outputs)
            
            val normalized = l2NormalizeInPlace(embedding)
            
            inputTensor.close()
            outputs.close()
            
            Timber.d("Generated embedding of size ${normalized.size}")
            normalized
        } catch (e: Exception) {
            Timber.e(e, "Recognition failed")
            FloatArray(512)
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val src = if (bitmap.width == targetSize && bitmap.height == targetSize) {
            bitmap
        } else {
            inputCanvas.drawColor(Color.BLACK)
            inputCanvas.drawBitmap(bitmap, null, Rect(0, 0, targetSize, targetSize), null)
            inputBitmap
        }

        src.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        val planeSize = targetSize * targetSize
        for (i in 0 until planeSize) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            inputFloats[i] = (r - 127.5f) / 127.5f
            inputFloats[planeSize + i] = (g - 127.5f) / 127.5f
            inputFloats[planeSize * 2 + i] = (b - 127.5f) / 127.5f
        }

        inputFloatBuffer.rewind()
        return OnnxTensor.createTensor(ortEnv, inputFloatBuffer, inputShape)
    }
    
    private fun extractEmbedding(outputs: OrtSession.Result): FloatArray {
        val iterator = outputs.iterator()
        if (!iterator.hasNext()) {
            Timber.w("No outputs from model")
            return FloatArray(512)
        }
        
        val output = iterator.next().value
        
        return when (val value = output.value) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val array = value[0] as? FloatArray ?: FloatArray(512)
                array
            }
            is FloatArray -> value
            else -> {
                Timber.w("Unexpected output type: ${value?.javaClass}")
                FloatArray(512)
            }
        }
    }
    
    private fun l2NormalizeInPlace(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = sqrt(norm)
        
        if (norm < 1e-6f) {
            Timber.w("Embedding norm is too small: $norm")
            return embedding
        }
        
        for (i in embedding.indices) {
            embedding[i] = embedding[i] / norm
        }

        return embedding
    }
    
    override fun cosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        require(emb1.size == emb2.size) { "Embeddings must have same size" }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
            norm1 += emb1[i] * emb1[i]
            norm2 += emb2[i] * emb2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        
        return if (denominator > 1e-6f) {
            dotProduct / denominator
        } else {
            0f
        }
    }
    
    override fun close() {
        try {
            session?.close()
            inputBitmap.recycle()
            Timber.d("ArcFace Recognizer closed")
        } catch (e: Exception) {
            Timber.e(e, "Error closing recognizer")
        }
    }
}
