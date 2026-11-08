package com.kaplay.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MetaDetailsRequestKeyTest {
    @Test
    fun `request key keeps metadata origins distinct`() {
        assertNotEquals(
            buildMetaDetailsRequestKey("movie", "tt123", "https://one.example/manifest.json"),
            buildMetaDetailsRequestKey("movie", "tt123", "https://two.example/manifest.json"),
        )
    }

    @Test
    fun `missing origin retains one stable key`() {
        assertEquals(
            buildMetaDetailsRequestKey("movie", "tt123"),
            buildMetaDetailsRequestKey("movie", "tt123", ""),
        )
    }
}
