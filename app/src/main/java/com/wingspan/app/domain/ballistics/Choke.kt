package com.wingspan.app.domain.ballistics

enum class Choke(val label: String, val patternRangeYards: Double) {
    CYLINDER("Cylinder", 25.0),
    SKEET("Skeet", 25.0),
    IMPROVED_CYLINDER("Improved Cylinder", 30.0),
    LIGHT_MODIFIED("Light Modified", 33.0),
    MODIFIED("Modified", 35.0),
    IMPROVED_MODIFIED("Improved Modified", 38.0),
    FULL("Full", 40.0),
    EXTRA_FULL("Extra Full", 45.0),
}
