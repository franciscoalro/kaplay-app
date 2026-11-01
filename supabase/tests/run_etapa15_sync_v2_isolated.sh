#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
report_dir="${REPORT_DIR:-${repo_root}/.artifacts/ka-etapa18-sql}"
mkdir -p "$report_dir"
run_log="$report_dir/run.log"
: > "$run_log"
container="ka-etapa15-sync-v2-$$"

cleanup() {
  docker logs "$container" >"$report_dir/postgres.log" 2>&1 || true
  docker rm -f "$container" >>"$run_log" 2>&1 || true
}
trap cleanup EXIT

run() {
  printf '\n$ %q' "$@" >>"$run_log"
  printf '\n' >>"$run_log"
  "$@" >>"$run_log" 2>&1
}

run docker run -d --name "$container" \
  -e POSTGRES_PASSWORD=kaplay-test \
  -e POSTGRES_DB=kaplay_sync_test \
  postgres:17-alpine

ready=false
for _ in $(seq 1 60); do
  if docker exec "$container" psql -U postgres -d kaplay_sync_test -v ON_ERROR_STOP=1 -c 'select 1' >>"$run_log" 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo "Postgres did not become query-ready" | tee -a "$run_log" >&2
  exit 1
fi

files=(
  "supabase/tests/etapa15_bootstrap.sql"
  "supabase/migrations/20261029000000_ka_profiles.sql"
  "supabase/migrations/20261028000000_ka_ad_rewards.sql"
  "supabase/migrations/20261101000004_ka_ad_rewards_ownership.sql"
  "supabase/proposed/20261031000001_ka_profile_identity_expand_v2.sql"
  "supabase/proposed/20261031000002_ka_sync_storage_uuid_v2.sql"
  "supabase/proposed/20261031000003_ka_sync_rpcs_v2.sql"
  "supabase/tests/etapa15_sync_v2_test.sql"
  "supabase/tests/etapa18_rewards_test.sql"
)

for relative in "${files[@]}"; do
  run docker cp "$repo_root/$relative" "$container:/tmp/$(basename "$relative")"
done

for relative in "${files[@]}"; do
  run docker exec "$container" psql \
    -v ON_ERROR_STOP=1 \
    -U postgres \
    -d kaplay_sync_test \
    -f "/tmp/$(basename "$relative")"
done

echo "isolated_sql_status=PASS" | tee -a "$run_log"
