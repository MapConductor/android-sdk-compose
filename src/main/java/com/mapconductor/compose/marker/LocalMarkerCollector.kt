package com.mapconductor.compose.marker

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.marker.MarkerState

val LocalMarkerCollector =
    compositionLocalOf<OverlayCollectorInterface<MarkerState>> {
        error("Marker must be under the <MapView />")
    }
