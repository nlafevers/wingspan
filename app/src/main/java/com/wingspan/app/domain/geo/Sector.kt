package com.wingspan.app.domain.geo

import kotlin.math.abs

object Sector {

    fun arcPoints(
        origin: LatLon,
        leftDeg: Double,
        rightDeg: Double,
        radiusM: Double,
        stepDeg: Double = 1.0,
    ): List<LatLon> {
        val proj = EnuProjection(origin)
        val isFullCircle = isFullCircle(leftDeg, rightDeg)
        val left = if (isFullCircle) 0.0 else leftDeg
        val width = if (isFullCircle) 360.0 else Geometry2D.normalizeBearing(rightDeg - leftDeg)

        val points = mutableListOf<LatLon>()
        var offset = 0.0
        while (offset < width) {
            val bearing = Geometry2D.normalizeBearing(left + offset)
            points.add(proj.fromEnu(Geometry2D.bearingToUnitVector(bearing) * radiusM))
            offset += stepDeg
        }
        val endBearing = Geometry2D.normalizeBearing(left + width)
        points.add(proj.fromEnu(Geometry2D.bearingToUnitVector(endBearing) * radiusM))
        return points
    }

    fun sectorOutline(
        origin: LatLon,
        leftDeg: Double,
        rightDeg: Double,
        radiusM: Double,
        stepDeg: Double = 1.0,
    ): List<LatLon> {
        val arc = arcPoints(origin, leftDeg, rightDeg, radiusM, stepDeg)
        return if (isFullCircle(leftDeg, rightDeg)) {
            arc
        } else {
            listOf(origin) + arc + listOf(origin)
        }
    }

    fun circleOutline(center: LatLon, radiusM: Double, stepDeg: Double = 5.0): List<LatLon> =
        arcPoints(center, 0.0, 0.0, radiusM, stepDeg)

    // FanCalculator emits an unobstructed field of fire as 0.0..360.0, which normalises to a
    // zero width, so equal bearings alone would miss it and collapse the circle to one spoke.
    private fun isFullCircle(leftDeg: Double, rightDeg: Double): Boolean =
        leftDeg == rightDeg || abs(rightDeg - leftDeg) >= 360.0
}
