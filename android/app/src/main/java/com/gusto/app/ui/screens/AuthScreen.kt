package com.gusto.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gusto.app.data.repository.AuthRepository
import kotlinx.coroutines.delay
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
    val haptic = LocalHapticFeedback.current
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    // Efeito do contador de reenvio do OTP
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    // Critérios de validação de senha
    val hasMinLength = newPassword.length >= 8
    val hasUppercase = newPassword.any { it.isUpperCase() }
    val hasDigit = newPassword.any { it.isDigit() }
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val isNewPasswordFormValid = hasMinLength && hasUppercase && hasDigit && passwordsMatch && otp.length == 6

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand Title
            Text(
                text = "Gusto.",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (mode) {
                    AuthMode.LOGIN -> "Cozinhe com simplicidade e precisão."
                    AuthMode.REGISTER -> "Crie sua conta para colecionar suas receitas."
                    AuthMode.FORGOT_PASSWORD -> "Recupere o acesso à sua conta via código seguro."
                    AuthMode.RESET_OTP -> "Digite o código de 6 dígitos e crie sua nova senha."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feedback Message Banner
            AnimatedVisibility(visible = statusMessage != null) {
                statusMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else Color(0xFF2E7D32).copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Banner informativo com o e-mail no modo RESET_OTP
            if (mode == AuthMode.RESET_OTP) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Código enviado para:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = email.ifBlank { "seu e-mail" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(onClick = {
                            statusMessage = null
                            mode = AuthMode.FORGOT_PASSWORD
                        }) {
                            Text("Alterar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Input: Nome Completo (Apenas Cadastro)
            if (mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Input: E-mail (Login, Cadastro e Esqueci a Senha)
            if (mode != AuthMode.RESET_OTP) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Input: Senha (Login e Cadastro)
            if (mode == AuthMode.LOGIN || mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Inputs no modo RESET_OTP: Código OTP + Nova Senha + Confirmação + Critérios
            if (mode == AuthMode.RESET_OTP) {
                // Código OTP
                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otp = it },
                    label = { Text("Código de 6 dígitos") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        TextButton(
                            enabled = resendCooldown == 0 && !isLoading,
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    val res = authRepository.forgotPassword(email)
                                    res.onSuccess {
                                        isError = false
                                        statusMessage = "Novo código enviado com sucesso!"
                                        resendCooldown = 60
                                    }.onFailure {
                                        isError = true
                                        statusMessage = it.message
                                    }
                                    isLoading = false
                                }
                            }
                        ) {
                            Text(
                                text = if (resendCooldown > 0) "${resendCooldown}s" else "Reenviar",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Nova Senha
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nova Senha") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (newPasswordVisible) "Ocultar senha" else "Mostrar senha",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Confirmar Nova Senha
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar Nova Senha") },
                    leadingIcon = {
                        Icon(Icons.Outlined.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Ocultar senha" else "Mostrar senha",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Checklist dinâmico dos requisitos de senha
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PasswordRequirementItem("Mínimo de 8 caracteres", hasMinLength)
                        PasswordRequirementItem("Pelo menos 1 letra maiúscula (A-Z)", hasUppercase)
                        PasswordRequirementItem("Pelo menos 1 número (0-9)", hasDigit)
                        PasswordRequirementItem("As senhas são idênticas", passwordsMatch)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Primary Action Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                    resendCooldown = 60
                                }.onFailure {
                                    isError = true
                                    statusMessage = it.message ?: "Erro ao enviar código"
                                }
                            }
                            AuthMode.RESET_OTP -> {
                                if (!isNewPasswordFormValid) {
                                    isError = true
                                    statusMessage = when {
                                        otp.length != 6 -> "Digite o código de 6 dígitos completo."
                                        !hasMinLength -> "A senha deve ter no mínimo 8 caracteres."
                                        !hasUppercase -> "A senha deve conter ao menos uma letra maiúscula."
                                        !hasDigit -> "A senha deve conter ao menos um número."
                                        !passwordsMatch -> "A confirmação de senha não coincide."
                                        else -> "Verifique os dados informados."
                                    }
                                    isLoading = false
                                    return@launch
                                }

                                val res = authRepository.resetPassword(email, otp, newPassword)
                                res.onSuccess {
                                    isError = false
                                    statusMessage = "Senha alterada com sucesso! Entre com sua nova senha."
                                    password = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    otp = ""
                                    mode = AuthMode.LOGIN
                                }.onFailure {
                                    isError = true
                                    statusMessage = it.message ?: "Código inválido ou erro"
                                }
                            }
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = when (mode) {
                            AuthMode.LOGIN -> "Entrar"
                            AuthMode.REGISTER -> "Criar Conta"
                            AuthMode.FORGOT_PASSWORD -> "Enviar Código de Verificação"
                            AuthMode.RESET_OTP -> "Redefinir e Salvar Nova Senha"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navegação entre modos de autenticação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (mode) {
                    AuthMode.LOGIN -> {
                        Text(
                            text = "Esqueci a senha",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable {
                                statusMessage = null
                                mode = AuthMode.FORGOT_PASSWORD
                            }
                        )
                        Text(
                            text = "Cadastre-se",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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

@Composable
private fun PasswordRequirementItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isMet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isMet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
