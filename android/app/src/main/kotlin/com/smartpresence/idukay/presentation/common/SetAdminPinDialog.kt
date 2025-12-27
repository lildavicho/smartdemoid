package com.smartpresence.idukay.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smartpresence.idukay.data.local.AdminPinDataStore
import com.smartpresence.idukay.data.local.util.PinHashUtil
import kotlinx.coroutines.launch

@Composable
fun SetAdminPinDialog(
    adminPinDataStore: AdminPinDataStore,
    onPinSet: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSetting by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Admin PIN") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Set a 4-6 digit PIN for admin access",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text("New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null && error?.contains("PIN") == true,
                    supportingText = {
                        if (pin.isNotEmpty() && !PinHashUtil.isValidPinFormat(pin)) {
                            Text("PIN must be 4-6 digits", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            confirmPin = it
                            error = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null && error?.contains("match") == true,
                    supportingText = {
                        if (confirmPin.isNotEmpty() && pin != confirmPin) {
                            Text("PINs do not match", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (!PinHashUtil.isValidPinFormat(pin)) {
                            error = "PIN must be 4-6 digits"
                            return@launch
                        }
                        if (pin != confirmPin) {
                            error = "PINs do not match"
                            return@launch
                        }
                        
                        isSetting = true
                        try {
                            adminPinDataStore.setPin(pin)
                            onPinSet()
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to set PIN"
                        } finally {
                            isSetting = false
                        }
                    }
                },
                enabled = pin.length >= 4 && pin == confirmPin && !isSetting
            ) {
                if (isSetting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Set PIN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
