package com.wingspan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.domain.ballistics.Choke
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.PelletMaterial
import com.wingspan.app.domain.ballistics.RangeCalculator
import com.wingspan.app.domain.ballistics.RangeResult
import com.wingspan.app.domain.ballistics.ShotSize
import com.wingspan.app.domain.ballistics.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<LoadSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val preview: StateFlow<RangeResult?> = settings.mapLatest {
        withContext(Dispatchers.Default) {
            // A custom diameter/density of exactly zero (or negative) fails Pellet's own
            // validation. That shouldn't reach here anymore now that the custom fields reject
            // non-positive input, but this is cheap insurance against ANY other path producing
            // one: hide the preview rather than crash on every recomposition (and, since this
            // reads persisted settings, on every future launch too).
            runCatching { RangeCalculator.compute(it.toBallisticInput()) }.getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setShotSize(shotSize: ShotSize) {
        viewModelScope.launch { settingsRepository.update { it.copy(shotSize = shotSize) } }
    }

    fun setMaterial(material: PelletMaterial) {
        viewModelScope.launch { settingsRepository.update { it.copy(material = material) } }
    }

    fun setCustomDiameterInches(diameterInches: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(customDiameterInches = diameterInches) } }
    }

    fun setCustomDensityGcc(densityGcc: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(customDensityGcc = densityGcc) } }
    }

    fun setMuzzleVelocityFps(muzzleVelocityFps: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(muzzleVelocityFps = muzzleVelocityFps) } }
    }

    fun setChoke(choke: Choke) {
        viewModelScope.launch { settingsRepository.update { it.copy(choke = choke) } }
    }

    fun setEnergyThresholdFtLbf(energyThresholdFtLbf: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(energyThresholdFtLbf = energyThresholdFtLbf) } }
    }

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch { settingsRepository.update { it.copy(unitSystem = unitSystem) } }
    }

    fun setWindSpeedMph(windSpeedMph: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(windSpeedMph = windSpeedMph) } }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(container.settingsRepository)
            }
        }
    }
}
