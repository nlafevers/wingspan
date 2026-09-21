package com.wingspan.app.domain.ballistics

enum class PelletMaterial(val label: String, val densityGcc: Double) {
    LEAD("Lead", 11.34),
    STEEL("Steel", 7.86),
    BISMUTH("Bismuth", 9.6),
    TUNGSTEN("Tungsten (TSS)", 18.0),
    CUSTOM("Custom", 0.0),
}
