# Etapa 18 SQL follow-up

Added deterministic daily-quota assertion: after 24 accepted claims for one owned profile, the 25th claim must raise `P0001`.

- Commit: `6b3189b7`
- CI run: `41312864564`
- Isolated PostgreSQL job: success
- Sync host job: success
- Artifact: `ka-etapa18-isolated-sql-41312864564`
- No production Supabase access.

Remaining: true concurrent transaction race testing and a full hosted Supabase RLS matrix remain separate follow-ups.
