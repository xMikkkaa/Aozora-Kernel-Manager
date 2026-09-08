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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
            DojoEdgeTab(onTap = { onKaikinChange(true) }, onDrag = onDrag)
        } else {
            DojoToolbar(
                shiai = shiai,
                kehai = kehai,
                onKehaiChange = onKehaiChange,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun DojoEdgeTab(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onTap,
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        contentColor = colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier.size(width = 36.dp, height = 76.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Buka panel dojo",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DojoToolbar(
    shiai: ShiaiSession?,
    kehai: Boolean,
    onKehaiChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val asobi = shiai?.asobi ?: AsobiMode.UNKNOWN
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.inverseSurface.copy(alpha = 0.94f),
        contentColor = colorScheme.inverseOnSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Ciutkan panel"
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = colorScheme.inverseOnSurface.copy(alpha = 0.16f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onKehaiChange(!kehai) }) {
                    Icon(
                        imageVector = asobiIcon(asobi),
                        contentDescription = asobiLabel(asobi)
                    )
                }
                Text(
                    text = asobiLabel(asobi),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(visible = kehai) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = shiai?.shiai ?: "Dojo",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (shiai == null) "Menunggu shiai" else "pid ${shiai.pid}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colorScheme.inverseOnSurface.copy(alpha = 0.72f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "shiai berjalan dengan profil ${asobiLabel(asobi)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colorScheme.inverseOnSurface.copy(alpha = 0.72f)
                        )
                    )
                }
            }
        }
    }
}
