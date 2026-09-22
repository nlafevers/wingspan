package com.wingspan.app.data.geojson

import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Encodes and decodes [NoFireZone]s to/from GeoJSON `FeatureCollection` text.
 *
 * GeoJSON coordinates are always `[longitude, latitude]`, whereas [LatLon] is
 * latitude-first, so every conversion here explicitly reorders the pair.
 */
object GeoJsonCodec {

    private const val DEFAULT_MARKER_RADIUS_M = 25.0
    private const val DEFAULT_LINE_BUFFER_M = 0.0
    private const val DEFAULT_POLYGON_NAME = "Imported polygon"
    private const val DEFAULT_MARKER_NAME = "Imported marker"
    private const val DEFAULT_LINE_NAME = "Imported line"

    private val prettyJson = Json { prettyPrint = true }

    fun encode(zones: List<NoFireZone>): String {
        val featureCollection = buildJsonObject {
            put("type", "FeatureCollection")
            put(
                "features",
                buildJsonArray {
                    zones.forEach { zone -> add(encodeFeature(zone)) }
                },
            )
        }
        return prettyJson.encodeToString(JsonObject.serializer(), featureCollection)
    }

    private fun encodeFeature(zone: NoFireZone): JsonObject = when (zone) {
        is NoFirePolygon -> buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", "Polygon")
                    put("coordinates", buildJsonArray { add(encodeRing(zone.vertices)) })
                },
            )
            put(
                "properties",
                buildJsonObject {
                    put("name", zone.name)
                    put("type", "no_fire_polygon")
                },
            )
        }
        is NoFireMarker -> buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", "Point")
                    put("coordinates", encodePosition(zone.center))
                },
            )
            put(
                "properties",
                buildJsonObject {
                    put("name", zone.name)
                    put("type", "no_fire_marker")
                    put("radius_m", zone.radiusM)
                },
            )
        }
        is NoFireLine -> buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", "LineString")
                    put(
                        "coordinates",
                        buildJsonArray {
                            zone.vertices.forEach { vertex -> add(encodePosition(vertex)) }
                        },
                    )
                },
            )
            put(
                "properties",
                buildJsonObject {
                    put("name", zone.name)
                    put("type", "no_fire_line")
                    put("buffer_m", zone.bufferM)
                },
            )
        }
    }

    private fun encodeRing(vertices: List<LatLon>): JsonArray {
        val closedRing = if (vertices.isEmpty()) vertices else vertices + vertices.first()
        return buildJsonArray {
            closedRing.forEach { vertex -> add(encodePosition(vertex)) }
        }
    }

    private fun encodePosition(position: LatLon): JsonArray = buildJsonArray {
        add(position.lon)
        add(position.lat)
    }

    fun decode(text: String): List<NoFireZone> {
        val root = Json.parseToJsonElement(text)
        val rootObject = root as? JsonObject
            ?: throw IllegalArgumentException("Invalid GeoJSON: root is not a JSON object")
        val rootType = rootObject["type"]?.jsonPrimitive?.contentOrNull
        val features: List<JsonObject> = when (rootType) {
            "FeatureCollection" -> rootObject["features"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
            "Feature" -> listOf(rootObject)
            else -> throw IllegalArgumentException(
                "Invalid GeoJSON: expected root type FeatureCollection or Feature, was $rootType",
            )
        }
        return features.flatMap { decodeFeature(it) }
    }

    private fun decodeFeature(feature: JsonObject): List<NoFireZone> {
        val geometry = feature["geometry"]?.jsonObject ?: return emptyList()
        val properties = feature["properties"]?.jsonObject
        val name = properties?.get("name")?.jsonPrimitive?.contentOrNull

        return when (geometry["type"]?.jsonPrimitive?.contentOrNull) {
            "Polygon" -> {
                val ring = geometry["coordinates"]?.jsonArray?.firstOrNull()?.jsonArray
                    ?: return emptyList()
                listOf(
                    NoFirePolygon(
                        id = 0,
                        name = name ?: DEFAULT_POLYGON_NAME,
                        vertices = decodeRing(ring),
                    ),
                )
            }
            "MultiPolygon" -> {
                val polygons = geometry["coordinates"]?.jsonArray ?: return emptyList()
                polygons.mapNotNull { polygonElement ->
                    val ring = polygonElement.jsonArray.firstOrNull()?.jsonArray ?: return@mapNotNull null
                    NoFirePolygon(
                        id = 0,
                        name = name ?: DEFAULT_POLYGON_NAME,
                        vertices = decodeRing(ring),
                    )
                }
            }
            "LineString" -> {
                val coordinates = geometry["coordinates"]?.jsonArray ?: return emptyList()
                val bufferM = properties?.get("buffer_m")?.jsonPrimitive?.doubleOrNull
                    ?: DEFAULT_LINE_BUFFER_M
                listOf(
                    NoFireLine(
                        id = 0,
                        name = name ?: DEFAULT_LINE_NAME,
                        vertices = coordinates.map { decodePosition(it) },
                        bufferM = bufferM,
                    ),
                )
            }
            "MultiLineString" -> {
                val lines = geometry["coordinates"]?.jsonArray ?: return emptyList()
                val bufferM = properties?.get("buffer_m")?.jsonPrimitive?.doubleOrNull
                    ?: DEFAULT_LINE_BUFFER_M
                lines.map { lineElement ->
                    NoFireLine(
                        id = 0,
                        name = name ?: DEFAULT_LINE_NAME,
                        vertices = lineElement.jsonArray.map { decodePosition(it) },
                        bufferM = bufferM,
                    )
                }
            }
            "Point" -> {
                val coordinates = geometry["coordinates"]?.jsonArray ?: return emptyList()
                val radiusM = properties?.get("radius_m")?.jsonPrimitive?.doubleOrNull
                    ?: DEFAULT_MARKER_RADIUS_M
                listOf(
                    NoFireMarker(
                        id = 0,
                        name = name ?: DEFAULT_MARKER_NAME,
                        center = decodePosition(coordinates),
                        radiusM = radiusM,
                    ),
                )
            }
            else -> emptyList()
        }
    }

    private fun decodeRing(ring: JsonArray): List<LatLon> {
        val vertices = ring.map { decodePosition(it.jsonArray) }
        return if (vertices.size > 1 && vertices.first() == vertices.last()) {
            vertices.dropLast(1)
        } else {
            vertices
        }
    }

    private fun decodePosition(coordinates: JsonElement): LatLon {
        val position = coordinates.jsonArray
        val lon = position[0].jsonPrimitive.double
        val lat = position[1].jsonPrimitive.double
        return LatLon(lat = lat, lon = lon)
    }
}
