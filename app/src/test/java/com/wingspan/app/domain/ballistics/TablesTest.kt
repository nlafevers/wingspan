package com.wingspan.app.domain.ballistics

import org.junit.Assert.assertEquals
import org.junit.Test

class TablesTest {

    @Test
    fun shotSizeDiameters() {
        assertEquals(0.150, ShotSize.NO_2.diameterInches, 1e-9)
        assertEquals(17, ShotSize.entries.size)
    }

    @Test
    fun pelletMaterialDensity() {
        assertEquals(11.34, PelletMaterial.LEAD.densityGcc, 1e-9)
    }

    @Test
    fun chokePatternRange() {
        assertEquals(40.0, Choke.FULL.patternRangeYards, 1e-9)
    }

    @Test
    fun unitConversions() {
        assertEquals(304.8, Units.fpsToMps(1000.0), 1e-6)
        assertEquals(100.0, Units.metersToYards(Units.yardsToMeters(100.0)), 1e-9)
    }
}
