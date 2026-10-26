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

module.exports = { getStreams, onSettings };\n