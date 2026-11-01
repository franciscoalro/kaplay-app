# Playback Pre-Merge Security Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the operator's eight pre-merge findings without changing valid HTTPS playback, credential-free legacy HTTP, authentication, Supabase, rewards, synchronization, or production release behavior.

**Architecture:** Extend the existing pure request policy to sanitize sensitive `Referer` values and preserve exact same-origin resource ownership. Add real MockWebServer integration tests around the existing OkHttp network interceptor and platform TLS. Update GitHub Actions so security branches cannot enter release assembly, while retaining Full/Play Store compile and focused-test gates.

**Tech Stack:** Kotlin, OkHttp 4.12, MockWebServer/okhttp-tls 5.3.2, Media3, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`

## Global Constraints

- Preserve valid HTTPS and credential-free HTTP playback.
- Never weaken certificate or hostname verification.
- Never send sensitive credentials or token-bearing Referer values over HTTP or unauthorized origins.
- Keep resource-scoped exact header name/value authorization, including different resources on the same origin.
- Run all Gradle compilation/tests only in GitHub Actions.
- Do not publish APKs, merge, or modify production.
- Preserve Supabase, authentication, rewards, synchronization, and UI behavior.
- Document why signed HTTP parameters such as `signature`, `expires`, and `key` remain allowed and their residual tampering risk.

## Review Focus

- Referer with token/user-info/query credentials: tests must prove stripping on HTTP and cross-origin HTTPS while preserving safe same-origin Referer.
- Same-origin but different resource URL: tests must prove credentials are not authorized merely by host; exact registered name/value ownership remains required.
- HTTPS redirect to a different HTTPS origin: token must be removed on the second exchange while compatibility headers survive.
- HTTPS redirect to HTTP: request must fail before the downgraded exchange and no credential-bearing request may reach the HTTP server.
- TLS trust/hostname behavior: self-signed and wrong-host certificates fail; a valid HTTPS certificate succeeds.

---

### Task 1: Policy and MockWebServer Security Tests

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicy.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicyTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlayerPlaybackNetworkingSecurityTest.kt`

**Interfaces:**
- Consumes existing `PlaybackRequestSecurityContext`, `PlaybackRequestSecurityInterceptor`, and `PlayerPlaybackNetworking.createPlaybackHttpClient`.
- Produces no new public API; policy behavior remains internal.

- [ ] Write failing tests for `Referer` containing sensitive query/user-info data on HTTP and unauthorized HTTPS; assert no `Referer` remains, while safe same-origin Referer remains.
- [ ] Write failing tests for two registered resources on one origin with different exact sensitive header values; assert each request retains only its owning value and never authorizes the other resource's token.
- [ ] Write real MockWebServer redirect tests: HTTPS→HTTPS cross-origin strips Authorization/Referer credentials on the second request; HTTPS→HTTP throws before the HTTP server receives a request.
- [ ] Add TLS tests for wrong hostname failure, valid HTTPS control success using server certificates trusted by the test client, and self-signed failure with no fallback request.
- [ ] Implement minimal Referer sanitization in the existing request policy; do not broaden sensitive query names beyond the approved list.
- [ ] Run RED/GREEN only through GitHub Actions, then commit policy/tests.

### Task 2: CI Release Guard and Risk Documentation

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`
- Create: `docs/superpowers/reports/2026-10-31-playback-premerge-security-followups.md`

**Interfaces:**
- Consumes Task 1 tests and existing Full/Play Store compile gates.
- Produces a security-branch release guard and an auditable risk report.

- [ ] Add a branch condition/job guard so `assembleFullRelease`, APK upload, and publication cannot run for `security/*` branches; preserve normal non-security workflow behavior.
- [ ] Keep separate Full and Play Store compile tasks, focused tests, and static TLS gate.
- [ ] Add explicit report text for the pre-existing `publish-plugin-pages` environment-protection failure, distinct from product/test failures.
- [ ] Document signed HTTP query parameters (`signature`, `expires`, `key`) as intentionally allowed compatibility identifiers with residual cleartext tampering risk; do not block them indiscriminately.
- [ ] Push and verify all gates with GitHub Actions only; no APK/release artifact publication.

### Task 3: Final Review and Handoff

**Files:**
- Review all Task 1/2 changes and CI logs.

- [ ] Run source-only static checks and `git diff --check`.
- [ ] Request independent task reviews and one whole-branch review.
- [ ] Re-run Full/Play Store compile and focused security tests after verified fixes.
- [ ] Record final SHA, CI URLs/job IDs, test counts, release guard, Pages failure, changed files, and residual risks.
