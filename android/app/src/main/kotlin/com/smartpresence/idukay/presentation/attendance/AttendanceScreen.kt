package com.smartpresence.idukay.presentation.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartpresence.idukay.BuildConfig
import com.smartpresence.idukay.ai.RecognitionConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.nativeCanvas

// Time formatter for confirmed records
private val confirmedTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatConfirmedTime(timestampMs: Long): String {
    return confirmedTimeFormatter.format(Instant.ofEpochMilli(timestampMs))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    courseId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var navigateBackAfterEndSession by remember { mutableStateOf(false) }
    val pendingSyncCount = uiState.pendingCount + uiState.pendingSessionUpdateCount
    
    // Kiosk mode from UiState
    val kioskEnabled = uiState.kioskEnabled
    var showExitPinDialog by remember { mutableStateOf(false) }
    
    BackHandler(enabled = kioskEnabled && uiState.isSessionActive) {
        showExitPinDialog = true
    }
    
    // Camera permission handling
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCameraPermission(isGranted)
    }
    
    // Check camera permission on start
    LaunchedEffect(Unit) {
        viewModel.setCourseId(courseId)
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            viewModel.setCameraPermission(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Show error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AttendanceUiEvent.Snackbar -> {
                    snackbarHostState.showSnackbar(message = event.message, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    LaunchedEffect(navigateBackAfterEndSession, uiState.isLoading, uiState.isSessionActive) {
        if (navigateBackAfterEndSession && !uiState.isLoading && !uiState.isSessionActive) {
            navigateBackAfterEndSession = false
            onNavigateBack()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Tomar Asistencia") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSessionActive) {
                                navigateBackAfterEndSession = true
                                viewModel.endSession()
                            } else {
                                onNavigateBack()
                            }
                        },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToDiagnostics() }) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isCompactWidth = maxWidth < 600.dp
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Model Loading Error Banner
                if (uiState.modelLoadingError != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ AI Models Failed to Load",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.modelLoadingError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                if (isCompactWidth) {
                    // PHONE LAYOUT: Column with camera on top, controls bottom
                    PhoneLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                        pendingSyncCount = pendingSyncCount,
                        cameraPermissionLauncher = cameraPermissionLauncher,
                        onEndSession = {
                            navigateBackAfterEndSession = true
                            viewModel.endSession()
                        }
                    )
                } else {
                    // TABLET LAYOUT: Row with camera left, panel right
                    TabletLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                        pendingSyncCount = pendingSyncCount,
                        cameraPermissionLauncher = cameraPermissionLauncher,
                        onEndSession = {
                            navigateBackAfterEndSession = true
                            viewModel.endSession()
                        }
                    )
                }
            }
        }
    }
    
    // Kiosk exit dialog
    KioskExitDialog(
        showDialog = showExitPinDialog,
        onDismiss = { showExitPinDialog = false },
        onPinVerified = {
            showExitPinDialog = false
            viewModel.endSession()
            onNavigateBack()
        }
    )
}

