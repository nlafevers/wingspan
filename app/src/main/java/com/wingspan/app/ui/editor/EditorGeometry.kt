package com.wingspan.app.ui.editor

import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.Sector

data class EditorRender(
    val ring: List<LatLon>,
    val closed: Boolean,
    val handles: List<LatLon>,
    val selectedIndex: Int?,
    val midpoints: List<LatLon>,
)

object EditorGeometry {

    fun polygonRender(vertices: List<LatLon>, selectedIndex: Int?): EditorRender {
        val ring = if (vertices.size >= 3) vertices + vertices.first() else emptyList()
        val midpoints = when {
            vertices.size >= 3 -> vertices.indices.map { i -> midpoint(vertices[i], vertices[(i + 1) % vertices.size]) }
            vertices.size == 2 -> listOf(midpoint(vertices[0], vertices[1]))
            else -> emptyList()
        }
        return EditorRender(
            ring = ring,
            closed = true,
            handles = vertices,
            selectedIndex = selectedIndex,
            midpoints = midpoints,
        )
    }

    fun markerRender(center: LatLon?, radiusM: Double): EditorRender {
        val ring = if (center != null) Sector.circleOutline(center, radiusM) else emptyList()
        val handles = if (center != null) listOf(center) else emptyList()
        return EditorRender(
            ring = ring,
            closed = true,
            handles = handles,
            selectedIndex = if (center != null) 0 else null,
            midpoints = emptyList(),
        )
    }

    fun lineRender(vertices: List<LatLon>, selectedIndex: Int?): EditorRender {
        val midpoints = if (vertices.size >= 2) {
            (0 until vertices.size - 1).map { i -> midpoint(vertices[i], vertices[i + 1]) }
        } else {
            emptyList()
        }
        return EditorRender(
            ring = vertices,
            closed = false,
            handles = vertices,
            selectedIndex = selectedIndex,
            midpoints = midpoints,
        )
    }

    private fun midpoint(a: LatLon, b: LatLon): LatLon =
        LatLon(lat = (a.lat + b.lat) / 2.0, lon = (a.lon + b.lon) / 2.0)
}
