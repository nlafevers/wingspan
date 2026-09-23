package com.wingspan.app.domain.ballistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RangeCalculatorTest {

    private val leadDensity = 11.34
    private val steelDensity = 7.86

    private data class ShotSize(val label: String, val diameterInches: Double)

    private val shotSizes = listOf(
        ShotSize("#9", 0.080),
        ShotSize("#6", 0.110),
        ShotSize("#2", 0.150),
        ShotSize("BB", 0.180),
        ShotSize("F", 0.220),
    )

    @Test
    fun `max range matches Journee's rule within 12 percent for lead shot at 1300 fps`() {
        for (shot in shotSizes) {
            val input = BallisticInput(
                diameterInches = shot.diameterInches,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1300.0,
                choke = Choke.MODIFIED,
                energyThresholdFtLbf = 1.5,
            )
            val result = RangeCalculator.compute(input)
            val actualYards = Units.metersToYards(result.maxRangeM)
            val targetYards = 2200 * shot.diameterInches
            val tolerance = 0.12 * targetYards
            assertTrue(
                "Shot ${shot.label}: expected ~$targetYards yd (+-12%), got $actualYards yd",
                abs(actualYards - targetYards) <= tolerance,
            )
        }
    }

    @Test
    fun `steel BB has smaller max range than lead BB`() {
        val leadBB = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.180,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1300.0,
                choke = Choke.MODIFIED,
                energyThresholdFtLbf = 1.5,
            ),
        )
        val steelBB = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.180,
                densityGcc = steelDensity,
                muzzleVelocityFps = 1450.0,
                choke = Choke.MODIFIED,
                energyThresholdFtLbf = 1.5,
            ),
        )
        assertTrue(
            "Expected steel BB max range (${steelBB.maxRangeM}) < lead BB max range (${leadBB.maxRangeM})",
            steelBB.maxRangeM < leadBB.maxRangeM,
        )
    }

    @Test
    fun `optimal angle for number 2 lead is between 20 and 30 degrees`() {
        val result = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.150,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1300.0,
                choke = Choke.MODIFIED,
                energyThresholdFtLbf = 1.5,
            ),
        )
        assertTrue(
            "Expected optimalAngleDeg between 20 and 30, got ${result.optimalAngleDeg}",
            result.optimalAngleDeg in 20.0..30.0,
        )
    }

    @Test
    fun `number 8 lead with full choke has effective range capped near 40 yards`() {
        val result = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.090,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1200.0,
                choke = Choke.FULL,
                energyThresholdFtLbf = 1.5,
            ),
        )
        assertTrue(
            "Expected effectiveRangeM <= 40 yards, got ${result.effectiveRangeM}",
            result.effectiveRangeM <= Units.yardsToMeters(40.0),
        )
        assertTrue(
            "Expected effectiveRangeM <= maxRangeM",
            result.effectiveRangeM <= result.maxRangeM,
        )
    }

    @Test
    fun `BB lead with cylinder choke is pattern limited`() {
        val result = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.180,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1300.0,
                choke = Choke.CYLINDER,
                energyThresholdFtLbf = 1.5,
            ),
        )
        assertEquals("pattern", result.effectiveRangeLimiter)
    }

    @Test
    fun `zero wind speed yields zero wind buffer and fanRangeM equal to maxRangeM`() {
        val result = RangeCalculator.compute(
            BallisticInput(
                diameterInches = 0.110,
                densityGcc = leadDensity,
                muzzleVelocityFps = 1250.0,
                choke = Choke.MODIFIED,
                energyThresholdFtLbf = 1.5,
            ),
        )
        assertEquals(0.0, result.windBufferM, 1e-9)
        assertEquals(result.maxRangeM, result.fanRangeM, 1e-9)
    }

    @Test
    fun `20 mph wind increases the wind buffer without changing range or energy results`() {
        val noWindInput = BallisticInput(
            diameterInches = 0.110,
            densityGcc = leadDensity,
            muzzleVelocityFps = 1250.0,
            choke = Choke.MODIFIED,
            energyThresholdFtLbf = 1.5,
        )
        val windInput = noWindInput.copy(windSpeedMps = 8.94)

        val noWindResult = RangeCalculator.compute(noWindInput)
        val windResult = RangeCalculator.compute(windInput)

        assertTrue("Expected windBufferM > 0, got ${windResult.windBufferM}", windResult.windBufferM > 0.0)
        assertTrue(
            "Expected fanRangeM (${windResult.fanRangeM}) > maxRangeM (${windResult.maxRangeM})",
            windResult.fanRangeM > windResult.maxRangeM,
        )
        assertEquals(noWindResult.maxRangeM, windResult.maxRangeM, 1e-9)
        assertEquals(noWindResult.effectiveRangeM, windResult.effectiveRangeM, 1e-9)
        assertEquals(noWindResult.muzzleEnergyJ, windResult.muzzleEnergyJ, 1e-9)
    }
}
