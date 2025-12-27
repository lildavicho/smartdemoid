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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AdminPinDialog(
    title: String = "Enter Admin PIN",
    adminPinDataStore: AdminPinDataStore,
    onPinVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var remainingAttempts by remember { mutableStateOf(5) }
    var lockoutSeconds by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val isPinSet by adminPinDataStore.isPinSet.collectAsState(initial = false)
    val failedAttempts by adminPinDataStore.failedAttempts.collectAsState(initial = 0)
    
    LaunchedEffect(failedAttempts) {
        remainingAttempts = 5 - failedAttempts
    }
    
    LaunchedEffect(Unit) {
        if (adminPinDataStore.isLocked()) {
            lockoutSeconds = adminPinDataStore.getRemainingLockoutSeconds()
            while (lockoutSeconds > 0) {
                delay(1000)
                lockoutSeconds = adminPinDataStore.getRemainingLockoutSeconds()
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPinSet) {
                    Text(
                        text = "No admin PIN set. Please set a PIN in Diagnostics first.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (lockoutSeconds > 0) {
                    Text(
                        text = "Too many failed attempts. Locked for $lockoutSeconds seconds.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { newValue ->
                            if (newValue.length <= 6 && newValue.all { char -> char.isDigit() }) {
                                pin = newValue
                                error = null
                            }
                        },
                        label = { Text("PIN (4-6 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = error != null,
                        supportingText = {
                            if (error != null) {
                                Text(error!!, color = MaterialTheme.colorScheme.error)
                            } else if (remainingAttempts < 5) {
                                Text("$remainingAttempts attempts remaining")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (isPinSet && lockoutSeconds == 0) {
                Button(
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            error = null
                            try {
                                val isCorrect = adminPinDataStore.verifyPin(pin)
                                if (isCorrect) {
                                    onPinVerified()
                                } else {
                                    error = "Incorrect PIN"
                                    pin = ""
                                    val currentAttempts = adminPinDataStore.failedAttempts.first()
                                    remainingAttempts = 5 - currentAttempts
                                    if (remainingAttempts <= 0) {
                                        lockoutSeconds = adminPinDataStore.getRemainingLockoutSeconds()
                                    }
                                }
                            } catch (e: SecurityException) {
                                error = e.message
                                lockoutSeconds = adminPinDataStore.getRemainingLockoutSeconds()
                            } finally {
                                isVerifying = false
                            }
                        }
                    },
                    enabled = pin.length >= 4 && !isVerifying
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Verify")
                    }
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
