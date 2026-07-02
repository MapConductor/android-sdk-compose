package com.mapconductor.compose.map

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.map.EmptyMapServiceRegistry
import com.mapconductor.core.map.MapServiceRegistry

val LocalMapServiceRegistry =
    compositionLocalOf<MapServiceRegistry> {
        EmptyMapServiceRegistry
    }
