# Tomato for Nuvio

QuickJS port of the KA Play/CloudStream Tomato provider. It implements Nuvio's `getStreams(tmdbId, mediaType, season, episode)` contract and returns HLS qualities exposed by the Tomato API.

## What was ported

- TMDB title and alternative-title lookup.
- Tomato search with feed fallback.
- Anime detail and season selection.
- Zero-based episode paging with host failover.
- Dubbed/subbed season handling.
- Stream quality mapping: `fhd` → 1080p, `mhd` → 720p, `hd` → HD, `shd` → 480p, `sd` → 360p.
- Expired signed-URL rejection.
- Edge/production host retry behavior.

No Tomato credential is committed. Configure the Bearer token through the scraper Settings screen. The Nuvio settings field is password-masked by the accompanying UI change.

## Install from the published repository

In Nuvio, open **Settings → Plugins → Add repository** and install:

`https://franciscoalro.github.io/nuvio-build-ci/`

The custom build pre-fills this address in the repository field; the user still confirms installation so removing the repository remains respected.

## Install from a local checkout

Nuvio repositories are loaded over HTTP(S), not from device filesystem paths. Serve this directory:

```bash
cd plugins/tomato
python3 -m http.server 8000
```

On the device/emulator, open **Settings → Plugins → Add repository** and enter a URL reachable from that device, for example:

- Physical device on the same LAN: `http://192.168.1.10:8000/`
- Android emulator reaching the host: `http://10.0.2.2:8000/`

Nuvio resolves that URL to `manifest.json`, downloads `tomato.js`, and exposes a Tomato settings action. Enter a Tomato Bearer JWT there before testing a title.

For permanent distribution, publish `manifest.json` and `tomato.js` in the same HTTPS directory and add that directory URL to Nuvio.

## Tests

```bash
node test/tomato.test.cjs
```

The test covers normalization, response variants, matching, season/episode aliases, quality parsing, settings, and a complete mocked `getStreams` request flow.

## Backend behavior

The Tomato backend is intermittently returning HTTP 500 for episode and stream endpoints. The port preserves the original provider's alternating host retries and returns an empty stream list instead of crashing Nuvio when both origins fail.
