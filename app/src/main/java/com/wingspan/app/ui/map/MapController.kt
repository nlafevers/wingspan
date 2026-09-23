package com.wingspan.app.ui.map

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.domain.geo.Sector
import com.wingspan.app.domain.geo.Vec2
import com.wingspan.app.ui.editor.EditorRender
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The result of a tap on the map: where it landed, and the zone (if any) hit there.
 */
data class TapHit(val position: LatLon, val zoneId: Long?)

/**
 * The only place in the app that talks to MapLibre directly.
 */
class MapController(private val context: Context) {

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var basemap: Basemap? = null
    private var shooter: ShooterPosition? = null
    private var zones: List<NoFireZone> = emptyList()
    private var onLongPress: ((LatLon) -> Unit)? = null
    private var onTap: ((TapHit) -> Unit)? = null
    private var handleDragListener: HandleDragListener? = null
    private var editorRender: EditorRender? = null
    private var dragIndex: Int? = null
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var moved: Boolean = false

    interface HandleDragListener {
        fun onHandleMoved(index: Int, p: LatLon)
        fun onHandleTapped(index: Int)
        fun onMidpointPressed(insertAfter: Int, p: LatLon): Int
    }

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
        map.addOnMapClickListener {
            val position = LatLon(it.latitude, it.longitude)
            val screenPoint = map.projection.toScreenLocation(it)
            val features = map.queryRenderedFeatures(
                screenPoint,
                "zones-marker-points",
                "zones-marker-circles-fill",
                "zones-polygons-fill",
                "zones-lines-outline",
                "zones-lines-buffer-fill",
            )
            val zoneId = features.firstOrNull()?.getNumberProperty("zoneId")?.toLong()
            onTap?.invoke(TapHit(position, zoneId))
            true
        }
        mapView.setOnTouchListener { _, ev -> handleTouch(ev) }
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
        // 7.  zones-lines-buffer-fill
        // 8.  zones-lines-outline
        // 9.  fans-fill
        // 10. fans-outline
        // 11. fans-effective
        // 12. fans-selected
        // 13. snapshot-fans-fill
        // 14. snapshot-fans-outline
        // 15. position-dot
        // 16. editor-fill
        // 17. editor-outline (bound to editor-polygon)
        // 17b. editor-outline-line (bound to editor-line; same paint, mutually exclusive with 17)
        // 18. editor-midpoints
        // 19. editor-handles

        style.addSource(GeoJsonSource("position-accuracy"))
        style.addLayer(
            FillLayer("position-accuracy-fill", "position-accuracy")
                .withProperties(fillColor("#1E88E5"), fillOpacity(0.15f))
        )

