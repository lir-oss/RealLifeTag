package com.example.reallifetag.platform

import androidx.compose.runtime.Composable

interface AppPreferences {
    fun getDeviceId(): String
    fun getUserNickname(): String
    fun setUserNickname(nickname: String)
}

@Composable
expect fun rememberAppPreferences(): AppPreferences
