# Task 2 Report — Manual-only Release Workflow

## Status

GREEN via GitHub Actions. No local Gradle, Java, Kotlin, or test command was run. No manual `workflow_dispatch` release was triggered.

## Commit

- `e7445fac` — `ci: make release publication manual-only`
- Branch: `security/playback-tls-hardening`
- Push target: `buildci/security/playback-tls-hardening`

## Workflow changes

- Added `workflow_dispatch.inputs.publish_release` as a required boolean with default `false`.
- Changed `assemble-full-release` condition to:

  ```yaml
  if: ${{ github.event_name == 'workflow_dispatch' && inputs.publish_release == true }}
  ```

- Push events continue to run Full debug compile, Play Store debug compile, focused playback tests, static TLS gate, and Pages job.
- Push events cannot enter release signing, `assembleFullRelease`, or APK upload, including future pushes to `kaplay-rebrand` after merge.

## GitHub Actions evidence

- Push validation run: `41244659898`
  - URL: https://github.com/franciscoalro/kaplay-app/actions/runs/41244659898
  - Android job `124665476828`: **success**
  - Pages job `124665476647`: **failure**, classified separately below
  - Release job `124668124719`: **skipped**
- Full debug compile: `BUILD SUCCESSFUL in 3m 42s`.
- Play Store compile: `:androidApp:compilePlaystoreDebugKotlin`, `BUILD SUCCESSFUL in 3m 33s`.
- Focused selectors invoked:
  - `com.kaplay.app.features.player.PlaybackRequestSecurityPolicyTest`
  - `com.kaplay.app.features.player.PlayerPlaybackNetworkingSecurityTest`
- Host test task: `BUILD SUCCESSFUL in 3m 53s`.
- Static gate: `No forbidden playback TLS patterns found`.
- No APK or release artifact was built or uploaded.

## Pages classification

`publish-plugin-pages` job `124665476647` failed because GitHub environment protection rejects deployment from `security/playback-tls-hardening`. This is unrelated to the workflow condition, playback code, compilation, focused tests, or TLS gate. Pages behavior was intentionally preserved on push.

## Release conclusion

The normal push path ran all debug/security validation and skipped `assembleFullRelease`. Release publication now requires an explicit manual dispatch with `publish_release=true`; no such dispatch was run for this task.
