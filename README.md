# MapConductor Compose

## Description

`com.mapconductor:compose` is the Jetpack Compose layer of the MapConductor Android SDK.

It provides the overlay composables that every provider shares — `Marker`, `Markers`,
`InfoBubble`, `Circle`, `Polyline`, `Polygon`, `GroundImage` and `RasterLayer` — as
extensions on `MapViewScope`. The provider modules (`android-for-googlemaps`,
`android-for-maplibre`, `android-for-arcgis`, and so on) supply the map view itself and
open a `MapViewScope`; the content you declare inside it comes from this module.

That is why the same overlay code compiles unchanged when you switch providers.

```kotlin
MapLibreMapView(mapViewState) {   // ← provider module
    Marker(markerState)           // ← this module
    Polygon(polygonState)         // ← this module
}
```

App developers normally depend on a provider module, which brings this one in
transitively. Depend on it directly only when writing a driver for a new provider.

## Setup

https://mapconductor.com/setup/

```kotlin
dependencies {
    implementation("com.mapconductor:compose:<version>")
}
```

## Components

| Composable | Docs |
| --- | --- |
| `MapViewScope.Marker` | https://mapconductor.com/markers/ |
| `MapViewScope.Markers` | Bulk variant. One collector replacement instead of one component tree per marker |
| `MapViewScope.InfoBubble` | https://mapconductor.com/info-bubble/ |
| `MapViewScope.InfoBubbleCustom` | InfoBubble with a caller-supplied frame |
| `MapViewScope.Circle` | https://mapconductor.com/circle/ |
| `MapViewScope.Polyline` | https://mapconductor.com/polyline/ |
| `MapViewScope.Polygon` | https://mapconductor.com/polygon/ |
| `MapViewScope.GroundImage` | https://mapconductor.com/ground-image/ |
| `MapViewScope.RasterLayer` | Raster tile overlay |

Usage examples live in each provider's README, because the surrounding map view differs.
See `android-for-maplibre/README.md` for a full set.

### Markers vs Marker

Use `Markers(states = states)` for large collections. It performs one collector
replacement, instead of building one Compose component and effect tree per marker.
Individual `Marker` remains the right choice for small or independently managed markers.

## For driver implementors

- `CollectAndRenderOverlays` and `OverlayProvider` are the wiring a provider's map view
  calls to collect declared overlays and hand them to its controller.
- `MarkerAnimationOverlayLayer` draws marker animations above the map surface.
- Declarations marked `@InternalMapConductorApi` are driver implementation points and are
  excluded from the frozen public API surface (`./gradlew apiCheck`). Annotate new ones —
  forgetting puts them on the app-facing API.

## License

Apache License 2.0
