package com.mapconductor.compose.map

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.controller.MapViewControllerInterface
import com.mapconductor.core.map.MapOverlayRegistry

val LocalMapOverlayRegistry =
    compositionLocalOf<MapOverlayRegistry> {
        error("Map overlays must be registered under the <MapView />")
    }

val LocalMapViewController =
    compositionLocalOf<MapViewControllerInterface> {
        error("Map controller must be available under the <MapView />")
    }
