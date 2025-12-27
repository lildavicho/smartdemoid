package com.smartpresence.idukay.presentation.attendance

import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analysisIntervalMs: Long,
    onFrameAvailable: (CameraFrame) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var cameraError: String? by remember { mutableStateOf(null) }

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

    if (cameraError != null) {
        CameraErrorUI(
            modifier = modifier,
            errorMessage = cameraError.orEmpty(),
            onRetry = { cameraError = null }
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
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                targetSize,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setImageQueueDepth(1)
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(rotation)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

                    tryEnableOutputRotation(analysisBuilder)

                    val imageAnalysis = analysisBuilder.build().also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                    val cameraSelector = selectAvailableCamera(provider)
                    if (cameraSelector == null) {
                        cameraError = "No hay camara disponible en este dispositivo"
                        Timber.e("No front or back camera available")
                        return@addListener
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    Timber.d("Camera initialized successfully with ${getCameraName(cameraSelector)}")
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Camera selector unable to resolve camera")
                    cameraError = "Camara no disponible: ${e.message}"
                } catch (e: Exception) {
                    Timber.e(e, "Camera initialization failed")
                    cameraError = "No se pudo inicializar la camara: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        update = {
            analyzer.minAnalyzeIntervalMs = analysisIntervalMs
        }
    )
}

private fun selectAvailableCamera(provider: ProcessCameraProvider): CameraSelector? {
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

    Timber.e("No camera available on device")
    return null
}

private fun getCameraName(selector: CameraSelector): String {
    return when (selector) {
        CameraSelector.DEFAULT_FRONT_CAMERA -> "front camera"
        CameraSelector.DEFAULT_BACK_CAMERA -> "back camera"
        else -> "unknown camera"
    }
}

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "CAM",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Error de camara",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
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
