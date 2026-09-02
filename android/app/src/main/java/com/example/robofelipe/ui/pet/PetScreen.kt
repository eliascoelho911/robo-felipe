package com.example.robofelipe.ui.pet

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.robofelipe.ui.voice.ConnectionState
import com.example.robofelipe.ui.voice.VoiceViewModel

@Composable
fun PetScreen(
    onNavigateToConfig: () -> Unit,
    voiceViewModel: VoiceViewModel = viewModel(
        factory = VoiceViewModel.factory(LocalContext.current.applicationContext as android.app.Application)
    ),
    petViewModel: PetViewModel = viewModel(
        factory = PetViewModel.factory(
            app = LocalContext.current.applicationContext as android.app.Application,
            petActionEvents = voiceViewModel.petActionEvents,
        )
    ),
) {
    val petState by petViewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.config))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Face do pet com animações
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PetAnimationHost(
                    animation = petState.animation,
                    mood = petState.mood,
                )
                if (petState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            // Stage e mood
            Text(
                text = "${petState.stage.name} • ${petState.mood.name}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

            // Barras de stats
            StatBars(
                stats = petState.stats,
                sickness = petState.sickness,
            )

            // Botões de tool (cuidar do pet sem falar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolButton(
                    label = stringResource(R.string.feed),
                    onClick = petViewModel::feed,
                    modifier = Modifier.weight(1f),
                )
                ToolButton(
                    label = stringResource(R.string.play),
                    onClick = petViewModel::play,
                    modifier = Modifier.weight(1f),
                )
                ToolButton(
                    label = stringResource(R.string.rest),
                    onClick = petViewModel::rest,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolButton(
                    label = stringResource(R.string.clean),
                    onClick = petViewModel::clean,
                    modifier = Modifier.weight(1f),
                )
                ToolButton(
                    label = stringResource(R.string.cuddle),
                    onClick = petViewModel::cuddle,
                    modifier = Modifier.weight(1f),
                )
                ToolButton(
                    label = stringResource(R.string.shake),
                    onClick = petViewModel::sendShakeTrigger,
                    modifier = Modifier.weight(1f),
                )
            }

            // Erro
            petState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Push-to-talk — reusa VoiceViewModel
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            LaunchedEffect(isPressed) {
                val isConnected = voiceState.connectionState == ConnectionState.CONNECTED
                if (isPressed && isConnected) {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        voiceViewModel.startListening()
                    }
                } else if (!isPressed && isConnected) {
                    voiceViewModel.stopListening()
                }
            }

            Button(
                onClick = {},
                interactionSource = interactionSource,
                enabled = voiceState.connectionState == ConnectionState.CONNECTED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (voiceState.isListening) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (voiceState.isListening) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(if (voiceState.isListening) R.string.release_to_stop else R.string.press_to_talk),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun PetAnimationHost(
    animation: PetAnimation,
    mood: com.example.robofelipe.data.Emotion,
) {
    when (animation) {
        is PetAnimation.Idle -> PetFace(mood = mood)
        is PetAnimation.Dance -> DanceAnimation(
            durationMs = animation.durationMs,
            mood = mood,
            onComplete = {},
        )
        is PetAnimation.ExpressEmotion -> ExpressEmotionAnimation(
            emotion = animation.emotion,
            previousMood = mood,
            onComplete = {},
        )
        is PetAnimation.GetDizzy -> GetDizzyAnimation(
            intensity = animation.intensity,
            mood = mood,
            onComplete = {},
        )
        is PetAnimation.Sleep -> SleepAnimation(
            durationMs = animation.durationMs,
            onComplete = {},
        )
        is PetAnimation.Speak -> SpeakAnimation(
            text = animation.text,
            mood = mood,
            onComplete = {},
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
