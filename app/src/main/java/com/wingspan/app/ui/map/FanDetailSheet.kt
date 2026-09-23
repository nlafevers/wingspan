package com.wingspan.app.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wingspan.app.ui.Formatters
import kotlin.math.roundToInt

/**
 * Bottom sheet shown when a range fan is tapped: its magnetic field-of-fire limits, the ranges
 * and buffers it was computed against, and where the shooter's position came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanDetailSheet(state: FanUiState, fan: FanView, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Fan detail", style = MaterialTheme.typography.titleMedium)

            if (fan.fan.fullCircle) {
                Text("Clear in all directions")
            } else {
                Text("Left limit: ${Formatters.bearing(fan.leftMagDeg)}")
                Text("Right limit: ${Formatters.bearing(fan.rightMagDeg)}")
                val widthDeg = ((fan.rightMagDeg - fan.leftMagDeg) % 360.0 + 360.0) % 360.0
                Text("Width: ${Formatters.angle(widthDeg)}")
            }

            Text("Maximum range: ${Formatters.distance(state.range.maxRangeM, state.units)}")
            Text("Wind buffer: +${Formatters.distance(state.range.windBufferM, state.units)}")
            Text("Fan radius: ${Formatters.distance(state.range.fanRangeM, state.units)}")

            val limiter = state.range.effectiveRangeLimiter.lowercase()
            Text(
                "Effective range: ${Formatters.distance(state.range.effectiveRangeM, state.units)} " +
                    "($limiter-limited)"
            )

            Text(
                "Magnetic declination: ${"%+.1f".format(state.declinationDeg)}° " +
                    "(magnetic = true − declination)"
            )

            val positionText = when {
                state.source == PositionSource.GPS && state.accuracyM != null ->
                    "Position: GPS ±${state.accuracyM.roundToInt()} m"
                state.source == PositionSource.GPS -> "Position: GPS"
                else -> "Position: manual"
            }
            Text(positionText)
        }
    }
}
