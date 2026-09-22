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

    /** Used only by offline downloads; MapLibre's offline downloader requires an https:// style URL. */
    val remoteStyleUrl: String
        get() = "https://raw.githubusercontent.com/nlafevers/wingspan/main/app/src/main/assets/styles/$assetFile"

    companion object {
        fun fromKey(key: String?): Basemap = entries.firstOrNull { it.key == key } ?: USGS_TOPO
    }
}
