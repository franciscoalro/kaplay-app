# KA Play profile-sync contract v1

## Identity and ownership

Every profile operation is scoped by the authenticated `user_id` and a bounded `profile_index`. The client may select its own profile index but never supplies another user's ownership. Anonymous sessions are treated as local-only for cloud sync.

## RPC surface

- `sync_pull_profiles`: returns profiles owned by `auth.uid()`.
- `sync_push_profiles`: writes only profiles owned by `auth.uid()` and rejects reassignment.
- Existing feature sync RPCs receive the same profile scope and publish state through `SyncManager.profileSyncStates`.

## State contract

The Kotlin contract preserves `LOADING`, `SYNCING`, `SYNCED`, `PENDING`, `OFFLINE`, and `ERROR`. `ERROR` carries a stable backend error code when available; network failures map to `OFFLINE`. UI must not render success when the backend has not confirmed it.

## Acceptance

Isolated PostgreSQL tests must prove same-user profile access, cross-user rejection, anonymous rejection, stable RPC errors, and profile-index isolation. Host tests must prove state mapping and profile identity preservation. GitHub Actions artifacts are the source of evidence; production Supabase is never contacted.
