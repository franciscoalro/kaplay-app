# Optional Trakt Release Configuration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Remove the artificial Trakt release blocker and ensure no future APK embeds `TRAKT_CLIENT_SECRET`.

**Architecture:** Keep the release workflow's signing properties mandatory, but remove Trakt from its build-time required list. Generate an empty Trakt client secret in `TraktConfig.kt`; the existing `hasRequiredCredentials()` path then reports Trakt unavailable without affecting other features. OAuth PKCE/backend migration remains a separate future design.

**Tech Stack:** Kotlin Gradle configuration, GitHub Actions, existing Trakt auth repository.

**Constraints:** No real credentials, no signing/publish/draft release, no Supabase/auth/sync/rewards changes, Gradle/tests only through GitHub Actions.

### Task 1: Workflow and generated-config correction

- [ ] Write/update regression checks for optional Trakt and secret-free generated config.
- [ ] Remove `TRAKT_CLIENT_ID` and `TRAKT_CLIENT_SECRET` from `required_properties` in `android-release.yml`; keep signing and existing required non-Trakt properties as-is.
- [ ] Change generated `TraktConfig.CLIENT_SECRET` to an empty constant; retain client ID/redirect compatibility and existing unavailable behavior.
- [ ] Push, run Debug Full/Play Store/tests/static gate via GitHub Actions, and run `android-release.yml` `dry-run` only.
- [ ] Record exact runs and confirm no signed APK/artifact was generated.

### Task 2: Independent review and handoff

- [ ] Review diff for secret leakage and workflow correctness.
- [ ] Document OAuth PKCE/backend follow-up, Trakt unavailable behavior, and rotation risk.
- [ ] Do not execute draft/publish/signing.
