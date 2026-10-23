package com.ayman.ecolift.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressChartLabelTest {
    @Test
    fun top_set_labels_preserve_the_logged_decimal_and_unit() {
        assertEquals("102.5 lb", formatMetricValue(102.5f, ProgressMetricV2.WEIGHT))
        assertEquals("100 lb", formatMetricValue(100f, ProgressMetricV2.WEIGHT))
    }
}
