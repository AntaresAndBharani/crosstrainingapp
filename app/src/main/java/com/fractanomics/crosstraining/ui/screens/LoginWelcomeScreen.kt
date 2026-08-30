package com.fractanomics.crosstraining.ui.screens

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fractanomics.crosstraining.R
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.fractanomics.crosstraining.BuildConfig
import com.fractanomics.crosstraining.ui.components.ResetPasswordDialog

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
    var rememberMe by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }

    val isSnapshot = runCatching { BuildConfig.APP_ENV }.getOrDefault("snapshot") == "snapshot"

    // Direct single-pass Google System Account Chooser (never shows double popups)
    val googleAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val accountName = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!accountName.isNullOrBlank()) {
            isLoading = true
            viewModel.logInWithGoogleAccount(accountName, accountName.substringBefore("@"), remember = rememberMe) { ok, err ->
                isLoading = false
                if (ok) {
                    scope.launch { snackbar.showSnackbar("Welcome, $accountName!") }
                } else {
                    errorMessage = err ?: "Google account synchronization failed"
                }
            }
        } else {
            isLoading = false
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Logo & Header
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
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
                    if (isSnapshot) "Snapshot / Testing Environment" else "Master Your Lifts & Conditioning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Authentication Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; errorMessage = null },
                            text = { Text("Log In", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; errorMessage = null },
                            text = { Text("Sign Up", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text(if (selectedTab == 0) "Email or Username" else "Email Address") },
                        leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Remember Me Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMe = !rememberMe }
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Remember me on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

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
                                Text("Forgot or Set Password?", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Button(
                        enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                        onClick = {
                            isLoading = true
                            if (selectedTab == 0) {
                                viewModel.logInWithEmail(email.trim(), password, remember = rememberMe) { ok, err ->
                                    isLoading = false
                                    if (ok) {
                                        scope.launch { snackbar.showSnackbar("Welcome back!") }
                                    } else {
                                        errorMessage = err ?: "Log in failed"
                                    }
                                }
                            } else {
                                viewModel.signUpWithEmail(email.trim(), password, remember = rememberMe) { ok, err ->
                                    isLoading = false
                                    if (ok) {
                                        scope.launch { snackbar.showSnackbar("Account created! Cloud sync active.") }
                                    } else {
                                        errorMessage = err ?: "Sign up failed"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (selectedTab == 0) "Log In" else "Sign Up", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick-login chips for testing & snapshot mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isSnapshot) "Snapshot Test Accounts:" else "Quick Test Accounts:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSnapshot) {
                                FilterChip(
                                    selected = email == "coach",
                                    onClick = {
                                        email = "coach"
                                        password = "coach"
                                        errorMessage = null
                                    },
                                    label = { Text("🏋️ coach/coach", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = email == "athlete",
                                    onClick = {
                                        email = "athlete"
                                        password = "athlete"
                                        errorMessage = null
                                    },
                                    label = { Text("🏃 athlete/athlete", style = MaterialTheme.typography.labelSmall) }
                                )
                            } else {
                                FilterChip(
                                    selected = email == "pv.joseangel@gmail.com",
                                    onClick = {
                                        email = "pv.joseangel@gmail.com"
                                        errorMessage = null
                                    },
                                    label = { Text("🏋️ Coach (pv.joseangel)", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = email == "jangelpv",
                                    onClick = {
                                        email = "jangelpv"
                                        password = "crossAthlet3"
                                        errorMessage = null
                                    },
                                    label = { Text("🏃 Athlete (jangelpv)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
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
                Text(
                    "OR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // Modern, Elegant Google Sign-In Button (Clean Vector Logo & Single Dialog)
            OutlinedButton(
                enabled = !isLoading,
                onClick = {
                    errorMessage = null
                    try {
                        val intent = AccountManager.newChooseAccountIntent(
                            null, null, arrayOf("com.google"), null, null, null, null
                        )
                        googleAccountLauncher.launch(intent)
                    } catch (e: Exception) {
                        errorMessage = "Unable to open Google Account selector: ${e.localizedMessage}"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Continue with Google",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Secondary option: Continue as Guest
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Continue as Guest",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showForgotDialog) {
        var resetMsg by remember { mutableStateOf<String?>(null) }
        var isResetting by remember { mutableStateOf(false) }

        ResetPasswordDialog(
            initialEmail = email,
            isLoading = isResetting,
            message = resetMsg,
            onDismiss = {
                showForgotDialog = false
                resetMsg = null
            },
            onSendResetLink = { targetEmail ->
                isResetting = true
                resetMsg = null
                viewModel.sendPasswordReset(targetEmail.trim()) { ok, err ->
                    isResetting = false
                    if (ok) {
                        resetMsg = "Reset link sent to ${targetEmail.trim()}!"
                    } else {
                        resetMsg = err ?: "Failed to send reset email"
                    }
                }
            }
        )
    }
}
