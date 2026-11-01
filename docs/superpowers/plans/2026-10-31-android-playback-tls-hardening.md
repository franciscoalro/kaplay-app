# Android Playback TLS Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android playback validate HTTPS normally, prevent credential leakage through cleartext or unauthorized origins, and retain ordinary HTTP playback compatibility.

**Architecture:** A focused OkHttp network interceptor will enforce one transport/origin policy on every media exchange, including redirects and HLS/DASH child requests. The player will use Android/OkHttp platform TLS defaults; each top-level resource explicitly registers its URL and sensitive header name/value pairs, so credentials survive only on their owning HTTPS origin while exact HTTP top-level URLs remain credential-free compatibility sources.

**Tech Stack:** Kotlin Multiplatform, Android Media3/ExoPlayer, OkHttp 4.12, Robolectric, kotlin.test, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`

## Global Constraints

- Do not modify the global Android network-security configuration in this implementation.
- Do not disable certificate or hostname verification anywhere.
- Do not add automatic HTTPS-to-HTTP fallback.
- Do not publish or release an APK.
- Gradle compilation and tests run exclusively in GitHub Actions; never execute `gradlew`, `java`, `kotlinc`, assemble, or compile locally on this VPS.
- Keep legacy HTTP playback working when the URL contains no credentials; strip sensitive headers before the request.
- Block HTTP URLs containing user-info credentials or these case-insensitive query keys: `token`, `access_token`, `auth`, `authorization`, `api_key`, `apikey`, `password`, `passwd`, `secret`.
- Treat origin as scheme + lowercase host + effective port.
- Preserve a sensitive HTTPS header only when its destination origin and exact header name/value pair were registered by the resource that owns it; never authorize a video's token solely because a subtitle uses another origin.
- Apply the policy to playlists/manifests, segments, encryption keys, redirects, alternate audio, and Media3 external-subtitle requests.
- Preserve existing Full and Play Store distribution behavior.

## Review Focus

- Encoded/mixed-case sensitive query keys such as `ToKeN`, `access%5Ftoken`, and duplicate keys must block cleartext before network I/O; Task 1 tests pin this.
- Origin comparison must normalize default ports while rejecting a different explicit port; Task 1 tests pin this.
- HTTPS signed URLs with non-listed parameters such as `signature`, `expires`, or `key` must remain usable; Task 1 tests pin this.
- A primary HTTP HLS/DASH source may load credential-free HTTP child URLs, while an HTTPS primary must reject HTTP child/downgrade requests; Task 1 tests pin this.
- Cross-origin HTTPS child requests must keep compatibility headers but lose sensitive headers; Task 1 tests pin this and Task 2 verifies the interceptor is installed.

---

### Task 1: Playback Request Security Policy

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicy.kt`
- Create: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicyTest.kt`
- Modify: `.github/workflows/build.yml:66-75`

**Interfaces:**
- Consumes: OkHttp `HttpUrl`, `Request`, `Interceptor`, and `Response`.
- Produces:
  - `internal data class PlaybackTopLevelRequest(val url: String, val headers: Map<String, String> = emptyMap())`
  - `internal data class PlaybackRequestSecurityContext`
  - `internal companion object fun PlaybackRequestSecurityContext.create(primary: PlaybackTopLevelRequest, additional: Iterable<PlaybackTopLevelRequest> = emptyList()): PlaybackRequestSecurityContext`
  - `internal fun PlaybackRequestSecurityContext.secure(request: Request): Request`
  - `internal class PlaybackRequestSecurityInterceptor(private val context: PlaybackRequestSecurityContext) : Interceptor`

- [ ] **Step 1: Add the policy test class and CI test selector**

Create `PlaybackRequestSecurityPolicyTest` with these tests and assertions:

- `sameOriginHttpsRetainsRegisteredSensitiveAndCompatibilityHeaders`: registered `Authorization`, `Cookie`, `User-Agent`, `Referer`, and `Origin` remain on an HTTPS request matching the primary origin.
- `sameOriginHttpsStripsUnregisteredSensitiveValue`: a sensitive header with the right name but a value not registered by that resource is removed.
- `subtitleOriginAcceptsOnlySubtitleCredential`: a subtitle-origin request keeps the credential registered in that subtitle's `PlaybackTopLevelRequest`, while the primary video's bearer token is removed.
- `crossOriginHttpsStripsSensitiveHeadersOnly`: cross-origin HTTPS removes `Authorization`, `Cookie`, `Proxy-Authorization`, `X-Api-Key`, and `Api-Key`, but retains `User-Agent`, `Referer`, and `Origin`.
- `differentPortIsNotAuthorizedButDefaultPortNormalizes`: `https://media.example` authorizes port 443, not 8443.
- `httpAlwaysStripsSensitiveHeadersWithoutMutatingInput`: an explicitly supplied HTTP source retains compatibility headers, removes every sensitive header case-insensitively, and leaves the original `Request` unchanged.
- `httpRejectsUserInfoAndSensitiveQueryKeys`: assert `IOException` for user-info credentials and each listed query key, including mixed-case, percent-encoded underscore, and duplicate-key cases.
- `httpAllowsCommonSignedMediaParameters`: `signature`, `expires`, and `key` do not trigger URL blocking.
- `httpsPrimaryRejectsHttpChildAndDowngrade`: an HTTPS primary rejects HTTP `.m3u8`, `.ts`, `.mpd`, `.m4s`, and `.key` requests unless the exact URL was supplied as an additional top-level source.
- `httpPrimaryAllowsCredentialFreeHttpChildren`: an HTTP primary permits credential-free cross-origin playlist and segment requests after sensitive-header stripping.
- `adaptiveChildrenUseSameOriginPolicy`: same-origin HTTPS `.m3u8`, `.ts`, `.mpd`, `.m4s`, and `.key` retain authorization; cross-origin variants lose it.
- `invalidAndBlankAdditionalUrlsAuthorizeNothing`: malformed and blank URLs do not add credential origins or cleartext exceptions.

