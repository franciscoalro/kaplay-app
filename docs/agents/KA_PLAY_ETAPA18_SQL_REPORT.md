# KA Play Etapa 18 — SQL isolated report

## Implemented
- Runner now waits for query-ready PostgreSQL, captures command output and container logs, preserves non-zero assertion failures, and uploads sanitized artifacts.
- Added rewards assertions for owned-profile claim, same-event idempotency, cross-profile rejection, and cross-user replay rejection.
- Registered the isolated PostgreSQL job in `ka-play-sync-tests.yml`; no production URL/secrets are used.

## Evidence
- Branch: `agentic/ka-etapa18-sql`
- Commits: `ef60fa01`, `6a75d471`, `5e444cc4`, `39278234`, `b4d1cd6e`
- GitHub Actions run `41311389770`: isolated PostgreSQL assertions **success**; sync host tests **success**.
- Artifact: `ka-etapa18-isolated-sql-41311389770`, containing `run.log` and `postgres.log`.
- Earlier runs failed reproducibly and uploaded logs: first due the timestamp assertion, second due a PostgreSQL readiness race; both failures are now fixed and visible in artifact history.

## Residual limits
- Concurrent quota race and real Supabase RLS role matrix still need a dedicated follow-up assertion; this evidence covers isolated ownership/idempotency, not production readiness.
- No production migration or Supabase project access occurred.
