# RealLifeTag - Kotlin Multiplatform (Android & iOS)

RealLifeTag is a real-world GPS tag game built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**, targeting both **Android** and **iOS (iPhone)** from a shared codebase.

---

## 📱 Platform Architecture

The project is structured following the official JetBrains Compose Multiplatform standard layout:

```text
RealLifeTag/
├── composeApp/                     # Shared multiplatform Compose module
│   ├── build.gradle.kts            # KMP build configuration (Android + iOS targets)
│   ├── google-services.json        # Android Firebase configuration
│   └── src/
│       ├── commonMain/             # 100% Shared UI, Business Logic, and Game Engine
│       │   └── kotlin/com/example/reallifetag/
│       │       ├── model/          # Role, LatLng, Player, PlayerNetworkState, LobbyData, GameStage
│       │       ├── util/           # GeoUtils (Haversine distance, zoom math, player generators)
│       │       ├── platform/       # Multiplatform expect contracts (Location, Heading, Haptics, Maps)
│       │       ├── data/           # LobbyRepository contract & shared data flows
│       │       ├── ui/             # App.kt (Game engine loop, timers, state manager)
│       │       │   ├── screens/    # MainMenu, Setup, PublicLobbies, Lobby, Gameplay, GameOver
│       │       │   ├── components/ # RunningManLogo, RuleStepper
│       │       │   └── theme/      # Color, Typography, RealLifeTagTheme
│       ├── androidMain/            # Android implementations (actuals)
│       │   ├── AndroidManifest.xml # Permissions, Maps API key, GameTimerService
│       │   └── kotlin/com/example/reallifetag/
│       │       ├── MainActivity.kt # Android Activity hosting shared Compose App
│       │       ├── GameTimerService.kt # Android Foreground Service with WakeLock
│       │       ├── platform/       # Android Location (FusedClient), Compass (SensorManager),
│       │       │                   # Audio (ToneGenerator), Haptics (Vibrator), GoogleMap (maps-compose)
│       │       └── data/           # AndroidLobbyRepository (FirebaseFirestore)
│       └── iosMain/                # iOS implementations (actuals)
│           └── kotlin/com/example/reallifetag/
│               ├── MainViewController.kt # ComposeUIViewController exporting App to Swift
│               ├── platform/       # iOS Location (CoreLocation), Compass (CLHeading),
│               │                   # Audio (AudioToolbox), Haptics (UIImpactFeedbackGenerator),
│               │                   # Native Apple Maps (MKMapView + MKCircle + Annotations)
│               └── data/           # IosLobbyRepository
└── iosApp/                         # Native Xcode Project wrapper for iPhone
    ├── iosApp.xcodeproj/           # Xcode project (automatically runs Gradle framework build)
    └── iosApp/
        ├── iOSApp.swift            # SwiftUI App entry point
        ├── ContentView.swift       # Embeds ComposeUIViewController into SwiftUI
        ├── Info.plist              # Location permissions & UIBackgroundModes ("location")
        └── Assets.xcassets         # AppIcon & image assets
```

---

## 🎯 Native Feature Implementation Mapping

| Feature | Shared (`commonMain`) | Android (`androidMain`) | iOS (`iosMain`) |
| :--- | :--- | :--- | :--- |
| **UI Framework** | Compose Multiplatform (Material 3) | Jetpack Compose runtime | UIKit / Canvas Metal rendering |
| **Map Rendering** | `GameMap` expect composable | Google Maps (`maps-compose`) | Apple Maps (`MKMapView` + `MKCircle`) |
| **GPS Tracking** | `LocationTracker` (Flow of LatLng) | `FusedLocationProviderClient` | Core Location (`CLLocationManager`) |
| **Compass / Heading** | `HeadingTracker` (Flow of degrees) | `SensorManager` (Rotation Vector) | `CLLocationManager.startUpdatingHeading()` |
| **Audio Alert (Ting)** | `AudioHaptics.playTingSound()` | `ToneGenerator` (Beep tone) | `AudioServicesPlaySystemSound(1057)` |
| **Haptic Alerts** | `AudioHaptics.triggerVibration()` | `Vibrator` / `VibrationEffect` | `UIImpactFeedbackGenerator` |
| **Background Execution** | `BackgroundGameTimer` | `GameTimerService` (Foreground Service) | `UIBackgroundModes` location updates |
| **Local Persistence** | `AppPreferences` | Android `SharedPreferences` | Apple `NSUserDefaults` |
| **Multiplayer Sync** | `LobbyRepository` | Google Cloud Firebase Firestore | iOS Firestore / REST / shared flow |

---

## 🚀 How to Run the App

### 🤖 Running on Android
1. Open this project in **Android Studio**.
2. Select the **`composeApp`** run configuration.
3. Select an Android emulator or connected device (Android 7.0 / API 24+).
4. Click **Run** (`Shift + F10`).

### 🍏 Running on iPhone (iOS)
#### Option A: From Xcode (Recommended for iOS testing)
1. Open `iosApp/iosApp.xcodeproj` in **Xcode** on a Mac:
   ```bash
   open iosApp/iosApp.xcodeproj
   ```
2. Select your target (iPhone Simulator or physical iPhone).
3. Press **Cmd + R** to run. Xcode automatically runs Gradle to compile and link `ComposeApp.framework`.

#### Option B: From Android Studio (using Kotlin Multiplatform plugin)
1. In Android Studio, ensure the **Kotlin Multiplatform Mobile** plugin is installed.
2. Select the **`iosApp`** run configuration in the top toolbar.
3. Choose an iOS Simulator and click **Run**.
