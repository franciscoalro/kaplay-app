# KA Play runtime write evidence — 2026-11-02

## Device and profile

- ADB endpoint: `127.0.0.1:15555` via SSH reverse tunnel
- Device: Pixel / `sailfish`
- Package: `com.kaplay.app`
- Active profile selected in the UI: `kito`, profile index `1`

## Reproduction

1. Opened the Home screen.
2. Opened the title details screen for **Black Clover (Dublado)**.
3. Pressed the `+` library/favorite action.
4. Waited six seconds and collected logcat.

The UI action produced a local save log:

```text
LibraryRepository: Saving local library item item=tomato:1224 type=tv profile=1
```

The remote write then failed:

```text
LibraryRepository: Failed to push library delta profile=1 upserts=1 deletes=0
Could not find the function public.sync_push_library_items(p_items, p_origin_client_id, p_profile_id) in the schema cache
Code: PGRST202
Hint: Perhaps you meant to call the function public.sync_push_library_items_v2
URL: https://<redacted>/rest/v1/rpc/sync_push_library_items
```

## Runtime artifact

Screenshot after the action: `ka-favorite-after.png` (captured from the forwarded emulator).

## Interpretation

This is concrete evidence that the local favorite mutation is recorded but the connected runtime did not persist it remotely. The server explicitly advertises `sync_push_library_items_v2` while the running process requested `sync_push_library_items`.

The checked source adapter contains the v2 call, and the downloaded APK dex contains `sync_push_library_items_v2`; therefore the next investigation must identify why the installed process still emits the v1 endpoint (stale process/package, artifact mismatch, or another runtime adapter path). No production SQL, migration, deletion, or workaround was executed.

## Fresh package retest

The old `com.kaplay.app.debug` package was removed only after explicit operator confirmation because its signing key conflicted with the new CI APK. The new APK installed successfully as version `0.9.20` / code `193`.

With the fresh package and the same existing profile, the favorite action produced:

```text
LibraryRepository: Saving local library item item=tomato:6945 type=tv profile=1
LibraryRepository: Failed to push library delta profile=1 attempt=1 upserts=1 deletes=0
HttpRequestTimeoutException: Request timeout has expired [url=https://<redacted>/rest/v1/rpc/sync_push_library_items_v2, request_timeout=10000 ms]
LibraryRepository: Failed to push library delta profile=1 attempt=2 upserts=1 deletes=0
LibraryRepository: Failed to push library delta profile=1 attempt=3 upserts=1 deletes=0
LibraryRepository: Failed to push library delta profile=1 attempt=4 upserts=1 deletes=0
```

This confirms the stale-v1 issue was removed: the fresh APK calls the v2 endpoint and retains the local mutation while the connected request times out. A host-side HTTPS probe reached the Supabase host in `0.099s` and the emulator reached the host by ICMP/TCP, so the remaining failure is specific to the authenticated v2 request or connected RPC execution, not DNS alone.
