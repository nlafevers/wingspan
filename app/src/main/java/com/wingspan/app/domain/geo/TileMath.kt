package com.wingspan.app.domain.geo

import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

object TileMath {

    private const val MAX_LAT = 85.0511

    fun tileX(lon: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom)
        val maxIndex = (n.toInt() - 1).coerceAtLeast(0)
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        return x.coerceIn(0, maxIndex)
    }

    fun tileY(lat: Double, zoom: Int): Int {
        val clampedLat = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = toRadians(clampedLat)
        val n = 2.0.pow(zoom)
        val maxIndex = (n.toInt() - 1).coerceAtLeast(0)
        val y = floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n,
        ).toInt()
        return y.coerceIn(0, maxIndex)
    }

    fun tileCount(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            val xMin = tileX(west, zoom)
            val xMax = tileX(east, zoom)
            val yMin = tileY(north, zoom)
            val yMax = tileY(south, zoom)
            total += (xMax - xMin + 1).toLong() * (yMax - yMin + 1).toLong()
        }
        return total
    }
}
