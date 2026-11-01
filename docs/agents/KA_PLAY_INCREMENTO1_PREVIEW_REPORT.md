# KA Play Incremento 1 — preview visual

- Source integration commit before preview-only CI additions: `40732fe1`.
- Preview workflow runs:
  - `41325959718`: Full Debug APK build success at `40732fe1`.
  - `41330156590`: Full Debug APK + isolated emulator job success; workflow-only follow-up commits `e9d0ae30`, `98c8f89c`, `47316c53`.
- Artifact `ka-play-full-debug-build-only` (`13093025976`): Full Debug APK, 167,460,714 bytes.
- SHA-256: `970f8ba9cada08100fbf7bea905fa20c58f27937091cc63ebecc359ef906927d`.
- `aapt2 dump badging`: `com.kaplay.app.debug`, versionName `0.9.20`, versionCode `193`.
- Build metadata states `functional_homologation=NOT_PERFORMED`; generated config/APK privileged-credential scan passed.

## Screenshot result

Artifact `ka-play-profile-preview-screenshots` (`13093670902`) was captured from an isolated Android emulator and contains `profile-selection-debug.png` (1080x1920, SHA-256 `6fe4227e03857434b203f108d94fa17cf1ce3f207c1c9729c26890900521b95c`).

The screenshot is real emulator output, but it shows a `Process system isn't responding` dialog rather than the profile-selection UI. It must not be presented as a valid before/after UI capture. No profile-state screenshot was invented. Device/emulator instability remains a preview blocker.

No APK installation occurred on a user device or over the production app. No production backend was contacted.
