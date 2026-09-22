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
}
