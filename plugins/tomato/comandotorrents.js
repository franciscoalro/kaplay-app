function titles(tmdbId, mediaType) {
  if (typeof TMDB_API_KEY === "undefined" || !TMDB_API_KEY) return Promise.resolve([]);
  const type = mediaType === "movie" ? "movie" : "tv";
  return fetch("https://api.themoviedb.org/3/" + type + "/" + encodeURIComponent(tmdbId) + "?api_key=" + encodeURIComponent(TMDB_API_KEY) + "&language=pt-BR&append_to_response=alternative_titles")
    .then(r => r.ok ? r.json() : null).then(d => d ? [d.title,d.name,d.original_title,d.original_name].concat((d.alternative_titles && d.alternative_titles.results || []).map(x => x.title || x.name)).filter(Boolean) : []).catch(() => []);
}
function quality(label) { const s=String(label||"").toLowerCase(); if (/2160|4k|uhd/.test(s)) return "2160p"; if (/1080|full.?hd|blu.?ray/.test(s)) return "1080p"; if (/720|hd/.test(s)) return "720p"; if (/480|sd/.test(s)) return "480p"; return "Auto"; }
function decode(s) { return String(s||"").replace(/&amp;/g,"&").replace(/&#39;/g,"'").replace(/&quot;/g,'"'); }
async function getStreams(tmdbId, mediaType, season, episode) {
  const qs=await titles(tmdbId,mediaType), base="https://comandotorrents.to", headers={"Referer":base+"/","User-Agent":"Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"};
  for (const q of qs.slice(0,3)) { try { const r=await fetch(base+"/?s="+encodeURIComponent(q),{headers}); if(!r.ok)continue; const h=await r.text(); const m=[...h.matchAll(/<h2[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>[\s\S]*?<a[^>]+href=["']([^"']+)["']/gi)][0] || [...h.matchAll(/<a[^>]+href=["']([^"']+)["'][^>]*>/gi)][0]; if(!m)continue; const p=await fetch(decode(m[1]),{headers}); if(!p.ok)continue; const html=await p.text(), out=[]; for(const x of html.matchAll(/href=["'](magnet:\?[^"']+)["'][^>]*>([\s\S]*?)<\//gi)){const u=decode(x[1]);out.push({title:"ComandoTorrents • "+quality(x[2].replace(/<[^>]+>/g,"")),name:"ComandoTorrents",provider:"ComandoTorrents",quality:quality(x[2]),type:"torrent",headers, url:u});} if(out.length)return out;}catch(_){}} return [];
}
function onSettings(){return [{type:"header",label:"ComandoTorrents"},{type:"info",label:"Fontes torrent do ComandoTorrents."}];}
module.exports={getStreams,onSettings};
