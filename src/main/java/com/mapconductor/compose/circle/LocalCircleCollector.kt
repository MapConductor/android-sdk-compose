package com.mapconductor.compose.circle

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.circle.CircleState

val LocalCircleCollector =
    compositionLocalOf<ChildCollector<CircleState>> {
        error("Circle must be under the <MapView />")
    }
