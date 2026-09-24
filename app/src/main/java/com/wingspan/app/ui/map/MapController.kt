package com.wingspan.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import com.wingspan.app.data.FiringSnapshot
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.domain.geo.Sector
import com.wingspan.app.ui.Formatters
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.pow

/**
 * The result of a tap on the map: where it landed, the zone (if any) hit there, and the fan
 * (if any) hit there when no zone was hit.
 */
data class TapHit(val position: LatLon, val zoneId: Long?, val fanIndex: Int?)

/**
 * The map's currently visible geographic extent, in the same south/west/north/east order used
 * throughout the offline-download plumbing (see [com.wingspan.app.domain.geo.TileMath.tileCount]
 * and [com.wingspan.app.data.map.OfflineRepository.startDownload]).
 */
data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)

/**
 * The only place in the app that talks to MapLibre directly.
 */
class MapController(private val context: Context) {

    companion object {
        // Web Mercator ground resolution at the equator, zoom 0 (meters/pixel); pixels-per-meter
        // at any zoom/latitude is 2^zoom / (this * cos(latitude)).
        private const val EARTH_MERCATOR_METERS_PER_PIXEL_AT_ZOOM0 = 156543.03392804097
    }

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var basemap: Basemap? = null
    private var shooter: ShooterPosition? = null
    private var zones: List<NoFireZone> = emptyList()
    private var fanState: FanUiState? = null
    private var selectedFanState: FanUiState? = null
    private var selectedFanIndexValue: Int? = null
    private var snapshotFanState: FiringSnapshot? = null
    private var onLongPress: ((LatLon) -> Unit)? = null
    private var onTap: ((TapHit) -> Unit)? = null
    private var handleDragListener: HandleDragListener? = null
    private var editorRender: EditorRender? = null
    private var dragIndex: Int? = null
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var moved: Boolean = false
    private var pendingCameraTarget: LatLon? = null
    private var pendingCameraZoom: Double? = null
    private var hasCenteredOnShooter = false

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
        map.addOnCameraMoveListener { updateSelectedFanLabels() }
        map.addOnMapClickListener {
            val position = LatLon(it.latitude, it.longitude)
            val screenPoint = map.projection.toScreenLocation(it)
            val hitBox = RectF(screenPoint.x - 12f, screenPoint.y - 12f, screenPoint.x + 12f, screenPoint.y + 12f)
            val zoneFeatures = map.queryRenderedFeatures(
                hitBox,
                "zones-marker-points",
                "zones-marker-circles-fill",
                "zones-polygons-fill",
                "zones-lines-outline",
                "zones-lines-buffer-stroke",
            )
            val zoneId = zoneFeatures.firstOrNull()?.getNumberProperty("zoneId")?.toLong()
            val fanIndex = if (zoneId == null) {
                map.queryRenderedFeatures(hitBox, "fans-fill").firstOrNull()
                    ?.getNumberProperty("fanIndex")?.toInt()
            } else {
                null
            }
            onTap?.invoke(TapHit(position, zoneId, fanIndex))
            true
        }
        mapView.setOnTouchListener { _, ev -> handleTouch(ev) }
        if (basemap != null) {
            loadStyle()
        }
        // A camera move requested before this map view finished attaching (native MapView/
        // getMapAsync setup is asynchronous) would otherwise be silently dropped: animateCamera()
        // no-ops while `map` is null, and the request isn't re-delivered once it's set. Re-apply
        // whatever was last requested now that a map actually exists to move.
        pendingCameraTarget?.let { animateCamera(it, pendingCameraZoom) }
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
        // 5.  zones-marker-points
        // 6.  zones-lines-buffer-stroke
        // 7.  zones-lines-outline
        // 8.  fans-fill
        // 9.  fans-outline
        // 10. fans-effective
        // 11. fans-selected
        // 12. snapshot-fans-fill
        // 13. snapshot-fans-outline
        // 14. position-dot
        // 15. editor-fill
        // 16. editor-outline (bound to editor-polygon)
        // 16b. editor-outline-line (bound to editor-line; same paint, mutually exclusive with 16)
        // 17. editor-midpoints
        // 18. editor-handles

        style.addSource(GeoJsonSource("position-accuracy"))
        style.addLayer(
            FillLayer("position-accuracy-fill", "position-accuracy")
                .withProperties(fillColor("#1E88E5"), fillOpacity(0.15f))
        )

