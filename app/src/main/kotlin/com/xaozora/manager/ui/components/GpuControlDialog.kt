package com.xaozora.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.xaozora.manager.core.utils.GpuConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuControlDialog(
    hazeState: HazeState,
    gpuConfig: GpuConfig,
    onDismiss: () -> Unit,
    onApply: (minFreq: String, maxFreq: String, governor: String, adrenoBoost: String?) -> Unit
) {
    var minFreq by remember { mutableStateOf(gpuConfig.minFreq) }
    var maxFreq by remember { mutableStateOf(gpuConfig.maxFreq) }
    var governor by remember { mutableStateOf(gpuConfig.governor) }
    var adrenoBoost by remember { mutableStateOf(gpuConfig.adrenoBoost) }

    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val dialogStyle = remember(surfaceContainer) {
        HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(surfaceContainer.copy(alpha = 0.25f)))
        )
    }

    val showAdrenoBoost = gpuConfig.adrenoBoostSupported && governor != "powersave" && governor != "performance"

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
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "GPU Control",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    DropdownSelector(
                        label = "Max Frequency",
                        options = gpuConfig.availableFreqs,
                        selectedOption = maxFreq,
                        onOptionSelected = { maxFreq = it },
                        isFrequency = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DropdownSelector(
                        label = "Min Frequency",
                        options = gpuConfig.availableFreqs,
                        selectedOption = minFreq,
                        onOptionSelected = { minFreq = it },
                        isFrequency = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DropdownSelector(
                        label = "Governor",
                        options = gpuConfig.availableGovernors,
                        selectedOption = governor,
                        onOptionSelected = { governor = it },
                        isFrequency = false
                    )

                    if (showAdrenoBoost) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AdrenoBoostSelector(
                            selectedLevel = adrenoBoost,
                            onLevelSelected = { adrenoBoost = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onApply(minFreq, maxFreq, governor, if (showAdrenoBoost) adrenoBoost else null) }) {
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isFrequency: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (isFrequency) formatGpuFreq(selectedOption) else selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (isFrequency) formatGpuFreq(option) else option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdrenoBoostSelector(
    selectedLevel: String,
    onLevelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("0" to "Off", "1" to "Low", "2" to "Medium", "3" to "High")
    val selectedText = options.find { it.first == selectedLevel }?.second ?: "Unknown"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Adreno Boost") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (level, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onLevelSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatGpuFreq(value: String): String {
    val freq = value.toLongOrNull()
    return if (freq != null) {
        "$freq MHz"
    } else {
        value
    }
}
