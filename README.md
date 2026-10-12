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
- Vanilla HTML, CSS, and JavaScript for the Chrome extension and PWA ports

## Web Ports

Two phone-layout web ports live alongside the Android app:

- [`chrome-ext/`](chrome-ext/) is a Manifest V3 Chrome side-panel extension. Load it unpacked from `chrome://extensions`. Its scheduled summary uses only the toolbar icon badge and tooltip—there are no popup/system notifications.
- [`pwa/`](pwa/) is an installable, Android-oriented PWA. Serve it from HTTPS (or `localhost` during development), configure its feed proxy in Settings, and install it from Chrome. It does not expose or use notifications.

Both ports retain browsing, search, product/status/stage filters, timeline sorting and date filtering, Complete/Skipped/Saved/Hidden tracking, sharing, email and docs links, product settings, light/dark themes, streaks, badges, reward debug controls, API diagnostics, deep links, and offline local caching. Browser storage replaces Room.

The Microsoft feed hosts currently omit CORS headers, so a static PWA cannot refresh them directly. [`pwa/proxy/`](pwa/proxy/) contains a small allow-listed Cloudflare Worker for the two Microsoft hosts. This is the only material runtime dependency for PWA refresh; cached data and all local tracking remain available offline. See [`WEB_PORTS.md`](WEB_PORTS.md) for the full capability and setup notes.

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

## Play Store Release

Release signing reads `release.storeFile`, `release.storePassword`, `release.keyAlias`, and `release.keyPassword` from local properties, Gradle properties, or their corresponding `RELEASE_*` environment variables. Keep signing credentials out of source control.

Run `./gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:bundleRelease` on Windows to validate and build the signed bundle. Versioned upload bundles, Play release notes, and SHA-256 checksums are kept in [`release/`](release/).

## Data Source

The release sources are defined in `api.json` and bundled in the app. Each source has a `Schema` value:

- `ReleasePlanner` for the legacy `results` response.
- `M365` for the release communications `value` response.

Sources using either schema are adapted into the same local release model. M365 month-only dates are treated as covering the full month for date and week filters.
M365 sources appear first in Products and are enabled by default; legacy sources default to off. Explicit product selections are preserved. Settings includes a notice that non-M365 sources will be deprecated in November. Platform is displayed as Power Platform without changing stored source identities.
API diagnostics always use an M365 source. Refresh fetches up to four sources concurrently with a 30-second limit per feed, uses one cache snapshot for item matching, and retains successful feeds when others fail. Updates includes a Complete status filter alongside Open and Skipped.
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
