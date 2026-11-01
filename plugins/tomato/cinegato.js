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
