package com.wingspan.app.domain.ballistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectoryTest {

    @Test
    fun dragCoefficientInterpolatesAndClamps() {
        assertEquals(0.47, DragModel.dragCoefficient(0.3), 1e-9)
        assertEquals(0.875, DragModel.dragCoefficient(1.05), 1e-9)
        assertEquals(0.92, DragModel.dragCoefficient(5.0), 1e-9)
    }

    @Test
    fun horizontalRangePeaksNearOptimalAngle() {
        val pellet = Pellet.of(0.150, 11.34)
        val muzzleVelocityMps = 396.24

        val range25 = Trajectory.horizontalRangeM(pellet, muzzleVelocityMps, 25.0)
        val range5 = Trajectory.horizontalRangeM(pellet, muzzleVelocityMps, 5.0)
        val range60 = Trajectory.horizontalRangeM(pellet, muzzleVelocityMps, 60.0)

        assertTrue("range at 25deg ($range25) should exceed range at 5deg ($range5)", range25 > range5)
        assertTrue("range at 25deg ($range25) should exceed range at 60deg ($range60)", range25 > range60)
    }

    @Test
    fun flatFireDistanceReturnsZeroWhenAlreadyBelowThreshold() {
        val pellet = Pellet.of(0.150, 11.34)
        val muzzleVelocityMps = 396.24
        val muzzleEnergyJ = 0.5 * pellet.massKg * muzzleVelocityMps * muzzleVelocityMps

        val distance = Trajectory.flatFireDistanceToEnergyM(
            pellet,
            muzzleVelocityMps,
            energyThresholdJ = muzzleEnergyJ * 2.0,
        )

        assertEquals(0.0, distance, 1e-9)
    }

    @Test
    fun flatFireDistanceToEnergyMForNo2LeadIsInExpectedRange() {
        val pellet = Pellet.of(0.150, 11.34)
        val muzzleVelocityMps = 396.24

        val distance = Trajectory.flatFireDistanceToEnergyM(
            pellet,
            muzzleVelocityMps,
            energyThresholdJ = 2.03,
        )

        assertTrue("distance ($distance) should be > 90 m", distance > 90.0)
        assertTrue("distance ($distance) should be < 130 m", distance < 130.0)
    }

    @Test
    fun simulateWithNoWindMatchesHorizontalRangeM() {
        val pellet = Pellet.of(0.110, 11.34)
        val muzzleVelocityMps = Units.fpsToMps(1250.0)

        val simulated = Trajectory.simulate(pellet, muzzleVelocityMps, 30.0).rangeM
        val expected = Trajectory.horizontalRangeM(pellet, muzzleVelocityMps, 30.0)

        assertEquals(expected, simulated, 1e-9)
    }

    @Test
    fun tailwindIncreasesRange() {
        val pellet = Pellet.of(0.110, 11.34)
        val muzzleVelocityMps = Units.fpsToMps(1250.0)

        val noWindRangeM = Trajectory.simulate(pellet, muzzleVelocityMps, 30.0, tailwindMps = 0.0).rangeM
        val tailwindRangeM = Trajectory.simulate(pellet, muzzleVelocityMps, 30.0, tailwindMps = 10.0).rangeM

        assertTrue(
            "Expected tailwind range ($tailwindRangeM) > no-wind range ($noWindRangeM)",
            tailwindRangeM > noWindRangeM,
        )
    }

    @Test
    fun timeOfFlightIsPositiveAndIncreasesWithLaunchAngle() {
        val pellet = Pellet.of(0.110, 11.34)
        val muzzleVelocityMps = Units.fpsToMps(1250.0)

        val timeOfFlight20 = Trajectory.simulate(pellet, muzzleVelocityMps, 20.0).timeOfFlightS
        val timeOfFlight40 = Trajectory.simulate(pellet, muzzleVelocityMps, 40.0).timeOfFlightS

        assertTrue("Expected timeOfFlight20 ($timeOfFlight20) > 0", timeOfFlight20 > 0.0)
        assertTrue(
            "Expected timeOfFlight40 ($timeOfFlight40) > timeOfFlight20 ($timeOfFlight20)",
            timeOfFlight40 > timeOfFlight20,
        )
    }
}
