package com.kaplay.app.features.plugins

object EmbeddedTomatoPlugin {
    const val MANIFEST_URL = "https://franciscoalro.github.io/kaplay-app/manifest.json"
    const val SCRAPER_ID = "https://franciscoalro.github.io/kaplay-app/manifest.json:tomato"

    val CODE_TOMATO: String = """const API_HOSTS = [
  "https://edge.betomato.com",
  "https://prod-api.tomatoanimes.com",
];

const QUALITY_FIELDS = [
  ["fhd", "1080p"],
  ["mhd", "720p"],
  ["hd", "HD"],
  ["shd", "480p"],
  ["sd", "360p"],
];

function normalizeToken(value) {
  return String(value || "").trim().replace(/^Bearer\s+/i, "");
}

function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);

  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach((token) => {
    if (rightTokens.has(token)) common += 1;
  });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

function requestHeaders(token, kind) {
  const headers = {
    "User-Agent": kind === "stream" ? "tomato-android" : "okhttp/4.11.0",
    Authorization: `Bearer ${'$'}{token}`,
    Accept: "application/json, text/plain, */*",
  };
  if (kind === "stream" || kind === "post") headers["Content-Type"] = "application/json";
  if (kind !== "stream") headers["request-time"] = String(Date.now());
  if (kind === "metadata") headers["x-app"] = "1.4.3";
  return headers;
}

function retryDelay() {
  return new Promise((resolve) => setTimeout(resolve, 150));
}

async function requestJson(path, token, options, attempts) {
  const requestOptions = options || {};
  const maxAttempts = attempts || API_HOSTS.length;
  const method = requestOptions.method || "GET";
  const kind = requestOptions.kind || (path.endsWith("/stream") ? "stream" : method === "POST" ? "post" : "metadata");
  let lastError = null;

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const host = API_HOSTS[attempt % API_HOSTS.length];
    try {
      const response = await fetch(`${'$'}{host}${'$'}{path}`, {
        method,
        headers: requestHeaders(token, kind),
        body: requestOptions.body == null ? undefined : JSON.stringify(requestOptions.body),
      });
      if (response.ok) return { host, data: await response.json() };
      lastError = new Error(`Tomato HTTP ${'$'}{response.status} for ${'$'}{path}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt + 1 < maxAttempts) await retryDelay();
  }

  throw lastError || new Error(`Tomato request failed for ${'$'}{path}`);
}

function extractSearchItems(payload) {
  if (!payload || typeof payload !== "object") return [];
  const candidates = [
    payload.result,
    payload.data && payload.data.result,
    payload.data && payload.data.data,
    Array.isArray(payload.data) ? payload.data : null,
  ];
  for (const value of candidates) {
    if (Array.isArray(value) && value.length) return value;
  }
  return [];
}

function itemId(item) {
  const value = item && (item.anime_id ?? item.id ?? item.animeId);
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function itemTitle(item) {
  return item && (item.anime_name || item.name || item.title || "");
}

function collectFeedItems(value, output) {
  if (Array.isArray(value)) {
    value.forEach((entry) => collectFeedItems(entry, output));
    return output;
  }
  if (!value || typeof value !== "object") return output;
  if (itemId(value) && itemTitle(value)) output.push(value);
  Object.keys(value).forEach((key) => collectFeedItems(value[key], output));
  return output;
}

function bestTitleMatch(titles, items) {
  let best = null;
  let bestScore = 0;
  titles.forEach((title) => {
    items.forEach((item) => {
      const score = titleScore(title, itemTitle(item));
      if (score > bestScore) {
        bestScore = score;
        best = item;
      }
    });
  });
  return bestScore >= 250 ? best : null;
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (!TMDB_API_KEY) throw new Error("TMDB_API_KEY is not configured in KAPlay");
  const normalizedType = mediaType === "movie" ? "movie" : "tv";
  const response = await fetch(
    `https://api.themoviedb.org/3/${'$'}{normalizedType}/${'$'}{encodeURIComponent(tmdbId)}` +
      `?api_key=${'$'}{encodeURIComponent(TMDB_API_KEY)}&language=pt-BR&append_to_response=alternative_titles`,
  );
  if (!response.ok) throw new Error(`TMDB HTTP ${'$'}{response.status}`);
  const data = await response.json();
  const values = [data.title, data.name, data.original_title, data.original_name];
  const alternatives = data.alternative_titles;
  const alternativeValues = alternatives && (alternatives.titles || alternatives.results);
  if (Array.isArray(alternativeValues)) {
    alternativeValues.forEach((entry) => values.push(entry.title || entry.name));
  }
  const seen = new Set();
  return values.filter((value) => {
    const key = normalizeTitle(value);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

async function resolveAnime(titles, token) {
  const searchTitles = titles.slice(0, 3);
  for (const title of searchTitles) {
    try {
      const response = await requestJson(
        "/v2/content/search",
        token,
        {
          method: "POST",
          body: { token, search: title, content_type: "all", page: 0 },
        },
        6,
      );
      const match = bestTitleMatch(titles, extractSearchItems(response.data));
      if (match) return match;
    } catch (_) {
      // The original provider also falls back to the feed when search is unstable.
    }
  }

  const feed = await requestJson("/v2/animes/feed", token, null, 3);
  return bestTitleMatch(titles, collectFeedItems(feed.data, []));
}

function animeDetails(payload) {
  if (!payload || typeof payload !== "object") return {};
  return payload.data && typeof payload.data === "object" ? payload.data : payload;
}

function seasonNumber(season, index) {
  const explicit = Number(season && season.season_number);
  if (Number.isFinite(explicit) && explicit > 0) return explicit;
  const label = String((season && (season.season_name || season.name)) || "");
  const match = label.match(/(?:season|temporada)\s*(\d+)/i);
  return match ? Number(match[1]) : index + 1;
}

function selectSeasons(payload, requestedSeason) {
  const details = animeDetails(payload);
  const seasons = details.anime_seasons || details.seasons || [];
  if (!Array.isArray(seasons)) return [];
  const hasRequestedSeason = requestedSeason !== undefined && requestedSeason !== null;
  const target = Number(hasRequestedSeason ? requestedSeason : 1);
  const exact = seasons.filter((season, index) => seasonNumber(season, index) === target);
  if (exact.length) return exact;
  return hasRequestedSeason ? [] : seasons.slice(0, 1);
}

function episodeItems(payload) {
  if (!payload || typeof payload !== "object") return [];
  if (Array.isArray(payload.data)) return payload.data;
  if (Array.isArray(payload.episodes)) return payload.episodes;
  return [];
}

function episodeId(item) {
  const value = item && (item.ep_id ?? item.episode_id ?? item.episodeId ?? item.id);
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function episodeNumber(item) {
  const value = item && (item.episode_number ?? item.ep_number ?? item.episodeNumber ?? item.number);
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

async function resolveEpisode(season, requestedEpisode, token) {
  const seasonId = Number(season.season_id ?? season.id);
  if (!Number.isFinite(seasonId) || seasonId <= 0) return null;
  const target = Number(requestedEpisode || 1);
  const seenIds = new Set();

  for (let page = 0; page < 20; page += 1) {
    let response;
    try {
      response = await requestJson(
        `/season/${'$'}{seasonId}/episodes`,
        token,
        { method: "POST", body: { token, page, order: "ASC" } },
        page === 0 ? 3 : 2,
      );
    } catch (_) {
      break;
    }
    const items = episodeItems(response.data);
    const match = items.find((item) => episodeNumber(item) === target);
    if (match) return match;
    items.forEach((item) => {
      const id = episodeId(item);
      if (id) seenIds.add(id);
    });
    if (!items.length) break;
    const declaredTotal = Number(response.data.episodes);
    if (Number.isFinite(declaredTotal) && seenIds.size >= declaredTotal) break;
  }
  return null;
}

function signedUrlExpired(value) {
  try {
    const match = String(value).match(/[?&]Policy=([^&]+)/i);
    if (!match) return false;
    let encoded = decodeURIComponent(match[1]).replace(/-/g, "+").replace(/_/g, "/");
    while (encoded.length % 4) encoded += "=";
    const policy = atob(encoded);
    const expiration = policy.match(/"exp"\s*:\s*(\d+)/i);
    return expiration ? Math.floor(Date.now() / 1000) >= Number(expiration[1]) : false;
  } catch (_) {
    return false;
  }
}

function streamResults(payload, dubbed) {
  const streams = payload && payload.streams;
  if (!streams || typeof streams !== "object") return [];
  const candidates = QUALITY_FIELDS.filter(([field]) => {
    const url = streams[field];
    return typeof url === "string" && /^https?:\/\//i.test(url);
  });
  if (candidates.length && candidates.every(([field]) => signedUrlExpired(streams[field]))) return [];

  const label = dubbed ? "Dublado" : "Legendado";
  return candidates.map(([field, quality]) => ({
    title: `Tomato • ${'$'}{label} • ${'$'}{quality}`,
    name: `Tomato ${'$'}{label}`,
    provider: "Tomato",
    quality,
    language: "Português",
    type: "hls",
    headers: { "User-Agent": "tomato-android" },
    url: streams[field],
  }));
}

async function getStreams(tmdbId, mediaType, season, episode) {
  const defaultToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6NDg0NjcyOSwidXVpZCI6ImQ0ODk1NjZjLTI0NDMtNDU3OS1iMzkwLWI1YjQxOTgzMTA5MCIsImlhdCI6MTc5MDUwNjI4MH0.3MP87IJav4bhPJzt5YUUv1mEeOdWp2zxo5_hc6w51YU";
  const token = normalizeToken(SCRAPER_SETTINGS && SCRAPER_SETTINGS.bearerToken) || defaultToken;
  if (!token) {
    console.error("Tomato: configure the bearer token in scraper settings");
    return [];
  }

  try {
    const titles = await fetchTmdbTitles(tmdbId, mediaType);
    const anime = await resolveAnime(titles, token);
    const animeId = itemId(anime);
    if (!animeId) return [];

    const details = await requestJson(`/v2/anime/${'$'}{animeId}`, token, null, 3);
    const seasons = selectSeasons(details.data, season);
    const resolved = [];

    for (const seasonItem of seasons) {
      const episodeItem = await resolveEpisode(seasonItem, episode, token);
      const id = episodeId(episodeItem);
      if (!id) continue;
      try {
        const stream = await requestJson(`/v2/anime/episode/${'$'}{id}/stream`, token, null, 4);
        const dubbed = Boolean(episodeItem.dubbed) || Number(seasonItem.season_dubbed) === 1;
        resolved.push(...streamResults(stream.data, dubbed));
      } catch (_) {
        // Try the next matching season (for example, a separate dubbed season).
      }
    }

    const seen = new Set();
    return resolved.filter((item) => {
      if (seen.has(item.url)) return false;
      seen.add(item.url);
      return true;
    });
  } catch (error) {
    console.error("Tomato:", error && error.message ? error.message : error);
    return [];
  }
}

function onSettings() {
  return [
    { type: "header", label: "Tomato" },
    {
      type: "info",
      label: "Informe o token Bearer da sua conta/instalação Tomato. O token não é incluído neste repositório.",
    },
    {
      type: "text",
      key: "bearerToken",
      label: "Token Bearer",
      placeholder: "eyJ...",
      description: "Aceita o JWT puro ou o valor completo iniciado por Bearer.",
      isPassword: true,
    },
  ];
}

module.exports = {
  getStreams,
  onSettings,
  _test: {
    normalizeToken,
    normalizeTitle,
    titleScore,
    extractSearchItems,
    collectFeedItems,
    bestTitleMatch,
    seasonNumber,
    selectSeasons,
    episodeItems,
    episodeId,
    episodeNumber,
    signedUrlExpired,
    streamResults,
  },
};
"""

