# CloudStream providers for KAPlay

QuickJS ports of the KA Play/CloudStream providers. Every scraper implements KAPlay's `getStreams(tmdbId, mediaType, season, episode)` contract.

## Tomato behavior ported

- TMDB title and alternative-title lookup.
- Tomato search with feed fallback.
- Anime detail and season selection.
- Zero-based episode paging with host failover.
- Dubbed/subbed season handling.
- Stream quality mapping: `fhd` → 1080p, `mhd` → 720p, `hd` → HD, `shd` → 480p, `sd` → 360p.
- Expired signed-URL rejection.
- Edge/production host retry behavior.

The original CloudStream Tomato provider ships a rotating public session token; the port keeps that compatibility fallback and lets the user override it through the password-masked Settings field. Rotate the setting when the backend invalidates the session.

## Install from the published repository

In KAPlay, open **Settings → Plugins → Add repository** and install:

`https://franciscoalro.github.io/kaplay-app/`

The custom build pre-fills this address in the repository field; the user still confirms installation so removing the repository remains respected.

## Install from a local checkout

KAPlay repositories are loaded over HTTP(S), not from device filesystem paths. Serve this directory:

```bash
cd plugins/tomato
python3 -m http.server 8000
```

On the device/emulator, open **Settings → Plugins → Add repository** and enter a URL reachable from that device, for example:

- Physical device on the same LAN: `http://192.168.1.10:8000/`
- Android emulator reaching the host: `http://10.0.2.2:8000/`

KAPlay resolves that URL to `manifest.json`, downloads the provider JavaScript files, and exposes their stream actions. Tomato can use the compatibility fallback or an overridden Bearer JWT.

For permanent distribution, publish `manifest.json` and `tomato.js` in the same HTTPS directory and add that directory URL to KAPlay.

## Tests

```bash
node test/tomato.test.cjs
node test/provider_contract.test.cjs
```

The test covers normalization, response variants, matching, season/episode aliases, quality parsing, settings, and a complete mocked `getStreams` request flow.

## Backend behavior

The Tomato backend is intermittently returning HTTP 500 for episode and stream endpoints. The port preserves the original provider's alternating host retries and returns an empty stream list instead of crashing KAPlay when both origins fail.
