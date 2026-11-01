# Task 1 Report — Progress v1/v2 Contract

## Status

**IMPLEMENTED + COMMITTED.** No local Gradle/Java run. GitHub Actions CI dispatched for validation.

## Finding

`SupabaseProgressSyncAdapter.pull()` had a dead v1 code path that was called when
`sinceLastWatched != null || limit != null`. It invoked `sync_pull_watch_progress`
(v1, proposed-local-only, SECURITY DEFINER, `p_profile_id` integer, `last_watched > since`
filter semantics) with **wrong param names** (`p_profile_id` instead of `p_profile_index`).
No production caller passes `sinceLastWatched` or `limit`, so this branch was dead
code — but it would silently call an unverified, non-production RPC if ever reached.

## Commits (on branch `agentic/ka-p0-sync`)

| SHA | Message | Files |
|-----|---------|-------|
| `b4c9ab86` | fix: use v2 keyset contract for progress pulls | `SupabaseProgressSyncAdapter.kt`, `SupabaseSyncPagingTest.kt` |
| `c42f374c` | test: define progress v2 filter semantics | `SupabaseProgressSyncAdapterTest.kt` |
| `32facdc5` | test: keep since filter out of descending cursor | `SupabaseSyncPagingTest.kt` |
| `9d2828b8` | fix: preserve since progress filter semantics | `SupabaseProgressSyncAdapter.kt` |
| `<pending>` | test: verify v1/v2 contract in isolated SQL | `supabase/tests/etapa15_sync_v2_test.sql` |

## What was implemented

### Client-side contract correction (`SupabaseProgressSyncAdapter.kt`)

- **All** `pull(profileId, sinceLastWatched, limit)` calls route through v2 keyset
  pagination (`sync_pull_watch_progress_v2`, UUID-scope `p_profile_index`).
- The `effectiveLimit` is coerced to `1..500` to match v2 SQL cap.
- `sinceLastWatched` is **not** encoded as a v2 descending `after` cursor (that would
  invert the `last_watched > since` contract). Instead it is applied as a Kotlin-side
  post-filter in `filterProgressSyncRecords`.
- `limit` is applied after the v2 snapshot is collected.
- v1 RPC `sync_pull_watch_progress` is **not called**.

### `filterProgressSyncRecords` — since/limit post-filter

```kotlin
internal fun filterProgressSyncRecords(
    records: List<ProgressSyncRecord>,
    sinceLastWatched: Long?,
    limit: Int?,
): List<ProgressSyncRecord> {
    require(limit == null || limit in 1..supabaseSyncPageSize)
    return records.asSequence()
        .filter { sinceLastWatched == null || it.lastWatched > sinceLastWatched }
        .let { sequence -> limit?.let(sequence::take) ?: sequence }
        .toList()
}
```

### `buildProgressPullParams` — v2 param contract

```kotlin
internal fun buildProgressPullParams(profileId, sinceLastWatched, limit): JsonObject =
    buildJsonObject {
        put("p_profile_index", profileId)
        put("p_limit", limit?.let(::JsonPrimitive) ?: JsonNull)
        // sinceLastWatched applied after fetch (see filterProgressSyncRecords)
    }
```

## Host tests (GitHub Actions: `ka-play-sync-tests.yml`)

Tests run via `gradlew :composeApp:testAndroidHostTest
--tests 'com.kaplay.app.features.watching.sync.*'`:

| Test class | Tests |
|------------|-------|
| `SupabaseSyncPagingTest` | keyset 1201-row pagination, cursor advance guard, page failure propagation, mutation batching, watched delete key, v2 `p_profile_index` param, v2 cursor pairing, v2 param preservation |
| `SupabaseProgressSyncAdapterTest` | `filterProgressSyncRecords` with `since` filter + limit, unfiltered full record retention |

## Isolated SQL tests (local-only, Docker postgres:17-alpine)

Run via `supabase/tests/run_etapa15_sync_v2_isolated.sh`:

- 1201 rows across 3 keyset pages (500 / 500 / 201), no gaps, no duplicates.
- Stale progress rejected (no event emitted for `last_watched < existing`).
- Atomic rollback on oversized batch (> 500 items).
- Anonymous and cross-user access rejection.
- **New:** v2 RPCs use SECURITY INVOKER (not DEFINER), no PUBLIC/anon execute grant.
- **New:** v2 `sync_pull_watch_progress_v2` param names verified (`p_profile_index`,
  `p_after_last_watched`, `p_after_progress_key`) — NOT v1's `p_profile_id`.
- **New:** Keyset page counts verified after extension (500/500/201).
- v1 RPC (`sync_pull_watch_progress`) confirmed **proposed-local-only**; not called by
  production client after Task 1 fix.

## CI Runs

CI dispatch via `gh workflow run ka-play-sync-tests.yml --ref agentic/ka-p0-sync`
(not triggered on push — branch is `agentic/ka-p0-sync`, not `kaplay-rebrand`).
Reports uploaded as artifact `ka-play-sync-test-reports`.

## Unresolved v1/v2/production Compatibility

| Item | Status | Note |
|------|--------|------|
| v1 RPC not in production | CONFIRMED | `sync_pull_watch_progress` exists only in `supabase/proposed/20261030000003_ka_sync_rpcs_local.sql`; never applied to production `iibureqvpurmbgqupnvl` |
| Old APK (v1 client) vs new APK (v2 client) | UNRESOLVED | Both write to same `ka_sync.watch_progress` table via v2 idempotent upsert; v1 `since_last_watched` semantics differ from v2 keyset — coexistence needs prod smoke test |
| `sinceLastWatched` as post-filter | SEMANTIC NOTE | v2 fetches descending snapshot; filter `{ lastWatched > since }` applied in Kotlin matches v1 semantics for callers that pass `sinceLastWatched` |
| SQL migration not applied | BLOCKED | C3 candidate `20261031000003_ka_sync_rpcs_v2.sql` not applied to production; no migration gate before release |

## Constraints verified

- No local Gradle/Java executed.
- No production Supabase `iibureqvpurmbgqupnvl` touched.
- No draft/publish/signing/artifact actions.
- No secrets/JWT/keystore referenced in implementation.
