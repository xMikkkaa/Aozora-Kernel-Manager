package com.xaozora.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

@Composable
fun ToggleCard(
    title: String,
    checked: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: ImageVector = Icons.Rounded.Settings,
    showExpandableWhenOff: Boolean = false,
    expandableContent: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(if (checked) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surfaceContainer, label = "bg")
    val borderColor by animateColorAsState(if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), label = "border")
    val iconBgColor by animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), label = "iconBg")
    val iconColor by animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.secondary, label = "iconColor")

    val switchOffset by animateDpAsState(if (checked) 24.dp else 0.dp, label = "switchOffset")
    val switchBg by animateColorAsState(if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest, label = "switchBg")
    val thumbColor by animateColorAsState(if (checked) colorScheme.onPrimaryContainer else colorScheme.outline, label = "thumbColor")

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .border(1.2.dp, borderColor, RoundedCornerShape(24.dp)),
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(if (checked) bgColor else Color.Transparent).padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(iconBgColor, CircleShape).padding(12.dp)) {
                        Icon(icon, contentDescription = null, tint = iconColor)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (checked) colorScheme.onPrimaryContainer else colorScheme.onSurface))
                        if (subtitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = if (checked) colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant))
                        }
                    }
                    Box(modifier = Modifier.size(52.dp, 28.dp).background(switchBg, CircleShape).border(1.dp, if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape).padding(4.dp), contentAlignment = Alignment.CenterStart) {
                        Box(modifier = Modifier.offset(x = switchOffset).size(20.dp).background(thumbColor, CircleShape))
                    }
                }

                if (expandableContent != null) {
                    AnimatedVisibility(visible = checked || showExpandableWhenOff) {
                        expandableContent()
                    }
                }
            }
        }
    }
}
