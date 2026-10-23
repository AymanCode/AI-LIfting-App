package com.ayman.ecolift.web.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android containers consume IME insets, so copied web content has no additional overlap. */
@Composable
fun keyboardOverlap(): Dp = 0.dp
