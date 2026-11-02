# KA Play runtime ADB evidence — 2026-11-02

## Connection

The SSH reverse tunnel exposed the emulator at `127.0.0.1:15555`.

```text
127.0.0.1:15555 device product:sailfish model:Pixel device:sailfish
```

## APK installation

The Full Debug APK was installed with `adb install -r` and returned:

```text
Performing Streamed Install
Success
```

The package was then force-stopped and relaunched with `monkey`.

## Fresh sync log check

After clearing logcat, launching `com.kaplay.app`, and waiting 10 seconds, the filtered log contained only these unrelated missing-RPC warnings:

```text
AvatarRepository: Code: PGRST202
DeviceSessionRegistration: Code: PGRST202
```

There were no fresh `PGRST202` entries from `WatchedRepository`, `WatchProgressRepository`, or `SyncManager`. The installed APK dex contains the v2 RPC identifiers `sync_pull_watched_items_v2`, `sync_pull_watch_progress_v2`, and their delta variants.

## Limits

No favorite, progress, or watched-item mutation was performed in the existing account during this check. Therefore this is runtime/startup evidence, not SELECT-after-write evidence. Clean reinstall recovery remains unverified.
