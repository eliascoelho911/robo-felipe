package com.example.robofelipe.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robofelipe.data.DefaultRobotRepository
import com.example.robofelipe.data.RobotCommand
import com.example.robofelipe.data.RobotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RobotUiState(
    val host: String = "192.168.4.1",
    val connected: Boolean = false,
    val checking: Boolean = false,
    val lastCommand: String? = null,
    val lastError: String? = null,
    val activeHoldCommand: RobotCommand? = null,
)

class MainScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RobotUiState())
    val uiState: StateFlow<RobotUiState> = _uiState.asStateFlow()

    private val repository: RobotRepository = DefaultRobotRepository { _uiState.value.host }
    private var holdJob: Job? = null

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host, connected = false, lastError = null) }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true, lastError = null) }
            repository.ping()
                .onSuccess { _uiState.update { it.copy(checking = false, connected = true) } }
                .onFailure { e -> _uiState.update { it.copy(checking = false, connected = false, lastError = e.message) } }
        }
    }

    fun sendCommand(command: RobotCommand, repeat: Boolean = false) {
        stopHold()
        viewModelScope.launch {
            repository.sendCommand(command.code)
                .onSuccess { _uiState.update { it.copy(lastCommand = command.label, lastError = null) } }
                .onFailure { e -> _uiState.update { it.copy(lastError = e.message, connected = false) } }
        }
        if (repeat && command in HOLD_COMMANDS) startHold(command)
    }

    fun stopHold() {
        holdJob?.cancel()
        holdJob = null
        _uiState.update { it.copy(activeHoldCommand = null) }
    }

    private fun startHold(command: RobotCommand) {
        _uiState.update { it.copy(activeHoldCommand = command) }
        holdJob = viewModelScope.launch {
            while (true) {
                delay(HOLD_INTERVAL_MS)
                repository.sendCommand(command.code)
            }
        }
    }

    companion object {
        private val HOLD_COMMANDS =
            setOf(RobotCommand.FORWARD, RobotCommand.BACKWARD, RobotCommand.LEFT, RobotCommand.RIGHT)
        private const val HOLD_INTERVAL_MS = 1200L
    }
}