        style.addSource(GeoJsonSource("zones-polygons"))
        style.addSource(GeoJsonSource("zones-marker-circles"))
        style.addSource(GeoJsonSource("zones-marker-points"))
        style.addSource(GeoJsonSource("zones-lines"))
        style.addSource(GeoJsonSource("zones-lines-buffer"))
        style.addLayer(
            FillLayer("zones-polygons-fill", "zones-polygons")
                .withProperties(fillColor("#D32F2F"), fillOpacity(0.30f))
        )
        style.addLayer(
            LineLayer("zones-polygons-outline", "zones-polygons")
                .withProperties(lineColor("#B71C1C"), lineWidth(2f))
        )
        style.addLayer(
            FillLayer("zones-marker-circles-fill", "zones-marker-circles")
                .withProperties(fillColor("#D32F2F"), fillOpacity(0.25f))
        )
        style.addLayer(
            LineLayer("zones-marker-circles-outline", "zones-marker-circles")
                .withProperties(lineColor("#B71C1C"), lineWidth(1.5f))
        )
        style.addLayer(
            CircleLayer("zones-marker-points", "zones-marker-points")
                .withProperties(
                    circleRadius(6f),
                    circleColor("#B71C1C"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(1.5f),
                )
        )
        style.addLayer(
            FillLayer("zones-lines-buffer-fill", "zones-lines-buffer")
                .withProperties(fillColor("#D32F2F"), fillOpacity(0.20f))
        )
        style.addLayer(
            LineLayer("zones-lines-outline", "zones-lines")
                .withProperties(lineColor("#B71C1C"), lineWidth(3f))
        )

        // Steps 9-14 (fans/snapshot-fans) slot in here in future work, above.

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

        style.addSource(GeoJsonSource("editor-polygon"))
        style.addSource(GeoJsonSource("editor-line"))
        style.addSource(GeoJsonSource("editor-midpoints"))
        style.addSource(GeoJsonSource("editor-handles"))
        style.addLayer(
            FillLayer("editor-fill", "editor-polygon")
                .withProperties(fillColor("#1E88E5"), fillOpacity(0.20f))
        )
        style.addLayer(
            LineLayer("editor-outline", "editor-polygon")
                .withProperties(lineColor("#1E88E5"), lineWidth(2f))
        )
        style.addLayer(
            LineLayer("editor-outline-line", "editor-line")
                .withProperties(lineColor("#1E88E5"), lineWidth(2f))
        )
        style.addLayer(
            CircleLayer("editor-midpoints", "editor-midpoints")
                .withProperties(
                    circleRadius(6f),
                    circleColor("#9E9E9E"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(1f),
                )
        )
        style.addLayer(
            CircleLayer("editor-handles", "editor-handles")
                .withProperties(
                    circleRadius(10f),
                    circleColor("#FFFFFF"),
                    circleStrokeWidth(3f),
                    circleStrokeColor(
                        Expression.switchCase(
                            Expression.eq(Expression.get("selected"), Expression.literal(true)),
                            Expression.literal("#E53935"),
                            Expression.literal("#1E88E5"),
                        )
                    ),
                )
        )

        applyShooter()
        applyZones()
        applyEditorRender()
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

    fun setZones(zones: List<NoFireZone>) {
        this.zones = zones
        applyZones()
    }

    private fun applyZones() {
        val style = style ?: return

        val polygonFeatures = mutableListOf<Feature>()
        val markerCircleFeatures = mutableListOf<Feature>()
        val markerPointFeatures = mutableListOf<Feature>()
        val lineFeatures = mutableListOf<Feature>()
        val lineBufferFeatures = mutableListOf<Feature>()

        for (zone in zones) {
            when (zone) {
                is NoFirePolygon -> {
                    val ring = zone.vertices.map { Point.fromLngLat(it.lon, it.lat) }
                    val closedRing = if (ring.isNotEmpty() && ring.first() != ring.last()) ring + ring.first() else ring
                    val feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(closedRing)))
                    feature.addNumberProperty("zoneId", zone.id)
                    feature.addStringProperty("name", zone.name)
                    polygonFeatures.add(feature)
                }
                is NoFireMarker -> {
                    val ring = Sector.circleOutline(zone.center, zone.radiusM).map { Point.fromLngLat(it.lon, it.lat) }
                    val closedRing = if (ring.isNotEmpty() && ring.first() != ring.last()) ring + ring.first() else ring
                    val circleFeature = Feature.fromGeometry(Polygon.fromLngLats(listOf(closedRing)))
                    circleFeature.addNumberProperty("zoneId", zone.id)
                    circleFeature.addStringProperty("name", zone.name)
                    markerCircleFeatures.add(circleFeature)

                    val pointFeature = Feature.fromGeometry(Point.fromLngLat(zone.center.lon, zone.center.lat))
                    pointFeature.addNumberProperty("zoneId", zone.id)
                    pointFeature.addStringProperty("name", zone.name)
                    markerPointFeatures.add(pointFeature)
                }
                is NoFireLine -> {
                    val linePoints = zone.vertices.map { Point.fromLngLat(it.lon, it.lat) }
                    val lineFeature = Feature.fromGeometry(LineString.fromLngLats(linePoints))
                    lineFeature.addNumberProperty("zoneId", zone.id)
                    lineFeature.addStringProperty("name", zone.name)
                    lineFeatures.add(lineFeature)

                    if (zone.bufferM > 0.0 && zone.vertices.isNotEmpty()) {
                        val proj = EnuProjection(zone.vertices.first())
                        val vecs = zone.vertices.map { proj.toEnu(it) }
                        val rectangles = Geometry2D.segmentBuffers(vecs, zone.bufferM)
                        for (rectangle in rectangles) {
                            val rectRing = rectangle.map { corner ->
                                val latLon = proj.fromEnu(corner)
                                Point.fromLngLat(latLon.lon, latLon.lat)
                            }
                            val bufferFeature = Feature.fromGeometry(Polygon.fromLngLats(listOf(rectRing)))
                            bufferFeature.addNumberProperty("zoneId", zone.id)
                            bufferFeature.addStringProperty("name", zone.name)
                            lineBufferFeatures.add(bufferFeature)
                        }
                    }
                }
            }
        }

        style.getSourceAs<GeoJsonSource>("zones-polygons")
            ?.setGeoJson(FeatureCollection.fromFeatures(polygonFeatures))
        style.getSourceAs<GeoJsonSource>("zones-marker-circles")
            ?.setGeoJson(FeatureCollection.fromFeatures(markerCircleFeatures))
        style.getSourceAs<GeoJsonSource>("zones-marker-points")
            ?.setGeoJson(FeatureCollection.fromFeatures(markerPointFeatures))
        style.getSourceAs<GeoJsonSource>("zones-lines")
            ?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        style.getSourceAs<GeoJsonSource>("zones-lines-buffer")
            ?.setGeoJson(FeatureCollection.fromFeatures(lineBufferFeatures))
    }

