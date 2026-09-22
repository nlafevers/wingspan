package com.wingspan.app.ui.map

import android.content.Context
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.Sector
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The only place in the app that talks to MapLibre directly.
 */
class MapController(private val context: Context) {

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var basemap: Basemap? = null
    private var shooter: ShooterPosition? = null
    private var onLongPress: ((LatLon) -> Unit)? = null

    fun attach(mapView: MapView, map: MapLibreMap) {
        this.map = map
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isAttributionEnabled = true
        map.addOnMapLongClickListener {
            onLongPress?.invoke(LatLon(it.latitude, it.longitude))
            true
        }
        if (basemap != null) {
            loadStyle()
        }
    }

    fun setBasemap(b: Basemap) {
        if (b == basemap) return
        basemap = b
        loadStyle()
    }

    private fun loadStyle() {
        val current = basemap ?: return
        val json = context.assets.open(current.assetPath).bufferedReader().use { it.readText() }
        map?.setStyle(Style.Builder().fromJson(json)) { loaded ->
            style = loaded
            installOverlays(loaded)
        }
    }

    private fun installOverlays(style: Style) {
        // Canonical overlay order. Later steps must add their sources/layers by inserting
        // code at the matching position below so that plain style.addLayer(...) calls
        // produce this order (never append out of order; if unavoidable, use
        // style.addLayerBelow(layer, "<next id in the list>")). Each step must also
        // re-apply its last known data after a style reload (keep the last data in fields).
        //
        // 1.  position-accuracy-fill
        // 2.  zones-polygons-fill
        // 3.  zones-polygons-outline
        // 4.  zones-marker-circles-fill
        // 5.  zones-marker-circles-outline
        // 6.  zones-marker-points
        // 7.  fans-fill
        // 8.  fans-outline
        // 9.  fans-effective
        // 10. fans-selected
        // 11. snapshot-fans-fill
        // 12. snapshot-fans-outline
        // 13. position-dot
        // 14. editor-fill
        // 15. editor-outline
        // 16. editor-midpoints
        // 17. editor-handles

        style.addSource(GeoJsonSource("position-accuracy"))
        style.addLayer(
            FillLayer("position-accuracy-fill", "position-accuracy")
                .withProperties(fillColor("#1E88E5"), fillOpacity(0.15f))
        )

        // Steps 2-12 (zones/fans/snapshot-fans) slot in here in future work, above.

        style.addSource(GeoJsonSource("position"))
        style.addLayer(
            CircleLayer("position-dot", "position")
                .withProperties(
                    circleRadius(7f),
                    circleColor(Expression.get("color")),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(2f),
                )
        )

        applyShooter()
    }

    fun moveCamera(target: LatLon, zoom: Double? = null) {
        val latLng = LatLng(target.lat, target.lon)
        val update = if (zoom != null) {
            CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        } else {
            CameraUpdateFactory.newLatLng(latLng)
        }
        map?.moveCamera(update)
    }

    fun animateCamera(target: LatLon, zoom: Double? = null) {
        val latLng = LatLng(target.lat, target.lon)
        val update = if (zoom != null) {
            CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        } else {
            CameraUpdateFactory.newLatLng(latLng)
        }
        map?.animateCamera(update)
    }

    fun currentZoom(): Double = map?.cameraPosition?.zoom ?: 3.0

    fun setShooter(s: ShooterPosition?) {
        shooter = s
        applyShooter()
    }

    private fun applyShooter() {
        val style = style ?: return
        val s = shooter

        val dotCollection = if (s != null) {
            val color = if (s.source == PositionSource.GPS) "#1E88E5" else "#FB8C00"
            val feature = Feature.fromGeometry(Point.fromLngLat(s.position.lon, s.position.lat))
            feature.addStringProperty("color", color)
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>("position")?.setGeoJson(dotCollection)

        val accuracyCollection = if (s != null && s.source == PositionSource.GPS && s.accuracyM != null) {
            val ring = Sector.circleOutline(s.position, s.accuracyM).map { Point.fromLngLat(it.lon, it.lat) }
            val closedRing = if (ring.isNotEmpty() && ring.first() != ring.last()) ring + ring.first() else ring
            FeatureCollection.fromFeature(Feature.fromGeometry(Polygon.fromLngLats(listOf(closedRing))))
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>("position-accuracy")?.setGeoJson(accuracyCollection)
    }

    fun setOnLongPress(listener: (LatLon) -> Unit) {
        onLongPress = listener
    }
}
