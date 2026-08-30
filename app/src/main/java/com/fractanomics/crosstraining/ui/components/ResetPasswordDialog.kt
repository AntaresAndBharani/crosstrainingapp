package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Validates email addresses against standard RFC-like syntax matching:
 * ^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
 */
object ResetPasswordValidator {
    val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email.trim())
    }
}

/**
 * Maps Firebase Auth exceptions and network failures to user-friendly UI messages.
 */
object PasswordResetErrorMapper {
    const val GENERIC_ERROR_MESSAGE = "An error occurred. Please try again."
    const val NETWORK_ERROR_MESSAGE = "Network error. Please check your connection."
    const val RATE_LIMIT_ERROR_MESSAGE = "Too many attempts. Try again later."
    const val SUCCESS_SNACKBAR_MESSAGE = "If an account exists, a setup link has been sent to your inbox."

    fun map(error: Throwable?): String {
        if (error == null) return GENERIC_ERROR_MESSAGE
        return when {
            error is com.google.firebase.FirebaseNetworkException -> NETWORK_ERROR_MESSAGE
            error is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException -> RATE_LIMIT_ERROR_MESSAGE
            error is java.net.UnknownHostException || error is java.net.SocketTimeoutException || error is java.io.IOException -> NETWORK_ERROR_MESSAGE
            error.javaClass.simpleName.contains("Network", ignoreCase = true) -> NETWORK_ERROR_MESSAGE
            !error.localizedMessage.isNullOrBlank() -> error.localizedMessage!!
            else -> GENERIC_ERROR_MESSAGE
        }
    }
}

/**
 * Modular, stateless dialog component for requesting a password reset email.
 *
 * Encapsulates:
 * - Pre-filling with [initialEmail] and autofocus via [FocusRequester]
 * - Email format validation via [ResetPasswordValidator]
 * - Keyboard IME Done submission
 * - Dismiss lock and [CircularProgressIndicator] when [isLoading] is true
 */
@Composable
fun ResetPasswordDialog(
    initialEmail: String = "",
    isLoading: Boolean = false,
    message: String? = null,
    onDismiss: () -> Unit,
    onSendResetLink: (String) -> Unit
) {
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    val focusRequester = remember { FocusRequester() }
    val isEmailValid = remember(email) { ResetPasswordValidator.isValidEmail(email) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading
        ),
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your email address to receive a password reset link:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isEmailValid && !isLoading) {
                                onSendResetLink(email.trim())
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp)
                )
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isEmailValid && !isLoading,
                onClick = {
                    if (isEmailValid && !isLoading) {
                        onSendResetLink(email.trim())
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send Link")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}
