package com.mapconductor.compose.circle

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.circle.CircleState

val LocalCircleCollector =
    compositionLocalOf<OverlayCollectorInterface<CircleState>> {
        error("Circle must be under the <MapView />")
    }