Add `--tests 'com.kaplay.app.features.player.PlaybackRequestSecurityPolicyTest'` to the existing Android host-test command in `.github/workflows/build.yml`.

- [ ] **Step 2: Commit and push the red test**

```bash
git add composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicyTest.kt .github/workflows/build.yml
git commit -m "test(player): define playback request security policy"
git push buildci HEAD:security/playback-tls-hardening
```

Expected GitHub Actions result: `kaplay-build / compile-full-debug` fails because `PlaybackRequestSecurityContext` and `PlaybackRequestSecurityInterceptor` do not exist.

Verify with:

```bash
gh run list --repo franciscoalro/kaplay-app --branch security/playback-tls-hardening --workflow build.yml --limit 1
gh run watch --repo franciscoalro/kaplay-app <run-id> --exit-status
```

- [ ] **Step 3: Implement `PlaybackRequestSecurityContext` and interceptor**

In `PlaybackRequestSecurityPolicy.kt`:

- Parse top-level URLs with OkHttp `toHttpUrlOrNull()`.
- Store the primary scheme, canonical exact HTTP top-level URLs, and sensitive header name/value pairs grouped by canonical HTTPS origin.
- Register only sensitive headers actually owned by each `PlaybackTopLevelRequest`; do not copy all primary headers into additional resources.
- Compare origins by scheme, lowercase host, and effective port.
- Decode query names through `HttpUrl.queryParameterName(index)` before case-insensitive comparison.
- Treat `Authorization`, `Cookie`, `Proxy-Authorization`, `X-Api-Key`, and `Api-Key` as sensitive headers, case-insensitively.
- On HTTP: reject user-info or listed sensitive query keys; reject child/downgrade HTTP when the primary is HTTPS unless the exact URL is an explicitly supplied HTTP top-level URL; otherwise remove sensitive headers.
- On HTTPS: preserve a sensitive header only when the destination origin and exact case-insensitive-name/value pair were registered; remove every other sensitive header.
- On non-HTTP(S): return the request unchanged because Media3 routes local schemes through other data sources.
- Make the interceptor call `chain.proceed(context.secure(chain.request()))` so it evaluates every network exchange.

