package com.wingspan.app.domain.geo

import kotlin.math.ceil

data class Fan(
    val leftTrueDeg: Double,
    val rightTrueDeg: Double,
    val fullCircle: Boolean = false,
) {
    val widthDeg: Double
        get() = if (fullCircle) 360.0 else Geometry2D.normalizeBearing(rightTrueDeg - leftTrueDeg)
}

data class FanResult(
    val fans: List<Fan>,
    val insideZone: Boolean,
    val blockedBearings: Int,
)

object FanCalculator {

    fun compute(
        origin: LatLon,
        maxRangeM: Double,
        zones: List<NoFireZone>,
        stepDeg: Double = 1.0,
        marginDeg: Double = 2.0,
        minFanDeg: Double = 5.0,
        windBufferM: Double = 0.0,
    ): FanResult {
        val proj = EnuProjection(origin)
        val polygons = zones.filterIsInstance<NoFirePolygon>().map { polygon ->
            polygon.vertices.map { proj.toEnu(it) }
        }
        val markers = zones.filterIsInstance<NoFireMarker>().map { marker ->
            proj.toEnu(marker.center) to marker.radiusM
        }
        val lines = zones.filterIsInstance<NoFireLine>().map { line ->
            line.vertices.map { proj.toEnu(it) } to line.bufferM
        }

        val originVec = Vec2(0.0, 0.0)

        val insideZone = polygons.any { Geometry2D.pointInPolygon(originVec, it) } ||
            (windBufferM > 0.0 && polygons.any { polygon ->
                val size = polygon.size
                polygon.indices.any { j ->
                    val a = polygon[j]
                    val b = polygon[(j + 1) % size]
                    Geometry2D.distancePointToSegment(originVec, a, b) <= windBufferM
                }
            }) ||
            markers.any { (center, radiusM) -> (originVec - center).length < radiusM + windBufferM }

        if (insideZone) {
            return FanResult(fans = emptyList(), insideZone = true, blockedBearings = 360)
        }

        val n = (360.0 / stepDeg).toInt()
        val blocked = BooleanArray(n)

        for (i in 0 until n) {
            val bearing = i * stepDeg
            val end = Geometry2D.bearingToUnitVector(bearing) * maxRangeM

            var isBlocked = false

            outer@ for (polygon in polygons) {
                val size = polygon.size
                for (j in polygon.indices) {
                    val a = polygon[j]
                    val b = polygon[(j + 1) % size]
                    if (Geometry2D.segmentsIntersect(originVec, end, a, b) ||
                        (windBufferM > 0.0 && Geometry2D.distanceSegmentToSegment(originVec, end, a, b) <= windBufferM)
                    ) {
                        isBlocked = true
                        break@outer
                    }
                }
            }

            if (!isBlocked) {
                for ((center, radiusM) in markers) {
                    if (Geometry2D.distancePointToSegment(center, originVec, end) < radiusM + windBufferM) {
                        isBlocked = true
                        break
                    }
                }
            }

            if (!isBlocked) {
                outerLines@ for ((lineVertices, bufferM) in lines) {
                    for (j in 0 until lineVertices.size - 1) {
                        val segStart = lineVertices[j]
                        val segEnd = lineVertices[j + 1]
                        if (Geometry2D.distanceSegmentToSegment(originVec, end, segStart, segEnd) <= bufferM + windBufferM) {
                            isBlocked = true
                            break@outerLines
                        }
                    }
                }
            }

            blocked[i] = isBlocked
        }

        val m = ceil(marginDeg / stepDeg).toInt()
        val dilated = BooleanArray(n) { i ->
            (-m..m).any { offset -> blocked[((i + offset) % n + n) % n] }
        }

        val blockedBearings = dilated.count { it }

        if (dilated.none { it }) {
            return FanResult(
                fans = listOf(Fan(0.0, 360.0, fullCircle = true)),
                insideZone = false,
                blockedBearings = 0,
            )
        }

        var s = -1
        for (i in 0 until n) {
            val prev = dilated[(i - 1 + n) % n]
            if (prev && !dilated[i]) {
                s = i
                break
            }
        }

        val fans = mutableListOf<Fan>()
        if (s != -1) {
            var i = 0
            while (i < n) {
                val idx = (s + i) % n
                if (!dilated[idx]) {
                    val runStart = idx
                    var runEnd = idx
                    i++
                    while (i < n && !dilated[(s + i) % n]) {
                        runEnd = (s + i) % n
                        i++
                    }
                    val left = runStart * stepDeg
                    val right = runEnd * stepDeg
                    val fan = Fan(left, right)
                    if (fan.widthDeg >= minFanDeg) {
                        fans.add(fan)
                    }
                } else {
                    i++
                }
            }
        }

        return FanResult(fans = fans, insideZone = false, blockedBearings = blockedBearings)
    }
}
