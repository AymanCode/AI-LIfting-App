package com.ayman.ecolift.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressAxisColumnTest {
    @Test
    fun normal_tick_labels_keep_the_existing_column() {
        assertEquals(40.dp, progressAxisColumnWidth(22.dp))
    }

    @Test
    fun wide_tick_labels_leave_space_for_the_latest_marker_halo() {
        assertEquals(60.dp, progressAxisColumnWidth(48.dp))
        assertEquals(92.dp, progressAxisColumnWidth(80.dp))
    }
}
