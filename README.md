# Sencha

Sencha is a Kotlin Multiplatform client (Android/iOS first) for local/remote AI models. The shared core is split by
capability and targets offline-first execution with a job system and model manager.

## Project Layout

- `androidApp/` - Android application (Jetpack Compose UI).
- `iosApp/` - iOS application (Xcode project, UIKit UI).
- `shared/` - KMP umbrella framework for iOS (`Shared`) and shared dependencies.
- `shared/core/model/` - Model capabilities and parameters.
- `shared/core/domain/` - Use-cases.
- `shared/core/data/` - Repositories and storage.
- `shared/core/jobs/` - Job engine primitives.
- `shared/core/security/` - Key management abstraction.

## Build Android APK

- macOS/Linux:
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- Windows:
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  ```

The APK will be at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Run Android App

Open the project in Android Studio and run the `androidApp` configuration, or install via:

```shell
./gradlew :androidApp:installDebug
```

## Run iOS App

Open `iosApp/iosApp.xcodeproj` in Xcode (macOS only) and run the iOS target. Xcode will invoke Gradle to build
and embed the `Shared` framework automatically.

## Run Tests

```shell
./gradlew test
```

## Lint

```shell
./gradlew ktlintCheck detekt
```
