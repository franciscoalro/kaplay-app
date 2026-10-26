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

module.exports = { getStreams, onSettings };\n