        style.addSource(GeoJsonSource("zones-polygons"))
        style.addSource(GeoJsonSource("zones-marker-circles"))
        style.addSource(GeoJsonSource("zones-marker-points"))
        style.addSource(GeoJsonSource("zones-lines"))
        style.addLayer(
            FillLayer("zones-polygons-fill", "zones-polygons")
                .withProperties(fillColor("#D32F2F"), fillOpacity(0.25f))
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
            CircleLayer("zones-marker-points", "zones-marker-points")
                .withProperties(
                    circleRadius(6f),
                    circleColor("#B71C1C"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(1.5f),
                )
        )
        style.addLayer(
            LineLayer("zones-lines-buffer-stroke", "zones-lines")
                .withProperties(
                    lineColor("#D32F2F"),
                    lineOpacity(0.25f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(
                        Expression.interpolate(
                            Expression.exponential(2f),
                            Expression.zoom(),
                            Expression.stop(0f, Expression.get("bufferPxZ0")),
                            Expression.stop(20f, Expression.get("bufferPxZ20")),
                        )
                    ),
                )
                .withFilter(Expression.gt(Expression.get("bufferPxZ0"), Expression.literal(0f)))
        )
        style.addLayer(
            LineLayer("zones-lines-outline", "zones-lines")
                .withProperties(lineColor("#B71C1C"), lineWidth(3f))
        )

        style.addSource(GeoJsonSource("fans"))
        style.addSource(GeoJsonSource("fans-effective"))
        style.addSource(GeoJsonSource("fans-selected"))
        style.addLayer(
            FillLayer("fans-fill", "fans")
                .withProperties(fillColor("#43A047"), fillOpacity(0.25f))
        )
        style.addLayer(
            LineLayer("fans-outline", "fans")
                .withProperties(lineColor("#2E7D32"), lineWidth(2f))
        )
        style.addLayer(
            LineLayer("fans-effective", "fans-effective")
                .withProperties(lineColor("#2E7D32"), lineWidth(1.5f), lineDasharray(arrayOf(2f, 2f)))
        )
        style.addLayer(
            LineLayer("fans-selected", "fans-selected")
                .withProperties(lineColor("#FFD600"), lineWidth(4f))
        )

        style.addSource(GeoJsonSource("snapshot-fans"))
        style.addLayer(
            FillLayer("snapshot-fans-fill", "snapshot-fans")
                .withProperties(fillColor("#FFB300"), fillOpacity(0.25f))
        )
        style.addLayer(
            LineLayer("snapshot-fans-outline", "snapshot-fans")
                .withProperties(lineColor("#FF8F00"), lineWidth(2f))
        )

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
        applyFans()
        applySelectedFan()
        applySnapshotFans()
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
        pendingCameraTarget = target
        pendingCameraZoom = zoom
        val latLng = LatLng(target.lat, target.lon)
        val update = if (zoom != null) {
            CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        } else {
            CameraUpdateFactory.newLatLng(latLng)
        }
        map?.animateCamera(update)
    }

    fun currentZoom(): Double = map?.cameraPosition?.zoom ?: 3.0

    /** Renders the current map, including all overlay layers, into a bitmap. */
    fun captureBitmap(callback: (Bitmap) -> Unit) {
        map?.snapshot { callback(it) }
    }

    /** The area currently on screen, or null before the map has finished attaching. */
    fun visibleBounds(): Bounds? {
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return Bounds(
            south = bounds.latitudeSouth,
            west = bounds.longitudeWest,
            north = bounds.latitudeNorth,
            east = bounds.longitudeEast,
        )
    }

    fun setShooter(s: ShooterPosition?) {
        shooter = s
        applyShooter()
        // Recenter on this controller's first known shooter position, whether that's a brand
        // new GPS fix (cold start) or a position the ViewModel already had (e.g. returning from
        // Settings recreates this controller/native map from scratch, so it has no memory of
        // where the camera used to be - but the Compose-collected shooter state reflects the
        // ViewModel's current value immediately regardless of how the map view itself restarted).
        if (!hasCenteredOnShooter && s != null) {
            hasCenteredOnShooter = true
            animateCamera(s.position, zoom = maxOf(currentZoom(), 15.0))
        }
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
                    // The buffer corridor is drawn as a translucent stroke on this same line
                    // (see "zones-lines-buffer-stroke"), not as filled rectangles/discs: MapLibre
                    // alpha-blends overlapping translucent polygons per-triangle, so per-segment
                    // shapes double-darken at every joint. A single continuous stroke with round
                    // joins/caps has no overlap. Its width is a meters-to-pixels approximation
                    // (exact at this zone's own latitude, interpolated exactly across zoom via the
                    // 2^zoom scaling of Web Mercator) rather than an exact projected polygon; the
                    // real no-fire blocking math in FanCalculator is unaffected, since it works in
                    // real meters independently of this rendering.
                    //
                    // The pixel width is calibrated against MapLibre's own
                    // Projection.getMetersPerPixelAtLatitude at the current zoom (rather than a
                    // hand-derived Web Mercator constant, which measured visibly narrower than the
                    // real buffer on-device - MapLibre's own tile/DPI conventions are the ground
                    // truth here, not a manually re-derived formula). One calibration point is
                    // enough because pixel density doubles exactly every zoom level regardless of
                    // that convention, so the zoom-0/zoom-20 stops below are derived from it.
                    if (zone.bufferM > 0.0 && zone.vertices.isNotEmpty()) {
                        val refLat = zone.vertices.first().lat
                        val nowZoom = currentZoom()
                        val metersPerPixelNow = map?.projection?.getMetersPerPixelAtLatitude(refLat)
                            ?: (EARTH_MERCATOR_METERS_PER_PIXEL_AT_ZOOM0 * cos(Math.toRadians(refLat)) / 2.0.pow(nowZoom))
                        val widthNow = zone.bufferM * 2.0 / metersPerPixelNow
                        val widthAtZoom0 = widthNow / 2.0.pow(nowZoom)
                        lineFeature.addNumberProperty("bufferPxZ0", widthAtZoom0)
                        lineFeature.addNumberProperty("bufferPxZ20", widthAtZoom0 * 2.0.pow(20))
                    } else {
                        lineFeature.addNumberProperty("bufferPxZ0", 0.0)
                        lineFeature.addNumberProperty("bufferPxZ20", 0.0)
                    }
                    lineFeatures.add(lineFeature)
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
    }

    fun setFans(state: FanUiState?) {
        fanState = state
        applyFans()
    }

    private fun applyFans() {
        val style = style ?: return
        val state = fanState

        val fillFeatures = mutableListOf<Feature>()
        val effectiveFeatures = mutableListOf<Feature>()

        if (state != null && !state.insideZone) {
            for (fanView in state.fans) {
                val fan = fanView.fan
                val outline = Sector.sectorOutline(
                    state.origin, fan.leftTrueDeg, fan.rightTrueDeg, state.range.fanRangeM
                ).map { Point.fromLngLat(it.lon, it.lat) }
                val closedOutline = if (outline.isNotEmpty() && outline.first() != outline.last()) {
                    outline + outline.first()
                } else {
                    outline
                }
                val fillFeature = Feature.fromGeometry(Polygon.fromLngLats(listOf(closedOutline)))
                fillFeature.addNumberProperty("fanIndex", fanView.index)
                fillFeatures.add(fillFeature)

                val effectivePoints = Sector.arcPoints(
                    state.origin, fan.leftTrueDeg, fan.rightTrueDeg, state.range.effectiveRangeM
                ).map { Point.fromLngLat(it.lon, it.lat) }
                val effectiveFeature = Feature.fromGeometry(LineString.fromLngLats(effectivePoints))
                effectiveFeature.addNumberProperty("fanIndex", fanView.index)
                effectiveFeatures.add(effectiveFeature)
            }
        }

        style.getSourceAs<GeoJsonSource>("fans")?.setGeoJson(FeatureCollection.fromFeatures(fillFeatures))
        style.getSourceAs<GeoJsonSource>("fans-effective")
            ?.setGeoJson(FeatureCollection.fromFeatures(effectiveFeatures))
    }

    fun setSnapshotFans(snapshot: FiringSnapshot?) {
        snapshotFanState = snapshot
        applySnapshotFans()
    }

    private fun applySnapshotFans() {
        val style = style ?: return
        val snapshot = snapshotFanState

        val fillFeatures = mutableListOf<Feature>()
        if (snapshot != null) {
            for (fan in snapshot.fans) {
                val outline = Sector.sectorOutline(
                    snapshot.position, fan.leftTrueDeg, fan.rightTrueDeg, snapshot.maxRangeM
                ).map { Point.fromLngLat(it.lon, it.lat) }
                val closedOutline = if (outline.isNotEmpty() && outline.first() != outline.last()) {
                    outline + outline.first()
                } else {
                    outline
                }
                fillFeatures.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(closedOutline))))
            }

