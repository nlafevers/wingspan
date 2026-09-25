package com.wingspan.app.ui.report

import android.content.Context
import android.graphics.Bitmap
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.report.ReportLayout
import com.wingspan.app.domain.report.ReportLayout.MapFrame
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * Renders an off-screen basemap image for the PDF report via MapLibre's off-screen
 * [MapSnapshotter] (distinct from [com.wingspan.app.ui.map.MapController.captureBitmap], which
 * captures the on-screen map). Returns null on timeout, error or exception so a report still
 * exports with a plain background when no tiles are cached (e.g. offline).
 */
object ReportSnapshotter {

    private const val TIMEOUT_MS = 15_000L

    suspend fun capture(context: Context, basemap: Basemap, frame: MapFrame): Bitmap? =
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                withContext(Dispatchers.Main) {
                    val json = context.assets.open(basemap.assetPath).bufferedReader().use { it.readText() }
                    val options = MapSnapshotter.Options(ReportLayout.SNAPSHOT_PX, ReportLayout.SNAPSHOT_PX)
                        .withStyleJson(json)
                        .withPixelRatio(1.0f)
                        .withCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(frame.center.lat, frame.center.lon))
                                .zoom(frame.zoom)
                                .build()
                        )
                    val snapshotter = MapSnapshotter(context, options)
                    suspendCancellableCoroutine<Bitmap?> { continuation ->
                        snapshotter.start(
                            { snapshot -> continuation.resume(snapshot.bitmap) },
                            { _ -> continuation.resume(null) },
                        )
                        continuation.invokeOnCancellation { snapshotter.cancel() }
                    }
                }
            }.getOrNull()
        }
}