@Composable
private fun PhoneLayout(
    uiState: AttendanceUiState,
    viewModel: AttendanceViewModel,
    pendingSyncCount: Int,
    cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Camera preview - takes most space
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CameraPreviewContent(
                uiState = uiState,
                viewModel = viewModel,
                pendingSyncCount = pendingSyncCount,
                cameraPermissionLauncher = cameraPermissionLauncher
            )
        }
        
        // Compact controls panel for phone
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.courseName.ifEmpty { "Cargando..." },
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (pendingSyncCount > 0) {
                        Text(
                            text = "⏳ $pendingSyncCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                
                // Confirmed count
                Text(
                    text = "Confirmados: ${uiState.confirmedRecords.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Main action button
                Button(
                    onClick = {
                        if (uiState.isSessionActive) {
                            onEndSession()
                        } else {
                            viewModel.startSession()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !uiState.isLoading && uiState.cameraPermissionGranted && uiState.modelLoadingError == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isSessionActive)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = if (uiState.isSessionActive) "Detener" else "Iniciar Asistencia",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletLayout(
    uiState: AttendanceUiState,
    viewModel: AttendanceViewModel,
    pendingSyncCount: Int,
    cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onEndSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left side: Camera preview
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Course info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uiState.courseName.ifEmpty { "Cargando..." },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Código: ${uiState.courseId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        ThresholdModeSelector(
                            selected = uiState.thresholdMode,
                            enabled = !uiState.isLoading,
                            onSelected = { mode -> viewModel.setThresholdMode(mode) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        PowerModeSelector(
                            selected = uiState.powerMode,
                            enabled = !uiState.isLoading,
                            onSelected = { mode -> viewModel.setPowerMode(mode) }
                        )

                        if (pendingSyncCount > 0) {
                            Text(
                                text = "⏳ $pendingSyncCount pendientes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (uiState.isOffline) {
                            Text(
                                text = "📡 Sin conexión - guardando local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sincronizar"
                        )
                    }
                }
            }
            
            // Camera preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreviewContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    pendingSyncCount = pendingSyncCount,
                    cameraPermissionLauncher = cameraPermissionLauncher
                )
            }
            
            // Control button
            Button(
                onClick = {
                    if (uiState.isSessionActive) {
                        onEndSession()
                    } else {
                        viewModel.startSession()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading && uiState.cameraPermissionGranted && uiState.modelLoadingError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isSessionActive)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (uiState.isSessionActive) "Detener Asistencia" else "Iniciar Asistencia",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // Right side: Teacher panel
        TeacherPanel(
            uiState = uiState,
            viewModel = viewModel,
            pendingSyncCount = pendingSyncCount,
            onEndSession = onEndSession
        )
    }
}

@Composable
private fun CameraPreviewContent(
    uiState: AttendanceUiState,
    viewModel: AttendanceViewModel,
    pendingSyncCount: Int,
    cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.cameraPermissionGranted && uiState.isSessionActive) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                analysisIntervalMs = RecognitionConfig.frameIntervalMs(uiState.powerMode),
                onFrameAvailable = { frame ->
                    viewModel.submitFrame(frame)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                if (!uiState.cameraPermissionGranted) {
                    Text(
                        text = "📷",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "Permiso de cámara requerido",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Esta aplicación necesita acceso a la cámara para reconocer rostros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text("Permitir cámara")
                    }
                } else if (!uiState.isSessionActive) {
                    Text(
                        text = "📹",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "Presiona 'Iniciar Asistencia'",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    CircularProgressIndicator()
                    Text(
                        text = "Cargando cámara...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (BuildConfig.DEBUG && uiState.cameraPermissionGranted && uiState.isSessionActive && uiState.debugOverlay) {
            DebugRecognitionOverlay(
                modifier = Modifier.fillMaxSize(),
                faces = uiState.overlayFaces,
                frameWidth = uiState.frameWidth,
                frameHeight = uiState.frameHeight,
                fps = uiState.fps,
                msPerFrame = uiState.msPerFrame,
                thresholdMode = uiState.thresholdMode,
                powerMode = uiState.powerMode,
                pendingCount = pendingSyncCount
            )
        }
        
        // Session status indicator
        if (uiState.isSessionActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.error,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔴 GRABANDO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Composable
private fun RowScope.TeacherPanel(
    uiState: AttendanceUiState,
    viewModel: AttendanceViewModel,
    pendingSyncCount: Int,
    onEndSession: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Panel docente",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val confirmedList = remember(uiState.confirmedRecords) {
                uiState.confirmedRecords.values.sortedByDescending { it.confirmedAt }
            }
            val recognizedList = remember(uiState.recognizedStudents) {
                uiState.recognizedStudents.entries.toList()
            }
            var localMinConfidence by remember(uiState.autoConfirmMinConfidence) {
                mutableStateOf(uiState.autoConfirmMinConfidence)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirmados: ${confirmedList.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Pendientes: $pendingSyncCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (pendingSyncCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { viewModel.syncNow() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sincronizar ahora")
                }
                Button(
                    onClick = { onEndSession() },
                    enabled = uiState.isSessionActive && !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Finalizar sesión")
                }
            }

            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Debug overlay", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.debugOverlay,
                        onCheckedChange = { checked -> viewModel.setDebugOverlay(checked) },
                        enabled = !uiState.isLoading
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Auto-confirm", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = uiState.autoConfirmEnabled,
                    onCheckedChange = { checked -> viewModel.setAutoConfirmEnabled(checked) },
                    enabled = !uiState.isLoading
                )
            }

            if (uiState.autoConfirmEnabled) {
                Text(
                    text = "Mín confianza: ${(localMinConfidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = localMinConfidence,
                    onValueChange = { value -> localMinConfidence = value },
                    onValueChangeFinished = { viewModel.setAutoConfirmMinConfidence(localMinConfidence) },
                    valueRange = 0.50f..0.95f,
                    enabled = !uiState.isLoading
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "Confirmados",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (confirmedList.isEmpty()) {
                    item {
                        Text(
                            text = "Aún no hay confirmados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(confirmedList, key = { it.localId }) { record ->
                        ConfirmedRecordItem(
                            record = record,
                            onUndo = { viewModel.undoConfirmation(record.studentId) }
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    Text(
                        text = "Detectados (para confirmar)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (recognizedList.isEmpty()) {
                    item {
                        Text(
                            text = "Nadie detectado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(recognizedList, key = { entry -> entry.key }) { entry ->
                        val studentId = entry.key
                        val result = entry.value
                        RecognizedStudentItem(
                            studentId = studentId,
                            confidence = result.confidence,
                            onConfirm = { viewModel.confirmStudent(studentId) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// HELPER COMPOSABLES - Defined at top-level without 'private' to avoid issues
// ============================================================================

@Composable
fun RecognizedStudentItem(
    studentId: String,
    confidence: Float,
    onConfirm: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ID: $studentId",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Confianza: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onConfirm) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Confirmar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ConfirmedRecordItem(
    record: ConfirmedRecord,
    onUndo: () -> Unit
) {
    val isSent = record.status == "SENT"
    val statusLabel = if (isSent) "SENT" else "PENDING"
    val statusColor = if (isSent) Color(0xFF00C853) else Color(0xFFFFB300)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Hora: ${formatConfirmedTime(record.confirmedAt)} · ${(record.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = statusLabel,
                            color = statusColor
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onUndo,
                    enabled = !isSent
                ) {
                    Text("Deshacer")
                }
            }
        }
    }
}

@Composable
fun ThresholdModeSelector(
    selected: RecognitionConfig.ThresholdMode,
    enabled: Boolean,
    onSelected: (RecognitionConfig.ThresholdMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThresholdModeChip(
            label = "Strict",
            mode = RecognitionConfig.ThresholdMode.STRICT,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
        ThresholdModeChip(
            label = "Normal",
            mode = RecognitionConfig.ThresholdMode.NORMAL,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
        ThresholdModeChip(
            label = "Lenient",
            mode = RecognitionConfig.ThresholdMode.LENIENT,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
    }
}

@Composable
fun ThresholdModeChip(
    label: String,
    mode: RecognitionConfig.ThresholdMode,
    selected: RecognitionConfig.ThresholdMode,
    enabled: Boolean,
    onSelected: (RecognitionConfig.ThresholdMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelected(mode) },
        enabled = enabled,
        label = { Text(label) }
    )
}

@Composable
fun PowerModeSelector(
    selected: RecognitionConfig.PowerMode,
    enabled: Boolean,
    onSelected: (RecognitionConfig.PowerMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PowerModeChip(
            label = "Perf",
            mode = RecognitionConfig.PowerMode.PERFORMANCE,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
        PowerModeChip(
            label = "Balanced",
            mode = RecognitionConfig.PowerMode.BALANCED,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
        PowerModeChip(
            label = "Eco",
            mode = RecognitionConfig.PowerMode.ECO,
            selected = selected,
            enabled = enabled,
            onSelected = onSelected
        )
    }
}

@Composable
fun PowerModeChip(
    label: String,
    mode: RecognitionConfig.PowerMode,
    selected: RecognitionConfig.PowerMode,
    enabled: Boolean,
    onSelected: (RecognitionConfig.PowerMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelected(mode) },
        enabled = enabled,
        label = { Text(label) }
    )
}

@Composable
fun DebugRecognitionOverlay(
    modifier: Modifier = Modifier,
    faces: List<OverlayFaceUi>,
    frameWidth: Int,
    frameHeight: Int,
    fps: Int,
    msPerFrame: Long,
    thresholdMode: RecognitionConfig.ThresholdMode,
    powerMode: RecognitionConfig.PowerMode,
    pendingCount: Int
) {
    if (frameWidth <= 0 || frameHeight <= 0) return

    val density = LocalDensity.current
    val textSizePx = with(density) { 12.sp.toPx() }

    val textPaint = remember(textSizePx) {
        Paint().apply {
            isAntiAlias = true
            color = Color.White.toArgb()
            textSize = textSizePx
        }
    }

    Canvas(modifier = modifier) {
        val frameW = frameWidth.toFloat()
        val frameH = frameHeight.toFloat()

        val scale = max(size.width / frameW, size.height / frameH)
        val dx = (size.width - frameW * scale) / 2f
        val dy = (size.height - frameH * scale) / 2f

        val strokeWidthPx = 2.5f
        val labelPadding = 6f
        val labelHeight = textPaint.textSize + labelPadding * 1.5f

        for (face in faces) {
            if (face.bbox.size < 4) continue

            val x1 = min(face.bbox[0], face.bbox[2])
            val y1 = min(face.bbox[1], face.bbox[3])
            val x2 = max(face.bbox[0], face.bbox[2])
            val y2 = max(face.bbox[1], face.bbox[3])

            val left = x1 * scale + dx
            val top = y1 * scale + dy
            val right = x2 * scale + dx
            val bottom = y2 * scale + dy

            val rectColor = if (face.isConfirmed) Color(0xFF00E676) else Color(0xFFFFC107)

            drawRect(
                color = rectColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = strokeWidthPx)
            )

            val label = face.label
            val labelWidth = textPaint.measureText(label)
            val labelTop = (top - labelHeight).coerceAtLeast(0f)

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.60f),
                topLeft = Offset(left, labelTop),
                size = Size(labelWidth + labelPadding * 2, labelHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            drawContext.canvas.nativeCanvas.drawText(
                label,
                left + labelPadding,
                labelTop + labelHeight - labelPadding,
                textPaint
            )
        }

        val threshold = RecognitionConfig.cosineDistanceThreshold(thresholdMode)
        val lines = listOf(
            "fps: $fps | ms: $msPerFrame",
            "mode: ${thresholdMode.name.lowercase(Locale.US)} <= ${String.format(Locale.US, "%.2f", threshold)}",
            "power: ${powerMode.name.lowercase(Locale.US)}",
            "offline: $pendingCount"
        )

        val panelPadding = 10f
        val lineHeight = textPaint.textSize + 8f
        val panelWidth = lines.maxOf { line -> textPaint.measureText(line) } + panelPadding * 2
        val panelHeight = lineHeight * lines.size + panelPadding
        val panelTopLeft = Offset(12f, 12f)

        drawRoundRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = panelTopLeft,
            size = Size(panelWidth, panelHeight),
            cornerRadius = CornerRadius(12f, 12f)
        )

        var y = panelTopLeft.y + panelPadding + textPaint.textSize
        for (line in lines) {
            drawContext.canvas.nativeCanvas.drawText(line, panelTopLeft.x + panelPadding, y, textPaint)
            y += lineHeight
        }
    }
}

@Composable
fun KioskExitDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onPinVerified: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Salir de Sesión") },
            text = { Text("¿Estás seguro que deseas terminar la sesión?") },
            confirmButton = {
                TextButton(onClick = onPinVerified) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }
}
