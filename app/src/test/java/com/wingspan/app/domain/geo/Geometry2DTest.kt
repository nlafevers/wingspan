package com.wingspan.app.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Geometry2DTest {

    @Test
    fun crossingSegmentsIntersect() {
        val p1 = Vec2(0.0, 0.0)
        val p2 = Vec2(10.0, 10.0)
        val q1 = Vec2(0.0, 10.0)
        val q2 = Vec2(10.0, 0.0)

        assertTrue(Geometry2D.segmentsIntersect(p1, p2, q1, q2))
    }

    @Test
    fun parallelDisjointSegmentsDoNotIntersect() {
        val p1 = Vec2(0.0, 0.0)
        val p2 = Vec2(10.0, 0.0)
        val q1 = Vec2(0.0, 5.0)
        val q2 = Vec2(10.0, 5.0)

        assertFalse(Geometry2D.segmentsIntersect(p1, p2, q1, q2))
    }

    @Test
    fun segmentsTouchingAtEndpointIntersect() {
        val p1 = Vec2(0.0, 0.0)
        val p2 = Vec2(10.0, 0.0)
        val q1 = Vec2(10.0, 0.0)
        val q2 = Vec2(10.0, 10.0)

        assertTrue(Geometry2D.segmentsIntersect(p1, p2, q1, q2))
    }

    @Test
    fun pointInsideSquareIsDetected() {
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(10.0, 0.0),
            Vec2(10.0, 10.0),
            Vec2(0.0, 10.0),
        )

        assertTrue(Geometry2D.pointInPolygon(Vec2(5.0, 5.0), square))
    }

    @Test
    fun pointOutsideSquareIsNotDetected() {
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(10.0, 0.0),
            Vec2(10.0, 10.0),
            Vec2(0.0, 10.0),
        )

        assertFalse(Geometry2D.pointInPolygon(Vec2(15.0, 5.0), square))
    }

    @Test
    fun distanceFromPointToSegment() {
        val distance = Geometry2D.distancePointToSegment(
            Vec2(0.0, 5.0),
            Vec2(-10.0, 0.0),
            Vec2(10.0, 0.0),
        )

        assertEquals(5.0, distance, 1e-9)
    }

    @Test
    fun bearing90DegreesPointsEast() {
        val v = Geometry2D.bearingToUnitVector(90.0)

        assertEquals(1.0, v.x, 1e-9)
        assertEquals(0.0, v.y, 1e-9)
    }

    @Test
    fun normalizeNegativeBearing() {
        assertEquals(350.0, Geometry2D.normalizeBearing(-10.0), 1e-9)
    }

    @Test
    fun enuProjectionRoundTripsNearbyPoint() {
        val origin = LatLon(39.0, -105.0)
        val projection = EnuProjection(origin)

        val bearing = Geometry2D.bearingToUnitVector(45.0)
        val original = bearing * 500.0

        val latLon = projection.fromEnu(original)
        val roundTripped = projection.toEnu(latLon)

        assertEquals(original.x, roundTripped.x, 0.01)
        assertEquals(original.y, roundTripped.y, 0.01)
    }

    @Test
    fun enuProjectionYForPointNorthOfOrigin() {
        val origin = LatLon(39.0, -105.0)
        val projection = EnuProjection(origin)

        val v = projection.toEnu(LatLon(39.001, -105.0))

        assertEquals(111.2, v.y, 0.5)
    }
}
