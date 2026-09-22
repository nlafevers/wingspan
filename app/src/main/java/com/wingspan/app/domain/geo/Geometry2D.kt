package com.wingspan.app.domain.geo

import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Double): Vec2 = Vec2(x * scalar, y * scalar)

    val length: Double
        get() = sqrt(x * x + y * y)
}

object Geometry2D {

    private fun cross(o: Vec2, a: Vec2, b: Vec2): Double =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun onSegment(p: Vec2, q: Vec2, r: Vec2): Boolean {
        return q.x <= maxOf(p.x, r.x) && q.x >= minOf(p.x, r.x) &&
            q.y <= maxOf(p.y, r.y) && q.y >= minOf(p.y, r.y)
    }

    fun segmentsIntersect(p1: Vec2, p2: Vec2, q1: Vec2, q2: Vec2): Boolean {
        val d1 = cross(q1, q2, p1)
        val d2 = cross(q1, q2, p2)
        val d3 = cross(p1, p2, q1)
        val d4 = cross(p1, p2, q2)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) {
            return true
        }

        if (d1 == 0.0 && onSegment(q1, p1, q2)) return true
        if (d2 == 0.0 && onSegment(q1, p2, q2)) return true
        if (d3 == 0.0 && onSegment(p1, q1, p2)) return true
        if (d4 == 0.0 && onSegment(p1, q2, p2)) return true

        return false
    }

    fun pointInPolygon(p: Vec2, polygon: List<Vec2>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val vi = polygon[i]
            val vj = polygon[j]
            if ((vi.y > p.y) != (vj.y > p.y)) {
                val xIntersect = vi.x + (p.y - vi.y) * (vj.x - vi.x) / (vj.y - vi.y)
                if (p.x < xIntersect) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val lenSq = ab.x * ab.x + ab.y * ab.y
        if (lenSq == 0.0) return (p - a).length
        val t = (((p.x - a.x) * ab.x) + ((p.y - a.y) * ab.y)) / lenSq
        val tClamped = t.coerceIn(0.0, 1.0)
        val projection = a + (ab * tClamped)
        return (p - projection).length
    }

    fun bearingToUnitVector(bearingDeg: Double): Vec2 {
        val theta = toRadians(bearingDeg)
        return Vec2(sin(theta), cos(theta))
    }

    fun normalizeBearing(deg: Double): Double {
        var result = deg % 360.0
        if (result < 0.0) result += 360.0
        return result
    }
}
