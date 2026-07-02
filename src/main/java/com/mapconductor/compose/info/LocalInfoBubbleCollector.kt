package com.mapconductor.compose.info

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

val LocalInfoBubbleCollector =
    compositionLocalOf<MutableStateFlow<MutableMap<String, InfoBubbleEntry>>> {
        error("InfoBubble must be under <MapView />")
    }