    val CODE_ANIMEFIRE: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const API_URL = "https://api.animefire.one";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
    "Accept": "application/json, text/plain, */*",
    "Origin": "https://animefire.one",
    "Referer": "https://animefire.one/"
  };

  let matchedAnime = null;
  for (const title of titles.slice(0, 3)) {
    try {
      const searchRes = await fetch(API_URL + "/animes?q=" + encodeURIComponent(title), { headers });
      if (searchRes.ok) {
        const body = await searchRes.json();
        const items = body && body.data ? body.data : [];
        let best = null, bestScore = 0;
        for (const item of items) {
          const names = [item.titles && item.titles.br, item.titles && item.titles.us, item.titles && item.titles.jp].filter(Boolean);
          for (const name of names) {
            const sc = titleScore(title, name);
            if (sc > bestScore) { bestScore = sc; best = item; }
          }
        }
        if (bestScore >= 250 && best) {
          matchedAnime = best;
          break;
        }
      }
    } catch (_) {}
  }

  if (!matchedAnime || !matchedAnime.id) return [];

  try {
    const detailRes = await fetch(API_URL + "/anime/" + matchedAnime.id, { headers });
    if (!detailRes.ok) return [];
    const detailBody = await detailRes.json();
    const episodes = (detailBody.data && detailBody.data.episodes) || [];

    const targetEpNum = Number(episode || 1);
    const targetSeason = Number(season || 1);

    const ep = episodes.find(e => {
      const epNum = Number(e.number);
      const epSeason = Number(e.season || 1);
      return epNum === targetEpNum && (season ? epSeason === targetSeason : true);
    }) || episodes.find(e => Number(e.number) === targetEpNum);

    if (!ep || !ep.id) return [];

    const epRes = await fetch(API_URL + "/episode/" + ep.id, { headers });
    if (!epRes.ok) return [];
    const epData = await epRes.json();
    const streams = (epData.data && epData.data.streams) || [];

    const results = [];
    streams.forEach(st => {
      if (!st.url) return;
      const audio = st.audio === "dub" ? "Dublado" : "Legendado";
      const qualities = Array.isArray(st.qualities) ? st.qualities : ["720p"];
      qualities.forEach(q => {
        results.push({
          title: "AnimeFire • " + audio + " • " + q,
          name: "AnimeFire " + audio,
          provider: "AnimeFire",
          quality: q,
          language: "Português",
          type: st.url.includes(".m3u8") ? "hls" : "file",
          headers: { "Referer": "https://animefire.one/" },
          url: st.url
        });
      });
    });

    return results;
  } catch (_) {
    return [];
  }
}

