<div align="center">

  <img src="https://kaplay.tv/assets/kaplay-app-logo-wordmark.webp" alt="KAPlay" width="320" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. KAPlay turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://kaplay.tv) · [GitHub releases](https://github.com/KAPlayMedia/KAPlayMobile/releases/latest) · [Support KAPlay](https://kaplay.tv/support)

</div>

## Get KAPlay Mobile

- [Android on Google Play](https://play.google.com/store/apps/details?id=com.kaplay.app)
- [Android APK](https://github.com/KAPlayMedia/KAPlayMobile/releases/latest)
- iOS via AltStore or SideStore: add [this source URL](https://raw.githubusercontent.com/KAPlayMedia/KAPlayMobile/cmp-rewrite/store.json) in the app's Sources section, then install KAPlay.

## Build from source

```bash
git clone https://github.com/KAPlayMedia/KAPlayMobile.git
cd KAPlayMobile
```

### Android

Android development requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env KAPLAY_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
