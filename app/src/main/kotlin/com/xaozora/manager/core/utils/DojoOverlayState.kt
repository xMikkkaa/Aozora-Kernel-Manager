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
 * GameSession pattern adapted from chaldeaprjkt GameSpace (Apache-2.0)
 * via AxionAOSP fork (Apache-2.0):
 * https://github.com/chaldeaprjkt/packages_apps_GameSpace
 * https://github.com/AxionAOSP/android_packages_apps_GameSpace
 * publishKehai/clearShiai session publish flow rewritten in pure Compose state.
 */
package com.xaozora.manager.core.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AsobiMode {
    PERFORMANCE,
    GAMING,
    GAMING2,
    POWERSAVE,
    BALANCE,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): AsobiMode {
            if (raw == null) return UNKNOWN
            return when (raw.trim().lowercase()) {
                "performance" -> PERFORMANCE
                "gaming" -> GAMING
                "gaming2" -> GAMING2
                "powersave" -> POWERSAVE
                "balance" -> BALANCE
                else -> UNKNOWN
            }
        }
    }
}

data class ShiaiSession(
    @SerializedName("shiai") val shiai: String,
    @SerializedName("pid") val pid: Int,
    @SerializedName("asobi") val asobi: AsobiMode
)

data class KehaiEvent(
    @SerializedName("shiai") val shiai: String,
    @SerializedName("pid") val pid: Int,
    @SerializedName("asobi") val asobi: AsobiMode,
    @SerializedName("phase") val phase: String
)

private data class KehaiPayload(
    @SerializedName("shiai") val shiai: String?,
    @SerializedName("pid") val pid: Int?,
    @SerializedName("asobi") val asobi: String?,
    @SerializedName("phase") val phase: String?
)

object DojoOverlayState {
    private val gson = Gson()

    private val _shiai = MutableStateFlow<ShiaiSession?>(null)
    val shiai: StateFlow<ShiaiSession?> = _shiai.asStateFlow()

    private val _kehaiEvents = MutableSharedFlow<KehaiEvent>(replay = 0, extraBufferCapacity = 1)
    val kehaiEvents: SharedFlow<KehaiEvent> = _kehaiEvents.asSharedFlow()

    fun parseKehai(kehaiJson: String): KehaiEvent? {
        return try {
            val trimmed = kehaiJson.trim()
            if (trimmed.isEmpty()) return null
            val payload = gson.fromJson(trimmed, KehaiPayload::class.java) ?: return null
            val base = payload.shiai?.trim()
            if (base.isNullOrEmpty()) return null
            val pidValue = payload.pid ?: return null
            val phaseValue = payload.phase?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "start"
            KehaiEvent(
                shiai = base,
                pid = pidValue,
                asobi = AsobiMode.fromRaw(payload.asobi),
                phase = phaseValue
            )
        } catch (_: Exception) {
            null
        }
    }

    fun publishKehai(kehai: KehaiEvent) {
        if (kehai.phase == "end") {
            _shiai.value = null
        } else {
            _shiai.value = ShiaiSession(
                shiai = kehai.shiai,
                pid = kehai.pid,
                asobi = kehai.asobi
            )
        }
        _kehaiEvents.tryEmit(kehai)
    }

    fun publishShiai(session: ShiaiSession?) {
        _shiai.value = session
    }

    fun clearShiai() {
        _shiai.value = null
    }
}
