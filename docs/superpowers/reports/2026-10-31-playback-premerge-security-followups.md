# KA Play Playback TLS — Pre-merge Security Follow-ups

## Scope

Follow-up Task 2 for `security/playback-tls-hardening`. No Supabase, authentication, rewards, synchronization, UI, production, merge, or APK changes are included.

## Changes

- `.github/workflows/build.yml`
  - Added a job-level guard to `assemble-full-release`:
    `if: ${{ !startsWith(github.ref, 'refs/heads/security/') }}`.
  - Normal non-`security/*` branches retain the existing release behavior.
  - Full/Play Store compilation, focused playback security tests, static TLS gate, and the Pages job are unchanged.
- `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`
  - Documents that HTTP `signature`, `expires`, and `key` parameters remain allowed for legitimate signed-CDN compatibility.
  - Documents that HTTP remains mutable and these parameters do not provide confidentiality or integrity; HTTPS is recommended.

## CI evidence

- Follow-up commits: `9b675b93`, `ab48a3d8`, `fb5586a2` (policy/tests), `0ecfe642`, `b4b96d4f`, `1224e3c9`, `7b68e4d8`, `e3858e4d` (guard/evidence), and `693432ce` (explicit TLS failure assertion).
- Guard verification run: `41234741151` — https://github.com/franciscoalro/kaplay-app/actions/runs/41234741151; Android job `124636026307` succeeded and `assembleFullRelease` job `124638739169` was skipped.
- Final code verification run: `41238859372` — https://github.com/franciscoalro/kaplay-app/actions/runs/41238859372; Android job `124648372539` succeeded and `assembleFullRelease` job `124651086625` was skipped.
- Full compile: `BUILD SUCCESSFUL in 3m 36s` on the final run.
- Play Store compile: `:androidApp:compilePlaystoreDebugKotlin`, `BUILD SUCCESSFUL in 3m 34s` on the final run.
- Focused tests: both `PlaybackRequestSecurityPolicyTest` and `PlayerPlaybackNetworkingSecurityTest` selectors were invoked; host test task completed successfully in 3m 47s.
- Static TLS gate: `No forbidden playback TLS patterns found`.
- No APK or release artifact is authorized or published.

Known prior evidence before this follow-up:

- Final prior gate run `41229364217`, job `124620108549`: Full compile, Play Store compile, focused host tests, and static TLS gate succeeded.
- Prior run included the pre-existing release assembly job, which was manually cancelled after validation.

## Separate Pages-job classification

The `publish-plugin-pages` failure is job `124648372698` (run `41238859372`), plus earlier guard-verification job `124636026132` (run `41234741151`). They are separate from the Android security/product gates and are caused by GitHub environment protection rejecting deployment from the feature branch (`security/playback-tls-hardening`). They are not caused by playback code, tests, compilation, or the static TLS gate. The Pages job behavior is intentionally unchanged in this task.

## Release guard behavior

On `refs/heads/security/*`, the release assembly job is skipped at the job level, preventing keystore resolution, `assembleFullRelease`, and APK upload. Ordinary branches do not match the guard and retain their existing behavior.

## Remaining risks

1. Legacy HTTP media remains mutable in transit. Sensitive headers and credential-bearing URL forms are blocked, but HTTP cannot provide confidentiality or authenticity.
2. HTTP signed parameters named `signature`, `expires`, and `key` remain allowed intentionally for compatibility. They are not a security boundary over cleartext HTTP; HTTPS is recommended.
3. Redirect behavior is enforced by the network interceptor and policy tests; a dedicated end-to-end redirect test remains useful follow-up coverage.
4. The self-signed certificate test now directly asserts `SSLException` from the client's TLS handshake, while the playback call remains platform-tolerant and verifies failure before server observation.
5. The static gate is intentionally conservative and rejects broad TLS customization symbols in the playback package.
