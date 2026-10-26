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
      password: "pbkdf2_sha256$1200000$TiUO0Gu2Ttcb45XR4xPvmC$Ry1LAe8SayrzAhKgyp0PIMSp9HDnjXbQ2JRC2OFpL+0=",
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

module.exports = { getStreams, onSettings };\n