# KA Play Debug Login Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent debug APKs from silently targeting `https://localhost` or production and make the authentication backend endpoint explicit and verifiable before the APK is built.

**Architecture:** Keep authentication code unchanged. Centralize endpoint validation in the existing Gradle runtime-config generation path, require an explicit endpoint for debug builds, and mark build-only artifacts with a non-routable placeholder. Preserve the integration workflow as the only path for real isolated-backend authentication.

**Tech Stack:** Gradle Kotlin DSL, Kotlin Multiplatform, GitHub Actions, existing KA Play runtime configuration generation.

**Spec:** `docs/agents/KA_PLAY_PRODUCTION_GATE.md` and `AGENTS.md`; observed failure: debug APK attempted `https://localhost/auth/v1/token` from the emulator.

## Global Constraints

- Do not run Gradle, Java, Kotlin compilation, APK assembly, installation, signing, or release publication on the VPS.
- Do not apply migrations, change production Auth/RLS, or write production data.
- Do not embed service-role credentials, private keys, JWTs, or user passwords.
- Build-only artifacts must not claim functional authentication.
- Real login verification must use the isolated integration workflow and a reachable HTTPS backend.

## Review Focus

- Missing debug endpoint must fail before APK generation, not fall back to production or localhost; test the Gradle validation branch.
- Build-only endpoint must remain non-routable and must not be treated as functional auth; test workflow environment values.
- Explicit loopback endpoints must require an opt-in flag; test localhost and private-address rejection without the flag.
- Public HTTPS endpoint with publishable key must remain accepted for an explicitly authorized debug preview; test the accepted configuration.
- Error text must identify the missing/invalid configuration without printing keys or passwords; test sanitized diagnostics.

---

### Task 1: Add explicit runtime backend validation

**Files:**
- Modify: `composeApp/build.gradle.kts:299-340`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/core/network/ServerConfigurationTest.kt`

**Interfaces:**
- Consumes: `KAPLAY_SUPABASE_URL`, `KAPLAY_SUPABASE_ANON_KEY`, `KAPLAY_BUILD_ONLY`, and `KAPLAY_ALLOW_LOOPBACK_BACKEND` runtime configuration values.
- Produces: deterministic validation rules used by runtime-config generation; no change to `AuthRepository`.

- [ ] **Step 1: Write failing tests** for loopback rejection without opt-in, loopback acceptance with opt-in, and public HTTPS acceptance using the existing server-configuration test style.
- [ ] **Step 2: Run the focused common test command in the GitHub Actions environment definition and confirm the new assertions fail before implementation. Do not run Gradle on the VPS; record `NÃO TESTADO` locally if the runner is unavailable.
- [ ] **Step 3: Implement the smallest validation helper in the existing configuration path. Require a nonblank debug URL; reject localhost/private URLs unless `KAPLAY_ALLOW_LOOPBACK_BACKEND=true`; allow the non-routable build-only URL only when `KAPLAY_BUILD_ONLY=true`; never include key values in thrown messages.
- [ ] **Step 4: Run the focused test in GitHub Actions and confirm PASS.
- [ ] **Step 5: Commit with `fix: require explicit debug backend endpoint`.

### Task 2: Make the build-only workflow safe and reproducible

**Files:**
- Modify: `.github/workflows/ka-play-build-only.yml:17-25`
- Modify: `.github/workflows/pr-full-debug-build.yml` if it supplies runtime values
- Test: workflow validation via GitHub Actions YAML/check job

**Interfaces:**
- Consumes: Task 1 validation rules.
- Produces: build-only APK configured with `https://build-only.invalid` and a placeholder publishable key, clearly marked `functional_homologation=NOT_PERFORMED`.

- [ ] **Step 1: Add a workflow assertion that the generated `SupabaseConfig.kt` uses the build-only endpoint and never contains `localhost` or the production project URL.
- [ ] **Step 2: Run the workflow YAML/check validation and confirm the assertion fails against the old production/localhost configuration if reproducible in CI.
- [ ] **Step 3: Set `KAPLAY_BUILD_ONLY=true`, `KAPLAY_SUPABASE_URL=https://build-only.invalid`, and `KAPLAY_SUPABASE_ANON_KEY=build-only-placeholder`; remove production endpoint values from the build-only job.
- [ ] **Step 4: Run the build-only workflow in GitHub Actions; verify APK metadata, generated config scan, and artifact metadata. Do not claim login functionality from this job.
- [ ] **Step 5: Commit with `ci: isolate build-only backend configuration`.

### Task 3: Document the real debug-login path

**Files:**
- Modify: `RELATORIO_BUILD_KA_PLAY.md` or add `docs/agents/KA_PLAY_DEBUG_LOGIN.md`

**Interfaces:**
- Consumes: validated configuration from Tasks 1–2.
- Produces: exact instructions for a debug APK with an isolated HTTPS backend, including the required endpoint, allowlist, anon key source, and verification criterion.

- [ ] **Step 1: Document that the build-only APK cannot authenticate and that `https://localhost` is invalid from the emulator unless an explicit, tested loopback bridge is configured.
- [ ] **Step 2: Document the integration workflow inputs without including secrets or real user credentials.
- [ ] **Step 3: Verify the document against the workflow names and current gate status; mark Android login as `NÃO TESTADO` until a new integration artifact is installed.
- [ ] **Step 4: Commit with `docs: document isolated debug login configuration`.

## Final Verification

- Run the focused configuration tests in GitHub Actions.
- Run the build-only workflow and inspect the generated config/artifact metadata.
- Run the integration workflow only with an isolated HTTPS backend and synthetic test account.
- Do not install or test the new APK on the current emulator until the artifact provenance and backend endpoint are verified.
