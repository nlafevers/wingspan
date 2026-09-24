package com.wingspan.app.domain.geo

import kotlinx.serialization.Serializable

@Serializable
data class LatLon(val lat: Double, val lon: Double)
