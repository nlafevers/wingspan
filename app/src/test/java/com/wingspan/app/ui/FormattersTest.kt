package com.wingspan.app.ui

import com.wingspan.app.domain.ballistics.UnitSystem.IMPERIAL
import com.wingspan.app.domain.ballistics.UnitSystem.METRIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun distanceImperial() {
        assertEquals("328 yd", Formatters.distance(300.0, IMPERIAL))
    }

    @Test
    fun distanceMetric() {
        assertEquals("300 m", Formatters.distance(300.0, METRIC))
    }

    @Test
    fun bearingRoundsAndZeroPads() {
        assertEquals("057° M", Formatters.bearing(57.4))
    }

    @Test
    fun bearingWrapsAtThreeSixty() {
        assertEquals("000° M", Formatters.bearing(359.6))
    }

    @Test
    fun velocityInputToFpsConvertsFromMetric() {
        val fps = Formatters.velocityInputToFps("400", METRIC)
        assertEquals(1312.3, fps!!, 0.1)
    }

    @Test
    fun velocityInputToFpsReturnsNullForInvalidInput() {
        assertNull(Formatters.velocityInputToFps("abc", IMPERIAL))
    }

    @Test
    fun windSpeedImperial() {
        assertEquals("20 mph", Formatters.windSpeed(20.0, IMPERIAL))
    }

    @Test
    fun windSpeedMetric() {
        assertEquals("9 m/s", Formatters.windSpeed(20.0, METRIC))
    }

    @Test
    fun windInputToMphConvertsFromMetric() {
        val mph = Formatters.windInputToMph("10", METRIC)
        assertEquals(22.37, mph!!, 0.1)
    }

    @Test
    fun windInputToMphReturnsNullForEmptyInput() {
        assertNull(Formatters.windInputToMph("", IMPERIAL))
    }
}
