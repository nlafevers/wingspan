package com.wingspan.app.ui.report

import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.ballistics.Units
import com.wingspan.app.ui.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppendixTextTest {

    @Test
    fun `effectiveRange names the binding limiter and mentions the threshold and wind`() {
        val energyText = AppendixText.effectiveRange("energy", 1.5, UnitSystem.IMPERIAL)
        val patternText = AppendixText.effectiveRange("pattern", 1.5, UnitSystem.IMPERIAL)

        val thresholdText = Formatters.energy(Units.ftLbfToJoules(1.5), UnitSystem.IMPERIAL)

        assertTrue(energyText.contains("energy"))
        assertTrue(energyText.contains(thresholdText))
        assertTrue(patternText.contains("pattern"))
        assertTrue(energyText.contains("wind"))
        assertTrue(patternText.contains("wind"))
    }

    @Test
    fun `windApplies is false at zero speed and true otherwise`() {
        assertFalse(AppendixText.windApplies(0.0))
        assertTrue(AppendixText.windApplies(12.0))
    }

    @Test
    fun `wind mentions the formatted speed and buffer`() {
        val text = AppendixText.wind(12.0, 40.0, UnitSystem.IMPERIAL)

        assertTrue(text.contains(Formatters.windSpeed(12.0, UnitSystem.IMPERIAL)))
        assertTrue(text.contains(Formatters.distance(40.0, UnitSystem.IMPERIAL)))
    }

    @Test
    fun `positionLine for a GPS fix mentions GPS, accuracy and signed declination`() {
        val text = AppendixText.positionLine("P1", "GPS", 4.0, -7.3, UnitSystem.METRIC)

        assertTrue(text.contains("P1"))
        assertTrue(text.contains("GPS"))
        assertTrue(text.contains(Formatters.distance(4.0, UnitSystem.METRIC)))
        assertTrue(text.contains("-7.3"))
    }

    @Test
    fun `positionLine for a GPS fix with no accuracy says so without printing null or zero`() {
        val text = AppendixText.positionLine("P2", "GPS", null, 1.0, UnitSystem.METRIC)

        assertTrue(text.contains("accuracy not recorded"))
        assertFalse(text.contains("null"))
        assertFalse(text.contains("0 m"))
    }

    @Test
    fun `positionLine for a manual position says so and omits GPS`() {
        val text = AppendixText.positionLine("P3", "MANUAL", null, 1.0, UnitSystem.METRIC)

        assertTrue(text.contains("manual"))
        assertFalse(text.contains("GPS"))
    }
}