- [ ] **Step 4: Push the implementation and verify the policy tests pass in GitHub Actions**

```bash
git add composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicy.kt
git commit -m "fix(player): enforce playback request security policy"
git push buildci HEAD:security/playback-tls-hardening
```

Expected: `PlaybackRequestSecurityPolicyTest` passes; unrelated compile failures, if any, are investigated rather than ignored.

- [ ] **Step 5: Review Task 1 diff**

Run without compiling locally:

```bash
git diff HEAD~2..HEAD -- composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicy.kt composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlaybackRequestSecurityPolicyTest.kt .github/workflows/build.yml
```

Confirm every listed sensitive header/query key and every Review Focus case appears in a test.

---

### Task 2: Strict TLS Client and Media3 Integration

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlayerPlaybackNetworking.kt:1-169`
- Modify: `composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlayerEngine.android.kt:311-330`
- Modify: `composeApp/src/androidFull/kotlin/com/kaplay/app/features/player/PlatformPlaybackDataSourceFactory.android.kt:8-37`
- Modify: `composeApp/src/androidPlaystore/kotlin/com/kaplay/app/features/player/PlatformPlaybackDataSourceFactory.android.kt:8-32`
- Modify: `composeApp/src/androidFull/kotlin/com/kaplay/app/features/details/components/HeroTrailerPlayerSurface.android.kt:61-68`
- Create: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlayerPlaybackNetworkingSecurityTest.kt`
- Modify: `composeApp/build.gradle.kts:488-495`
- Modify: `.github/workflows/build.yml:66-75`

**Interfaces:**
- Consumes: `PlaybackRequestSecurityContext.create(...)` and `PlaybackRequestSecurityInterceptor` from Task 1.
- Produces:
  - `internal fun createPlaybackHttpClient(context: PlaybackRequestSecurityContext, useLongReadTimeout: Boolean): OkHttpClient`
  - `PlayerPlaybackNetworking.createHttpDataSourceFactory(primary: PlaybackTopLevelRequest, additional: Iterable<PlaybackTopLevelRequest>, defaultHeaders: Map<String, String> = emptyMap(), useLongReadTimeout: Boolean = false): DataSource.Factory`
  - Both distribution-specific `PlatformPlaybackDataSourceFactory.create(...)` methods accept `sourceUrl: String` and `sourceAudioUrl: String?` in addition to their existing arguments, and build resource-specific credential scopes.

- [ ] **Step 1: Write failing networking integration tests**

Add test-only `com.squareup.okhttp3:okhttp-tls:5.3.2` beside the existing MockWebServer 5.3.2 dependency, then create `PlayerPlaybackNetworkingSecurityTest` with:

- `clientRejectsUntrustedHttpsCertificate`: run a MockWebServer with a self-signed certificate that is not in the client trust store and assert the request fails with an SSL handshake/certificate exception; no fallback request is observed.
- `clientUsesDefaultHostnameVerifier`: compare the hostname verifier and certificate pinner with a fresh default `OkHttpClient` and assert no permissive override is installed.
- `clientInstallsOnePlaybackSecurityNetworkInterceptor`: assert exactly one `PlaybackRequestSecurityInterceptor` is present in `networkInterceptors`.
- `longTimeoutChangesOnlyReadTimeout`: assert normal and loopback clients share TLS/hostname behavior while read timeout changes from 15 to 65 seconds.
- `factoryAcceptsResourceSpecificScopes`: construct the data-source factory with video headers, alternate-audio headers, and distinct subtitle headers and verify construction succeeds without network I/O.

Add `--tests 'com.kaplay.app.features.player.PlayerPlaybackNetworkingSecurityTest'` to `.github/workflows/build.yml`.

- [ ] **Step 2: Commit/push the red integration tests and confirm failure in GitHub Actions**

```bash
git add composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player/PlayerPlaybackNetworkingSecurityTest.kt .github/workflows/build.yml
git commit -m "test(player): require strict playback TLS integration"
git push buildci HEAD:security/playback-tls-hardening
```

