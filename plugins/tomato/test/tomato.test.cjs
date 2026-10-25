const assert = require("node:assert/strict");

const responses = new Map([
  ["https://api.themoviedb.org/3/tv/46260", {
    name: "Naruto",
    original_name: "Naruto",
    alternative_titles: { results: [{ title: "Naruto Shippuden" }] },
  }],
  ["/v2/content/search", {
    status: true,
    result: [{ anime_id: 10, anime_name: "Naruto" }],
  }],
  ["/v2/anime/10", {
    anime_details: { anime_name: "Naruto" },
    anime_seasons: [{ season_id: 20, season_number: 1, season_dubbed: 0 }],
  }],
  ["/season/20/episodes", {
    status: true,
    episodes: 1,
    data: [{ ep_id: 30, ep_number: 2, ep_name: "Teste" }],
  }],
  ["/v2/anime/episode/30/stream", {
    streams: {
      fhd: "https://video.example/naruto-1080.m3u8",
      mhd: "https://video.example/naruto-720.m3u8",
    },
  }],
]);

global.TMDB_API_KEY = "test-key";
global.SCRAPER_SETTINGS = { bearerToken: "Bearer test-token" };
const requests = [];
global.fetch = async (url, options = {}) => {
  requests.push({ url, options });
  const key = url.startsWith("https://api.themoviedb.org/")
    ? url.split("?")[0]
    : new URL(url).pathname;
  const payload = responses.get(key);
  if (!payload) return { ok: false, status: 404, json: async () => ({}) };
  return { ok: true, status: 200, json: async () => payload };
};

const plugin = require("../tomato.js");
const test = plugin._test;

assert.equal(test.normalizeToken(" Bearer abc "), "abc");
assert.equal(test.normalizeTitle("Shingeki no Kyōjin!"), "shingeki no kyojin");
assert.ok(test.titleScore("Naruto", "Naruto") > test.titleScore("Naruto", "Boruto"));
assert.deepEqual(
  test.extractSearchItems({ data: { result: [{ anime_id: 1 }] } }),
  [{ anime_id: 1 }],
);
assert.equal(
  test.bestTitleMatch(["One Piece"], [
    { anime_id: 1, anime_name: "Bleach" },
    { anime_id: 2, anime_name: "One Piece" },
  ]).anime_id,
  2,
);
const seasons = { anime_seasons: [
  { season_id: 1, season_number: 1 },
  { season_id: 2, season_number: 2 },
] };
assert.equal(test.selectSeasons(seasons, 2)[0].season_id, 2);
assert.deepEqual(test.selectSeasons(seasons, 3), []);
assert.equal(test.selectSeasons(seasons, undefined)[0].season_id, 1);
assert.equal(test.episodeId({ ep_id: 44 }), 44);
assert.equal(test.episodeNumber({ episode_number: 7 }), 7);
assert.deepEqual(
  test.streamResults({ streams: { fhd: "https://x/1.m3u8", sd: "invalid" } }, false)
    .map((entry) => entry.quality),
  ["1080p"],
);
assert.equal(plugin.onSettings().find((field) => field.key === "bearerToken").isPassword, true);

(async () => {
  const streams = await plugin.getStreams("46260", "tv", 1, 2);
  assert.equal(streams.length, 2);
  assert.deepEqual(streams.map((entry) => entry.quality), ["1080p", "720p"]);
  assert.ok(streams.every((entry) => entry.provider === "Tomato" && entry.type === "hls"));
  assert.ok(streams.every((entry) => entry.headers["User-Agent"] === "tomato-android"));
  assert.ok(streams.every((entry) => !entry.headers.Authorization));

  const searchRequest = requests.find((entry) => entry.url.endsWith("/v2/content/search"));
  assert.equal(searchRequest.options.method, "POST");
  assert.equal(searchRequest.options.headers.Authorization, "Bearer test-token");
  assert.deepEqual(JSON.parse(searchRequest.options.body), {
    token: "test-token",
    search: "Naruto",
    content_type: "all",
    page: 0,
  });

  const detailsRequest = requests.find((entry) => entry.url.endsWith("/v2/anime/10"));
  assert.equal(detailsRequest.options.headers["x-app"], "1.4.3");
  assert.ok(Number(detailsRequest.options.headers["request-time"]) > 0);

  const streamRequest = requests.find((entry) => entry.url.endsWith("/v2/anime/episode/30/stream"));
  assert.equal(streamRequest.options.headers["User-Agent"], "tomato-android");
  assert.equal(streamRequest.options.headers.Authorization, "Bearer test-token");
  console.log("Tomato plugin tests passed: protocol, parsing, and full mocked getStreams flow.");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
