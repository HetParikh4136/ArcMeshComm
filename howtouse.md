# ArcMeshComm - How to Use

Welcome to ArcMeshComm! This is a minimalistic Android application built with Kotlin and Jetpack Compose, featuring a clean user interface that supports both Light and Dark themes.

## Features
- **Minimalistic UI**: Clean and straightforward design using Material 3 guidelines.
- **Dark/Light Theme**: Automatically adapts based on the user's system preferences.
- **Navigation Menu**: A centralized menu screen for navigating through the app.
- **In-App Quickstart Guide**: A user manual built directly into the app for easy reference at any time.

## Requirements
- Android Studio Ladybug or newer (with Compose support).
- JDK 11 or higher.
- Minimum SDK: API 26 (Android 8.0).
- Target SDK: API 36.

## Running the Application
1. **Open the Project**: Open Android Studio, click **File > Open**, and select the `ArcMeshComm` directory.
2. **Sync Project with Gradle Files**: Android Studio should automatically sync. If not, click the "Sync Project with Gradle Files" icon in the top right.
3. **Build and Run**: Connect your Android device via USB (ensure Developer Options and USB Debugging are enabled) or start an Android Virtual Device (AVD). Click the green **Run** arrow (or press Shift+F10) to build and run the app.

## Using the App
- **Home Screen**: When the app starts, you are greeted with the Home Screen. From here, you can directly access the Quickstart Guide by tapping the button at the bottom.
- **Navigation Menu**: Tap the **hamburger icon** in the top-left corner of the Top App Bar to open the Menu Screen.
- **Menu Screen**: This screen provides navigation links to return to the Home Screen or view the User Manual.
- **User Manual**: Provides basic instructions on how to use the app and its features.

## Architecture & Code
- **Jetpack Compose Navigation**: We use `androidx.navigation:navigation-compose` to manage transitions between screens natively.
- **MainActivity.kt**: Contains the complete Compose UI layout including the `NavHost` and the individual composable screens (`HomeScreen`, `MenuScreen`, and `UserManualScreen`).
- **Theming**: The app leverages the auto-generated `ArcMeshCommTheme` located in `ui/theme` to handle standard Material 3 dynamic color and dark mode switching automatically.

Enjoy building upon this minimal base for your communication app!