Expected: failure because `createPlaybackHttpClient` and the new factory signature do not exist.

- [ ] **Step 3: Replace permissive TLS with the platform defaults**

In `PlayerPlaybackNetworking.kt`:

- Delete `trustAllManager`, `playbackHostnameVerifier`, `sslContext`, and their imports.
- Build the base client without `sslSocketFactory(...)` or `hostnameVerifier(...)` overrides.
- Keep `IPv4FirstDns`, timeouts, redirects, retry policy, and Sentry breadcrumb behavior.
- Implement `createPlaybackHttpClient(...)` by cloning the strict base client, changing only the long read timeout when requested, and adding one `PlaybackRequestSecurityInterceptor` as a network interceptor.
- Update `createHttpDataSourceFactory(...)` to build its context from the primary and additional top-level URLs.
- Remove the unused `openConnection(...)` helper rather than preserving an alternate TLS path.
- If `createDataSourceFactory(...)` remains, update it to require and forward the same URL context; remove it if repository-wide search confirms it has no callers.

- [ ] **Step 4: Thread top-level URLs through both Android distributions**

In both `PlatformPlaybackDataSourceFactory.android.kt` files:

- Add `sourceUrl: String` and `sourceAudioUrl: String?` parameters.
- Build the primary `PlaybackTopLevelRequest(sourceUrl, defaultRequestHeaders)`.
- Add nonblank `sourceAudioUrl` with `defaultRequestHeaders` as its own top-level request.
- Add each external subtitle as `PlaybackTopLevelRequest(subtitle.url, subtitle.headers.orEmpty())`; never register the video's default sensitive headers for a subtitle-only origin.
- Pass these resource-specific scopes to `PlayerPlaybackNetworking.createHttpDataSourceFactory`.

Update callers:

- `PlayerEngine.android.kt`: pass its existing `sourceUrl` and `sourceAudioUrl`.
- `HeroTrailerPlayerSurface.android.kt`: pass `sourceUrl` and `sourceAudioUrl`; include them in the `remember(...)` keys so a source change cannot reuse an authorization context from the previous trailer.

- [ ] **Step 5: Push the integration and verify both security test classes pass**

```bash
git add composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlayerPlaybackNetworking.kt \
  composeApp/src/androidMain/kotlin/com/kaplay/app/features/player/PlayerEngine.android.kt \
  composeApp/src/androidFull/kotlin/com/kaplay/app/features/player/PlatformPlaybackDataSourceFactory.android.kt \
  composeApp/src/androidPlaystore/kotlin/com/kaplay/app/features/player/PlatformPlaybackDataSourceFactory.android.kt \
  composeApp/src/androidFull/kotlin/com/kaplay/app/features/details/components/HeroTrailerPlayerSurface.android.kt \
  composeApp/build.gradle.kts
git commit -m "fix(player): restore platform TLS validation"
git push buildci HEAD:security/playback-tls-hardening
```

Expected: both playback security test classes pass in GitHub Actions and Full debug compilation succeeds.

- [ ] **Step 6: Confirm no alternate permissive TLS path remains**

Run source-only checks locally (no compilation):

```bash
! grep -RniE 'trustAllManager|HostnameVerifier[[:space:]]*\{[^}]*true|checkServerTrusted.*=[[:space:]]*Unit|sslSocketFactory\(sslContext' \
  composeApp/src/androidMain/kotlin/com/kaplay/app/features/player

grep -Rni 'PlaybackRequestSecurityInterceptor' \
  composeApp/src/androidMain/kotlin/com/kaplay/app/features/player
```

Expected: first command exits 0 with no matches; second shows the interceptor definition and installation.

---

### Task 3: CI Regression Gate for Both Android Distributions

**Files:**
- Modify: `.github/workflows/build.yml:56-75`

**Interfaces:**
- Consumes: the tests and factory signatures from Tasks 1-2.
- Produces: CI gates for Full/Play Store compilation, focused playback-security tests, and forbidden TLS patterns.

