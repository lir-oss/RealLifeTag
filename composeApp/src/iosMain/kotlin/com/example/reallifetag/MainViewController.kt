package com.example.reallifetag

import androidx.compose.ui.window.ComposeUIViewController
import com.example.reallifetag.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
