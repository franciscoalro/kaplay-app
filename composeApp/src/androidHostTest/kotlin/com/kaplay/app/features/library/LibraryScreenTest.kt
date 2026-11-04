package com.kaplay.app.features.library

import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.home.components.posterGridColumnCountForWidth
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScreenTest {
    @Test
    fun posterGridColumnsAdaptToDeviceWidth() {
        assertEquals(3, posterGridColumnCountForWidth(380.dp))
        assertEquals(2, posterGridColumnCountForWidth(320.dp))
        assertEquals(4, posterGridColumnCountForWidth(850.dp))
        assertEquals(5, posterGridColumnCountForWidth(1050.dp))
        assertEquals(6, posterGridColumnCountForWidth(1250.dp))
        assertEquals(7, posterGridColumnCountForWidth(1450.dp))
    }

    @Test
    fun libraryChipCornerRadiusMatchesSpec() {
        assertEquals(8.dp, KAShapes.ChipRadius)
    }
}
