package com.example.robofelipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.robofelipe.data.PetConfig
import com.example.robofelipe.theme.RoboFelipeTheme
import com.example.robofelipe.ui.config.ConfigScreen
import com.example.robofelipe.ui.pet.PetScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RoboFelipeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showConfig by remember { mutableStateOf(false) }

                    if (showConfig) {
                        ConfigScreen(
                            config = PetConfig(this),
                            onBack = { showConfig = false },
                        )
                    } else {
                        PetScreen(
                            onNavigateToConfig = { showConfig = true },
                        )
                    }
                }
            }
        }
    }
}
