package com.mapconductor.compose.info

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.marker.MarkerIconInterface

class InfoBubbleEntry(
    val id: String,
    /** Called during Compose recomposition to read the current position. */
    val positionProvider: () -> GeoPointInterface,
    val icon: MarkerIconInterface? = null,
    val tailOffset: Offset = Offset(0.5f, 1.0f),
    val content: @Composable () -> Unit,
)
