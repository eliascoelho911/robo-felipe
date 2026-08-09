package com.example.robofelipe.ui.main

import com.example.robofelipe.data.RobotCommand
import com.example.robofelipe.data.RobotRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {

  @Test
  fun initialState_isDisconnected() = runTest {
    val viewModel = MainScreenViewModel()
    assertEquals("192.168.4.1", viewModel.uiState.value.host)
    assertEquals(false, viewModel.uiState.value.connected)
    assertNull(viewModel.uiState.value.lastCommand)
  }

  @Test
  fun updateHost_changesHost() = runTest {
    val viewModel = MainScreenViewModel()
    viewModel.updateHost("10.0.0.1")
    assertEquals("10.0.0.1", viewModel.uiState.value.host)
  }
}
