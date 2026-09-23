package com.wingspan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wingspan.app.domain.ballistics.Choke
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.PelletMaterial
import com.wingspan.app.domain.ballistics.ShotSize
import com.wingspan.app.domain.ballistics.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SHOT_SIZE = stringPreferencesKey("shot_size")
        val MATERIAL = stringPreferencesKey("material")
        val CUSTOM_DIAMETER_IN = doublePreferencesKey("custom_diameter_in")
        val CUSTOM_DENSITY_GCC = doublePreferencesKey("custom_density_gcc")
        val MUZZLE_VELOCITY_FPS = doublePreferencesKey("muzzle_velocity_fps")
        val CHOKE = stringPreferencesKey("choke")
        val ENERGY_THRESHOLD_FTLBF = doublePreferencesKey("energy_threshold_ftlbf")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val WIND_SPEED_MPH = doublePreferencesKey("wind_speed_mph")
        val BASEMAP = stringPreferencesKey("basemap")
    }

    val settings: Flow<LoadSettings> = dataStore.data.map { prefs ->
        val defaults = LoadSettings()
        LoadSettings(
            shotSize = prefs[Keys.SHOT_SIZE]?.let { runCatching { enumValueOf<ShotSize>(it) }.getOrNull() }
                ?: defaults.shotSize,
            material = prefs[Keys.MATERIAL]?.let { runCatching { enumValueOf<PelletMaterial>(it) }.getOrNull() }
                ?: defaults.material,
            customDiameterInches = prefs[Keys.CUSTOM_DIAMETER_IN] ?: defaults.customDiameterInches,
            customDensityGcc = prefs[Keys.CUSTOM_DENSITY_GCC] ?: defaults.customDensityGcc,
            muzzleVelocityFps = prefs[Keys.MUZZLE_VELOCITY_FPS] ?: defaults.muzzleVelocityFps,
            choke = prefs[Keys.CHOKE]?.let { runCatching { enumValueOf<Choke>(it) }.getOrNull() }
                ?: defaults.choke,
            energyThresholdFtLbf = prefs[Keys.ENERGY_THRESHOLD_FTLBF] ?: defaults.energyThresholdFtLbf,
            unitSystem = prefs[Keys.UNIT_SYSTEM]?.let { runCatching { enumValueOf<UnitSystem>(it) }.getOrNull() }
                ?: defaults.unitSystem,
            windSpeedMph = prefs[Keys.WIND_SPEED_MPH] ?: defaults.windSpeedMph,
        )
    }

    suspend fun update(transform: (LoadSettings) -> LoadSettings) {
        val current = settings.first()
        val updated = transform(current)
        dataStore.edit { prefs ->
            prefs[Keys.SHOT_SIZE] = updated.shotSize.name
            prefs[Keys.MATERIAL] = updated.material.name
            prefs[Keys.CUSTOM_DIAMETER_IN] = updated.customDiameterInches
            prefs[Keys.CUSTOM_DENSITY_GCC] = updated.customDensityGcc
            prefs[Keys.MUZZLE_VELOCITY_FPS] = updated.muzzleVelocityFps
            prefs[Keys.CHOKE] = updated.choke.name
            prefs[Keys.ENERGY_THRESHOLD_FTLBF] = updated.energyThresholdFtLbf
            prefs[Keys.UNIT_SYSTEM] = updated.unitSystem.name
            prefs[Keys.WIND_SPEED_MPH] = updated.windSpeedMph
        }
    }

    val basemapKey: Flow<String> = dataStore.data.map { prefs -> prefs[Keys.BASEMAP] ?: "USGS_TOPO" }

    suspend fun setBasemapKey(key: String) {
        dataStore.edit { prefs -> prefs[Keys.BASEMAP] = key }
    }
}
