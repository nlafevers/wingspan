package com.wingspan.app.domain.geo

object Sector {

    fun arcPoints(
        origin: LatLon,
        leftDeg: Double,
        rightDeg: Double,
        radiusM: Double,
        stepDeg: Double = 1.0,
    ): List<LatLon> {
        val proj = EnuProjection(origin)
        val isFullCircle = leftDeg == rightDeg
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
        return if (leftDeg == rightDeg) {
            arc
        } else {
            listOf(origin) + arc + listOf(origin)
        }
    }

    fun circleOutline(center: LatLon, radiusM: Double, stepDeg: Double = 5.0): List<LatLon> =
        arcPoints(center, 0.0, 0.0, radiusM, stepDeg)
}
