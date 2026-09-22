package com.wingspan.app.domain.geo

import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EnuProjection(val origin: LatLon) {

    companion object {
        const val EARTH_RADIUS_M = 6371008.8
    }

    fun toEnu(p: LatLon): Vec2 {
        val x = toRadians(p.lon - origin.lon) * cos(toRadians(origin.lat)) * EARTH_RADIUS_M
        val y = toRadians(p.lat - origin.lat) * EARTH_RADIUS_M
        return Vec2(x, y)
    }

    fun fromEnu(v: Vec2): LatLon {
        val lat = origin.lat + toDegrees(v.y / EARTH_RADIUS_M)
        val lon = origin.lon + toDegrees(v.x / (EARTH_RADIUS_M * cos(toRadians(origin.lat))))
        return LatLon(lat, lon)
    }
}

fun distanceM(a: LatLon, b: LatLon): Double {
    val r = EnuProjection.EARTH_RADIUS_M
    val lat1 = toRadians(a.lat)
    val lat2 = toRadians(b.lat)
    val dLat = toRadians(b.lat - a.lat)
    val dLon = toRadians(b.lon - a.lon)

    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
    val c = 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    return r * c
}
