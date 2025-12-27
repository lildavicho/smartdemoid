package com.smartpresence.idukay.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartpresence.idukay.data.local.AdminPinDataStore
import com.smartpresence.idukay.data.repository.AuthRepository
import com.smartpresence.idukay.presentation.common.AdminPinDialog
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun DeviceNotAuthorizedScreen(
    currentDeviceId: String,
    authRepository: AuthRepository,
    adminPinDataStore: AdminPinDataStore,
    onRebindSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var isRebinding by remember { mutableStateOf(false) }
    var rebindError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Device Locked",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Device Not Authorized",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This app is bound to a different device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Current Device:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "****${currentDeviceId.takeLast(4)}",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "To use this app on this device, you need to rebind it using the admin PIN.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (rebindError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = rebindError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { showPinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRebinding
            ) {
                if (isRebinding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Rebind with Admin PIN")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRebinding
            ) {
                Text("Cancel")
            }
        }
    }
    
    if (showPinDialog) {
        AdminPinDialog(
            title = "Rebind Device",
            adminPinDataStore = adminPinDataStore,
            onPinVerified = {
                scope.launch {
                    showPinDialog = false
                    isRebinding = true
                    rebindError = null
                    
                    // Generate admin PIN proof (simple token for now)
                    val adminPinProof = "admin_pin_verified_${System.currentTimeMillis()}"
                    
                    // Call backend to rebind
                    val result = authRepository.rebindDevice(currentDeviceId, adminPinProof)
                    
                    result.fold(
                        onSuccess = {
                            Timber.d("Device rebound successfully")
                            isRebinding = false
                            onRebindSuccess()
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to rebind device")
                            isRebinding = false
                            rebindError = error.message ?: "Failed to rebind device"
                        }
                    )
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }
}
