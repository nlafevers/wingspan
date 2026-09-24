package com.wingspan.app.data.map

enum class Basemap(
    val key: String,
    val label: String,
    val assetFile: String,
    val maxZoom: Int,
) {
    USGS_TOPO("USGS_TOPO", "Topo", "usgs_topo.json", 16),
    USGS_IMAGERY("USGS_IMAGERY", "USGS Imagery", "usgs_imagery.json", 16),
    ESRI_IMAGERY("ESRI_IMAGERY", "Esri Imagery", "esri_imagery.json", 19),
    ;

    val assetPath: String
        get() = "styles/$assetFile"

    /**
     * Used only by offline downloads; MapLibre's offline downloader requires a style URL it can
     * fetch over HTTP. This points at the in-process [LocalStyleServer] (WS-7.4) rather than
     * GitHub. [port] must be `LocalStyleServer.port` read after the server has been started via
     * `startServing()`.
     */
    fun remoteStyleUrl(port: Int): String = "http://127.0.0.1:$port/$assetPath"

    companion object {
        fun fromKey(key: String?): Basemap = entries.firstOrNull { it.key == key } ?: USGS_TOPO
    }
}
