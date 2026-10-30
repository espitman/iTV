const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises"),
  os = require("node:os"),
  path = require("node:path");
const { source, image, markers } = require("../service/catalog");
const { createServer } = require("../service/server");
test("source validation rejects other hosts, credentials, paths and schemes", () => {
  assert.equal(
    source("https://telewebion.net/product/0xAb12").sourceId,
    "0xab12",
  );
  for (const u of [
    "http://telewebion.net/program/0x1",
    "https://telewebion.net.evil.com/program/0x1",
    "https://x@telewebion.net/product/0x1",
    "https://telewebion.net/product/no",
    "https://telewebion.net/product/0x1/evil",
  ])
    assert.throws(() => source(u));
  assert.equal(
    image("abc", "programImages"),
    "https://static.telewebion.net/programImages/abc/default",
  );
});
test("pairing, origin protection, uploaded image persistence and progress", async (t) => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "itv-test-"));
  const fixture = {
    items: [
      {
        id: "ab12",
        sourceId: "0x1",
        kind: "program",
        sourceTitle: "نمونه",
        pageUrl: "https://telewebion.net/program/0x1",
        episodes: [
          { sourceEpisodeId: "0x2", seasonNumber: 1, title: "قسمت اول" },
        ],
        importState: "READY",
      },
    ],
    tokens: [],
  };
  await fs.writeFile(path.join(dir, "library.json"), JSON.stringify(fixture));
  const runtime = createServer({ dataDir: dir, host: "127.0.0.1", port: 0 });
  const s = await runtime.start();
  const base = `http://127.0.0.1:${s.port}`;
  t.after(async () => {
    await new Promise((r) => runtime.server.close(r));
    await fs.rm(dir, { recursive: true, force: true });
  });
  const request = async (url, body, token, method = "POST", extra = {}) =>
    fetch(base + url, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { "X-ITV-Token": token } : {}),
        ...extra,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  assert.equal((await fetch(base + "/api/series")).status, 401);
  assert.equal(
    (
      await request("/api/pair", { pin: s.pin }, null, "POST", {
        Origin: "https://evil.example",
      })
    ).status,
    403,
  );
  assert.equal((await request("/api/pair", { pin: "bad" })).status, 403);
  const pair = await (await request("/api/pair", { pin: s.pin })).json();
  assert.ok(pair.token);
  assert.equal(
    (await request("/api/series", undefined, pair.token, "GET")).status,
    200,
  );
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aTWQAAAAASUVORK5CYII=",
    "base64",
  );
  let r = await fetch(base + "/api/posters", {
    method: "POST",
    headers: { "X-ITV-Token": pair.token, "Content-Type": "image/png" },
    body: png,
  });
  assert.equal(r.status, 200);
  const upload = await r.json();
  assert.deepEqual(
    Buffer.from(await (await fetch(base + upload.posterUrl)).arrayBuffer()),
    png,
  );
  r = await request(
    "/api/series/ab12",
    { customPosterUrl: upload.customPosterUrl, localTitle: "عنوان تازه" },
    pair.token,
    "PATCH",
  );
  assert.equal(r.status, 200);
  r = await request(
    "/api/series/ab12/progress",
    { episodeId: "0x2", position: 995, duration: 1000 },
    pair.token,
  );
  assert.equal(r.status, 404);
  r = await request(
    "/api/series/ab12/progress",
    { episodeId: "0x2", position: 995, duration: 1000 },
    s.tvToken,
  );
  assert.equal(r.status, 200);
  const stored = JSON.parse(
    await fs.readFile(path.join(dir, "library.json"), "utf8"),
  );
  assert.equal(stored.items[0].episodes[0].position, 0);
  assert.equal(stored.items[0].episodes[0].watched, true);
  assert.equal(stored.items[0].customPosterUrl, upload.posterUrl);
  for (const watched of [false,true]) {
    const response = await request("/api/series/ab12/watched", {episodeId:"0x2",watched}, s.tvToken);
    assert.equal(response.status,200);
    const saved = JSON.parse(await fs.readFile(path.join(dir,"library.json"),"utf8"));
    assert.equal(saved.items[0].episodes[0].watched,watched);
    assert.equal(saved.items[0].episodes[0].position,0);
  }

  assert.equal((await request("/api/series/ab12/continue", {hidden:true}, pair.token)).status,404);
  assert.equal((await request("/api/series/ab12/continue", {hidden:true}, s.tvToken)).status,200);
  let hidden = JSON.parse(await fs.readFile(path.join(dir,"library.json"),"utf8")).items[0];
  assert.equal(hidden.continueHidden,true);
  assert.equal(hidden.episodes[0].watched,true);
  assert.equal((await request("/api/series/ab12/progress", {episodeId:"0x2",position:30,duration:1000}, s.tvToken)).status,200);
  hidden = JSON.parse(await fs.readFile(path.join(dir,"library.json"),"utf8")).items[0];
  assert.equal(hidden.continueHidden,false);

  r = await request("/api/series", undefined, s.tvToken, "GET", {
    Origin: "null",
  });
  assert.equal(r.status, 200);
  assert.equal(r.headers.get("access-control-allow-origin"), "null");
  assert.equal(
    (
      await request(
        "/api/series/ab12",
        { customPosterUrl: "file:///etc/passwd" },
        pair.token,
        "PATCH",
      )
    ).status,
    400,
  );
});

