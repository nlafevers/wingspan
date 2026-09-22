package com.wingspan.app.domain.ballistics

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadSettingsTest {

    @Test
    fun `defaults map to expected ballistic input`() {
        val input = LoadSettings().toBallisticInput()

        assertEquals(BallisticInput(0.110, 11.34, 1250.0, Choke.MODIFIED, 1.5), input)
    }

    @Test
    fun `custom shot size uses custom diameter`() {
        val settings = LoadSettings(shotSize = ShotSize.CUSTOM, customDiameterInches = 0.2)

        assertEquals(0.2, settings.effectiveDiameterInches(), 0.0)
    }

    @Test
    fun `round trips through json`() {
        val original = LoadSettings(material = PelletMaterial.STEEL)

        val encoded = Json.encodeToString(LoadSettings.serializer(), original)
        val decoded = Json.decodeFromString(LoadSettings.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
