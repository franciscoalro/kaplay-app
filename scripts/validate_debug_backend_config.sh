#!/usr/bin/env bash
set -euo pipefail

url=${KAPLAY_SUPABASE_URL:-}
key=${KAPLAY_SUPABASE_ANON_KEY:-}
build_only=${KAPLAY_BUILD_ONLY:-false}
allow_loopback=${KAPLAY_ALLOW_LOOPBACK_BACKEND:-false}

if [[ -z "$url" ]]; then
  echo 'KAPLAY_SUPABASE_URL is required for a debug build' >&2
  exit 2
fi
if [[ -z "$key" ]]; then
  echo 'KAPLAY_SUPABASE_ANON_KEY is required for a debug build' >&2
  exit 2
fi
case "${key,,}" in
  *service_role*|*sb_secret_*|eyj*)
    echo 'privileged or JWT-like key material is not allowed in a debug build' >&2
    exit 2
    ;;
esac

host=$(printf '%s' "$url" | sed -E 's#^[A-Za-z][A-Za-z0-9+.-]*://##; s#/.*$##; s/:.*$//')
if [[ -z "$host" ]]; then
  echo 'KAPLAY_SUPABASE_URL has no host' >&2
  exit 2
fi

if [[ "$build_only" == 'true' ]]; then
  if [[ "$url" != 'https://build-only.invalid' || "$key" != 'build-only-placeholder' ]]; then
    echo 'build-only artifacts must use the non-routable placeholder endpoint and key' >&2
    exit 2
  fi
  exit 0
fi

if [[ "$url" == 'https://build-only.invalid' || "$key" == 'build-only-placeholder' ]]; then
  echo 'build-only placeholder is valid only when KAPLAY_BUILD_ONLY=true' >&2
  exit 2
fi

is_loopback=false
case "$host" in
  localhost|*.local|127.*|10.*|192.168.*|172.16.*|172.17.*|172.18.*|172.19.*|172.2[0-9].*|172.3[0-1].*|0.0.0.0|::1)
    is_loopback=true
    ;;
esac

if [[ "$is_loopback" == 'true' && "$allow_loopback" != 'true' ]]; then
  echo 'loopback/private debug backend requires KAPLAY_ALLOW_LOOPBACK_BACKEND=true' >&2
  exit 2
fi
if [[ "$is_loopback" != 'true' && "$allow_loopback" == 'true' ]]; then
  echo 'KAPLAY_ALLOW_LOOPBACK_BACKEND=true is only valid for loopback/private hosts' >&2
  exit 2
fi

case "$url" in
  https://*) ;;
  http://*)
    [[ "$is_loopback" == 'true' && "$allow_loopback" == 'true' ]] || { echo 'non-loopback debug backend must use HTTPS' >&2; exit 2; }
    ;;
  *) echo 'KAPLAY_SUPABASE_URL must use https:// (or explicit loopback http://)' >&2; exit 2 ;;
esac
