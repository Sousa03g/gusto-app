package com.gusto.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gusto.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN, REGISTER, FORGOT_PASSWORD, RESET_OTP
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand Title
            Text(
                text = "Gusto.",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (mode) {
                    AuthMode.LOGIN -> "Cozinhe com simplicidade e precisão."
                    AuthMode.REGISTER -> "Crie sua conta para colecionar suas receitas."
                    AuthMode.FORGOT_PASSWORD -> "Recuperação com chave de uso único (OTP)."
                    AuthMode.RESET_OTP -> "Digite o código de 6 dígitos recebido e sua nova senha."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Feedback Message Banner
            AnimatedVisibility(visible = statusMessage != null) {
                statusMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Input Fields
            if (mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (mode != AuthMode.RESET_OTP) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (mode == AuthMode.LOGIN || mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (mode == AuthMode.RESET_OTP) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    label = { Text("Código OTP (6 dígitos)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nova Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Primary Action Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        statusMessage = null
                        isError = false
                        when (mode) {
                            AuthMode.LOGIN -> {
                                val res = authRepository.login(email, password)
                                res.onSuccess { onAuthSuccess() }
                                    .onFailure {
                                        isError = true
                                        statusMessage = it.message ?: "Erro ao entrar"
                                    }
                            }
                            AuthMode.REGISTER -> {
                                val res = authRepository.register(name, email, password)
                                res.onSuccess { onAuthSuccess() }
                                    .onFailure {
                                        isError = true
                                        statusMessage = it.message ?: "Erro no cadastro"
                                    }
                            }
                            AuthMode.FORGOT_PASSWORD -> {
                                val res = authRepository.forgotPassword(email)
                                res.onSuccess {
                                    isError = false
                                    statusMessage = it
                                    mode = AuthMode.RESET_OTP
                                }.onFailure {
                                    isError = true
                                    statusMessage = it.message ?: "Erro ao enviar código"
                                }
                            }
                            AuthMode.RESET_OTP -> {
                                val res = authRepository.resetPassword(email, otp, newPassword)
                                res.onSuccess {
                                    isError = false
                                    statusMessage = it
                                    mode = AuthMode.LOGIN
                                }.onFailure {
                                    isError = true
                                    statusMessage = it.message ?: "Falha na validação"
                                }
                            }
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = when (mode) {
                            AuthMode.LOGIN -> "Entrar"
                            AuthMode.REGISTER -> "Criar Conta"
                            AuthMode.FORGOT_PASSWORD -> "Enviar Código OTP"
                            AuthMode.RESET_OTP -> "Redefinir Senha"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation between auth modes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (mode) {
                    AuthMode.LOGIN -> {
                        Text(
                            text = "Esqueci a senha",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable {
                                statusMessage = null
                                mode = AuthMode.FORGOT_PASSWORD
                            }
                        )
                        Text(
                            text = "Não tem conta? Cadastre-se",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                statusMessage = null
                                mode = AuthMode.REGISTER
                            }
                        )
                    }
                    AuthMode.REGISTER, AuthMode.FORGOT_PASSWORD, AuthMode.RESET_OTP -> {
                        Text(
                            text = "← Voltar ao Login",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                statusMessage = null
                                mode = AuthMode.LOGIN
                            }
                        )
                    }
                }
            }
        }
    }
}