            val positionRing = Sector.circleOutline(snapshot.position, 3.0)
                .map { Point.fromLngLat(it.lon, it.lat) }
            val closedPositionRing = if (positionRing.isNotEmpty() && positionRing.first() != positionRing.last()) {
                positionRing + positionRing.first()
            } else {
                positionRing
            }
            fillFeatures.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(closedPositionRing))))
        }

        style.getSourceAs<GeoJsonSource>("snapshot-fans")?.setGeoJson(FeatureCollection.fromFeatures(fillFeatures))
    }

    fun setSelectedFan(state: FanUiState?, index: Int?) {
        selectedFanState = state
        selectedFanIndexValue = index
        applySelectedFan()
        updateSelectedFanLabels()
    }

    /**
     * Screen-space position and text for a selected fan's left/right bearing label. Rendered as
     * Compose text overlaid on the map, not a MapLibre SymbolLayer: none of the bundled offline
     * basemap styles declare a "glyphs" URL, which MapLibre requires to fetch/rasterize font
     * glyphs for any text-field - without it, SymbolLayer text silently renders nothing. Bundling
     * offline glyph assets is future work; a screen-space overlay works today with no font
     * dependency at all.
     */
    data class SelectedFanLabels(val left: PointF, val leftText: String, val right: PointF, val rightText: String)

    private var onSelectedFanLabelsChanged: ((SelectedFanLabels?) -> Unit)? = null

    fun setSelectedFanLabelsListener(listener: ((SelectedFanLabels?) -> Unit)?) {
        onSelectedFanLabelsChanged = listener
        updateSelectedFanLabels()
    }

    private fun updateSelectedFanLabels() {
        val map = map ?: return
        val state = selectedFanState
        val index = selectedFanIndexValue
        val fanView = if (state != null && index != null) state.fans.getOrNull(index) else null

        val labels = if (state != null && fanView != null && !fanView.fan.fullCircle) {
            // Labels sit a bit inside the fan's outer arc (not right on the edge) so they stay
            // legible even when the arc runs close to the screen edge.
            val proj = EnuProjection(state.origin)
            val labelRadiusM = state.range.fanRangeM * 0.92
            val leftLatLon = proj.fromEnu(Geometry2D.bearingToUnitVector(fanView.fan.leftTrueDeg) * labelRadiusM)
            val rightLatLon = proj.fromEnu(Geometry2D.bearingToUnitVector(fanView.fan.rightTrueDeg) * labelRadiusM)
            SelectedFanLabels(
                left = map.projection.toScreenLocation(LatLng(leftLatLon.lat, leftLatLon.lon)),
                leftText = Formatters.bearing(fanView.leftMagDeg),
                right = map.projection.toScreenLocation(LatLng(rightLatLon.lat, rightLatLon.lon)),
                rightText = Formatters.bearing(fanView.rightMagDeg),
            )
        } else {
            null
        }
        onSelectedFanLabelsChanged?.invoke(labels)
    }

    private fun applySelectedFan() {
        val style = style ?: return
        val state = selectedFanState
        val index = selectedFanIndexValue
        val fanView = if (state != null && index != null) state.fans.getOrNull(index) else null

        val outlineCollection = if (state != null && fanView != null) {
            val outline = Sector.sectorOutline(
                state.origin, fanView.fan.leftTrueDeg, fanView.fan.rightTrueDeg, state.range.fanRangeM
            ).map { Point.fromLngLat(it.lon, it.lat) }
            val closedOutline = if (outline.isNotEmpty() && outline.first() != outline.last()) {
                outline + outline.first()
            } else {
                outline
            }
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(closedOutline)))
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>("fans-selected")?.setGeoJson(outlineCollection)
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
