<p align="center">
  <img src="docs/logo.svg" alt="WebShelf logo" width="128" height="128">
</p>

<h1 align="center">WebShelf</h1>

<p align="center">A local-first Android library for your web apps and offline HTML projects.</p>

<p align="center">
  <a href="https://github.com/SkyeSlav/WebShelf/actions/workflows/android.yml"><img src="https://github.com/SkyeSlav/WebShelf/actions/workflows/android.yml/badge.svg" alt="Build status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/SkyeSlav/WebShelf" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/kotlin-2.0.21-7F52FF" alt="Kotlin 2.0.21">
  <img src="https://img.shields.io/badge/SDK-35%20%7C%20minSdk%2026-3DDC84" alt="SDK 35, minSdk 26">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84" alt="Platform: Android">
</p>

WebShelf is an open-source, local-first Android library for online web apps and offline HTML projects. Import a website by URL, or a standalone HTML file, a ZIP archive, or a whole project folder, and launch everything from one shelf. Local projects run through AndroidX WebKit's `WebViewAssetLoader` on their own secure HTTPS origin, so relative assets and browser APIs that need a secure context just work.

## Features

- Import a standalone `.html` / `.htm` file.
- Import a ZIP archive containing an HTML project.
- Import a project folder through Android's Storage Access Framework.
- Add online web apps by URL.
- Categorize, search, rename and remove library entries.
- Run local projects through AndroidX WebKit's `WebViewAssetLoader`, using a unique HTTPS origin per local app.
- Adaptive light/dark Material 3 appearance based on the Android system theme.
- Room database for library metadata; imported files stay in app-private storage.

## Get the app

There is no release on Google Play yet. Grab the latest debug APK from GitHub Actions:

1. Open the [Actions tab](https://github.com/SkyeSlav/WebShelf/actions).
2. Pick the latest successful **Build WebShelf APK** run.
3. Download the `webshelf-debug-apk` artifact and install `app-debug.apk` on your device.

The APK is a debug build, signed with the debug key. It is free to use and share under the MIT license.

## Build it yourself

Requirements: Android Studio (Ladybug or newer) with Android SDK 35 and JDK 17.

1. Clone the repository.
2. Open the folder in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Every push to `main` also builds the debug APK on GitHub Actions (`.github/workflows/android.yml`, Gradle 8.9, JDK 17, Android 35) and uploads it as the `webshelf-debug-apk` artifact.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, Room 2.6.1 and AndroidX WebKit 1.12.1.

## Import behavior

- ZIP imports are extracted into app-private storage. The app selects `index.html` if present, otherwise the first `.html` / `.htm` file found.
- ZIP extraction rejects path traversal and caps expanded content at 200 MB and 5,000 entries.
- Folder imports copy the selected directory into app-private storage.
- A single HTML import copies only that file. If it depends on sibling CSS, JavaScript or image files, import its containing folder or a ZIP instead.
- Removing a local library entry also removes its copied project files.

## Security and compatibility notes

- Local projects are served from a unique HTTPS origin (`app-<id>.local`) through `WebViewAssetLoader`. This supports relative assets and browser APIs that require a secure context.
- File URL access and content URL access are disabled in WebView.
- Local projects can still execute JavaScript. Only import HTML/ZIP projects you trust.
- Online web apps require an internet connection.
- The first version does not provide per-app isolated WebView cookies/storage for online sites. WebView's browser data store is shared by the app process.
- External links from local projects are not silently loaded inside the local app origin.
- This is an initial functional foundation, not yet a release-hardened product. Test projects with different asset paths, service workers, WASM, large files and browser APIs before relying on them.

## Roadmap

- Per-app icons / thumbnails.
- Edit categories and bulk organization.
- Per-app WebView profiles for online apps.
- Export/import a complete WebShelf backup.
- Better handling of nested project roots and multiple HTML entry points.
- Import progress, storage usage and duplicate detection.
- Signed release builds and a Google Play listing.
- Automated tests.

## License

MIT. See [LICENSE](LICENSE). The WebShelf logo (`docs/logo.svg`) is an original design, also under MIT.
