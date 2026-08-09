package com.example.robofelipe.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen() }
  }

  @Test
  fun title_isDisplayed() {
    composeTestRule.onNodeWithText("Robo Felipe").assertExists()
  }

  @Test
  fun dpadButtons_exist() {
    composeTestRule.onNodeWithText("Frente").assertExists()
    composeTestRule.onNodeWithText("Trás").assertExists()
    composeTestRule.onNodeWithText("Esq.").assertExists()
    composeTestRule.onNodeWithText("Dir.").assertExists()
  }

  @Test
  fun actionButtons_exist() {
    composeTestRule.onNodeWithText("Dançar").assertExists()
    composeTestRule.onNodeWithText("Desviar").assertExists()
    composeTestRule.onNodeWithText("Seguir").assertExists()
  }
}
