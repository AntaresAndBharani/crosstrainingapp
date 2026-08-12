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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.firebase.SyncStatus
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: AppViewModel,
    snackbar: SnackbarHostState
) {
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAuthModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val email = authUser?.email
                    if (!email.isNullOrBlank()) {
                        Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Permanent Account · Cloud Sync Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Guest Athlete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Anonymous session — Sign in to sync history", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (authUser?.email.isNullOrBlank()) {
                    Button(onClick = { showAuthModal = true }) {
                        Text("Log In / Sign Up")
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Cloud Backup & Sync Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cloud Backup & Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .background(
                                when (syncState) {
                                    SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                                    SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondaryContainer
                                    SyncStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                    SyncStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (syncState) {
                                SyncStatus.SYNCING -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                SyncStatus.SUCCESS -> Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                SyncStatus.ERROR -> Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                SyncStatus.IDLE -> Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                syncState.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    "Your exercises, daily routines, training cycles, logged workouts, and rep maxes are synced with Cloud Firestore.",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        viewModel.triggerCloudSync { ok, err ->
                            scope.launch {
                                snackbar.showSnackbar(if (ok) "Cloud sync completed!" else (err ?: "Sync error"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState != SyncStatus.SYNCING
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Now")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.recoverCloudRoutines { count ->
                            scope.launch {
                                snackbar.showSnackbar(if (count > 0) "Recovered $count routines from cloud!" else "No previous routines found in cloud.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search Cloud for Lost Routines")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.reseedDefaults {
                            scope.launch {
                                snackbar.showSnackbar("Default routines & exercises restored!")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Default Routines")
                }
            }
        }
    }

    if (showAuthModal) {
        AuthDialog(
            viewModel = viewModel,
            onDismiss = { showAuthModal = false },
            onSuccess = { msg ->
                showAuthModal = false
                scope.launch { snackbar.showSnackbar(msg) }
            }
        )
    }
}

@Composable
private fun AuthDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Log In, 1 = Sign Up
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedTab == 0) "Log In to Account" else "Create New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                onClick = {
                    isLoading = true
                    if (selectedTab == 0) {
                        viewModel.logInWithEmail(email.trim(), password) { ok, err ->
                            isLoading = false
                            if (ok) onSuccess("Logged in as $email") else errorMessage = err ?: "Log in failed"
                        }
                    } else {
                        viewModel.signUpWithEmail(email.trim(), password) { ok, err ->
                            isLoading = false
                            if (ok) onSuccess("Account created for $email!") else errorMessage = err ?: "Sign up failed"
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (selectedTab == 0) "Log In" else "Sign Up")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
