package com.example.robofelipe.ui.pet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Barras de stats visíveis: saciedade, energia, felicidade, saúde.
// Sickness aparece só se > 0. Cor por faixa: verde >60, amarelo 30-60,
// vermelho <30.

data class StatDisplay(
    val label: String,
    val value: Double,
    val icon: ImageVector,
)

@Composable
fun StatBars(
    stats: Map<String, Double>,
    sickness: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    val displays = buildList {
        stats["fullness"]?.let { add(StatDisplay("Saciedade", it, Icons.Default.Restaurant)) }
        stats["energy"]?.let { add(StatDisplay("Energia", it, Icons.Default.BatteryFull)) }
        stats["happiness"]?.let { add(StatDisplay("Felicidade", it, Icons.Default.Favorite)) }
        stats["health"]?.let { add(StatDisplay("Saúde", it, Icons.Default.MedicalServices)) }
        if (sickness > 0.0) {
            add(StatDisplay("Doença", sickness, Icons.Default.Thermostat))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        displays.forEach { display ->
            StatBarRow(display)
        }
    }
}

@Composable
private fun StatBarRow(display: StatDisplay) {
    val progress by animateFloatAsState(
        targetValue = (display.value / 100f).coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "stat_${display.label}",
    )
    val barColor = colorForValue(display.value)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = display.icon,
            contentDescription = display.label,
            modifier = Modifier.size(20.dp),
            tint = barColor,
        )
        Text(
            text = display.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.3f),
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(0.7f),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

private fun colorForValue(value: Double): Color = when {
    value > 60 -> Color(0xFF4CAF50)
    value > 30 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}
