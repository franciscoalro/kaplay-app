package com.kaplay.app.features.settings

import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.core.ui.KASpacing
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @Test
    fun settingsCardAndSpacingTokensMatchSpec() {
        assertEquals(10.dp, KAShapes.CardRadius)
        assertEquals(10.dp, KAShapes.ButtonRadius)
        assertEquals(12.dp, KAShapes.FieldRadius)
        assertEquals(28.dp, KASpacing.Section)
    }

    @Test
    fun surfaceColorsMatchDarkStreamingSpec() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF19191F), KAColors.Surface)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF23232B), KAColors.SurfaceVariant)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2C2C35), KAColors.SurfaceElevated)
    }
}
