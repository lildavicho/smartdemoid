package com.smartpresence.idukay.presentation.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartpresence.idukay.presentation.common.AdminPinDialog
import com.smartpresence.idukay.presentation.common.SetAdminPinDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shareDiagnostics() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DiagnosticSection(title = "Device Info") {
                DiagnosticItem(label = "Device ID", value = uiState.deviceId)
                DiagnosticItem(label = "Teacher ID", value = uiState.teacherId)
                DiagnosticItem(label = "App Version", value = uiState.appVersion)
            }
            
            DiagnosticSection(title = "AI Configuration") {
                DiagnosticItem(label = "ONNX Provider", value = uiState.onnxProvider)
                DiagnosticItem(label = "Power Mode", value = uiState.powerMode)
                DiagnosticItem(label = "Threshold Mode", value = uiState.thresholdMode)
                DiagnosticItem(label = "Quality Min", value = String.format("%.2f", uiState.qualityMin))
            }
            
            DiagnosticSection(title = "Sync Status") {
                DiagnosticItem(
                    label = "Pending Attendance",
                    value = uiState.pendingAttendanceCount.toString()
                )
                DiagnosticItem(
                    label = "Pending Session Updates",
                    value = uiState.pendingSessionUpdateCount.toString()
                )
                DiagnosticItem(
                    label = "Consecutive Failures",
                    value = uiState.consecutiveFailures.toString()
                )
                
                if (uiState.lastSuccessfulSyncAt > 0) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date = Date(uiState.lastSuccessfulSyncAt)
                    DiagnosticItem(
                        label = "Last Successful Sync",
                        value = dateFormat.format(date)
                    )
                }
                
                if (uiState.lastSyncError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Last Sync Error",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.lastSyncError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            if (uiState.modelLoadingErrors.isNotEmpty()) {
                DiagnosticSection(title = "Model Loading Errors") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            uiState.modelLoadingErrors.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
            
            // Admin Settings Section
            DiagnosticSection(title = "Admin Settings") {
                val scope = rememberCoroutineScope()
                val isPinSet by viewModel.adminPinDataStore.isPinSet.collectAsState(initial = false)
                val kioskEnabled by viewModel.settingsDataStore.kioskEnabled.collectAsState(initial = false)
                var showSetPinDialog by remember { mutableStateOf(false) }
                var showKioskPinDialog by remember { mutableStateOf(false) }
                var showAnonymizeDialog by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Admin PIN", fontWeight = FontWeight.Medium)
                    TextButton(onClick = { showSetPinDialog = true }) {
                        Text(if (isPinSet) "Change PIN" else "Set PIN")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kiosk Mode", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = kioskEnabled,
                        onCheckedChange = {
                            if (isPinSet) {
                                showKioskPinDialog = true
                            }
                        }
                    )
                }
                
                Button(
                    onClick = { showAnonymizeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Anonymized Diagnostics")
                }
                
                if (showSetPinDialog) {
                    SetAdminPinDialog(
                        adminPinDataStore = viewModel.adminPinDataStore,
                        onPinSet = { showSetPinDialog = false },
                        onDismiss = { showSetPinDialog = false }
                    )
                }
                
                if (showKioskPinDialog) {
                    AdminPinDialog(
                        title = "Toggle Kiosk Mode",
                        adminPinDataStore = viewModel.adminPinDataStore,
                        onPinVerified = {
                            scope.launch {
                                viewModel.settingsDataStore.setKioskEnabled(!kioskEnabled)
                                showKioskPinDialog = false
                            }
                        },
                        onDismiss = { showKioskPinDialog = false }
                    )
                }
                
                if (showAnonymizeDialog) {
                    AlertDialog(
                        onDismissRequest = { showAnonymizeDialog = false },
                        title = { Text("Export Anonymized") },
                        text = { Text("This will mask deviceId and teacherId") },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.shareDiagnostics(anonymize = true)
                                showAnonymizeDialog = false
                            }) {
                                Text("Export")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAnonymizeDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider()
            content()
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
