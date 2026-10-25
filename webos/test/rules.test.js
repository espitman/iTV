const { test } = require("node:test"),
  assert = require("node:assert/strict"),
  rules = require("../app/rules");
test("episode identity and stable duplicates survive ordering across seasons", () => {
  const rows = [
    {
      sourceEpisodeId: "b",
      seasonNumber: 1,
      displayNumber: "2",
      sourceOrder: 0,
    },
    {
      sourceEpisodeId: "a",
      seasonNumber: 1,
      displayNumber: "1",
      sourceOrder: 1,
    },
    {
      sourceEpisodeId: "c",
      seasonNumber: 1,
      displayNumber: "2",
      sourceOrder: 2,
    },
    {
      sourceEpisodeId: "d",
      seasonNumber: 2,
      displayNumber: "1",
      sourceOrder: 3,
    },
    { sourceEpisodeId: "gone", seasonNumber: 1, sourcePresent: false },
  ];
  assert.deepEqual(
    rules.ordered(rows).map((e) => e.sourceEpisodeId),
    ["a", "b", "c", "d"],
  );
});
test("resume selects latest in-progress and otherwise follows last watched episode", () => {
  const rows = [
    { seasonNumber: 1, sourceOrder: 0, displayNumber: "1", watched: true },
    { seasonNumber: 1, sourceOrder: 1, displayNumber: "2" },
    {
      seasonNumber: 1,
      sourceOrder: 2,
      displayNumber: "3",
      position: 40,
      lastPlayed: 100,
    },
  ];
  assert.equal(rules.select(rows), rows[2]);
  rows[2].position = 0;
  assert.equal(rules.select(rows), rows[1]);
});
test("watched and resume thresholds match Android rules including short clips", () => {
  assert.deepEqual(rules.progress(991, 1000), {
    position: 0,
    duration: 1000,
    watched: true,
  });
  assert.equal(rules.progress(891, 1000).watched, false);
  assert.equal(rules.progress(910, 1000).watched, true);
  assert.equal(rules.progress(29, 30).watched, false);
  assert.equal(rules.seek(5, -20, 100), 0);
  assert.equal(rules.seek(95, 20, 100), 100);
  assert.equal(
    rules.intro(20, { first_titraje_start: 10, first_titraje_end: 20 }),
    false,
  );
  assert.equal(
    rules.intro(15, { first_titraje_start: 10, first_titraje_end: 20 }),
    true,
  );
});
test("packaged service rules equal application rules", () => {
  assert.equal(
    require("fs").readFileSync(require.resolve("../app/rules"), "utf8"),
    require("fs").readFileSync(require.resolve("../service/rules"), "utf8"),
  );
});

test("outro is absent before its marker, after completion and without a valid duration", () => {
  const m = { last_titraje_start: 900 };
  assert.equal(rules.outro(50, m, 1000), false);
  assert.equal(rules.outro(900, m, 1000), true);
  assert.equal(rules.outro(999, m, 1000), true);
  assert.equal(rules.outro(1000, m, 1000), false);
  assert.equal(rules.outro(999, m, NaN), false);
  assert.equal(rules.outro(999, { last_titraje_start: 0 }, 1000), false);
});
test("Held D-pad and media keys always advance by ten seconds", () => {
  assert.equal(rules.seekStep(0), 10);
  assert.equal(rules.seekStep(2000), 10);
  assert.equal(rules.seekStep(2001), 10);
  assert.equal(rules.seekStep(5000, true), 10);
});
test("highest quality chooses height then bitrate instead of playlist ordering", () => {
  const rows = [
    { height: 720, bandwidth: 8000000, url: "720" },
    { height: 1080, bandwidth: 2000000, url: "1080-low" },
    { height: 480, bandwidth: 6000000, url: "480" },
    { height: 1080, bandwidth: 4000000, url: "1080-high" },
  ];
  assert.equal(rules.highest(rows).url, "1080-high");
  assert.equal(rows[0].url, "720");
});
test("HLS variant parser uses redirected base URL and rejects foreign hosts", () => {
  const { parseVariants } = require("../service/catalog");
  const rows = parseVariants(
    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\n1080/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\nhttps://evil.example/low.m3u8",
    "https://cdna.telewebion.net/program/master.m3u8",
  );
  assert.equal(rows.length, 1);
  assert.equal(rows[0].height, 1080);
  assert.equal(rows[0].bandwidth, 3000000);
  assert.equal(
    rows[0].url,
    "https://cdna.telewebion.net/program/1080/index.m3u8",
  );
});

test("continue watching matches Android series filtering and has one row per series", () => {
  const episode = {seasonNumber:1,sourceOrder:0,position:20,watched:false};
  const items = [
    {id:'a',addedAt:1,episodes:[episode,{...episode,sourceOrder:1}]},
    {id:'b',addedAt:2,episodes:[{...episode,position:0,watched:true},{...episode,position:0}]},
    {id:'c',episodes:[{...episode,position:0,watched:true}]},
    {id:'d',isMovie:true,episodes:[episode]},
    {id:'e',episodes:[{...episode,position:0}]}
  ];
  assert.deepEqual(rules.continueWatching(items).map(s=>s.id), ['b','a']);
});
