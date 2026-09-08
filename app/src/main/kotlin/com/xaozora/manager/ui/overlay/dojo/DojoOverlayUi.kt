/*
 * Copyright 2026 Aozora Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * GameSideBar pattern adapted from chaldeaprjkt GameSpace (Apache-2.0)
 * via AxionAOSP fork (Apache-2.0):
 * https://github.com/chaldeaprjkt/packages_apps_GameSpace
 * https://github.com/AxionAOSP/android_packages_apps_GameSpace
 * pill/panel params rewritten in pure Compose.
 */
package com.xaozora.manager.ui.overlay.dojo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xaozora.manager.core.utils.AsobiMode
import com.xaozora.manager.core.utils.ShiaiSession

fun asobiLabel(asobi: AsobiMode): String = when (asobi) {
    AsobiMode.GAMING -> "Gaming"
    AsobiMode.GAMING2 -> "Gaming+"
    AsobiMode.POWERSAVE -> "Hemat"
    AsobiMode.BALANCE -> "Balance"
    else -> "Perf"
}

private fun asobiIcon(asobi: AsobiMode): ImageVector = when (asobi) {
    AsobiMode.GAMING -> Icons.Rounded.SportsEsports
    AsobiMode.GAMING2 -> Icons.Rounded.VideogameAsset
    else -> Icons.Rounded.RocketLaunch
}

@Composable
fun DojoOverlay(
    shiai: ShiaiSession?,
    kaikin: Boolean,
    onKaikinChange: (Boolean) -> Unit,
    kehai: Boolean,
    onKehaiChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (!kaikin) {
            DojoPill(onTap = { onKaikinChange(true) }, onDrag = onDrag)
        } else {
            DojoPanel(
                shiai = shiai,
                kehai = kehai,
                onKehaiChange = onKehaiChange,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun DojoPill(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onTap,
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun DojoPanel(
    shiai: ShiaiSession?,
    kehai: Boolean,
    onKehaiChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val asobi = shiai?.asobi ?: AsobiMode.UNKNOWN
    ElevatedCard(
        modifier = modifier.width(320.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = asobiIcon(asobi),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shiai?.shiai ?: "Dojo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (shiai == null) "Menunggu shiai" else "pid ${shiai.pid}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            AssistChip(
                onClick = { onKehaiChange(!kehai) },
                label = { Text(asobiLabel(asobi)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            )

            AnimatedVisibility(visible = kehai) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "shiai berjalan dengan profil ${asobiLabel(asobi)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onKehaiChange(!kehai) }) {
                    Text(if (kehai) "Sembunyi info" else "Info")
                }
            }
        }
    }
}
