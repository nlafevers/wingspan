package com.wingspan.app.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.map.OfflineRegionInfo
import com.wingspan.app.data.map.OfflineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OfflineViewModel(private val repo: OfflineRepository) : ViewModel() {

    val regions = MutableStateFlow<List<OfflineRegionInfo>>(emptyList())
    val activeDownloads = repo.activeDownloads

    init {
        refresh()
        viewModelScope.launch {
            activeDownloads.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            regions.value = repo.list()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            refresh()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OfflineViewModel(container.offlineRepository)
            }
        }
    }
}
