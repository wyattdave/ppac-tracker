# Release Planner Tracker

A public, no-auth Android app for tracking Microsoft Power Platform release planner updates.

The app calls Microsoft's public release planner JSON endpoints directly, caches release items locally, and stores completion/saved/hidden state on the device. There is no Power Platform backend, no SharePoint, and no account requirement.

## Stack

- Kotlin
- Jetpack Compose / Material 3
- Room for local cache and tracking state
- Ktor client for public API calls
- WorkManager for periodic background refresh
- Android local notifications for new or changed updates

## Build

The project includes a Gradle wrapper, so a local Gradle install is not required. Local command-line builds need JDK 17 and Android SDK 35.

Useful commands:

```powershell
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

On Windows PowerShell, use `./gradlew.bat` if `./gradlew` is not available.

If you do not have a local Android SDK, local APK builds stop with an `SDK location not found` message. Use the GitHub Actions workflow below; it installs the Android SDK packages in CI.

## ARM Windows Development

If Android Studio is not compatible with your Windows ARM machine, use VS Code locally and GitHub Actions for Android builds.

1. Edit the Kotlin and Compose code in VS Code.
2. Push the project to GitHub.
3. Run the **Android Build** workflow from the Actions tab, or let it run on push.
4. Download the `release-tracker-debug-apk` artifact.
5. Install `app-debug.apk` on an Android device for testing.

The workflow is defined in `.github/workflows/android-build.yml` and builds `:app:assembleDebug` on an Ubuntu runner with JDK 17 and the Android SDK.

## Release Build Later

For Play Store distribution, add a separate signed `.aab` workflow later. That workflow should use GitHub Actions secrets for the keystore, key alias, and passwords, then run `./gradlew :app:bundleRelease` and upload the signed app bundle artifact.

## Data Source

The initial release sources are based on `api.json` and are bundled in the app. The API response shape is based on `examp-payload.json`.
Rewards are based on `rewards.json`, which is bundled alongside the release source config. Product settings, task tracking, and streak progress are stored locally and included in Android backup/device transfer.

## Current Scope

- Browse Power Platform release updates
- Search and filter by product/status
- Timeline by release month
- Mark updates complete
- Save/follow updates
- Hide updates locally
- Share updates via Android share sheet
- Direct docs links
- Periodic background sync with local notifications
