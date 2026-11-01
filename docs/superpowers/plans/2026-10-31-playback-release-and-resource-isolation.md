# Playback Release and Resource Isolation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Make release assembly explicit-manual-only and prove same-origin resources in one directory cannot exchange playback credentials.

**Architecture:** Keep Debug Full/Play Store compilation and security tests on every push. Gate the existing release job on `workflow_dispatch` plus an explicit `publish_release=true` input, so a merge push cannot sign, assemble, or upload an APK. Extend the existing real MockWebServer test suite with a same-origin/same-directory redirect and change production policy only if the test demonstrates credential transfer.

**Tech Stack:** GitHub Actions, Kotlin, OkHttp, MockWebServer/okhttp-tls 5.3.2.

**Spec:** `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`

## Constraints

- No merge, release, APK publication, Supabase/auth/sync/rewards/UI changes.
- Full/Play Store debug compile and focused security tests remain push-triggered.
- Release may run only from explicit `workflow_dispatch` with `publish_release=true`.
- Same-origin `/media/a.m3u8` and `/media/b.m3u8` use distinct credentials; redirect A→B must not send A's credential to B.
- Valid HTTPS and legacy credential-free HTTP remain compatible.
- Gradle/tests run only in GitHub Actions.

### Task 1: Resource isolation regression

- [ ] Add a real MockWebServer test with two same-origin resources in the same directory and distinct Authorization values; redirect A to B and assert A's header is present only on A and absent on B.
- [ ] Run RED/GREEN in GitHub Actions; change production policy only if the regression fails.
- [ ] Commit and independently review the test/policy diff.

### Task 2: Manual-only release workflow

- [ ] Add a boolean `publish_release` input to `workflow_dispatch`, default false.
- [ ] Change `assemble-full-release` job condition to require `github.event_name == 'workflow_dispatch' && inputs.publish_release == true`; preserve debug/test jobs for push.
- [ ] Add workflow/report evidence that push/merge paths skip release and no APK upload occurs.
- [ ] Run Full/Play Store compile, focused tests, static gate, and verify release skipped in GitHub Actions.
- [ ] Commit and independently review the workflow/report diff.

### Task 3: Final audit

- [ ] Run diff/static checks and a whole-branch independent review.
- [ ] Record final SHA, CI run/job IDs, tests, release skip, Pages classification, and residual risks.
