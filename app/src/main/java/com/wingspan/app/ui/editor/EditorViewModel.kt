package com.wingspan.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.ZoneRepository
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.ui.map.TapHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(private val zoneRepository: ZoneRepository) : ViewModel() {

    sealed interface EditorMode {
        data object Idle : EditorMode

        data class PolygonEdit(
            val zoneId: Long?,
            val name: String,
            val vertices: List<LatLon>,
            val selectedIndex: Int?,
        ) : EditorMode

        data class MarkerEdit(
            val zoneId: Long?,
            val name: String,
            val center: LatLon?,
            val radiusM: Double,
        ) : EditorMode

        data class LineEdit(
            val zoneId: Long?,
            val name: String,
            val vertices: List<LatLon>,
            val selectedIndex: Int?,
            val bufferM: Double,
        ) : EditorMode
    }

    val mode = MutableStateFlow<EditorMode>(EditorMode.Idle)
    val showNameDialog = MutableStateFlow(false)
    val selectedZone = MutableStateFlow<NoFireZone?>(null)
    val showRenameDialog = MutableStateFlow(false)

    val render: StateFlow<EditorRender?> = mode.map { m ->
        when (m) {
            is EditorMode.Idle -> null
            is EditorMode.PolygonEdit -> EditorGeometry.polygonRender(m.vertices, m.selectedIndex)
            is EditorMode.MarkerEdit -> EditorGeometry.markerRender(m.center, m.radiusM)
            is EditorMode.LineEdit -> EditorGeometry.lineRender(m.vertices, m.selectedIndex)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canFinish: StateFlow<Boolean> = mode.map { m ->
        when (m) {
            is EditorMode.Idle -> false
            is EditorMode.PolygonEdit -> m.vertices.size >= 3
            is EditorMode.LineEdit -> m.vertices.size >= 2
            is EditorMode.MarkerEdit -> m.center != null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startPolygon() {
        mode.value = EditorMode.PolygonEdit(zoneId = null, name = "", vertices = emptyList(), selectedIndex = null)
    }

    fun startMarker() {
        mode.value = EditorMode.MarkerEdit(zoneId = null, name = "", center = null, radiusM = 25.0)
    }

    fun startLine() {
        mode.value = EditorMode.LineEdit(
            zoneId = null,
            name = "",
            vertices = emptyList(),
            selectedIndex = null,
            bufferM = 0.0,
        )
    }

    fun editZone(zone: NoFireZone) {
        mode.value = when (zone) {
            is NoFirePolygon -> EditorMode.PolygonEdit(
                zoneId = zone.id,
                name = zone.name,
                vertices = zone.vertices,
                selectedIndex = null,
            )
            is NoFireMarker -> EditorMode.MarkerEdit(
                zoneId = zone.id,
                name = zone.name,
                center = zone.center,
                radiusM = zone.radiusM,
            )
            is NoFireLine -> EditorMode.LineEdit(
                zoneId = zone.id,
                name = zone.name,
                vertices = zone.vertices,
                selectedIndex = null,
                bufferM = zone.bufferM,
            )
        }
    }

    fun onTap(hit: TapHit, zones: List<NoFireZone>) {
        val currentMode = mode.value
        if (currentMode is EditorMode.Idle) {
            if (hit.zoneId != null) {
                selectedZone.value = zones.firstOrNull { zoneId(it) == hit.zoneId }
            }
        } else {
            onMapTap(hit.position)
        }
    }

    fun clearSelection() {
        selectedZone.value = null
    }

    fun editSelectedShape() {
        val zone = selectedZone.value ?: return
        editZone(zone)
        selectedZone.value = null
    }

    fun renameSelected(name: String) {
        val zone = selectedZone.value ?: return
        viewModelScope.launch {
            when (zone) {
                is NoFirePolygon -> zoneRepository.updatePolygon(zone.id, name, zone.vertices)
                is NoFireMarker -> zoneRepository.updateMarker(zone.id, name, zone.center, zone.radiusM)
                is NoFireLine -> zoneRepository.updateLine(zone.id, name, zone.vertices, zone.bufferM)
            }
            showRenameDialog.value = false
            selectedZone.value = null
        }
    }

    fun deleteSelected() {
        val zone = selectedZone.value ?: return
        viewModelScope.launch {
            zoneRepository.delete(zoneId(zone))
            selectedZone.value = null
        }
    }

    private fun zoneId(zone: NoFireZone): Long = when (zone) {
        is NoFirePolygon -> zone.id
        is NoFireMarker -> zone.id
        is NoFireLine -> zone.id
    }

    fun onMapTap(p: LatLon) {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> {
                val vertices = m.vertices + p
                mode.value = m.copy(vertices = vertices, selectedIndex = vertices.lastIndex)
            }
            is EditorMode.LineEdit -> {
                val vertices = m.vertices + p
                mode.value = m.copy(vertices = vertices, selectedIndex = vertices.lastIndex)
            }
            is EditorMode.MarkerEdit -> mode.value = m.copy(center = p)
            is EditorMode.Idle -> Unit
        }
    }

    fun undoLastVertex() {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> if (m.vertices.isNotEmpty()) {
                val vertices = m.vertices.dropLast(1)
                mode.value = m.copy(vertices = vertices, selectedIndex = vertices.lastIndex.takeIf { it >= 0 })
            }
            is EditorMode.LineEdit -> if (m.vertices.isNotEmpty()) {
                val vertices = m.vertices.dropLast(1)
                mode.value = m.copy(vertices = vertices, selectedIndex = vertices.lastIndex.takeIf { it >= 0 })
            }
            else -> Unit
        }
    }

    fun selectVertex(i: Int) {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> mode.value = m.copy(selectedIndex = i)
            is EditorMode.LineEdit -> mode.value = m.copy(selectedIndex = i)
            else -> Unit
        }
    }

    fun moveVertex(i: Int, p: LatLon) {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> if (i in m.vertices.indices) {
                mode.value = m.copy(vertices = m.vertices.toMutableList().also { it[i] = p })
            }
            is EditorMode.LineEdit -> if (i in m.vertices.indices) {
                mode.value = m.copy(vertices = m.vertices.toMutableList().also { it[i] = p })
            }
            is EditorMode.MarkerEdit -> if (i == 0) {
                mode.value = m.copy(center = p)
            }
            is EditorMode.Idle -> Unit
        }
    }

    fun insertVertexAfter(i: Int, p: LatLon): Int {
        return when (val m = mode.value) {
            is EditorMode.PolygonEdit -> {
                val newIndex = i + 1
                val vertices = m.vertices.toMutableList().apply { add(newIndex, p) }
                mode.value = m.copy(vertices = vertices, selectedIndex = newIndex)
                newIndex
            }
            is EditorMode.LineEdit -> {
                val newIndex = i + 1
                val vertices = m.vertices.toMutableList().apply { add(newIndex, p) }
                mode.value = m.copy(vertices = vertices, selectedIndex = newIndex)
                newIndex
            }
            else -> i
        }
    }

    fun deleteSelectedVertex() {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> {
                val index = m.selectedIndex ?: return
                if (index !in m.vertices.indices) return
                val vertices = m.vertices.toMutableList().apply { removeAt(index) }
                mode.value = m.copy(vertices = vertices, selectedIndex = null)
            }
            is EditorMode.LineEdit -> {
                val index = m.selectedIndex ?: return
                if (index !in m.vertices.indices) return
                val vertices = m.vertices.toMutableList().apply { removeAt(index) }
                mode.value = m.copy(vertices = vertices, selectedIndex = null)
            }
            else -> Unit
        }
    }

    fun setMarkerRadius(r: Double) {
        val m = mode.value
        if (m is EditorMode.MarkerEdit) {
            mode.value = m.copy(radiusM = r)
        }
    }

    fun setLineBuffer(m: Double) {
        val current = mode.value
        if (current is EditorMode.LineEdit) {
            mode.value = current.copy(bufferM = m)
        }
    }

    fun cancel() {
        mode.value = EditorMode.Idle
        showNameDialog.value = false
    }

    fun requestFinish() {
        showNameDialog.value = true
    }

    fun dismissNameDialog() {
        showNameDialog.value = false
    }

    fun confirmFinish(name: String, radiusM: Double?) {
        when (val m = mode.value) {
            is EditorMode.PolygonEdit -> viewModelScope.launch {
                if (m.zoneId == null) {
                    zoneRepository.addPolygon(name, m.vertices)
                } else {
                    zoneRepository.updatePolygon(m.zoneId, name, m.vertices)
                }
                finish()
            }
            is EditorMode.MarkerEdit -> {
                val center = m.center
                if (center != null) {
                    viewModelScope.launch {
                        val radius = radiusM ?: m.radiusM
                        if (m.zoneId == null) {
                            zoneRepository.addMarker(name, center, radius)
                        } else {
                            zoneRepository.updateMarker(m.zoneId, name, center, radius)
                        }
                        finish()
                    }
                }
            }
            is EditorMode.LineEdit -> viewModelScope.launch {
                val buffer = radiusM ?: m.bufferM
                if (m.zoneId == null) {
                    zoneRepository.addLine(name, m.vertices, buffer)
                } else {
                    zoneRepository.updateLine(m.zoneId, name, m.vertices, buffer)
                }
                finish()
            }
            is EditorMode.Idle -> Unit
        }
    }

    private fun finish() {
        mode.value = EditorMode.Idle
        showNameDialog.value = false
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditorViewModel(container.zoneRepository)
            }
        }
    }
}
