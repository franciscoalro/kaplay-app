# KA Play Incremento 1 — Perfis + Sincronização Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar perfis e sincronização como slice vertical, com frontend Compose e backend Supabase isolado validados por contratos e CI.

**Architecture:** Frontend and backend use separate branches from `agentic/ka-play-production-toolkit-20261101`. The backend validates existing/profile RPC contracts, ownership, RLS and errors only in ephemeral PostgreSQL. The frontend consumes the stable Kotlin sync state and renders real states without mocks or optimistic success. Integration is a controlled cherry-pick after independent QA/AppSec review.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Supabase/PostgreSQL RPC/RLS, Docker PostgreSQL, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-02-ka-incremento1-perfis-sync-design.md`

## Global Constraints

- Do not access or mutate Supabase production.
- Do not modify `build-tomato-cmp`, candidate `0.9.20`, or its history.
- Do not build, sign, publish, install, or merge automatically.
- Android/Gradle verification runs through GitHub Actions only.
- No frontend fake data or optimistic backend success.
- Keep TLS, profile ownership, RLS, rewards isolation, and error visibility intact.

## Review Focus

- Cross-user and cross-profile profile reads/writes must fail in isolated SQL; covered by backend SQL task.
- Anonymous/authenticated profile classification must not grant ownership; covered by backend role matrix and Kotlin state tests.
- Sync errors must distinguish offline, contract/auth, and transient failure; covered by contract presenter and SQL error assertions.
- Profile switching must publish the correct profile-indexed state; covered by frontend presenter tests and sync integration test.
- Missing/extra RPC fields must fail clearly rather than silently succeeding; covered by SQL `ON_ERROR_STOP` and Kotlin contract tests.

---

### Task 1: Shared profile-sync contract

**Files:**
- Create: `docs/contracts/ka-profile-sync-v1.md`
- Modify: `docs/agents/KA_PLAY_PRODUCTION_GATE.md`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/core/sync/ProfileSyncContractTest.kt`

**Interfaces:**
- Produces exact RPC names, profile identity fields, state/error mapping, ownership rules, and acceptance criteria for Tasks 2–4.

- [ ] Write failing contract tests for required profile identity (`user_id`, `profile_index`), states `SYNCING/SYNCED/PENDING/OFFLINE/ERROR`, and error classes.
- [ ] Run the targeted host test through a temporary CI selector and verify it fails for the missing contract helper.
- [ ] Write the contract document and minimal Kotlin mapping helper used by later tasks.
- [ ] Run the targeted CI test and verify it passes.
- [ ] Commit `docs: define profile sync contract v1`.

### Task 2: Backend isolated profile/RLS validation

**Files:**
- Modify: `supabase/tests/run_etapa15_sync_v2_isolated.sh`
- Create/modify: `supabase/tests/etapa19_profile_sync_test.sql`
- Modify: `.github/workflows/ka-play-sync-tests.yml`
- Create: `docs/agents/KA_PLAY_INCREMENTO1_BACKEND_REPORT.md`

**Interfaces:**
- Consumes the contract from Task 1.
- Produces isolated SQL evidence for RPC shape, ownership, anonymous/authenticated behavior, profile switch isolation, and error propagation.

- [ ] Write failing SQL assertions under authenticated and anonymous roles for same-user profiles and cross-user access.
- [ ] Run the isolated workflow and verify the assertions fail for each missing/incorrect contract case.
- [ ] Implement only candidate migration/RPC changes required by the contract; preserve `SECURITY DEFINER` auth checks and explicit grants.
- [ ] Run the isolated PostgreSQL workflow with `ON_ERROR_STOP=1`, sanitized artifacts, and query-ready readiness; verify all assertions pass.
- [ ] Run `bash -n`, `git diff --check`, and capture workflow/job/artifact IDs in the report.
- [ ] Commit backend code and report separately.

### Task 3: Frontend profile/sync UX

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/profiles/ProfileSelectionScreen.kt`
- Modify/create: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/profiles/ProfileSyncStatusPresenter.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/profiles/ProfileSyncStatusPresenterTest.kt`
- Modify: `.github/workflows/build.yml`
- Create: `docs/agents/KA_PLAY_INCREMENTO1_FRONTEND_REPORT.md`

**Interfaces:**
- Consumes `SyncManager.profileSyncStates` and the Task 1 state/error contract.
- Produces visible per-profile loading, synced, pending, offline, error/reconnect, and local states with D-pad-safe semantics.

- [ ] Add failing tests for authenticated/cloud, anonymous/local, profile-index state selection, and all sync/error states.
- [ ] Run the host test in GitHub Actions and verify the expected failure.
- [ ] Implement the minimal presenter and Compose banner/action without hardcoded success or unrelated redesign.
- [ ] Add focus/semantics assertions where the existing Compose host harness supports them.
- [ ] Run `compileFullDebugKotlin` and host tests in GitHub Actions; record protected Pages failures separately from compile/test status.
- [ ] Commit frontend code and report separately.

### Task 4: Vertical integration and independent review

**Files:**
- Modify: `docs/agents/KA_PLAY_PRODUCTION_GATE.md`
- Create: `docs/agents/KA_PLAY_INCREMENTO1_INTEGRATION_REPORT.md`

- [ ] Dispatch independent QA review for frontend state, profile switching, D-pad and offline behavior.
- [ ] Dispatch independent AppSec/Supabase review for grants, RLS, RPC exposure, auth role handling and error leakage.
- [ ] Cherry-pick only reviewed backend/frontend commits into the toolkit branch; do not merge automatically.
- [ ] Run both GitHub Actions workflows against the integrated toolkit branch and link jobs/artifacts.
- [ ] Verify `build-tomato-cmp` remains `e59fcc7f58678ac9dc1e483ab786a3d40f255349`.
- [ ] Update the readiness matrix with proven, untested, and blocking items; keep production status NO-GO until all P0 gates pass.
- [ ] Commit the integration report.
