package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

interface HeadingTracker {
    val headingDegrees: StateFlow<Float>
    fun start()
    fun stop()
}

@Composable
expect fun rememberHeadingTracker(): HeadingTracker
