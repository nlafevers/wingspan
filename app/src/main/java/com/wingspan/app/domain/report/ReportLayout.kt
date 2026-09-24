package com.wingspan.app.domain.report

import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.LatLon
import java.lang.Math.toRadians
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

object ReportLayout {

    const val PAGE_WIDTH_PT = 612.0
    const val PAGE_HEIGHT_PT = 792.0
    const val MARGIN_PT = 36.0
    const val MAP_SIDE_PT = 540.0
    const val SNAPSHOT_PX = 1080

    data class MapFrame(
        val center: LatLon,
        val halfExtentM: Double,
        val pointsPerMeter: Double,
        val metersPerPixel: Double,
        val zoom: Double,
    )

    fun frameFor(report: RangeReport, paddingFraction: Double = 0.08): MapFrame? {
        val positions = report.positions
        if (positions.isEmpty()) return null

        val centerLat = positions.sumOf { it.position.lat } / positions.size
        val centerLon = positions.sumOf { it.position.lon } / positions.size
        val center = LatLon(centerLat, centerLon)
        val proj = EnuProjection(center)

        var maxExtent = 0.0
        for (position in positions) {
            val enu = proj.toEnu(position.position)
            val xExtent = abs(enu.x) + position.fanRangeM
            val yExtent = abs(enu.y) + position.fanRangeM
            maxExtent = max(maxExtent, max(xExtent, yExtent))
        }

        val halfExtentM = max(maxExtent * (1.0 + paddingFraction), 50.0)
        val pointsPerMeter = MAP_SIDE_PT / (2.0 * halfExtentM)
        val metersPerPixel = (2.0 * halfExtentM) / SNAPSHOT_PX
        val zoom = ln(156543.03392 * cos(toRadians(center.lat)) / metersPerPixel) / ln(2.0)

        return MapFrame(
            center = center,
            halfExtentM = halfExtentM,
            pointsPerMeter = pointsPerMeter,
            metersPerPixel = metersPerPixel,
            zoom = zoom,
        )
    }

    fun toPagePoint(frame: MapFrame, point: LatLon): Pair<Double, Double> {
        val proj = EnuProjection(frame.center)
        val enu = proj.toEnu(point)
        val x = MARGIN_PT + MAP_SIDE_PT / 2.0 + enu.x * frame.pointsPerMeter
        val y = MARGIN_PT + MAP_SIDE_PT / 2.0 - enu.y * frame.pointsPerMeter
        return Pair(x, y)
    }

    fun scaleBarMeters(frame: MapFrame): Double {
        val maxMeters = 0.4 * (2.0 * frame.halfExtentM)
        var best = 0.0
        for (power in -10..15) {
            val base = 10.0.pow(power)
            for (multiplier in doubleArrayOf(1.0, 2.0, 5.0)) {
                val candidate = multiplier * base
                if (candidate <= maxMeters && candidate > best) {
                    best = candidate
                }
            }
        }
        return best
    }
}
