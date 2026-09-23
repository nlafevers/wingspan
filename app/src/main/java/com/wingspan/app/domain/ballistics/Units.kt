package com.wingspan.app.domain.ballistics

object Units {
    const val YARD_M = 0.9144
    const val FOOT_M = 0.3048
    const val INCH_M = 0.0254
    const val FTLBF_J = 1.3558179483
    const val GCC_TO_KGM3 = 1000.0
    const val MPH_MPS = 0.44704

    fun fpsToMps(fps: Double): Double = fps * FOOT_M

    fun mpsToFps(mps: Double): Double = mps / FOOT_M

    fun mphToMps(mph: Double): Double = mph * MPH_MPS

    fun mpsToMph(mps: Double): Double = mps / MPH_MPS

    fun metersToYards(m: Double): Double = m / YARD_M

    fun yardsToMeters(yd: Double): Double = yd * YARD_M

    fun joulesToFtLbf(j: Double): Double = j / FTLBF_J

    fun ftLbfToJoules(ftlbf: Double): Double = ftlbf * FTLBF_J
}
