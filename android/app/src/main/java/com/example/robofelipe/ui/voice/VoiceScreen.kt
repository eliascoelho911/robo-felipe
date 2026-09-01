package com.example.robofelipe.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robofelipe.R

@Composable
fun VoiceScreen(
    modifier: Modifier = Modifier,
    voiceViewModel: VoiceViewModel = viewModel(
        factory = VoiceViewModel.factory(LocalContext.current.applicationContext as android.app.Application)
    ),
) {
    val uiState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = voiceViewModel::updateServerUrl,
            label = { Text(stringResource(R.string.server_url)) },
            singleLine = true,
            enabled = uiState.connectionState == ConnectionState.DISCONNECTED,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { voiceViewModel.connect() },
            enabled = uiState.connectionState == ConnectionState.DISCONNECTED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.connect))
        }

        StatusCard(uiState = uiState)

        Spacer(modifier = Modifier.weight(1f))

        PushToTalkButton(
            isListening = uiState.isListening,
            isEnabled = uiState.connectionState == ConnectionState.CONNECTED,
            onPressed = {
                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    voiceViewModel.startListening()
                }
            },
            onReleased = voiceViewModel::stopListening,
        )

        uiState.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusCard(uiState: VoiceUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusRow(
                label = stringResource(R.string.connection_status),
                value = when (uiState.connectionState) {
                    ConnectionState.DISCONNECTED -> stringResource(R.string.disconnected)
                    ConnectionState.CONNECTING -> stringResource(R.string.connecting)
                    ConnectionState.CONNECTED -> stringResource(R.string.connected)
                },
            )
            if (uiState.connectionState == ConnectionState.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            StatusRow(
                label = stringResource(R.string.capture_status),
                value = when {
                    uiState.isListening -> stringResource(R.string.listening)
                    uiState.isSpeaking -> stringResource(R.string.pet_speaking)
                    else -> stringResource(R.string.idle)
                },
            )
            uiState.transcript?.let {
                Text(
                    text = "${stringResource(R.string.you_said)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            uiState.llmResponse?.let {
                Text(
                    text = "${stringResource(R.string.pet_replied)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PushToTalkButton(
    isListening: Boolean,
    isEnabled: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed && isEnabled) onPressed()
        else if (!isPressed && isEnabled) onReleased()
    }

    Button(
        onClick = {},
        interactionSource = interactionSource,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isListening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isListening) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = stringResource(if (isListening) R.string.release_to_stop else R.string.press_to_talk),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
