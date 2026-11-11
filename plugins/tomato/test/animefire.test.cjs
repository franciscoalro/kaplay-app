const assert = require("node:assert/strict");

const requests = [];
let mode = "season-mismatch";
let titleCasing = "lowercase";

global.TMDB_API_KEY = "test-key";
global.fetch = async (url, options = {}) => {
  requests.push({ url, options });
  const parsed = new URL(url);
  if (parsed.hostname === "api.themoviedb.org") {
    return jsonResponse({ name: "Demon Slayer", original_name: "Demon Slayer" });
  }
  if (parsed.pathname === "/animes") {
    const titles = titleCasing === "uppercase"
      ? { BR: "Demon Slayer", US: "Demon Slayer", JP: "Kimetsu no Yaiba" }
      : { br: "Demon Slayer" };
    return jsonResponse({ data: [{ id: "af-1", titles }] });
  }
  if (parsed.pathname === "/anime/af-1") {
    if (mode === "season-mismatch") {
      return jsonResponse({ data: { episodes: [{ id: "s1e1", number: 1, season: 1 }] } });
    }
    return jsonResponse({ data: { episodes: [{ id: "s1e1", number: 1, season: 1 }] } });
  }
  if (parsed.pathname === "/episode/s1e1") {
    if (mode === "audio-labels") {
      return jsonResponse({
        data: {
          streams: [
            { audio: "dublado", url: "https://cdn.example/dub-playlist", qualities: ["720p"] },
            { audio: "legendado", url: "https://cdn.example/sub-playlist", qualities: ["720p"] },
          ],
        },
      });
    }
    if (mode === "stream-file") {
      return jsonResponse({
        data: {
          streams: [
            null,
            { audio: "sub" },
            { audio: "dub", url: "https://cdn.example/video.mp4?fixture=1", qualities: [] },
            { audio: "dub", url: "https://cdn.example/video.mp4?fixture=1", qualities: ["1080p"] },
          ],
        },
      });
    }
    if (mode === "stream-validation") {
      return jsonResponse({
        data: {
          streams: [
            { audio: "dub", url: "javascript:alert(1)" },
            { audio: "dub", url: "/video.m3u8" },
            { audio: "dub", url: "https:///video.m3u8" },
            { audio: "dub", url: { nested: "https://cdn.example/video.mp4" } },
            { audio: "dub", url: "http://cdn.example/video.mp4", qualities: ["HD"] },
          ],
        },
      });
    }
    return jsonResponse({
      data: {
        streams: [{
          audio: "sub",
          url: "https://cdn.example/playlist",
          qualities: ["720p", "720p"],
        }],
      },
    });
  }
  return jsonResponse({}, false, 404);
};

function jsonResponse(payload, ok = true, status = 200) {
  return { ok, status, json: async () => payload };
}

const plugin = require("../animefire.js");

(async () => {
  requests.length = 0;
  const missingEpisode = await plugin.getStreams("1", "tv", 2, 1);
  assert.deepEqual(missingEpisode, [], "must not fall back to another season with the same episode number");
  assert.equal(
    requests.some(({ url }) => url.endsWith("/episode/s1e1")),
    false,
    "must not request the episode from another season",
  );

  titleCasing = "uppercase";
  mode = "stream-shape";
  const uppercaseTitleStreams = await plugin.getStreams("1", "tv", 1, 1);
  assert.equal(uppercaseTitleStreams.length, 1, "uppercase API title keys must match AnimeFire entries");

  titleCasing = "lowercase";
  requests.length = 0;
  const streams = await plugin.getStreams("1", "tv", 1, 1);
  assert.equal(streams.length, 1, "duplicate quality labels must not create duplicate stream choices");
  assert.equal(streams[0].type, "hls", "playlist URLs without .m3u8 must still be HLS");
  assert.equal(streams[0].headers["User-Agent"].includes("Android"), true);
  assert.equal(streams[0].headers.Origin, "https://animefire.one");
  assert.equal(streams[0].headers.Referer, "https://animefire.one/");

  mode = "audio-labels";
  const audioStreams = await plugin.getStreams("1", "tv", 1, 1);
  assert.deepEqual(
    audioStreams.map(stream => stream.name),
    ["AnimeFire Dublado", "AnimeFire Legendado"],
    "real dublado/legendado audio labels must keep their playback labels",
  );

  mode = "stream-file";
  const fileStreams = await plugin.getStreams("1", "tv", 1, 1);
  assert.equal(fileStreams.length, 2, "invalid stream entries must be ignored");
  assert.equal(fileStreams[0].type, "file", "explicit MP4 URLs must remain file streams");
  assert.equal(fileStreams[0].quality, "HD", "empty qualities must use the host-compatible default");
  assert.equal(fileStreams[0].name, "AnimeFire Dublado");
  assert.equal(fileStreams[1].quality, "1080p");
  assert.equal(fileStreams[1].type, "file");

  mode = "stream-validation";
  const validatedStreams = await plugin.getStreams("1", "tv", 1, 1);
  assert.equal(validatedStreams.length, 1, "only absolute HTTP(S) URLs with a host may become streams");
  assert.equal(validatedStreams[0].url, "http://cdn.example/video.mp4");
  assert.equal(validatedStreams[0].type, "file");

  console.log("AnimeFire regression tests passed.");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
