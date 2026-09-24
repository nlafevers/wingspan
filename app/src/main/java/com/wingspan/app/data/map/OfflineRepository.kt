package com.wingspan.app.data.map

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class RegionMetadata(
    val name: String,
    val basemapKey: String,
    val createdAtMs: Long,
)

data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val basemap: Basemap,
    val createdAtMs: Long,
    val completedTiles: Long,
    val requiredTiles: Long,
    val completedBytes: Long,
    val isComplete: Boolean,
)

data class DownloadProgress(
    val regionId: Long,
    val completedTiles: Long,
    val requiredTiles: Long,
    val completedBytes: Long,
    val isComplete: Boolean,
    val error: String?,
)

class OfflineRepository(context: Context) {
    private val manager = OfflineManager.getInstance(context)
    val activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())

    init {
        manager.setMaximumAmbientCacheSize(200L * 1024 * 1024, null)
    }

    suspend fun list(): List<OfflineRegionInfo> {
        val regions = suspendCancellableCoroutine<Array<OfflineRegion>> { continuation ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    continuation.resume(offlineRegions ?: emptyArray())
                }

                override fun onError(error: String) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }

        return regions.map { region ->
            val status = suspendCancellableCoroutine<OfflineRegionStatus> { continuation ->
                region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        continuation.resume(requireNotNull(status))
                    }

                    override fun onError(error: String?) {
                        continuation.resumeWithException(IllegalStateException(error))
                    }
                })
            }
            val metadata = runCatching {
                Json.decodeFromString<RegionMetadata>(region.metadata.decodeToString())
            }.getOrNull()

            OfflineRegionInfo(
                id = region.id,
                name = metadata?.name ?: "Region ${region.id}",
                basemap = Basemap.fromKey(metadata?.basemapKey),
                createdAtMs = metadata?.createdAtMs ?: 0L,
                completedTiles = status.completedTileCount,
                requiredTiles = status.requiredResourceCount,
                completedBytes = status.completedResourceSize,
                isComplete = status.isComplete,
            )
        }
    }

    fun startDownload(
        name: String,
        basemap: Basemap,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        minZoom: Int,
        maxZoom: Int,
        pixelRatio: Float,
    ) {
        val definition = OfflineTilePyramidRegionDefinition(
            basemap.remoteStyleUrl,
            LatLngBounds.from(north, east, south, west),
            minZoom.toDouble(),
            maxZoom.toDouble(),
            pixelRatio,
        )
        val metadataBytes = Json.encodeToString(
            RegionMetadata(name = name, basemapKey = basemap.key, createdAtMs = System.currentTimeMillis()),
        ).encodeToByteArray()

        manager.createOfflineRegion(
            definition,
            metadataBytes,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            activeDownloads.update {
                                it + (offlineRegion.id to DownloadProgress(
                                    regionId = offlineRegion.id,
                                    completedTiles = status.completedTileCount,
                                    requiredTiles = status.requiredResourceCount,
                                    completedBytes = status.completedResourceSize,
                                    isComplete = status.isComplete,
                                    error = null,
                                ))
                            }
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                activeDownloads.update { it - offlineRegion.id }
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            activeDownloads.update {
                                it + (offlineRegion.id to DownloadProgress(
                                    regionId = offlineRegion.id,
                                    completedTiles = 0,
                                    requiredTiles = 0,
                                    completedBytes = 0,
                                    isComplete = false,
                                    error = error.message,
                                ))
                            }
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            activeDownloads.update { it - offlineRegion.id }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            activeDownloads.update {
                                it + (offlineRegion.id to DownloadProgress(
                                    regionId = offlineRegion.id,
                                    completedTiles = 0,
                                    requiredTiles = 0,
                                    completedBytes = 0,
                                    isComplete = false,
                                    error = "Tile count limit of $limit exceeded",
                                ))
                            }
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            activeDownloads.update { it - offlineRegion.id }
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    // No region was created, so there is nothing to track in activeDownloads.
                }
            },
        )
    }

    suspend fun delete(id: Long) {
        val regions = suspendCancellableCoroutine<Array<OfflineRegion>> { continuation ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    continuation.resume(offlineRegions ?: emptyArray())
                }

                override fun onError(error: String) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }
        val region = regions.firstOrNull { it.id == id } ?: return

        suspendCancellableCoroutine<Unit> { continuation ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    continuation.resume(Unit)
                }

                override fun onError(error: String) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }
    }
}
