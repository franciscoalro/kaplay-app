#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
VALIDATOR="$SCRIPT_DIR/validate_debug_backend_config.sh"

expect_fail() {
  if "$@" >/tmp/kaplay-config-test.out 2>&1; then
    echo "expected failure: $*" >&2
    exit 1
  fi
}

expect_pass() {
  "$@" >/tmp/kaplay-config-test.out 2>&1
}

expect_fail env KAPLAY_SUPABASE_URL=https://localhost KAPLAY_SUPABASE_ANON_KEY=key "$VALIDATOR"
expect_fail env KAPLAY_SUPABASE_URL= KAPLAY_SUPABASE_ANON_KEY=key "$VALIDATOR"
expect_pass env KAPLAY_SUPABASE_URL=https://dev.example.supabase.co KAPLAY_SUPABASE_ANON_KEY=key "$VALIDATOR"
expect_pass env KAPLAY_BUILD_ONLY=true KAPLAY_SUPABASE_URL=https://build-only.invalid KAPLAY_SUPABASE_ANON_KEY=build-only-placeholder "$VALIDATOR"
expect_pass env KAPLAY_ALLOW_LOOPBACK_BACKEND=true KAPLAY_SUPABASE_URL=http://127.0.0.1:3301 KAPLAY_SUPABASE_ANON_KEY=key "$VALIDATOR"

echo 'all debug backend configuration checks passed'
