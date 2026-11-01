# KA Play Incremento 2 — AppSec review

**Reviewer:** independent AppSec review, branch `agentic/ka-incremento2-appsec`  
**Scope:** toolkit and Incremento 2 seams for Supabase Auth/RLS ownership, profile isolation, sync pagination/concurrency/conflict handling, token exposure, and debug APK configuration.  
**Environment:** source review plus isolated PostgreSQL 17 Docker run only. No production Supabase connection or write was performed.

## Executive result

**FAIL / no production GO.** The isolated SQL contract suite passes, but the repository contains credential-bearing Auth data and the default debug configuration targets the production Supabase host. Incremento 2's `_v2` schema/RPCs are also staged under `supabase/proposed`, while the application calls them; deployment state is therefore not proven.

## Ranked findings

### F-01 — CRITICAL — Auth refresh tokens committed in repository

**Status: FAIL / release blocker.** `evidence/homolog-20261030/kaplay_dev_backup.sql` is tracked (`git ls-files` confirms it) and contains plaintext `auth.refresh_tokens.token` values at lines 1341–1351. Several rows have `revoked = f` (for example lines 1342–1343 and 1346–1347). This is direct bearer credential exposure, regardless of the fixture email domains.

**Required action:** revoke/rotate every token represented by the dump and any sessions derived from it; remove or fully redact the dump from the repository/history; add a secret/credential scan gate. Do not reuse this dump in any environment.

### F-02 — HIGH — Default debug APK points at production Supabase

**Status: FAIL / release blocker.** `composeApp/build.gradle.kts:323–334` uses the production URL `https://iibureqvpurmbgqupnvl.supabase.co` and a non-empty publishable key as fallbacks when `KAPLAY_SUPABASE_URL`/`KAPLAY_SUPABASE_ANON_KEY` are absent. `androidApp/build.gradle.kts:146–149` only changes the debug application id; it does not require an isolated backend or change the endpoint. A locally built debug APK can therefore authenticate and sync against production by default.

Some CI jobs override this (integration uses isolated variables; build-only uses `build-only.invalid`), but this is not a safe default for developer/debug artifacts. Require an explicit non-production URL/key for debug and fail the build when missing; keep production endpoint selection release-only and explicit.

### F-03 — HIGH — Incremento 2 backend deployment seam is unproven/mismatched

**Status: FAIL / blocker.** The Android client calls `_v2` RPCs, e.g. `SupabaseProgressSyncAdapter.kt:30,44,84,130,145`, while the corresponding schema/storage/RPC files are under `supabase/proposed/20261031000001–3_*.sql`, not `supabase/migrations/`. The active migration directory contains only the older profile/reward migrations. The isolated runner explicitly applies proposed files before testing, so `isolated_sql_status=PASS` proves only the synthetic assembled schema—not that a deployable production migration sequence exists or has been applied.

**Required action:** produce a reviewed, ordered migration path and verify function signatures/grants/RLS against the target non-production Supabase project before any production release. Do not infer readiness from the isolated runner.

### F-04 — MEDIUM — Profile replacement remains last-writer-wins under concurrent devices

**Status: FAIL (integrity).** `supabase/proposed/20261031000001_ka_profile_identity_expand_v2.sql:41–115` changes profile sync to upsert then delete omitted rows, but has no revision/ETag/compare-and-swap condition, server version, or origin-client conflict policy. Two devices can read the same profile list, make independent edits, and the later request delete/overwrite the earlier edit. The original active migration is worse (`20261029000000_ka_profiles.sql:84–120` deletes all caller rows before inserting).

**Required action:** add a per-user/profile-set revision (or equivalent conditional write), reject stale writes with a stable conflict error, and test concurrent writes from two sessions. `p_origin_client_id` is accepted but does not provide conflict protection or idempotency.

### F-05 — MEDIUM — Pagination/resource behavior is not fully contract-safe in the client

**Status: FAIL (resource/convergence risk).** The v2 SQL has bounded keyset cursors and limits (good), but `SupabaseProgressSyncAdapter.pull()` (`SupabaseProgressSyncAdapter.kt:70–105`) walks all keyset pages and only then applies `sinceLastWatched` and the caller limit locally (`:104`, `:172–175`). A request for a small/filtered result can therefore read the entire profile history, creating avoidable bandwidth/CPU/resource consumption. The RPC does not accept the `sinceLastWatched` predicate.

**Required action:** encode the filter in the server RPC or define a bounded, cursor-compatible contract that cannot force a full-history scan; add tests for large datasets and concurrent inserts between pages.

## PASS findings / positive evidence

- **Profile RLS and cross-user isolation:** `ka_profiles` has RLS and owner predicate `(select auth.uid()) = user_id`; anonymous JWTs are explicitly denied in both policy branches (`20261029000000_ka_profiles.sql:24–41`). The isolated profile test passed: `isolated_sql_status=PASS`, including same-user visibility, cross-user rejection, anonymous read/write isolation, profile-index validation, reassignment protection, and non-`SECURITY DEFINER` profile RPC guard.
- **v2 ownership boundary:** proposed storage uses immutable profile UUIDs with `(profile_id,user_id)` foreign keys (`20261031000002_ka_sync_storage_uuid_v2.sql:11–31`) and private `ka_sync` schema, RLS, `FORCE ROW LEVEL SECURITY`, and owner checks (`:118–145`). RPCs resolve profile index through the caller's `auth.uid()` (`20261031000003_ka_sync_rpcs_v2.sql:8–35`) and use `security invoker`.
- **Pagination validation:** v2 RPCs enforce bounded limits and paired cursor fields, with deterministic ordering for library/watched/progress snapshots. Delta RPCs require non-negative cursors and bounded limits.
- **Client key exposure:** no `service_role`/secret Supabase key was found in tracked source; the generated client uses the publishable/anon key. This does not mitigate F-01's committed refresh tokens.
- **Token logging:** source search found no direct Supabase access/refresh-token logging. Error paths should still be checked in release instrumentation; this review did not claim runtime log proof.

## Verification and coverage limits

Executed `./supabase/tests/run_etapa15_sync_v2_isolated.sh` against Docker PostgreSQL 17; result: `isolated_sql_status=PASS`. This is review-only isolated validation. No production Supabase was contacted. I did not claim exploitability against a live deployment, did not validate production migration history, and did not perform a two-device race test against a running backend.

**Conclusion:** Incremento 2 remains **FAIL / blocked** until F-01 and F-02 are closed, the v2 migration deployment state is independently verified, and concurrency/conflict semantics are tested and documented.