function onSettings() {
  return [
    { type: "header", label: "AnimeFire" },
    { type: "info", label: "Provedor nativo de animes AnimeFire (Dublado e Legendado)." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_SUPERCINE: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://supercine-tv.net";

function unpackJs(p, a, c, k) {
  while (c--) {
    if (k[c]) {
      const token = c.toString(a);
      p = p.replace(new RegExp("\\b" + token + "\\b", "g"), k[c]);
    }
  }
  return p;
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const isMovie = mediaType === "movie";
    const embedUrl = isMovie
      ? BASE_URL + "/embed-api/?tmdb=" + encodeURIComponent(tmdbId) + "&type=movies"
      : BASE_URL + "/embed-api/?tmdb=" + encodeURIComponent(tmdbId) + "&type=tvshows&season=" + encodeURIComponent(season || 1) + "&episode=" + encodeURIComponent(episode || 1);

    const res = await fetch(embedUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer": BASE_URL + "/"
      }
    });
    if (!res.ok) return [];
    const html = await res.text();

    const results = [];
    // Extract direct m3u8 or mp4
    const urlMatches = html.match(/(https?:\/\/[^"'\s]+\.(?:m3u8|mp4)[^"'\s]*)/gi) || [];
    urlMatches.forEach((u, idx) => {
      results.push({
        title: "SuperCine • Servidor " + (idx + 1) + (u.includes(".m3u8") ? " • HLS" : " • MP4"),
        name: "SuperCine",
        provider: "SuperCine",
        quality: "Auto",
        language: "Português",
        type: u.includes(".m3u8") ? "hls" : "file",
        headers: { "Referer": BASE_URL + "/" },
        url: u
      });
    });

    // Check packed JS
    const packedMatch = html.match(/eval\(function\(p,a,c,k,e,d\)[\s\S]*?\.split\('\|'\)\)\)/);
    if (packedMatch) {
      try {
        const pRegex = /\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)/;
        const pMatch = packedMatch[0].match(pRegex);
        if (pMatch) {
          const unpacked = unpackJs(pMatch[1], parseInt(pMatch[2]), parseInt(pMatch[3]), pMatch[4].split("|"));
          const innerUrls = unpacked.match(/(https?:\/\/[^"'\s]+\.(?:m3u8|mp4)[^"'\s]*)/gi) || [];
          innerUrls.forEach((u, idx) => {
            results.push({
              title: "SuperCine (Extra) • Servidor " + (idx + 1),
              name: "SuperCine",
              provider: "SuperCine",
              quality: "Auto",
              language: "Português",
              type: u.includes(".m3u8") ? "hls" : "file",
              headers: { "Referer": BASE_URL + "/" },
              url: u
            });
          });
        }
      } catch (_) {}
    }

    return results;
  } catch (_) {
    return [];
  }
}

function onSettings() {
  return [
    { type: "header", label: "SuperCine" },
    { type: "info", label: "Extrai streams do SuperCine via API de embed TMDB direta." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_ANIMESORION: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://animesorion.cc";

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    const type = mediaType === "movie" ? "movie" : "tv";
    const s = season || 1;
    const ep = episode || 1;
    const ajaxUrl = "https://playerflix.ink/inc/Ajax.php?type=" + type + "&id=" + encodeURIComponent(tmdbId) +
      (type === "tv" ? "&season=" + encodeURIComponent(s) + "&episode=" + encodeURIComponent(ep) : "");

    const res = await fetch(ajaxUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://myembed.biz/",
        "X-Requested-With": "XMLHttpRequest"
      }
    });
    if (!res.ok) return [];
    const data = await res.json();
    const options = (data && data.data && data.data.options) || [];

    const results = [];
    options.forEach(opt => {
      const url = opt.embed || opt.url;
      if (!url) return;
      const label = opt.label || opt.lang || "Principal";
      results.push({
        title: "AnimesOrion • " + label,
        name: "AnimesOrion " + label,
        provider: "AnimesOrion",
        quality: "Auto",
        language: "Português",
        type: url.includes(".m3u8") ? "hls" : "file",
        headers: { "Referer": "https://playerflix.ink/" },
        url: url
      });
    });

    return results;
  } catch (_) {
    return [];
  }
}

function onSettings() {
  return [
    { type: "header", label: "AnimesOrion" },
    { type: "info", label: "Serviço de streaming de animes AnimesOrion via playerflix." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_VELOPLAYTV: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://fastcdn.bond/velo/tv";
let cachedToken = null;

async function login() {
  if (cachedToken) return cachedToken;
  try {
    const payload = JSON.stringify({
      canal_code: "75qC",
      device_id: "ee612a7d-a900-4860-91ad-5a31468cc312",
      password: "pbkdf2_sha256${'$'}1200000${'$'}TiUO0Gu2Ttcb45XR4xPvmC${'$'}Ry1LAe8SayrzAhKgyp0PIMSp9HDnjXbQ2JRC2OFpL+0=",
      username: "sZJ3Z9pEyN"
    });
    const res = await fetch(BASE_URL + "/user/v1/login", {
      method: "POST",
      headers: {
        "User-Agent": "Velo Play/1.2.0 (Linux; Android 11)",
        "Content-Type": "application/json; charset=UTF-8",
        "Accept-Language": "pt-BR"
      },
      body: payload
    });
    if (res.ok) {
      const j = await res.json();
      if (j && j.token && j.token.access) {
        cachedToken = j.token.access;
        return cachedToken;
      }
    }
  } catch (_) {}
  return null;
}

async function getStreams(tmdbId, mediaType, season, episode) {
  const token = await login();
  if (!token) return [];

  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Velo Play/1.2.0 (Linux; Android 11)",
    "Authorization": "Bearer " + token,
    "Accept-Language": "pt-BR"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/mar/v1/asset/search?q=" + encodeURIComponent(title) + "&page=1&page_size=20", { headers });
      if (!searchRes.ok) continue;
      const searchData = await searchRes.json();
      const assets = (searchData && searchData.assets) || [];

      let bestAsset = null, bestScore = 0;
      for (const a of assets) {
        const sc = titleScore(title, a.title);
        if (sc > bestScore) { bestScore = sc; bestAsset = a; }
      }

      if (bestScore >= 250 && bestAsset) {
        const assetId = bestAsset._id;
        const isSeries = mediaType === "tv";

        let playinfoUrl = BASE_URL + "/mar/v1/asset/" + assetId + "/playinfo";
        if (isSeries) {
          const childRes = await fetch(BASE_URL + "/mar/v1/asset/" + assetId + "/children", { headers });
          if (childRes.ok) {
            const childData = await childRes.json();
            const items = childData.items || childData.children || [];
            const targetEp = Number(episode || 1);
            const foundChild = items.find(c => Number(c.seq || c.num || 0) === targetEp) || items[0];
            if (foundChild && foundChild._id) {
              playinfoUrl = BASE_URL + "/mar/v1/asset/" + foundChild._id + "/playinfo";
            }
          }
        }

        const playRes = await fetch(playinfoUrl, { headers });
        if (!playRes.ok) continue;
        const playJson = await playRes.json();
        const info = playJson.info || playJson;
        const streams = info.play || info.streams || [];

        const results = [];
        streams.forEach(st => {
          if (!st.url) return;
          results.push({
            title: "VeloPlay • " + (st.definition || st.quality || "HD"),
            name: "VeloPlay",
            provider: "VeloPlayTV",
            quality: st.definition || st.quality || "1080p",
            language: "Português",
            type: st.url.includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": "https://fastcdn.bond/" },
            url: st.url
          });
        });
        if (results.length) return results;
      }
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "VeloPlay TV" },
    { type: "info", label: "Provedor VeloPlay TV com autenticação e catálogo oficial." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_CINEGATO: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://api.hwcty.info";
const PACKAGE_NAME = "com.naughty.cinegato";
const SIGN_APP_KEY = "T!BgJBAppSf";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; K) Chrome/120.0.0.0 Mobile",
    "version": "2.1.2",
    "clientType": "1",
    "deviceId": "e88383e97ba1da73",
    "guid": "5c1f010f3910c266a8775f0a35db4dbd",
    "channel": "google",
    "lang": "pt-BR",
    "Content-Type": "application/json; charset=utf-8",
    "Accept": "application/json"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchUrl = BASE_URL + "/film-api/v2.1.2/movie/searchByKeyword?keyword=" + encodeURIComponent(title) + "&clientType=1&packageName=" + PACKAGE_NAME + "&lang=pt-BR";
      const res = await fetch(searchUrl, { headers });
      if (!res.ok) continue;
      const text = await res.text();
      let data = null;
      try { data = JSON.parse(text); } catch (_) {}
      const rows = (data && data.data && (data.data.rows || data.data.list)) || [];

      let matched = null, bestScore = 0;
      for (const r of rows) {
        const name = r.title || r.name;
        const sc = titleScore(title, name);
        if (sc > bestScore) { bestScore = sc; matched = r; }
      }

      if (bestScore >= 250 && matched) {
        const mid = matched.id || matched.movieId;
        const videoUrl = BASE_URL + "/film-api/v2.0.7/movie/getVideo2";
        const body = JSON.stringify({
          episodeId: String(mid),
          movieId: String(mid),
          clientType: 1,
          mode: 0,
          resolution: 0,
          apkSignKey: SIGN_APP_KEY,
          androidVersion: "14"
        });

        const vRes = await fetch(videoUrl, { method: "POST", headers, body });
        if (!vRes.ok) continue;
        const vText = await vRes.text();
        let vData = null;
        try { vData = JSON.parse(vText); } catch (_) {}
        const streamUrl = vData && vData.data && (vData.data.videoUrl || vData.data.url);
        if (streamUrl) {
          return [{
            title: "CineGato • Principal",
            name: "CineGato",
            provider: "CineGato",
            quality: "1080p",
            language: "Português",
            type: streamUrl.includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": "https://api.hwcty.info/" },
            url: streamUrl
          }];
        }
      }
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "CineGato" },
    { type: "info", label: "Catálogo CineGato de filmes e séries brasileiras." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_POBREFLIX: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://profilmecloud.net";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      // Find first result link
      const linkMatch = html.match(/<article[^>]+class="[^"]*(?:item|movies|tvshows)[^"]*"[\s\S]*?<a[^>]+href="([^"]+)"/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      // Look for iframes and player embeds
      const iframes = pageHtml.match(/<iframe[^>]+src="([^"]+)"/gi) || [];
      const results = [];

      for (let i = 0; i < iframes.length; i++) {
        const srcMatch = iframes[i].match(/src="([^"]+)"/i);
        const src = srcMatch && srcMatch[1];
        if (src && !src.includes("google") && !src.includes("recaptcha")) {
          results.push({
            title: "Pobreflix • Servidor " + (i + 1),
            name: "Pobreflix",
            provider: "Pobreflix",
            quality: "Auto",
            language: "Português",
            type: src.includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: src
          });
        }
      }

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "Pobreflix" },
    { type: "info", label: "Provedor Pobreflix com catálogo atualizado de lançamentos." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_NETCINE: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://netcineso.lat";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      const linkMatch = html.match(/<a[^>]+href="([^"]+)"[^>]*>\s*<div class="title">/i) || html.match(/<div class="title">\s*<a[^>]+href="([^"]+)"/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      const iframes = pageHtml.match(/<iframe[^>]+src="([^"]+)"/gi) || [];
      const results = [];
      iframes.forEach((ifr, idx) => {
        const src = ifr.match(/src="([^"]+)"/i);
        if (src && src[1]) {
          results.push({
            title: "NetCine • Opção " + (idx + 1),
            name: "NetCine",
            provider: "NetCine",
            quality: "Auto",
            language: "Português",
            type: src[1].includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: src[1]
          });
        }
      });

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "NetCine" },
    { type: "info", label: "Provedor NetCine de filmes e séries dubladas." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_REDECANAIS: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://www3.redecanais.vip";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      const linkMatch = html.match(/<div class="imagen">\s*<a[^>]+href="([^"]+)"/i) || html.match(/<a[^>]+href="([^"]+)"[^>]*>\s*<div class="imagen">/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      const optionsMatch = pageHtml.match(/data-post="(\d+)"[^>]+data-nume="(\d+)"/g) || [];
      const results = [];

      for (let i = 0; i < Math.min(optionsMatch.length, 3); i++) {
        const pMatch = optionsMatch[i].match(/data-post="(\d+)"[^>]+data-nume="(\d+)"/);
        if (pMatch) {
          const postId = pMatch[1];
          const nume = pMatch[2];
          const ajaxRes = await fetch(BASE_URL + "/wp-admin/admin-ajax.php", {
            method: "POST",
            headers: {
              "User-Agent": headers["User-Agent"],
              "Content-Type": "application/x-www-form-urlencoded",
              "Referer": postUrl
            },
            body: "action=doo_player_ajax&post=" + postId + "&nume=" + nume + "&type=" + (mediaType === "movie" ? "movie" : "tv")
          });
          if (ajaxRes.ok) {
            const ajaxJson = await ajaxRes.json();
            const embed = ajaxJson && ajaxJson.embed_url;
            if (embed) {
              results.push({
                title: "RedeCanais • Player " + (i + 1),
                name: "RedeCanais",
                provider: "RedeCanais",
                quality: "Auto",
                language: "Português",
                type: embed.includes(".m3u8") ? "hls" : "file",
                headers: { "Referer": BASE_URL + "/" },
                url: embed
              });
            }
          }
        }
      }

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "RedeCanais" },
    { type: "info", label: "Provedor clássico RedeCanais de filmes, animes e séries." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_TOPANIMES: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://topanimes.net";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      const linkMatch = html.match(/<article[^>]+class="[^"]*item[^"]*"[\s\S]*?<a[^>]+href="([^"]+)"/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      const iframes = pageHtml.match(/<iframe[^>]+src="([^"]+)"/gi) || [];
      const results = [];
      iframes.forEach((ifr, idx) => {
        const src = ifr.match(/src="([^"]+)"/i);
        if (src && src[1]) {
          results.push({
            title: "TopAnimes • Servidor " + (idx + 1),
            name: "TopAnimes",
            provider: "TopAnimes",
            quality: "Auto",
            language: "Português",
            type: src[1].includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: src[1]
          });
        }
      });

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "TopAnimes" },
    { type: "info", label: "Provedor especializado em animes TopAnimes." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_MEGAFILMESHD: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://www.megafilmeshd.help";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      const linkMatch = html.match(/<div class="poster">\s*<a[^>]+href="([^"]+)"/i) || html.match(/<a[^>]+href="([^"]+)"[^>]*class="[^"]*poster/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      const iframes = pageHtml.match(/<iframe[^>]+src="([^"]+)"/gi) || [];
      const results = [];
      iframes.forEach((ifr, idx) => {
        const src = ifr.match(/src="([^"]+)"/i);
        if (src && src[1]) {
          results.push({
            title: "MegaFilmesHD • Player " + (idx + 1),
            name: "MegaFilmesHD",
            provider: "MegaFilmesHD",
            quality: "Auto",
            language: "Português",
            type: src[1].includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: src[1]
          });
        }
      });

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "MegaFilmesHD" },
    { type: "info", label: "Provedor MegaFilmesHD com acervo amplo de filmes." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_SARAFLIX: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://saraflix.com";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/?s=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const html = await searchRes.text();

      const linkMatch = html.match(/<a[^>]+href="([^"]+)"[^>]*class="[^"]*thumb/i) || html.match(/<article[^>]*>[\s\S]*?<a[^>]+href="([^"]+)"/i);
      const postUrl = linkMatch && linkMatch[1];
      if (!postUrl) continue;

      const pageRes = await fetch(postUrl, { headers });
      if (!pageRes.ok) continue;
      const pageHtml = await pageRes.text();

      const iframes = pageHtml.match(/<iframe[^>]+src="([^"]+)"/gi) || [];
      const results = [];
      iframes.forEach((ifr, idx) => {
        const src = ifr.match(/src="([^"]+)"/i);
        if (src && src[1]) {
          results.push({
            title: "Saraflix • Opção " + (idx + 1),
            name: "Saraflix",
            provider: "Saraflix",
            quality: "Auto",
            language: "Português",
            type: src[1].includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: src[1]
          });
        }
      });

      if (results.length) return results;
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "Saraflix" },
    { type: "info", label: "Provedor Saraflix de entretenimento e séries." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val CODE_CINEVISION: String = """function normalizeTitle(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function titleScore(query, candidate) {
  const left = normalizeTitle(query);
  const right = normalizeTitle(candidate);
  if (!left || !right) return 0;
  if (left === right) return 1000;
  if (left.includes(right) || right.includes(left)) return 700 - Math.abs(left.length - right.length);
  const leftTokens = new Set(left.split(" "));
  const rightTokens = new Set(right.split(" "));
  let common = 0;
  leftTokens.forEach(t => { if (rightTokens.has(t)) common++; });
  return Math.round((common / Math.max(leftTokens.size, rightTokens.size)) * 500);
}

async function fetchTmdbTitles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return [];
  const type = mediaType === "movie" ? "movie" : "tv";
  try {
    const res = await fetch(
      "https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) +
      "?api_key=" + encodeURIComponent(TMDB_API_KEY) +
      "&language=pt-BR&append_to_response=alternative_titles"
    );
    if (!res.ok) return [];
    const data = await res.json();
    const list = [data.title, data.name, data.original_title, data.original_name];
    if (data.alternative_titles && Array.isArray(data.alternative_titles.results)) {
      data.alternative_titles.results.forEach(r => list.push(r.title || r.name));
    }
    const seen = new Set();
    return list.filter(v => {
      const k = normalizeTitle(v);
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  } catch (_) {
    return [];
  }
}

const BASE_URL = "https://cinevision.app";

async function getStreams(tmdbId, mediaType, season, episode) {
  const titles = await fetchTmdbTitles(tmdbId, mediaType);
  if (!titles.length) return [];

  const headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile",
    "Referer": BASE_URL + "/"
  };

  try {
    for (const title of titles.slice(0, 3)) {
      const searchRes = await fetch(BASE_URL + "/api/search?q=" + encodeURIComponent(title), { headers });
      if (!searchRes.ok) continue;
      const data = await searchRes.json();
      const items = (data && (data.results || data.items || data.data)) || [];

      let matched = null, bestScore = 0;
      for (const it of items) {
        const sc = titleScore(title, it.title || it.name);
        if (sc > bestScore) { bestScore = sc; matched = it; }
      }

      if (bestScore >= 250 && matched) {
        const streamUrl = matched.stream_url || matched.url || matched.m3u8;
        if (streamUrl) {
          return [{
            title: "CineVision • HD",
            name: "CineVision",
            provider: "CineVision",
            quality: "1080p",
            language: "Português",
            type: streamUrl.includes(".m3u8") ? "hls" : "file",
            headers: { "Referer": BASE_URL + "/" },
            url: streamUrl
          }];
        }
      }
    }
  } catch (_) {}
  return [];
}

function onSettings() {
  return [
    { type: "header", label: "CineVision" },
    { type: "info", label: "Provedor CineVision com catálogo integrado e alta resolução." }
  ];
}

module.exports = { getStreams, onSettings };
"""

    val defaultRepository = PluginRepositoryItem(
        manifestUrl = MANIFEST_URL,
        name = "KAPlay Provedores BR",
        description = "Repositório com 17 provedores nacionais portados do CloudStream para KAPlay.",
        version = "1.3.0",
        scraperCount = 17,
        lastUpdated = 1730000000000L,
        isRefreshing = false,
        errorMessage = null,
    )

    fun createDefaultScraper(): PluginScraper = defaultScrapers.first()

    val defaultScrapers: List<PluginScraper> = listOf(
        PluginScraper(
            id = "${MANIFEST_URL}:tomato",
            repositoryUrl = MANIFEST_URL,
            name = "Tomato",
            description = "Streams e episódios de animes Tomato.",
            version = "1.0.0",
            filename = "tomato.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = true,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls"),
            code = CODE_TOMATO,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:animefire",
            repositoryUrl = MANIFEST_URL,
            name = "AnimeFire",
            description = "Animes dublados e legendados da API AnimeFire.",
            version = "1.0.0",
            filename = "animefire.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_ANIMEFIRE,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:supercine",
            repositoryUrl = MANIFEST_URL,
            name = "SuperCine",
            description = "Filmes e séries via API de embed SuperCine.",
            version = "1.0.0",
            filename = "supercine.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_SUPERCINE,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:animesorion",
            repositoryUrl = MANIFEST_URL,
            name = "AnimesOrion",
            description = "Acervo de animes do AnimesOrion.",
            version = "1.0.0",
            filename = "animesorion.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_ANIMESORION,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:veloplaytv",
            repositoryUrl = MANIFEST_URL,
            name = "VeloPlayTV",
            description = "Canais, séries e filmes VeloPlay com login automático.",
            version = "1.0.0",
            filename = "veloplaytv.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_VELOPLAYTV,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:cinegato",
            repositoryUrl = MANIFEST_URL,
            name = "CineGato",
            description = "Filmes e séries populares do CineGato.",
            version = "1.0.0",
            filename = "cinegato.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls"),
            code = CODE_CINEGATO,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:pobreflix",
            repositoryUrl = MANIFEST_URL,
            name = "Pobreflix",
            description = "Filmes e séries atualizadas do Pobreflix.",
            version = "1.0.0",
            filename = "pobreflix.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_POBREFLIX,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:netcine",
            repositoryUrl = MANIFEST_URL,
            name = "NetCine",
            description = "Catálogo do NetCine de filmes e séries dubladas.",
            version = "1.0.0",
            filename = "netcine.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_NETCINE,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:redecanais",
            repositoryUrl = MANIFEST_URL,
            name = "RedeCanais",
            description = "Acervo tradicional do RedeCanais de filmes e animes.",
            version = "1.0.0",
            filename = "redecanais.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_REDECANAIS,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:topanimes",
            repositoryUrl = MANIFEST_URL,
            name = "TopAnimes",
            description = "Animes e lançamentos do TopAnimes.",
            version = "1.0.0",
            filename = "topanimes.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls"),
            code = CODE_TOPANIMES,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:megafilmeshd",
            repositoryUrl = MANIFEST_URL,
            name = "MegaFilmesHD",
            description = "Filmes clássicos e lançamentos do MegaFilmesHD.",
            version = "1.0.0",
            filename = "megafilmeshd.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_MEGAFILMESHD,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:saraflix",
            repositoryUrl = MANIFEST_URL,
            name = "Saraflix",
            description = "Filmes e séries do Saraflix.",
            version = "1.0.0",
            filename = "saraflix.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls", "mp4"),
            code = CODE_SARAFLIX,
        ),
        PluginScraper(
            id = "${MANIFEST_URL}:cinevision",
            repositoryUrl = MANIFEST_URL,
            name = "CineVision",
            description = "Catálogo do CineVision.",
            version = "1.0.0",
            filename = "cinevision.js",
            supportedTypes = listOf("movie", "tv"),
            enabled = true,
            manifestEnabled = true,
            hasSettings = false,
            logo = null,
            contentLanguage = listOf("pt-BR"),
            formats = listOf("hls"),
            code = CODE_CINEVISION,
        )
    )
}
