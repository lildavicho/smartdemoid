package com.smartpresence.idukay.presentation.attendance

import android.view.ViewGroup
import android.view.Surface
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * CameraX preview composable for face recognition with automatic camera fallback.
 * Tries front camera first, then back camera. Shows error UI if no camera available.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analysisIntervalMs: Long,
    onFrameAvailable: (CameraFrame) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var cameraError: String? by remember { mutableStateOf(null) }
    var isCameraInitialized by remember { mutableStateOf(false) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val onFrameAvailableState = rememberUpdatedState(onFrameAvailable)
    val analyzer = remember { FaceRecognitionAnalyzer { frame -> onFrameAvailableState.value(frame) } }

    LaunchedEffect(analysisIntervalMs) {
        analyzer.minAnalyzeIntervalMs = analysisIntervalMs
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Timber.e(e, "Failed to unbind camera use cases")
            }
            cameraExecutor.shutdown()
        }
    }
    
    // Show error UI if camera initialization failed
    if (cameraError != null) {
        CameraErrorUI(
            modifier = modifier,
            errorMessage = cameraError!!,
            onRetry = {
                cameraError = null
                isCameraInitialized = false
            }
        )
        return
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    val targetSize = Size(640, 480)
                    
                    // Preview use case
                    val preview = Preview.Builder()
                        .setTargetResolution(targetSize)
                        .setTargetRotation(rotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    // Image analysis use case for face recognition
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setImageQueueDepth(1)
                        .setTargetResolution(targetSize)
                        .setTargetRotation(rotation)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

                    tryEnableOutputRotation(analysisBuilder)

                    val imageAnalysis = analysisBuilder.build().also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }
                    
                    // Select camera with fallback: front -> back -> error
                    val cameraSelector = selectAvailableCamera(provider)
                    
                    if (cameraSelector == null) {
                        cameraError = "No camera available on this device"
                        Timber.e("No front or back camera available")
                        return@addListener
                    }
                    
                    // Unbind all use cases before rebinding
                    provider.unbindAll()
                    
                    // Bind use cases to camera
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    
                    isCameraInitialized = true
                    Timber.d("Camera initialized successfully with ${getCameraName(cameraSelector)}")
                    
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Camera selector unable to resolve camera")
                    cameraError = "Camera not available: ${e.message}"
                } catch (e: Exception) {
                    Timber.e(e, "Camera initialization failed")
                    cameraError = "Failed to initialize camera: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        update = {
            analyzer.minAnalyzeIntervalMs = analysisIntervalMs
        }
    )
}

/**
 * Selects an available camera with fallback logic.
 * Priority: Front camera (for face recognition) -> Back camera -> null
 */
private fun selectAvailableCamera(provider: ProcessCameraProvider): CameraSelector? {
    // Try front camera first (better for face recognition)
    val hasFrontCamera = try {
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    } catch (e: Exception) {
        Timber.w(e, "Error checking front camera availability")
        false
    }
    
    if (hasFrontCamera) {
        Timber.d("Using front camera")
        return CameraSelector.DEFAULT_FRONT_CAMERA
    }
    
    // Fallback to back camera
    val hasBackCamera = try {
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
    } catch (e: Exception) {
        Timber.w(e, "Error checking back camera availability")
        false
    }
    
    if (hasBackCamera) {
        Timber.d("Using back camera (front not available)")
        return CameraSelector.DEFAULT_BACK_CAMERA
    }
    
    // No camera available
    Timber.e("No camera available on device")
    return null
}

/**
 * Returns a human-readable name for the camera selector
 */
private fun getCameraName(selector: CameraSelector): String {
    return when (selector) {
        CameraSelector.DEFAULT_FRONT_CAMERA -> "front camera"
        CameraSelector.DEFAULT_BACK_CAMERA -> "back camera"
        else -> "unknown camera"
    }
}

/**
 * Error UI shown when camera initialization fails
 */
@Composable
private fun CameraErrorUI(
    modifier: Modifier = Modifier,
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📷",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Error de Cámara",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

private fun tryEnableOutputRotation(builder: ImageAnalysis.Builder) {
    try {
        val method = builder.javaClass.getMethod("setOutputImageRotationEnabled", Boolean::class.javaPrimitiveType)
        method.invoke(builder, true)
    } catch (_: Throwable) {
        // Optional API depending on CameraX version.
    }
}