    fun setOnLongPress(listener: (LatLon) -> Unit) {
        onLongPress = listener
    }

    fun setOnTap(listener: (TapHit) -> Unit) {
        onTap = listener
    }

    fun setHandleDragListener(l: HandleDragListener?) {
        handleDragListener = l
    }

    fun setEditorRender(r: EditorRender?) {
        editorRender = r
        applyEditorRender()
    }

    private fun applyEditorRender() {
        val style = style ?: return
        val r = editorRender

        val polygonCollection = if (r != null && r.closed && r.ring.size >= 3) {
            val ring = r.ring.map { Point.fromLngLat(it.lon, it.lat) }
            FeatureCollection.fromFeature(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        val lineCollection = if (r != null && !r.closed && r.ring.size >= 2) {
            val points = r.ring.map { Point.fromLngLat(it.lon, it.lat) }
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>("editor-polygon")?.setGeoJson(polygonCollection)
        style.getSourceAs<GeoJsonSource>("editor-line")?.setGeoJson(lineCollection)

        val handleFeatures = r?.handles?.mapIndexed { index, p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addNumberProperty("index", index)
                addBooleanProperty("selected", index == r.selectedIndex)
            }
        }.orEmpty()
        style.getSourceAs<GeoJsonSource>("editor-handles")
            ?.setGeoJson(FeatureCollection.fromFeatures(handleFeatures))

        val midpointFeatures = r?.midpoints?.mapIndexed { insertAfter, p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addNumberProperty("insertAfter", insertAfter)
            }
        }.orEmpty()
        style.getSourceAs<GeoJsonSource>("editor-midpoints")
            ?.setGeoJson(FeatureCollection.fromFeatures(midpointFeatures))
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        val map = map ?: return false
        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val listener = handleDragListener ?: return false
                val x = ev.x
                val y = ev.y
                val handleHits = map.queryRenderedFeatures(RectF(x - 24, y - 24, x + 24, y + 24), "editor-handles")
                if (handleHits.isNotEmpty()) {
                    dragIndex = handleHits.first().getNumberProperty("index").toInt()
                    downX = x
                    downY = y
                    moved = false
                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    return true
                }
                val midpointHits = map.queryRenderedFeatures(RectF(x - 24, y - 24, x + 24, y + 24), "editor-midpoints")
                if (midpointHits.isNotEmpty()) {
                    val insertAfter = midpointHits.first().getNumberProperty("insertAfter").toInt()
                    downX = x
                    downY = y
                    moved = false
                    dragIndex = listener.onMidpointPressed(insertAfter, latLonAt(ev))
                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    return true
                }
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val index = dragIndex ?: return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > 8.0) {
                    moved = true
                }
                handleDragListener?.onHandleMoved(index, latLonAt(ev))
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val index = dragIndex ?: return false
                if (!moved) {
                    handleDragListener?.onHandleTapped(index)
                }
                map.uiSettings.isScrollGesturesEnabled = true
                map.uiSettings.isZoomGesturesEnabled = true
                dragIndex = null
                true
            }
            else -> false
        }
    }

    private fun latLonAt(ev: MotionEvent): LatLon {
        val latLng = map?.projection?.fromScreenLocation(PointF(ev.x, ev.y))
        return LatLon(latLng?.latitude ?: 0.0, latLng?.longitude ?: 0.0)
    }
}
