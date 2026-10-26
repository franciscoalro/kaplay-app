const API_HOSTS = [
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
    Authorization: `Bearer ${token}`,
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
      const response = await fetch(`${host}${path}`, {
        method,
        headers: requestHeaders(token, kind),
        body: requestOptions.body == null ? undefined : JSON.stringify(requestOptions.body),
      });
      if (response.ok) return { host, data: await response.json() };
      lastError = new Error(`Tomato HTTP ${response.status} for ${path}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt + 1 < maxAttempts) await retryDelay();
  }

  throw lastError || new Error(`Tomato request failed for ${path}`);
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
    `https://api.themoviedb.org/3/${normalizedType}/${encodeURIComponent(tmdbId)}` +
      `?api_key=${encodeURIComponent(TMDB_API_KEY)}&language=pt-BR&append_to_response=alternative_titles`,
  );
  if (!response.ok) throw new Error(`TMDB HTTP ${response.status}`);
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
        `/season/${seasonId}/episodes`,
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
    title: `Tomato • ${label} • ${quality}`,
    name: `Tomato ${label}`,
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

    const details = await requestJson(`/v2/anime/${animeId}`, token, null, 3);
    const seasons = selectSeasons(details.data, season);
    const resolved = [];

    for (const seasonItem of seasons) {
      const episodeItem = await resolveEpisode(seasonItem, episode, token);
      const id = episodeId(episodeItem);
      if (!id) continue;
      try {
        const stream = await requestJson(`/v2/anime/episode/${id}/stream`, token, null, 4);
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
