# Android Playback TLS Hardening — Design

## Intent

Remove the Android player's silent acceptance of invalid HTTPS certificates and mismatched hostnames without disrupting normal playback or legacy HTTP sources.

## Why

`PlayerPlaybackNetworking` currently installs a trust-all `X509TrustManager` and an always-true `HostnameVerifier` for every Android playback request. This exposes HTTPS streams and any attached request headers to man-in-the-middle interception. The behavior was introduced for parity with Nuvio TV, not for a documented host-specific requirement.

## Goals

- HTTPS playback uses the Android/OkHttp platform trust store and normal hostname verification.
- Valid HTTPS streams keep working without prompts or configuration.
- Existing HTTP stream playback remains available for compatibility in this change.
- Sensitive headers are never sent over cleartext HTTP.
- Cleartext URLs containing user-info credentials or sensitive query parameters are blocked before network I/O.
- Redirects never carry credentials from HTTPS to HTTP or to an unauthorized origin, and HTTPS failures never trigger an automatic HTTP fallback.
- HLS and DASH playlists, manifests, segments, encryption-key requests, and other child requests use the same policy.
- Both Android Full and Play Store variants use the hardened behavior.
- Add focused automated tests for transport/header policy and legitimate playback compatibility.

## Non-goals

- Disable all HTTP playback in this change.
- Add certificate pinning for arbitrary addon/CDN hosts.
- Add a user-facing insecure TLS override.
- Refactor unrelated player or networking code.

## Anti-goals

- Do not maintain a static allowlist of streaming domains; addon hosts are dynamic.
- Do not silently fall back from failed HTTPS validation to trust-all TLS.
- Do not expose a global switch that weakens API, auth, Supabase, or other non-playback traffic.

## Constraints

- The application intentionally supports dynamic addon streams and local media servers.
- Android cleartext traffic remains enabled temporarily because HTTP playback compatibility is user-visible.
- Per repository instructions, Gradle builds and tests must run through GitHub Actions, not locally on this VPS.

## Approaches considered

### 1. Harden HTTPS, retain legacy HTTP — selected

Remove the custom trust manager, SSL context, and hostname verifier. Build the playback `OkHttpClient` with the platform TLS defaults. Before creating a playback request, reject sensitive headers on an `http://` source.

Trade-off: cleartext media remains modifiable in transit, but credentials are not transmitted and normal legacy sources keep working.

### 2. HTTPS-only immediately

Disable cleartext traffic at the manifest/network-security level.

Rejected for this change because it can break IPTV, addons, and self-hosted Jellyfin/Emby installations without a migration path.

### 3. Explicit insecure mode per source

Use strict transport by default, with a persisted per-source exception and visible warning.

Deferred because it requires UI, persistence, source identity, and migration work beyond the security fix. It may be considered later for retiring global cleartext support.

## Design

### TLS client

`PlayerPlaybackNetworking` will use a standard `OkHttpClient.Builder`. No custom `SSLSocketFactory`, trust manager, hostname verifier, or silent retry through an insecure client will remain.

The currently unused `openConnection` helper will be removed so it cannot remain as an alternate permissive TLS path.

### Cleartext header policy

A small pure policy function will classify sensitive header names case-insensitively. At minimum:

- `Authorization`
- `Cookie`
- `Proxy-Authorization`
- `X-Api-Key`
- `Api-Key`

For an `http://` media URL, sensitive headers will be removed before the request is created. Non-sensitive playback headers such as `User-Agent`, `Referer`, and `Origin` remain available for compatibility.

An `http://` URL is rejected before network I/O when it contains user-info credentials or a case-insensitive sensitive query key: `token`, `access_token`, `auth`, `authorization`, `api_key`, `apikey`, `password`, `passwd`, or `secret`. This deliberately avoids broad names such as `key` and `signature`, which are commonly non-secret media identifiers or signed-CDN parameters.

Sensitive headers on HTTPS are preserved only when both the destination origin and the header name/value pair were explicitly registered by a top-level resource: the video and optional audio URLs own the default playback headers, while each external-subtitle URL owns only its own subtitle headers. This prevents a video's bearer token from becoming authorized merely because an unrelated subtitle uses another origin. Origin equality includes scheme, host, and effective port. Redirects and HLS/DASH child requests are evaluated independently before each network exchange: same-origin HTTPS retains registered credentials; cross-origin or unregistered credentials are removed; HTTPS-to-HTTP downgrade requests are rejected unless the HTTP URL was itself an explicitly supplied top-level source. TLS failures never trigger an HTTP retry.

### User experience

- Valid HTTPS playback: no visible change.
- HTTP playback without credentials: no visible change.
- HTTP playback that depends on sensitive credentials: it fails safely rather than leaking credentials. Existing player error/retry UI handles the failure; this change adds no new modal or prompt.
- HTTPS with an invalid certificate or hostname: fails using the existing player error path instead of silently bypassing TLS.

### Tests

Add JVM-compatible tests for the pure transport/header policy:

1. Same-origin HTTPS retains only sensitive header name/value pairs registered by that resource.
2. Cross-origin or unregistered HTTPS credentials are removed while compatibility headers remain.
3. HTTP removes each sensitive header case-insensitively.
4. HTTP retains non-sensitive compatibility headers.
5. HTTP URLs containing user-info or any listed sensitive query key are rejected.
6. HTTPS-to-HTTP downgrade child requests are rejected; explicitly supplied top-level HTTP sources remain compatible when their URLs contain no credentials.
7. HLS/DASH playlist, segment, and key-style paths all receive the same origin/header policy.
8. Blank/invalid source URLs never authorize an origin or weaken the policy.
9. Header filtering does not mutate the caller's map.
10. Both Full and Play Store Android variants compile against the updated factory interface.

Static checks will also confirm that `PlayerPlaybackNetworking` contains no trust-all manager, custom permissive hostname verifier, or custom SSL context.

## Verification

- Run focused tests and the relevant Android compile task only through GitHub Actions, as required by `AGENTS.md`.
- Review the generated diff to ensure only playback networking, focused tests, and necessary CI wiring are touched.
- Confirm the repository contains no permissive TLS constructs in the player path.

## Residual risk: signed HTTP URLs

For legacy CDN compatibility, cleartext HTTP URLs with the non-sensitive query parameters `signature`, `expires`, and `key` remain intentionally allowed. These names are common signed-media identifiers, so blocking them indiscriminately would reject legitimate legacy sources. They do not provide confidentiality or integrity against an active network attacker when transported over HTTP: an attacker can observe, alter, replay, or replace the URL and its signed parameters. HTTPS remains the recommended transport for all signed media. The policy continues to reject HTTP user-info credentials and the explicitly sensitive query names listed above, and it strips sensitive headers before permitted HTTP requests.

## Deferred follow-up

Design a separate migration that disables global cleartext by default and permits explicit, user-approved exceptions for identified local/server sources.
