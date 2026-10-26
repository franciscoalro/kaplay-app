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
