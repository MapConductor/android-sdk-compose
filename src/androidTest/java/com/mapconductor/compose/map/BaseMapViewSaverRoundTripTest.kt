package com.mapconductor.compose.map

import androidx.compose.runtime.saveable.SaverScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.map.AttributionRule
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapDesignTypeInterface
import com.mapconductor.core.map.MapPaddings
import com.mapconductor.core.map.MapUISettings
import com.mapconductor.core.map.MapViewState
import com.mapconductor.core.map.MapViewStateBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Bundle
import android.os.Parcel

/**
 * プロバイダの `*MapViewSaver` が継承する [BaseMapViewSaver] が、uiSettings とカメラ
 * （paddings を含む）を落とさないこと。Bundle は実機の Parcel を一度通す。
 */
@RunWith(AndroidJUnit4::class)
class BaseMapViewSaverRoundTripTest {
    @Test
    fun keepsUiSettingsCameraAndDesignRules() {
        val state =
            FakeState(
                id = "compose-state",
                design = FakeDesign("d", listOf(AttributionRule("© test", minZoom = 2))),
                camera =
                    MapCameraPosition(
                        position = GeoPoint(-33.86, 151.21),
                        zoom = 9.0,
                        bearing = 90.0,
                        tilt = 30.0,
                        paddings = MapPaddings(top = 5.0, left = 6.0, bottom = 7.0, right = 8.0),
                    ),
            )
        state.uiSettings = MapUISettings(rotateGesture = false, tiltGesture = false)

        val saver = FakeSaver().createSaver()
        val saved = with(saver) { SaverScope { true }.save(state) }!!
        val restored = saver.restore(throughParcel(saved))!!

        assertEquals("compose-state", restored.id)
        assertEquals("d", restored.mapDesignType.id)
        assertEquals(
            "© test",
            restored.mapDesignType.attributionRules
                .single()
                .attribution,
        )
        assertEquals(
            2,
            restored.mapDesignType.attributionRules
                .single()
                .minZoom,
        )
        assertEquals(true, restored.uiSettings.scrollGesture)
        assertEquals(true, restored.uiSettings.zoomGesture)
        assertEquals(false, restored.uiSettings.rotateGesture)
        assertEquals(false, restored.uiSettings.tiltGesture)
        assertEquals(-33.86, restored.cameraPosition.position.latitude, 1e-9)
        assertEquals(151.21, restored.cameraPosition.position.longitude, 1e-9)
        assertEquals(9.0, restored.cameraPosition.zoom, 1e-9)
        assertEquals(90.0, restored.cameraPosition.bearing, 1e-9)
        assertEquals(30.0, restored.cameraPosition.tilt, 1e-9)
        assertEquals(5.0, restored.cameraPosition.paddings!!.top, 1e-9)
        assertEquals(8.0, restored.cameraPosition.paddings!!.right, 1e-9)
    }

    private class FakeDesign(
        override val id: String,
        override val attributionRules: List<AttributionRule>,
    ) : MapDesignTypeInterface<String> {
        override fun getValue(): String = id
    }

    private class FakeState(
        override val id: String,
        design: FakeDesign,
        camera: MapCameraPosition = MapCameraPosition.Default,
    ) : MapViewState<FakeDesign>(camera) {
        override var mapDesignType: FakeDesign = design
    }

    private class FakeSaver : BaseMapViewSaver<FakeState>() {
        override fun saveMapDesign(
            state: FakeState,
            bundle: Bundle,
        ) {
            bundle.putString("id", state.mapDesignType.id)
            MapViewStateBundle.putAttributionRules(bundle, state.mapDesignType.attributionRules)
        }

        override fun createState(
            stateId: String,
            mapDesignBundle: Bundle?,
            cameraPosition: MapCameraPosition,
        ): FakeState =
            FakeState(
                id = stateId,
                design =
                    FakeDesign(
                        id = mapDesignBundle?.getString("id") ?: "",
                        attributionRules = MapViewStateBundle.getAttributionRules(mapDesignBundle),
                    ),
                camera = cameraPosition,
            )

        override fun getStateId(state: FakeState): String = state.id
    }

    private fun throughParcel(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return parcel.readBundle(javaClass.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }
}
