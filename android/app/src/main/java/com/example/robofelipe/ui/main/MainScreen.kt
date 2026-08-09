package com.example.robofelipe.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robofelipe.data.RobotCommand
import com.example.robofelipe.theme.RoboFelipeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.host) { viewModel.checkConnection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Robo Felipe", fontWeight = FontWeight.Bold) },
                actions = {
                    StatusIndicator(connected = state.connected, checking = state.checking)
                    IconButton(onClick = { viewModel.checkConnection() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reconectar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            HostField(host = state.host, onHostChange = viewModel::updateHost)

            state.lastError?.let {
                Text(
                    "Erro: $it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            DPad(
                onForward = { viewModel.sendCommand(RobotCommand.FORWARD, repeat = true) },
                onBackward = { viewModel.sendCommand(RobotCommand.BACKWARD, repeat = true) },
                onLeft = { viewModel.sendCommand(RobotCommand.LEFT, repeat = true) },
                onRight = { viewModel.sendCommand(RobotCommand.RIGHT, repeat = true) },
                onStop = {
                    viewModel.stopHold()
                    viewModel.sendCommand(RobotCommand.STOP)
                },
                activeHold = state.activeHoldCommand,
            )

            Spacer(Modifier.height(20.dp))

            ActionGrid(
                onAction = { cmd -> viewModel.sendCommand(cmd) },
            )

            Spacer(Modifier.height(8.dp))

            state.lastCommand?.let { cmd ->
                Text("Último comando: $cmd", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HostField(host: String, onHostChange: (String) -> Unit) {
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text("IP do robô") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusIndicator(connected: Boolean, checking: Boolean) {
    if (checking) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        )
    }
}

@Composable
private fun DPad(
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onStop: () -> Unit,
    activeHold: RobotCommand?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PadButton(
                icon = Icons.Filled.PlayArrow,
                label = "Frente",
                contentDescription = "Mover para frente",
                active = activeHold == RobotCommand.FORWARD,
                onPress = onForward,
                onRelease = onStop,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Esq.",
                    contentDescription = "Girar à esquerda",
                    active = activeHold == RobotCommand.LEFT,
                    onPress = onLeft,
                    onRelease = onStop,
                    modifier = Modifier.weight(1f),
                )
                PadButton(
                    icon = Icons.Filled.Stop,
                    label = "Parar",
                    contentDescription = "Parar",
                    active = activeHold == null,
                    onPress = onStop,
                    onRelease = {},
                    isStop = true,
                    modifier = Modifier.weight(1f),
                )
                PadButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "Dir.",
                    contentDescription = "Girar à direita",
                    active = activeHold == RobotCommand.RIGHT,
                    onPress = onRight,
                    onRelease = onStop,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            PadButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Trás",
                contentDescription = "Mover para trás",
                active = activeHold == RobotCommand.BACKWARD,
                onPress = onBackward,
                onRelease = onStop,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}

@Composable
private fun PadButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    isStop: Boolean = false,
) {
    val containerColor = when {
        isStop -> MaterialTheme.colorScheme.errorContainer
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isStop -> MaterialTheme.colorScheme.onErrorContainer
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Button(
        onClick = {},
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> onPress()
                            PointerEventType.Release, PointerEventType.Exit -> onRelease()
                            else -> {}
                        }
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActionGrid(onAction: (RobotCommand) -> Unit) {
    val actions = listOf(
        ActionItem(RobotCommand.SPRINT, Icons.Filled.PlayArrow),
        ActionItem(RobotCommand.DANCE, Icons.Filled.Favorite),
        ActionItem(RobotCommand.LEFT_KICK, Icons.Filled.WavingHand),
        ActionItem(RobotCommand.RIGHT_KICK, Icons.Filled.WavingHand),
        ActionItem(RobotCommand.LEFT_TILT, Icons.Filled.Sensors),
        ActionItem(RobotCommand.RIGHT_TILT, Icons.Filled.Sensors),
        ActionItem(RobotCommand.LEFT_STAMP, Icons.Filled.Adb),
        ActionItem(RobotCommand.RIGHT_STAMP, Icons.Filled.Adb),
        ActionItem(RobotCommand.LEFT_ANKLES, Icons.Filled.Sensors),
        ActionItem(RobotCommand.RIGHT_ANKLES, Icons.Filled.Sensors),
        ActionItem(RobotCommand.FOLLOW, Icons.Filled.Sensors),
        ActionItem(RobotCommand.AVOID, Icons.Filled.Sensors),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Modo Esporte",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        var index = 0
        while (index < actions.size) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until 3) {
                    val item = actions.getOrNull(index)
                    if (item != null) {
                        ActionButton(item, onAction, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    index++
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionButton(
    item: ActionItem,
    onAction: (RobotCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = { onAction(item.command) },
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, contentDescription = item.command.label, modifier = Modifier.size(22.dp))
            Text(item.command.label, fontSize = 10.sp)
        }
    }
}

private data class ActionItem(val command: RobotCommand, val icon: ImageVector)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    RoboFelipeTheme { MainScreen() }
}
