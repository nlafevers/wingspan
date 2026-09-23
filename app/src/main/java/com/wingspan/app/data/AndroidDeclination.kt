package com.wingspan.app.data

import android.hardware.GeomagneticField
import com.wingspan.app.domain.geo.DeclinationProvider
import com.wingspan.app.domain.geo.LatLon

class AndroidDeclination : DeclinationProvider {
    override fun declinationDeg(position: LatLon): Double =
        GeomagneticField(
            position.lat.toFloat(),
            position.lon.toFloat(),
            0f,
            System.currentTimeMillis(),
        ).declination.toDouble()
}
