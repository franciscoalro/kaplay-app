# KA Play Incremento 2 — Backend report

## Scope

Implemented isolated PostgreSQL/Supabase persistence for profile-scoped favorites and watch history, alongside the existing UUID-scoped library/watch/progress RPCs. No production project was contacted.

## Changes

- `supabase/proposed/20261102000001_ka_favorites_history_rpcs_v2.sql`
  - Adds UUID/profile-owned favorite and history tables plus append-only event streams.
  - Adds keyset pagination RPCs (`sync_pull_favorites_v2`, `sync_pull_history_v2`) with bounded limits and cursor validation.
  - Adds idempotent upsert/delete RPCs with monotonic history conflict handling and origin-client event metadata.
  - Keeps `SECURITY INVOKER`, private schema, RLS/force-RLS, and authenticated-only grants.
- `supabase/tests/etapa15_sync_v2_test.sql`
  - TDD assertions for favorite/history persistence, reconnect idempotency, stale conflict handling coverage through existing progress tests, cross-user rejection, and anonymous rejection.
- `supabase/tests/run_etapa15_sync_v2_isolated.sh`
  - Applies the new migration in the Docker PostgreSQL runner.

## TDD / CI evidence

1. RED observed before implementation: isolated runner failed at `etapa15_sync_v2_test.sql:55` with `function public.sync_push_favorites_v2(integer, jsonb, unknown) does not exist`.
2. GREEN after implementation: `supabase/tests/run_etapa15_sync_v2_isolated.sh` completed with `isolated_sql_status=PASS`.
3. GREEN after replay/isolation assertions: rerun completed with `isolated_sql_status=PASS`.

The runner uses `postgres:17-alpine` locally and is the only database exercised. Production Supabase project `iibureqvpurmbgqupnvl` was not contacted.
