package com.wingspan.app.data.geojson

import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonCodecTest {

    private val epsilon = 1e-9

    @Test
    fun `round trip preserves polygon and marker`() {
        val polygon = NoFirePolygon(
            id = 1,
            name = "Restricted Ridge",
            vertices = listOf(
                LatLon(lat = 37.1, lon = -122.1),
                LatLon(lat = 37.2, lon = -122.2),
                LatLon(lat = 37.3, lon = -122.05),
            ),
        )
        val marker = NoFireMarker(
            id = 2,
            name = "Tower Site",
            center = LatLon(lat = 38.5, lon = -121.9),
            radiusM = 150.0,
        )

        val encoded = GeoJsonCodec.encode(listOf(polygon, marker))
        val decoded = GeoJsonCodec.decode(encoded)

        assertEquals(2, decoded.size)

        val decodedPolygon = decoded[0] as NoFirePolygon
        assertEquals("Restricted Ridge", decodedPolygon.name)
        assertEquals(0L, decodedPolygon.id)
        assertEquals(polygon.vertices.size, decodedPolygon.vertices.size)
        polygon.vertices.zip(decodedPolygon.vertices).forEach { (expected, actual) ->
            assertEquals(expected.lat, actual.lat, epsilon)
            assertEquals(expected.lon, actual.lon, epsilon)
        }

        val decodedMarker = decoded[1] as NoFireMarker
        assertEquals("Tower Site", decodedMarker.name)
        assertEquals(0L, decodedMarker.id)
        assertEquals(marker.center.lat, decodedMarker.center.lat, epsilon)
        assertEquals(marker.center.lon, decodedMarker.center.lon, epsilon)
        assertEquals(marker.radiusM, decodedMarker.radiusM, epsilon)
    }

    @Test
    fun `decode drops closing vertex from closed ring without type property`() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [
                        [-122.1, 37.1],
                        [-122.2, 37.2],
                        [-122.05, 37.3],
                        [-122.1, 37.1]
                      ]
                    ]
                  },
                  "properties": {
                    "name": "Closed Ring Zone"
                  }
                }
              ]
            }
        """.trimIndent()

        val decoded = GeoJsonCodec.decode(geoJson)

        assertEquals(1, decoded.size)
        val polygon = decoded[0] as NoFirePolygon
        assertEquals("Closed Ring Zone", polygon.name)
        assertEquals(3, polygon.vertices.size)
        assertEquals(37.1, polygon.vertices[0].lat, epsilon)
        assertEquals(-122.1, polygon.vertices[0].lon, epsilon)
        assertEquals(37.3, polygon.vertices[2].lat, epsilon)
        assertEquals(-122.05, polygon.vertices[2].lon, epsilon)
    }

    @Test
    fun `decode point without radius_m defaults to 25 meters`() {
        val geoJson = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [-121.9, 38.5]
              },
              "properties": {
                "name": "No Radius Marker"
              }
            }
        """.trimIndent()

        val decoded = GeoJsonCodec.decode(geoJson)

        assertEquals(1, decoded.size)
        val marker = decoded[0] as NoFireMarker
        assertEquals("No Radius Marker", marker.name)
        assertEquals(25.0, marker.radiusM, epsilon)
        assertEquals(38.5, marker.center.lat, epsilon)
        assertEquals(-121.9, marker.center.lon, epsilon)
    }

    @Test
    fun `decode throws on invalid root`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            GeoJsonCodec.decode("""{"foo": 1}""")
        }
        assertTrue(exception.message?.isNotBlank() == true)
    }
}
