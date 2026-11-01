# Incremento 1 backend — profile sync

Added isolated SQL assertions for the profile-sync contract:
- same-user pull returns only owned profiles;
- cross-user profile data is absent;
- anonymous pull/write is isolated;
- invalid profile limits fail with stable SQL errors;
- direct profile RLS and profile reassignment are checked.

The runner applies `etapa19_profile_sync_test.sql`, keeps `ON_ERROR_STOP=1`, captures sanitized artifacts, and never contacts production.

Evidence:
- Branch: `agentic/ka-incremento1-backend`
- Final commit: `3623627f`
- GitHub Actions run: `41321534023`
- Isolated PostgreSQL and sync host jobs: **success**
- Artifact: `ka-etapa18-isolated-sql-41321534023`
- `bash -n` and `git diff --check`: pass.

Residual: this is isolated PostgreSQL evidence; hosted Supabase RLS/concurrency and Android instrumented integration remain unverified.
