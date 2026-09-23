package com.wingspan.app.ui.editor

import com.wingspan.app.domain.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGeometryTest {

    private val v0 = LatLon(39.0, -105.0)
    private val v1 = LatLon(39.001, -105.0)
    private val v2 = LatLon(39.001, -104.999)

    @Test
    fun `polygonRender with two vertices gives empty ring and one midpoint`() {
        val render = EditorGeometry.polygonRender(listOf(v0, v1), selectedIndex = null)

        assertTrue(render.ring.isEmpty())
        assertEquals(1, render.midpoints.size)
        assertEquals(LatLon((v0.lat + v1.lat) / 2.0, (v0.lon + v1.lon) / 2.0), render.midpoints[0])
    }

    @Test
    fun `polygonRender with three vertices closes ring and wraps midpoints`() {
        val render = EditorGeometry.polygonRender(listOf(v0, v1, v2), selectedIndex = 1)

        assertEquals(4, render.ring.size)
        assertEquals(v0, render.ring.first())
        assertEquals(v0, render.ring.last())
        assertEquals(3, render.midpoints.size)
        assertEquals(LatLon((v2.lat + v0.lat) / 2.0, (v2.lon + v0.lon) / 2.0), render.midpoints[2])
    }

    @Test
    fun `markerRender with null center has no handles`() {
        val render = EditorGeometry.markerRender(center = null, radiusM = 25.0)

        assertTrue(render.handles.isEmpty())
        assertTrue(render.ring.isEmpty())
        assertEquals(null, render.selectedIndex)
    }

    @Test
    fun `markerRender with center produces a dense circle outline`() {
        val render = EditorGeometry.markerRender(center = LatLon(39.0, -105.0), radiusM = 25.0)

        assertTrue(render.ring.size >= 72)
        assertEquals(listOf(LatLon(39.0, -105.0)), render.handles)
        assertEquals(0, render.selectedIndex)
    }

    @Test
    fun `lineRender with two vertices is open with one midpoint`() {
        val render = EditorGeometry.lineRender(listOf(v0, v1), selectedIndex = null)

        assertEquals(2, render.ring.size)
        assertFalse(render.closed)
        assertEquals(1, render.midpoints.size)
    }

    @Test
    fun `lineRender with three vertices has no closing-edge midpoint`() {
        val render = EditorGeometry.lineRender(listOf(v0, v1, v2), selectedIndex = null)

        assertEquals(2, render.midpoints.size)
        assertEquals(LatLon((v1.lat + v2.lat) / 2.0, (v1.lon + v2.lon) / 2.0), render.midpoints[1])
    }
}