test("titraje markers remain an object with nonnegative seconds", () => {
  assert.deepEqual(
    markers({
      titraje: { first_titraje_start: 10, first_titraje_end: 42 },
      last_titraje_start: -1,
    }),
    { first_titraje_start: 10, first_titraje_end: 42, last_titraje_start: 0 },
  );
});

test("refresh keeps progress and manual artwork, and archives missing episodes", async (t) => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "itv-refresh-"));
  const fixture = {
    items: [
      {
        id: "ab12",
        kind: "program",
        sourceId: "0x1",
        sourceTitle: "قدیمی",
        pageUrl: "https://telewebion.net/program/0x1",
        customPosterUrl: "https://example.org/poster.jpg",
        customBackdropUrl: "https://example.org/header.jpg",
        localTitle: "نام من",
        episodes: [
          {
            sourceEpisodeId: "0x2",
            seasonNumber: 1,
            position: 120,
            watched: false,
          },
          {
            sourceEpisodeId: "0x3",
            seasonNumber: 1,
            position: 12,
            watched: true,
          },
        ],
        importState: "READY",
      },
    ],
    tokens: [],
  };
  await fs.writeFile(path.join(dir, "library.json"), JSON.stringify(fixture));
  const catalogApi = {
    preview: async () => ({
      title: "تازه",
      kind: "program",
      sourceId: "0x1",
      posterUrl: "https://example.org/auto.jpg",
    }),
    episodes: async (meta, onPage) => {
      const rows = [{ sourceEpisodeId: "0x2", seasonNumber: 1, title: "تازه" }];
      await onPage(rows);
      return rows;
    },
  };
  const runtime = createServer({
      dataDir: dir,
      host: "127.0.0.1",
      port: 0,
      catalogApi,
    }),
    s = await runtime.start(),
    base = `http://127.0.0.1:${s.port}`;
  t.after(async () => {
    await new Promise((r) => runtime.server.close(r));
    await fs.rm(dir, { recursive: true, force: true });
  });
  await fetch(base + "/api/series/ab12/refresh", {
    method: "POST",
    headers: { "X-ITV-Token": s.tvToken },
    body: "{}",
  });
  let item;
  for (let n = 0; n < 30; n++) {
    item = (
      await (
        await fetch(base + "/api/series", {
          headers: { "X-ITV-Token": s.tvToken },
        })
      ).json()
    ).items[0];
    if (item.importState === "READY") break;
    await new Promise((r) => setTimeout(r, 10));
  }
  assert.equal(item.importState, "READY");
  assert.equal(item.title, "نام من");
  assert.equal(item.posterUrl, fixture.items[0].customPosterUrl);
  assert.equal(item.backdropUrl, fixture.items[0].customBackdropUrl);
  assert.equal(
    item.episodes.find((e) => e.sourceEpisodeId === "0x2").position,
    120,
  );
  assert.equal(
    item.episodes.find((e) => e.sourceEpisodeId === "0x3").sourcePresent,
    false,
  );
  assert.equal(
    item.episodes.find((e) => e.sourceEpisodeId === "0x3").watched,
    true,
  );
  assert.equal(item.episodeCount, 1);
});
