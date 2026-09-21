package com.wingspan.app.domain.ballistics

enum class ShotSize(val label: String, val diameterInches: Double) {
    NO_9("#9", 0.080),
    NO_8_5("#8½", 0.085),
    NO_8("#8", 0.090),
    NO_7_5("#7½", 0.095),
    NO_7("#7", 0.100),
    NO_6("#6", 0.110),
    NO_5("#5", 0.120),
    NO_4("#4", 0.130),
    NO_3("#3", 0.140),
    NO_2("#2", 0.150),
    NO_1("#1", 0.160),
    B("B", 0.170),
    BB("BB", 0.180),
    BBB("BBB", 0.190),
    T("T", 0.200),
    F("F", 0.220),
    CUSTOM("Custom", 0.0),
}
