package com.xaozora.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.xaozora.manager.core.utils.SystemInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun SystemInfoDialog(
    hazeState: HazeState,
    info: SystemInfo,
    onDismiss: () -> Unit
) {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val dialogStyle = remember(surfaceContainer) {
        HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(32.dp))
                    .hazeEffect(state = hazeState, style = dialogStyle)
                    .border(
                        1.2.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(32.dp)
                    )
                    .background(Color.Transparent)
                    .clickable(enabled = false) {},
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "System Information",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SystemInfoDoubleRow("Loadavg", info.loadAvg, "Capacity", info.capacity)
                    SystemInfoDoubleRow("Entropy", info.entropy, "Governor", info.governor)
                    SystemInfoDoubleRow("Uptime", info.uptime, "Battery Health", info.batteryHealth)
                    SystemInfoDoubleRow("Deep sleep", info.deepSleep, "Wireguard", info.wireguard)
                    SystemInfoRow("GPU Model", info.openGl)
                    SystemInfoRow("Kernel", info.kernel)

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun SystemInfoDoubleRow(label1: String, value1: String, label2: String, value2: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label1, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value1, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label2, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value2, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary))
        }
    }
}
