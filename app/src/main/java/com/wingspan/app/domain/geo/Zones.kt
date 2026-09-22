package com.wingspan.app.domain.geo

sealed interface NoFireZone {
    val id: Long
    val name: String
}

data class NoFirePolygon(
    override val id: Long,
    override val name: String,
    val vertices: List<LatLon>,
) : NoFireZone

data class NoFireMarker(
    override val id: Long,
    override val name: String,
    val center: LatLon,
    val radiusM: Double,
) : NoFireZone

data class NoFireLine(
    override val id: Long,
    override val name: String,
    val vertices: List<LatLon>,
    val bufferM: Double = 0.0,
) : NoFireZone

interface DeclinationProvider {
    fun declinationDeg(position: LatLon): Double
}

fun trueToMagnetic(trueDeg: Double, declinationDeg: Double): Double =
    Geometry2D.normalizeBearing(trueDeg - declinationDeg)
