package com.wingspan.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.map.Basemap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val basemap: StateFlow<Basemap> = settingsRepository.basemapKey
        .map(Basemap::fromKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Basemap.USGS_TOPO)

    fun setBasemap(b: Basemap) {
        viewModelScope.launch { settingsRepository.setBasemapKey(b.key) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MapViewModel(container.settingsRepository) }
        }
    }
}
