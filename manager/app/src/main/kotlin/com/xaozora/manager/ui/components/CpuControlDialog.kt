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
import androidx.compose.runtime.mutableStateMapOf
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
import com.xaozora.manager.core.utils.CpuClusterConfig
import com.xaozora.manager.core.utils.CpuPolicySelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuControlDialog(
    hazeState: HazeState,
    clusterConfigs: List<CpuClusterConfig>,
    onDismiss: () -> Unit,
    onApply: (List<CpuPolicySelection>) -> Unit
) {
    val selections = remember(clusterConfigs) {
        mutableStateMapOf<Int, CpuPolicySelection>()
    }
    clusterConfigs.forEach { config ->
        if (!selections.containsKey(config.id)) {
            selections[config.id] = CpuPolicySelection(
                config.id, config.minFreq, config.maxFreq, config.governor
            )
        }
    }

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
                    .background(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = false) {},
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "CPU Control",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    clusterConfigs.forEachIndexed { index, config ->
                        val sel = selections[config.id] ?: CpuPolicySelection(
                            config.id, config.minFreq, config.maxFreq, config.governor
                        )
                        ClusterControlSection(
                            title = "${config.name} Cluster (Policy ${config.id})",
                            config = config,
                            selectedMin = sel.minFreq,
                            selectedMax = sel.maxFreq,
                            selectedGov = sel.governor,
                            onMinChange = { selections[config.id] = sel.copy(minFreq = it) },
                            onMaxChange = { selections[config.id] = sel.copy(maxFreq = it) },
                            onGovChange = { selections[config.id] = sel.copy(governor = it) }
                        )
                        if (index < clusterConfigs.size - 1) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
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
                        Button(onClick = {
                            onApply(clusterConfigs.mapNotNull { selections[it.id] })
                        }) {
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
private fun ClusterControlSection(
    title: String,
    config: CpuClusterConfig,
    selectedMin: String,
    selectedMax: String,
    selectedGov: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onGovChange: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Max Freq
    DropdownSelector(
        label = "Max Frequency",
        options = config.availableFreqs,
        selectedOption = selectedMax,
        onOptionSelected = onMaxChange
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Min Freq
    DropdownSelector(
        label = "Min Frequency",
        options = config.availableFreqs,
        selectedOption = selectedMin,
        onOptionSelected = onMinChange
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Governor
    DropdownSelector(
        label = "Governor",
        options = config.availableGovernors,
        selectedOption = selectedGov,
        onOptionSelected = onGovChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = formatFreqOrGov(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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
                    text = { Text(formatFreqOrGov(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatFreqOrGov(value: String): String {
    val freq = value.toLongOrNull()
    return if (freq != null) {
        "${freq / 1000} MHz"
    } else {
        value
    }
}
