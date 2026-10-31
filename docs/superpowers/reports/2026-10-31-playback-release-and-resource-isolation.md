# KA Play — Final Release and Resource Isolation Validation

## Scope

Final pre-merge adjustments on `security/playback-tls-hardening`. No merge, release, APK publication, Supabase, authentication, synchronization, rewards, UI, or production changes.

## Release protection

`.github/workflows/build.yml` now defines a required boolean `workflow_dispatch` input:

```yaml
publish_release:
  required: true
  default: false
  type: boolean
```

The existing release job runs only when:

```yaml
github.event_name == 'workflow_dispatch' && inputs.publish_release == true
```

Therefore push events, including a future merge push to `kaplay-rebrand`, continue to run Debug Full/Play Store compilation, focused tests, and static TLS checks, but cannot resolve the release keystore, run `assembleFullRelease`, or upload an APK. A release requires an explicit manual dispatch with `publish_release=true` and was not dispatched here.

## Resource isolation evidence

Commit `24cf74eb` adds a real MockWebServer HTTPS regression test:

- `/media/a.m3u8` redirects to `/media/b.m3u8` on the same origin and directory.
- A's `Bearer resource-a` is present only on A and never reaches B.
- Direct B playback sends only `Bearer resource-b`.
- Production policy was not changed because the existing behavior passed the regression.

## GitHub Actions

Final push validation: [run 41244659898](https://github.com/franciscoalro/kaplay-app/actions/runs/41244659898)

- Android job `124665476828`: success.
- Full Debug compile: success.
- Play Store Debug compile: success.
- Focused security tests: success.
- Static gate: `No forbidden playback TLS patterns found`.
- Release job `124668124719`: skipped.
- Pages job `124665476647`: failed separately because GitHub environment protection rejects deployment from `security/playback-tls-hardening`; unrelated to playback code and tests.
- No APK was built or published.

## Commits

- `24cf74eb` — same-origin/same-directory resource redirect isolation test.
- `e7445fac` — manual-only release workflow guard.
- `d6d8c883` — validation evidence report.
- `9d084f88` — implementation plan.

Final HEAD: `d6d8c883`.

## Review

Task reviews and final whole-branch review: PASS. No Critical, Important, or Minor findings.

## Residual risk

Legacy HTTP remains mutable in transit. Sensitive credentials and unsafe Referer values are blocked, but HTTP cannot provide confidentiality or authenticity. Signed parameters such as `signature`, `expires`, and `key` remain allowed for compatibility and are not a security boundary; HTTPS remains recommended.
