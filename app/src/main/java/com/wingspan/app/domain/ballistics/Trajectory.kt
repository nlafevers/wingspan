package com.wingspan.app.domain.ballistics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object Trajectory {

    private const val MAX_STEPS = 200_000
    private const val MAX_RANGE_M = 3000.0

    private data class State(val x: Double, val y: Double, val vx: Double, val vy: Double)

    private data class Derivative(val dx: Double, val dy: Double, val dvx: Double, val dvy: Double)

    data class TrajectoryResult(val rangeM: Double, val timeOfFlightS: Double)

    private fun derivative(state: State, k: Double, tailwindMps: Double): Derivative {
        val vrelX = state.vx - tailwindMps
        val vrelY = state.vy
        val v = hypot(vrelX, vrelY)
        val mach = v / DragModel.SPEED_OF_SOUND_MPS
        val cd = DragModel.dragCoefficient(mach)
        val ax = -k * cd * v * vrelX
        val ay = -k * cd * v * vrelY - DragModel.GRAVITY_MPS2
        return Derivative(dx = state.vx, dy = state.vy, dvx = ax, dvy = ay)
    }

    private fun step(state: State, dt: Double, k: Double, tailwindMps: Double): State {
        val k1 = derivative(state, k, tailwindMps)
        val s2 = State(
            x = state.x + k1.dx * dt / 2.0,
            y = state.y + k1.dy * dt / 2.0,
            vx = state.vx + k1.dvx * dt / 2.0,
            vy = state.vy + k1.dvy * dt / 2.0,
        )
        val k2 = derivative(s2, k, tailwindMps)
        val s3 = State(
            x = state.x + k2.dx * dt / 2.0,
            y = state.y + k2.dy * dt / 2.0,
            vx = state.vx + k2.dvx * dt / 2.0,
            vy = state.vy + k2.dvy * dt / 2.0,
        )
        val k3 = derivative(s3, k, tailwindMps)
        val s4 = State(
            x = state.x + k3.dx * dt,
            y = state.y + k3.dy * dt,
            vx = state.vx + k3.dvx * dt,
            vy = state.vy + k3.dvy * dt,
        )
        val k4 = derivative(s4, k, tailwindMps)

        return State(
            x = state.x + (dt / 6.0) * (k1.dx + 2 * k2.dx + 2 * k3.dx + k4.dx),
            y = state.y + (dt / 6.0) * (k1.dy + 2 * k2.dy + 2 * k3.dy + k4.dy),
            vx = state.vx + (dt / 6.0) * (k1.dvx + 2 * k2.dvx + 2 * k3.dvx + k4.dvx),
            vy = state.vy + (dt / 6.0) * (k1.dvy + 2 * k2.dvy + 2 * k3.dvy + k4.dvy),
        )
    }

    fun simulate(
        pellet: Pellet,
        muzzleVelocityMps: Double,
        launchAngleDeg: Double,
        tailwindMps: Double = 0.0,
        dtSeconds: Double = 0.002,
    ): TrajectoryResult {
        val k = pellet.dragFactor
        val angleRad = launchAngleDeg * PI / 180.0
        var state = State(
            x = 0.0,
            y = 0.0,
            vx = muzzleVelocityMps * cos(angleRad),
            vy = muzzleVelocityMps * sin(angleRad),
        )

        var steps = 0
        while (steps < MAX_STEPS) {
            val next = step(state, dtSeconds, k, tailwindMps)
            if (next.y < 0.0) {
                val t = state.y / (state.y - next.y)
                val rangeM = state.x + t * (next.x - state.x)
                val timeOfFlightS = steps * dtSeconds + t * dtSeconds
                return TrajectoryResult(rangeM = rangeM, timeOfFlightS = timeOfFlightS)
            }
            state = next
            steps++
        }
        return TrajectoryResult(rangeM = state.x, timeOfFlightS = steps * dtSeconds)
    }

    fun horizontalRangeM(
        pellet: Pellet,
        muzzleVelocityMps: Double,
        launchAngleDeg: Double,
        dtSeconds: Double = 0.002,
    ): Double = simulate(pellet, muzzleVelocityMps, launchAngleDeg, 0.0, dtSeconds).rangeM

    fun flatFireDistanceToEnergyM(
        pellet: Pellet,
        muzzleVelocityMps: Double,
        energyThresholdJ: Double,
        dtSeconds: Double = 0.0005,
    ): Double {
        val k = pellet.dragFactor
        val m = pellet.massKg
        var v = muzzleVelocityMps
        var x = 0.0

        if (0.5 * m * v * v <= energyThresholdJ) {
            return 0.0
        }

        var steps = 0
        while (0.5 * m * v * v > energyThresholdJ && steps < MAX_STEPS && x < MAX_RANGE_M) {
            val mach = v / DragModel.SPEED_OF_SOUND_MPS
            val cd = DragModel.dragCoefficient(mach)
            v -= k * cd * v * v * dtSeconds
            x += v * dtSeconds
            steps++
        }
        return x.coerceAtMost(MAX_RANGE_M)
    }
}
