package net.kibotu.ro.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import net.kibotu.ro.client.viewmodel.GameViewModel

@Composable
fun LoginScreen(viewModel: GameViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Load saved credentials on first composition (but don't auto-login)
    LaunchedEffect(Unit) {
        val savedEmail = viewModel.getSavedEmail()
        val savedPassword = viewModel.getSavedPassword()
        if (savedEmail != null && savedPassword != null) {
            email = savedEmail
            password = savedPassword
            rememberMe = true
            // Don't auto-login - always show login page for each client process
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ragnarok Online",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 16.dp),
            singleLine = true
        )
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 16.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )
        
        // Remember Me checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Remember Me",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        if (isRegisterMode) {
                            viewModel.register(email, password)
                        } else {
                            viewModel.login(email, password, rememberMe)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRegisterMode) "Register" else "Login")
                }
            }
            
            OutlinedButton(
                onClick = { isRegisterMode = !isRegisterMode },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading
            ) {
                Text(if (isRegisterMode) "Switch to Login" else "Switch to Register")
            }
        }
        
        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.6f)
            )
        }
    }
}

