# KA Play runtime update evidence

Date: 2026-11-03

## Published release

- Release: `0.9.21`
- URL: https://github.com/franciscoalro/kaplay-app/releases/tag/0.9.21
- GitHub release: not draft, not prerelease
- Production package: `com.kaplay.app`
- Release certificate SHA-256: `97807c6f56415754cffb6d2ffb6cdff642b30bffc3f892dd9bbf513f839e99a8`
- Verified arm64 APK SHA-256: `67ab8d37e0abd3d157cdc2d78b901d3d87c22bd0f8efbd4a95015d398f96a770`

The published arm64 APK reports `versionName=0.9.21`, `versionCode=194`, package `com.kaplay.app`, and the expected persistent certificate.

## Updater endpoint

The live `releases/latest` endpoint returned tag `0.9.21`, `draft=false`, `prerelease=false`, and four APK assets: arm64-v8a, armeabi-v7a, x86, and x86_64. The arm64 asset returned HTTP 302 followed by HTTP 200 with content type `application/vnd.android.package-archive` and the expected release size.

## Non-destructive upgrade smoke test

- Workflow: https://github.com/franciscoalro/kaplay-app/actions/runs/41650821994
- Inputs: old `v0.9.17`, new `0.9.21`
- Result: success
- Emulator operation: `adb install -r`
- Old version: `0.9.17`
- New version: `0.9.21`
- Package after update: `com.kaplay.app`
- External app-storage marker: preserved
- `adb uninstall`: not run
- `adb shell pm clear`: not run
- Installer output: `Success`

The smoke test first installed the old signed APK, wrote a marker to app-specific storage, upgraded with `adb install -r`, and verified the marker remained after the upgrade. It also verified the package path before and after the operation.

## Release workflow

- Workflow: https://github.com/franciscoalro/kaplay-app/actions/runs/41631435604
- Result: success after retry
- Android APK build: success
- Release creation: success
- iOS build: intentionally skipped using `android_only=true`
