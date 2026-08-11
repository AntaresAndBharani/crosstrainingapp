package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginWelcomeScreen(
    viewModel: AppViewModel,
    snackbar: SnackbarHostState,
    onContinueAsGuest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Log In, 1 = Sign Up
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Logo & Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "CrossTraining",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Master Your Lifts & Conditioning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Auth Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; errorMessage = null }, text = { Text("Log In") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; errorMessage = null }, text = { Text("Sign Up") })
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (selectedTab == 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showForgotDialog = true }) {
                                Text("Forgot Password?", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Button(
                        enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                        onClick = {
                            isLoading = true
                            if (selectedTab == 0) {
                                viewModel.logInWithEmail(email.trim(), password) { ok, err ->
                                    isLoading = false
                                    if (ok) {
                                        scope.launch { snackbar.showSnackbar("Welcome back!") }
                                    } else {
                                        errorMessage = err ?: "Log in failed"
                                    }
                                }
                            } else {
                                viewModel.signUpWithEmail(email.trim(), password) { ok, err ->
                                    isLoading = false
                                    if (ok) {
                                        scope.launch { snackbar.showSnackbar("Account created! History synced.") }
                                    } else {
                                        errorMessage = err ?: "Sign up failed"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (selectedTab == 0) "Log In" else "Sign Up")
                        }
                    }
                }
            }

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // Google Sign-In Button
            var showGoogleDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showGoogleDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔴 🟡 🟢 🔵  Continue with Google Account", fontWeight = FontWeight.SemiBold)
            }

            // Secondary option: Continue as guest
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue as Guest")
            }

            if (showGoogleDialog) {
                var googleEmail by remember { mutableStateOf("") }
                var googlePass by remember { mutableStateOf("") }
                var isGoogleLoading by remember { mutableStateOf(false) }
                var googleErr by remember { mutableStateOf<String?>(null) }

                AlertDialog(
                    onDismissRequest = { showGoogleDialog = false },
                    title = { Text("Google Account Cloud Sync") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Enter your Google Gmail account credentials to restore all your cloud data, routines, and PR history:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = googleEmail,
                                onValueChange = { googleEmail = it; googleErr = null },
                                label = { Text("Google Email (@gmail.com)") },
                                leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googlePass,
                                onValueChange = { googlePass = it; googleErr = null },
                                label = { Text("Google Account Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (googleErr != null) {
                                Text(googleErr!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = googleEmail.isNotBlank() && googlePass.length >= 6 && !isGoogleLoading,
                            onClick = {
                                isGoogleLoading = true
                                viewModel.logInWithEmail(googleEmail.trim(), googlePass) { ok, err ->
                                    isGoogleLoading = false
                                    if (ok) {
                                        showGoogleDialog = false
                                        scope.launch { snackbar.showSnackbar("Google Account synced successfully!") }
                                    } else {
                                        googleErr = err ?: "Google sign-in failed"
                                    }
                                }
                            }
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Sign In & Sync Data")
                            }
                        }
                    },
                    dismissButton = { TextButton(onClick = { showGoogleDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }

    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetMsg by remember { mutableStateOf<String?>(null) }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your email address to receive a password reset link:")
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetMsg != null) {
                        Text(resetMsg!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = resetEmail.isNotBlank() && !isResetting,
                    onClick = {
                        isResetting = true
                        viewModel.sendPasswordReset(resetEmail.trim()) { ok, err ->
                            isResetting = false
                            if (ok) {
                                resetMsg = "Reset link sent to $resetEmail!"
                            } else {
                                resetMsg = err ?: "Failed to send reset email"
                            }
                        }
                    }
                ) { Text("Send Link") }
            },
            dismissButton = { TextButton(onClick = { showForgotDialog = false }) { Text("Close") } }
        )
    }
}
