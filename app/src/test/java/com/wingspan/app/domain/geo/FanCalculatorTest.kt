package com.wingspan.app.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FanCalculatorTest {

    private val origin = LatLon(39.0, -105.0)
    private val proj = EnuProjection(origin)

    private fun p(x: Double, y: Double): LatLon = proj.fromEnu(Vec2(x, y))

    @Test
    fun noZonesProducesFullCircleFan() {
        val result = FanCalculator.compute(origin, maxRangeM = 300.0, zones = emptyList())

        assertEquals(1, result.fans.size)
        assertTrue(result.fans[0].fullCircle)
        assertFalse(result.insideZone)
    }

    @Test
    fun squarePolygonBlocksNorthBearing() {
        val square = NoFirePolygon(
            id = 1,
            name = "square",
            vertices = listOf(
                p(-50.0, 150.0),
                p(50.0, 150.0),
                p(50.0, 250.0),
                p(-50.0, 250.0),
            ),
        )

        val result = FanCalculator.compute(origin, maxRangeM = 300.0, zones = listOf(square))

        assertEquals(1, result.fans.size)
        val fan = result.fans[0]
        assertTrue("leftTrueDeg ${fan.leftTrueDeg} should be in [20, 22]", fan.leftTrueDeg in 20.0..22.0)
        assertTrue("rightTrueDeg ${fan.rightTrueDeg} should be in [338, 340]", fan.rightTrueDeg in 338.0..340.0)
        assertTrue("widthDeg ${fan.widthDeg} should be < 330", fan.widthDeg < 330.0)
    }

    @Test
    fun squarePolygonOutOfRangeProducesFullCircleFan() {
        val square = NoFirePolygon(
            id = 1,
            name = "square",
            vertices = listOf(
                p(-50.0, 150.0),
                p(50.0, 150.0),
                p(50.0, 250.0),
                p(-50.0, 250.0),
            ),
        )

        val result = FanCalculator.compute(origin, maxRangeM = 100.0, zones = listOf(square))

        assertEquals(1, result.fans.size)
        assertTrue(result.fans[0].fullCircle)
    }

    @Test
    fun markerBlocksBearingAround90() {
        val marker = NoFireMarker(id = 1, name = "marker", center = p(200.0, 0.0), radiusM = 25.0)

        val result = FanCalculator.compute(origin, maxRangeM = 300.0, zones = listOf(marker))

        assertEquals(1, result.fans.size)
        val fan = result.fans[0]
        assertTrue("leftTrueDeg ${fan.leftTrueDeg} should be in [98, 101]", fan.leftTrueDeg in 98.0..101.0)
        assertTrue("rightTrueDeg ${fan.rightTrueDeg} should be in [79, 82]", fan.rightTrueDeg in 79.0..82.0)
    }

    @Test
    fun originInsideSquareReturnsInsideZone() {
        val square = NoFirePolygon(
            id = 1,
            name = "square",
            vertices = listOf(
                p(-10.0, -10.0),
                p(10.0, -10.0),
                p(10.0, 10.0),
                p(-10.0, 10.0),
            ),
        )

        val result = FanCalculator.compute(origin, maxRangeM = 300.0, zones = listOf(square))

        assertTrue(result.insideZone)
        assertTrue(result.fans.isEmpty())
    }

    @Test
    fun narrowGapBetweenMarkersProducesSingleFan() {
        val markerA = NoFireMarker(id = 1, name = "a", center = p(0.0, 200.0), radiusM = 1.0)
        val markerB = NoFireMarker(id = 2, name = "b", center = p(21.0, 199.0), radiusM = 1.0)

        val result = FanCalculator.compute(origin, maxRangeM = 300.0, zones = listOf(markerA, markerB))

        assertEquals(1, result.fans.size)
    }

    @Test
    fun trueToMagneticConversion() {
        assertEquals(2.0, trueToMagnetic(10.0, 8.0), 1e-9)
        assertEquals(357.0, trueToMagnetic(5.0, 8.0), 1e-9)
    }

    @Test
    fun tileCountAcrossWorldExtent() {
        assertEquals(5L, TileMath.tileCount(-85.0, -180.0, 85.0, 180.0, 0, 1))
    }

    @Test
    fun sectorOutlinePointCount() {
        val outline = Sector.sectorOutline(origin, 30.0, 60.0, 100.0)

        assertEquals(33, outline.size)
        assertEquals(origin, outline.first())
        assertEquals(origin, outline.last())
    }
}
