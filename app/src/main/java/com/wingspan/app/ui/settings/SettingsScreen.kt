package com.wingspan.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingspan.app.appContainer
import com.wingspan.app.domain.ballistics.Choke
import com.wingspan.app.domain.ballistics.PelletMaterial
import com.wingspan.app.domain.ballistics.ShotSize
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.ui.Formatters

private fun <T : Enum<T>> enumLabel(value: T): String =
    value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(LocalContext.current.appContainer())
    ),
) {
    val settings by viewModel.settings.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val units = settings.unitSystem
    val velocitySuffix = if (units == UnitSystem.IMPERIAL) "fps" else "m/s"
    val windSuffix = if (units == UnitSystem.IMPERIAL) "mph" else "m/s"

    var diameterText by remember(settings.customDiameterInches) {
        mutableStateOf(settings.customDiameterInches.toString())
    }
    var densityText by remember(settings.customDensityGcc) {
        mutableStateOf(settings.customDensityGcc.toString())
    }
    var velocityText by remember(settings.muzzleVelocityFps, units) {
        mutableStateOf(Formatters.fpsToVelocityInput(settings.muzzleVelocityFps, units))
    }
    var energyThresholdText by remember(settings.energyThresholdFtLbf) {
        mutableStateOf(settings.energyThresholdFtLbf.toString())
    }
    var windText by remember(settings.windSpeedMph, units) {
        mutableStateOf(Formatters.mphToWindInput(settings.windSpeedMph, units))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Load settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            EnumDropdown(
                label = "Shot size",
                options = ShotSize.values().toList(),
                selected = settings.shotSize,
                labelOf = ::enumLabel,
                onSelect = viewModel::setShotSize,
            )

            if (settings.shotSize == ShotSize.CUSTOM) {
                NumericField(
                    label = "Pellet diameter (in)",
                    value = diameterText,
                    onValueChange = { text ->
                        diameterText = text
                        text.toDoubleOrNull()?.let { viewModel.setCustomDiameterInches(it) }
                    },
                )
            }

            EnumDropdown(
                label = "Pellet material",
                options = PelletMaterial.values().toList(),
                selected = settings.material,
                labelOf = ::enumLabel,
                onSelect = viewModel::setMaterial,
            )

            if (settings.material == PelletMaterial.CUSTOM) {
                NumericField(
                    label = "Pellet density (g/cc)",
                    value = densityText,
                    onValueChange = { text ->
                        densityText = text
                        text.toDoubleOrNull()?.let { viewModel.setCustomDensityGcc(it) }
                    },
                )
            }

            EnumDropdown(
                label = "Choke",
                options = Choke.values().toList(),
                selected = settings.choke,
                labelOf = ::enumLabel,
                onSelect = viewModel::setChoke,
            )

            NumericField(
                label = "Muzzle velocity",
                value = velocityText,
                onValueChange = { text ->
                    velocityText = text
                    Formatters.velocityInputToFps(text, units)?.let { viewModel.setMuzzleVelocityFps(it) }
                },
                suffix = velocitySuffix,
            )

            NumericField(
                label = "Minimum pellet energy (ft·lbf)",
                value = energyThresholdText,
                onValueChange = { text ->
                    energyThresholdText = text
                    text.toDoubleOrNull()?.let { viewModel.setEnergyThresholdFtLbf(it) }
                },
            )

            NumericField(
                label = "Wind speed",
                value = windText,
                onValueChange = { text ->
                    windText = text
                    Formatters.windInputToMph(text, units)?.let { viewModel.setWindSpeedMph(it) }
                },
                suffix = windSuffix,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                UnitSystem.values().forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = UnitSystem.values().size),
                        selected = units == option,
                        onClick = { viewModel.setUnitSystem(option) },
                    ) {
                        Text(if (option == UnitSystem.IMPERIAL) "Imperial" else "Metric")
                    }
                }
            }

            preview?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Computed ranges",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Maximum range: ${Formatters.distance(result.maxRangeM, units)} at " +
                                "${Formatters.angle(result.optimalAngleDeg)} launch angle",
                        )
                        Text(
                            "Effective range: ${Formatters.distance(result.effectiveRangeM, units)} " +
                                "(${result.effectiveRangeLimiter}-limited)",
                        )
                        Text(
                            "Muzzle energy per pellet: ${Formatters.energy(result.muzzleEnergyJ, units)}",
                        )
                        Text(
                            "Wind buffer: +${Formatters.distance(result.windBufferM, units)}",
                        )
                        Text(
                            "Fan radius: ${Formatters.distance(result.fanRangeM, units)}",
                        )
                        Text(
                            "Fans are drawn at the fan radius and every no-fire zone is widened by the wind buffer",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
