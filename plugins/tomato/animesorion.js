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
