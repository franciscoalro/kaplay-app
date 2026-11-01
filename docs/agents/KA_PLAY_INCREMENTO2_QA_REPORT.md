# KA Play Incremento 2 — QA report

Date: 2026-11-02  
Branch: `agentic/ka-incremento2-qa`  
Scope: isolated SQL persistence/identity/profile/offline-replay checks only.

## Changes

- Added `supabase/tests/etapa20_reopen_identity_queue_test.sql`.
- Added the test to `supabase/tests/run_etapa15_sync_v2_isolated.sh`, which is the existing PostgreSQL 17 isolated workflow entry point.

The deterministic SQL test proves:

1. **Persistence/reopen:** committed library and watch-progress rows remain readable after `psql` `\\connect` starts a new database session.
2. **Logout/login identity:** switching JWT claims from account A to account B hides A's profiles and rejects A's profile slot; switching back restores exactly A's data.
3. **Profile isolation:** profile 2 cannot read profile 1's library or progress rows within the same account.
4. **Offline/reconnect queue replay:** replaying an identical queued library/progress mutation after reconnect creates one row and one event, not duplicates; the origin client id is retained by the RPC event path.

No production URL, production Supabase project, real account, destructive write, signing key, release action, or APK publication was used.

## Verification performed

Local isolated runner (Docker `postgres:17-alpine`, synthetic UUIDs only):

```text
ETAPA15_SYNC_V2_ISOLATED_OK | 1201 | 15
ETAPA19_PROFILE_SYNC_TEST_OK
ETAPA20_REOPEN_IDENTITY_QUEUE_OK
isolated_sql_status=PASS
```

Also passed `git diff --check`.

## Android instrumentation / CI blocker

The existing workflow `.github/workflows/ka-play-integration-android.yml` is correctly restricted to an explicitly allowlisted isolated HTTPS backend. It requires all of these GitHub Actions values:

- repository variable `KAPLAY_ISOLATED_BASE_URL`
- repository variable `KAPLAY_ALLOWED_BACKEND_HOSTS`
- secret `KAPLAY_ISOLATED_ANON_KEY`
- secret `KAPLAY_ISOLATED_TEST_EMAIL`
- secret `KAPLAY_ISOLATED_TEST_PASSWORD`

The current checkout has no safe way to inspect or invent those GitHub values. Therefore Android instrumentation against a hosted isolated backend is **BLOCKED / NOT TESTED** in this session. The workflow will fail closed before build if any value is missing, and it rejects the production Supabase hostname. The SQL assertions above remain runnable without hosted secrets and are the evidence for this branch.

To complete the Android gate, dispatch the existing `KA Play Android integration test` workflow only after the listed isolated-backend values are provisioned; do not substitute production credentials or URL. Record the workflow run/job and uploaded reports in the follow-up gate report.

## Verdict

- Isolated SQL persistence, identity switching, profile isolation, and idempotent reconnect replay: **TESTED / PASS**.
- Hosted Android instrumentation: **BLOCKED / NOT TESTED** pending isolated CI secrets and allowlisted host.
- Production deployment or migration authorization: **NOT requested and not approved**.