- [ ] **Step 1: Add the Play Store compile and static TLS checks**

In the `compile-full-debug` job:

- Keep the existing Full debug compile step.
- Add a separate Gradle invocation for `:androidApp:compilePlaystoreDebugKotlin` with the same `--no-daemon --no-parallel --max-workers=2 --stacktrace` controls. A separate invocation is required because the build script rejects mixed Android distributions in one Gradle request.
- Add a shell step that fails if the player package contains `trustAllManager`, an always-true hostname verifier, a no-op `checkServerTrusted`, or custom playback SSL-context wiring.
- Keep both focused playback security test selectors in the host-test step.
- Do not add assemble, release, upload, or publishing steps.

- [ ] **Step 2: Commit and push the CI gate**

```bash
git add .github/workflows/build.yml
git commit -m "ci: verify playback TLS security on Android variants"
git push buildci HEAD:security/playback-tls-hardening
```

- [ ] **Step 3: Verify the GitHub Actions job completes**

```bash
gh run list --repo franciscoalro/kaplay-app --branch security/playback-tls-hardening --workflow build.yml --limit 1
gh run watch --repo franciscoalro/kaplay-app <run-id> --exit-status
gh run view --repo franciscoalro/kaplay-app <run-id> --log
```

Expected evidence:

- `:androidApp:compileFullDebugKotlin` succeeds.
- `:androidApp:compilePlaystoreDebugKotlin` succeeds.
- `PlaybackRequestSecurityPolicyTest` passes.
- `PlayerPlaybackNetworkingSecurityTest` passes.
- Static TLS regression check reports no forbidden pattern.
- No APK release or publication step is introduced by this task.

---

### Task 4: Final Security and Compatibility Review

**Files:**
- Modify only if review finds a defect in files already listed above.
- Review: `docs/superpowers/specs/2026-10-31-android-playback-tls-hardening-design.md`
- Review: `docs/superpowers/plans/2026-10-31-android-playback-tls-hardening.md`

**Interfaces:**
- Consumes: completed Tasks 1-3 and their GitHub Actions evidence.
- Produces: reviewed branch ready for operator decision; no APK publication.

- [ ] **Step 1: Audit requirement coverage from the final diff**

```bash
git diff kaplay-rebrand...HEAD --check
git diff --stat kaplay-rebrand...HEAD
git diff kaplay-rebrand...HEAD -- \
  composeApp/src/androidMain/kotlin/com/kaplay/app/features/player \
  composeApp/src/androidFull/kotlin/com/kaplay/app/features/player \
  composeApp/src/androidPlaystore/kotlin/com/kaplay/app/features/player \
  composeApp/src/androidFull/kotlin/com/kaplay/app/features/details/components/HeroTrailerPlayerSurface.android.kt \
  composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/player \
  .github/workflows/build.yml
```

Confirm line-by-line:

- Valid HTTPS uses only platform TLS and hostname verification.
- Invalid HTTPS has no insecure retry or HTTP fallback.
- Credential-free HTTP remains allowed.
- Sensitive HTTP headers are stripped.
- Credential-bearing HTTP URLs are blocked.
- Redirect and cross-origin credential rules execute through a network interceptor.
- HLS/DASH child paths are covered by tests.
- Both Android distributions compile in CI.

- [ ] **Step 2: Request an independent code review**

Use `superpowers:requesting-code-review`. The reviewer must focus on OkHttp interceptor ordering, redirect behavior, origin normalization, percent-decoded query names, Media3 default-header propagation, and Full/Play Store parity.

- [ ] **Step 3: Apply only verified review fixes and re-run GitHub Actions**

If review changes code, commit the smallest correction and repeat the Task 3 workflow verification. Do not dismiss a security concern without a reproducing test.

- [ ] **Step 4: Record final evidence without releasing**

Capture the branch name, final commit SHA, GitHub Actions run URL/ID, test counts, Full/Play Store compile results, and static-check output in the handoff. Stop for operator approval before merge, APK build publication, or release.
