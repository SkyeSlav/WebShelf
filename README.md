# WebShelf

WebShelf is an open-source, local-first Android library for online web apps and offline HTML projects.

## Current features

- Import a standalone `.html` / `.htm` file.
- Import a ZIP archive containing an HTML project.
- Import a project folder through Android's Storage Access Framework.
- Add online web apps by URL.
- Categorize, search, rename and remove library entries.
- Run local projects through AndroidX WebKit's `WebViewAssetLoader`, using a unique HTTPS origin per local app.
- Adaptive light/dark Material 3 appearance based on Android system theme.
- Room database for library metadata; imported files stay in app-private storage.

## Build

1. Install Android Studio (Ladybug or newer) with Android SDK 35 and JDK 17.
2. Open this folder in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose, Room and AndroidX WebKit.

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
- Automated tests and CI build.

## License

MIT. See [LICENSE](LICENSE).
