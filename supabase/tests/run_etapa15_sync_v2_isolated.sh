#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
container="ka-etapa15-sync-v2-$$"

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "$container" \
  -e POSTGRES_PASSWORD=kaplay-test \
  -e POSTGRES_DB=kaplay_sync_test \
  postgres:17-alpine >/dev/null

for _ in $(seq 1 30); do
  if docker exec "$container" pg_isready -U postgres -d kaplay_sync_test >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container" pg_isready -U postgres -d kaplay_sync_test >/dev/null

files=(
  "supabase/tests/etapa15_bootstrap.sql"
  "supabase/migrations/20261029000000_ka_profiles.sql"
  "supabase/proposed/20261031000001_ka_profile_identity_expand_v2.sql"
  "supabase/proposed/20261031000002_ka_sync_storage_uuid_v2.sql"
  "supabase/proposed/20261031000003_ka_sync_rpcs_v2.sql"
  "supabase/tests/etapa15_sync_v2_test.sql"
)

for relative in "${files[@]}"; do
  docker cp "$repo_root/$relative" "$container:/tmp/$(basename "$relative")"
done

for relative in "${files[@]}"; do
  docker exec "$container" psql \
    -v ON_ERROR_STOP=1 \
    -U postgres \
    -d kaplay_sync_test \
    -f "/tmp/$(basename "$relative")"
done
