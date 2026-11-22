package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

class IosAudioHaptics : AudioHaptics {
    private val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    override fun playTingSound() {
        // iOS System Sound 1057 (Ting Alert Tone)
        AudioServicesPlaySystemSound(1057u)
    }

    override fun playShrinkSound() {
        // iOS System Sound 1005 (Warning Alert Tone)
        AudioServicesPlaySystemSound(1005u)
    }

    override fun triggerVibration(durationMillis: Long) {
        generator.prepare()
        generator.impactOccurred()
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }
}

@Composable
actual fun rememberAudioHaptics(): AudioHaptics {
    return remember { IosAudioHaptics() }
}
