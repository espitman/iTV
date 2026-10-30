"use strict";
const http = require("http"),
  fs = require("fs"),
  path = require("path"),
  crypto = require("crypto"),
  os = require("os");
const webdav = require("./webdav");
const filimo = require("./filimo");
const filmnet = require("./filmnet");
const sheyda = require("./sheyda");
const namava = require('./namava');
const metadataProviders = {filimo,filmnet,sheyda,namava};
const importSource = require("./import-source");
const catalog = require("./catalog"),
  rules = require("./rules");
function createServer({
  dataDir,
  appDir = path.join(__dirname, "../app"),
  host = "0.0.0.0",
  port = 18196,
  catalogApi = catalog,
}) {
  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(path.join(dataDir, "posters"), { recursive: true });
  const file = path.join(dataDir, "library.json");
  let db = { items: [], tokens: [] };
  if (fs.existsSync(file)) db = JSON.parse(fs.readFileSync(file, "utf8"));
  let write = Promise.resolve();
  function save() {
    const snapshot = JSON.stringify(db);
    write = write
      .catch(() => {})
      .then(async () => {
        await fs.promises.writeFile(file + ".tmp", snapshot);
        await fs.promises.rename(file + ".tmp", file);
      });
    return write;
  }
  const pin = String(crypto.randomInt(1000, 10000)),
    tvToken = crypto.randomBytes(32).toString("hex"),
    attempts = new Map();
  let active = 0;
  function address() {
    if (host === "127.0.0.1")
      return `http://127.0.0.1:${server.address()?.port || port}`;
    const ns = os.networkInterfaces();
    for (const list of Object.values(ns))
      for (const n of list || [])
        if (n.family === "IPv4" && !n.internal)
          return `http://${n.address}:${server.address()?.port || port}`;
    return null;
  }
  const mediaTicket = crypto.randomBytes(32).toString("hex");
  function output(s) {
    const {webdav:credentials, ...safe} = s;
    return {
      ...safe,
      ...(s.kind === "webdav" ? {credentialsConfigured:!!(credentials?.username || credentials?.password)} : {}),
      ...(s.kind === "webdav" ? {episodes:s.episodes.map(e => ({...e,
        imageUrl:e.imageUrl || s.customPosterUrl || s.automaticPosterUrl || null}))} : {}),
      title: s.localTitle || s.sourceTitle,
      posterUrl: s.customPosterUrl || s.automaticPosterUrl,
      backdropUrl: s.customBackdropUrl || s.automaticBackdropUrl,
      episodeCount: rules.ordered(s.episodes).length,
      seasonCount: new Set(rules.ordered(s.episodes).map((e) => e.seasonNumber))
        .size,
    };
  }
  async function refresh(s) {
    active++;
    s.importState = "RUNNING";
    try {
      if (metadataProviders[s.metadataProvider]) {
        const provider=metadataProviders[s.metadataProvider];
        Object.assign(s,provider.merge(await provider.preview(s.metadataUrl),s));
        await save(); return;
      }
      const meta = await catalogApi.preview(s.pageUrl),
        rows = await catalogApi.episodes(meta, async (partial) => {
          const old = new Map(s.episodes.map((e) => [e.sourceEpisodeId, e]));
          const seen = new Set(partial.map((e) => e.sourceEpisodeId));
          s.episodes = [
            ...partial.map((e) => ({
              ...old.get(e.sourceEpisodeId),
              ...e,
              sourcePresent: true,
            })),
            ...s.episodes.filter((e) => !seen.has(e.sourceEpisodeId)),
          ];
          await save();
        }),
        old = new Map(s.episodes.map((e) => [e.sourceEpisodeId, e]));
      Object.assign(s, meta, {
        sourceTitle: meta.title,
        automaticPosterUrl: meta.posterUrl,
        automaticBackdropUrl: meta.backdropUrl,
        episodes: [
          ...rows.map((e) => ({
            ...old.get(e.sourceEpisodeId),
            ...e,
            sourcePresent: true,
          })),
          ...s.episodes
            .filter(
              (e) => !rows.some((n) => n.sourceEpisodeId === e.sourceEpisodeId),
            )
            .map((e) => ({ ...e, sourcePresent: false })),
        ],
        importState: "READY",
        importError: null,
      });
      await save();
    } catch (e) {
      s.importState = "ERROR";
      s.importError = e.message;
      await save();
    } finally {
      active--;
    }
  }
  function imageValue(v) {
    if (!v) return null;
    if (
      /^\/posters\/[a-f\d]{32}\.(png|jpg|webp)$/.test(v) &&
      fs.existsSync(path.join(dataDir, v))
    )
      return v;
    const u = new URL(v);
    if (u.protocol !== "https:" || u.username || u.password)
      throw Error("لینک تصویر باید https باشد");
    return u.href;
  }
  const server = http.createServer(async (req, res) => {
    const send = (code, value, type = "application/json") => {
      res.writeHead(code, {
        "Content-Type": type,
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy":
          "default-src 'self'; img-src 'self' https: data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; media-src http: https: blob:",
      });
      res.end(type === "application/json" ? JSON.stringify(value) : value);
    };
    try {
      const u = new URL(req.url, "http://localhost"),
        p = u.pathname;
      const localClient =
        req.headers.origin === "null" || req.headers.origin === "file://";
      if (
        localClient &&
        (req.headers["x-itv-token"] === tvToken || req.method === "OPTIONS")
      ) {
        res.setHeader("Access-Control-Allow-Origin", req.headers.origin);
        res.setHeader(
          "Access-Control-Allow-Headers",
          "X-ITV-Token, Content-Type",
        );
        res.setHeader(
          "Access-Control-Allow-Methods",
          "GET, POST, PATCH, DELETE, OPTIONS",
        );
        if (req.method === "OPTIONS") return send(204, "", "text/plain");
      }
      if (
        req.headers.origin &&
        !localClient &&
        req.headers.origin !== `http://${req.headers.host}`
      )
        return send(403, { error: "مبدأ غیرمجاز است" });
      const media = p.match(/^\/media\/([a-f\d]+)\/([a-f\d]+)$/);
      if (media && ["GET","HEAD"].includes(req.method)) {
        if (u.searchParams.get("ticket") !== mediaTicket) return send(401,{error:"دسترسی پخش نامعتبر است"});
        const item = db.items.find(x => x.id === media[1] && x.kind === "webdav");
        const episode = item?.episodes.find(x => x.sourceEpisodeId === media[2]);
        if (!episode) return send(404,{error:"فایل پیدا نشد"});
        const remote = await webdav.open(episode.mediaUrl,item.webdav,req.headers.range,0,req.method);
        if (![200,206,416].includes(remote.statusCode)) {
          remote.resume(); return send(502,{error:webdav.errorMessage(remote.statusCode)});
        }
        const headers = {"Cache-Control":"no-store", "X-Content-Type-Options":"nosniff"};
        for (const name of ["content-type","content-length","content-range","accept-ranges","last-modified"])
          if (remote.headers[name]) headers[name] = remote.headers[name];
        if (!/^video\//i.test(headers["content-type"] || "")) {
          const extension = new URL(episode.mediaUrl).pathname.split(".").pop().toLowerCase();
          const mime = {mp4:"video/mp4",m4v:"video/mp4",webm:"video/webm",mkv:"video/x-matroska",mov:"video/quicktime"}[extension];
          if (mime) headers["content-type"] = mime;
        }
        res.writeHead(remote.statusCode,headers);
        remote.on("error",() => res.destroy());
        res.on("close",() => remote.destroy());
        if (req.method === "HEAD") {remote.resume();res.end();} else remote.pipe(res);
        return;
      }
      const token = req.headers["x-itv-token"];
      const tv = token === tvToken;
      if (req.method === "GET" && (p === "/" || p === "/dashboard"))
        return send(
          200,
          await fs.promises.readFile(path.join(__dirname, "dashboard.html")),
          "text/html; charset=utf-8",
        );
      if (req.method === "GET" && p.startsWith("/fonts/")) {
        const name = p.slice(7);
        if (!["regular.ttf", "bold.ttf"].includes(name)) return send(404, {});
        return send(
          200,
          await fs.promises.readFile(path.join(appDir, "fonts", name)),
          "font/ttf",
        );
      }
      if (
        req.method === "GET" &&
        /^\/posters\/[a-f\d]{32}\.(png|jpg|webp)$/.test(p)
      )
        return send(
          200,
          await fs.promises.readFile(path.join(dataDir, p)),
          p.endsWith(".png")
            ? "image/png"
            : p.endsWith(".webp")
              ? "image/webp"
              : "image/jpeg",
        );
      if (req.method === "GET" && p.startsWith("/tv/")) {
        const rel = p.slice(4) || "index.html";
        const target = path.resolve(appDir, rel);
        if (!target.startsWith(path.resolve(appDir) + path.sep))
          return send(404, {});
        return send(
          200,
          await fs.promises.readFile(target),
          {
            html: "text/html; charset=utf-8",
            js: "text/javascript",
            css: "text/css",
            png: "image/png",
            ttf: "font/ttf",
          }[target.split(".").pop()] || "application/octet-stream",
        );
      }
      if (p === "/api/status")
        return send(200, {
          name: "iTV",
          version: "0.44.0",
          paired: db.tokens.includes(token),
          running: true,
          importing: active > 0,
        });
      let chunks = [],
        length = 0;
      for await (const b of req) {
        length += b.length;
        if (length > 8 * 1024 * 1024)
          return send(413, { error: "حداکثر حجم ۸ مگابایت است" });
        chunks.push(b);
      }
      const raw = Buffer.concat(chunks);
      const body =
        p === "/api/posters"
          ? null
          : raw.length
            ? JSON.parse(raw.toString())
            : {};
      if (p === "/api/pair" && req.method === "POST") {
        const key = req.socket.remoteAddress;
        let a = attempts.get(key);
        if (!a || Date.now() - a.since > 60000) {
          a = { count: 0, since: Date.now() };
          attempts.set(key, a);
        }
        if (++a.count > 8) return send(429, { error: "کمی صبر کنید" });
        if (String(body.pin) !== pin)
          return send(403, { error: "کد اتصال نادرست است" });
        const token = crypto.randomBytes(32).toString("hex");
        db.tokens.push(token);
        db.tokens = db.tokens.slice(-32);
        await save();
        return send(200, { token });
      }
      if (!tv && !db.tokens.includes(token))
        return send(401, { error: "ابتدا به تلویزیون متصل شوید" });
      if (p === "/api/logout") {
        db.tokens = db.tokens.filter((t) => t !== token);
        await save();
        return send(200, { ok: true });
      }
      if (p === "/api/webdav/check" && req.method === "POST") {
        const old = db.items.find(x => x.id === body.id && x.kind === "webdav");
        const credentials = {username:body.username ?? old?.webdav?.username ?? "",
          password:body.password || old?.webdav?.password || ""};
        if (body.clearCredentials) {credentials.username="";credentials.password="";}
        const remote = await webdav.open(body.url,credentials,"bytes=0-0");
        const status = remote.statusCode;
        const result = {ok:[200,206].includes(status),rangeSupported:status === 206,
          contentType:remote.headers["content-type"] || "",size:remote.headers["content-range"] || remote.headers["content-length"] || ""};
        remote.destroy();
        if (!result.ok) throw Error(webdav.errorMessage(status));
        return send(200,result);
      }
      const importTarget = p === "/api/series" && req.method === "POST" ? importSource.detect(body.url) : null;
      if ((["/api/filimo","/api/filmnet"].includes(p) || metadataProviders[importTarget?.provider]) && req.method === "POST") {
        const provider=metadataProviders[importTarget?.provider || p.slice(5)];
        const meta = await provider.preview(body.url);
        const old = db.items.find(x=>x.metadataProvider===meta.metadataProvider && x.metadataSourceId===meta.metadataSourceId);
        const item = provider.merge(meta,old);
        item.id = old?.id || crypto.randomBytes(8).toString('hex');
        item.sourceId = item.id; item.addedAt = old?.addedAt || Date.now();
        if(old) Object.assign(old,item); else db.items.push(item);
        await save(); return send(200,output(item));
      }
      const manual = p.match(/^\/api\/manual(?:\/([a-f\d]+))?$/);
      if (manual && ["POST","PATCH"].includes(req.method)) {
        const old = manual[1] && db.items.find(x => x.id === manual[1] && x.kind === "webdav");
        if (manual[1] && !old) return send(404,{error:"عنوان دستی پیدا نشد"});
        const item = webdav.normalize(body,old || {});
        item.customPosterUrl = imageValue(body.posterUrl);
        item.customBackdropUrl = imageValue(body.backdropUrl);
        item.id = old?.id || crypto.randomBytes(8).toString("hex");
        item.sourceId = item.id;
        item.addedAt = old?.addedAt || Date.now();
        if (old) Object.assign(old,item); else db.items.push(item);
        await save();
        return send(200,{id:item.id});
      }
      if (p === "/api/preview" && req.method === "POST") {
        const target=importSource.detect(body.url),provider=metadataProviders[target.provider];
        if(!provider)return send(200,await catalogApi.preview(target.url));
        const meta=await provider.preview(target.url);
        return send(200,{...meta,title:meta.sourceTitle,posterUrl:meta.automaticPosterUrl,
          seasonCount:new Set(meta.episodes.map(e=>e.seasonNumber)).size,episodeCount:meta.episodes.length});
      }
      if (p === "/api/series" && req.method === "GET") {
        for (const item of db.items) if(metadataProviders[item.metadataProvider] && item.importState!=='RUNNING' && Date.now()-(item.lastMetadataAttempt||item.lastMetadataRefresh||0)>6*3600000) {
          item.lastMetadataAttempt=Date.now(); refresh(item).catch(e=>console.error('iTV metadata:',e.message));
        }
        return send(200, { items: db.items.map(output) });
      }
      if (p === "/api/series" && req.method === "POST") {
        const meta = await catalogApi.preview(body.url);
        let s = db.items.find(
          (x) => x.sourceId === meta.sourceId && x.kind === meta.kind,
        );
        if (!s) {
          s = {
            ...meta,
            id: crypto.randomBytes(8).toString("hex"),
            sourceTitle: meta.title,
            automaticPosterUrl: meta.posterUrl,
            automaticBackdropUrl: meta.backdropUrl,
            episodes: [],
            importState: "RUNNING",
          };
          db.items.push(s);
          await save();
          refresh(s).catch((e) => console.error("iTV import:", e.message));
        }
        return send(200, { id: s.id });
      }
      if (p === "/api/posters" && req.method === "POST") {
        let ext;
        if (
          raw
            .subarray(0, 8)
            .equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
        )
          ext = "png";
        else if (raw[0] === 255 && raw[1] === 216 && raw[2] === 255)
          ext = "jpg";
        else if (
          raw.toString("ascii", 0, 4) === "RIFF" &&
          raw.toString("ascii", 8, 12) === "WEBP"
        )
          ext = "webp";
        else
          return send(400, { error: "فقط تصویر JPEG، PNG یا WebP مجاز است" });
        const name = crypto.randomBytes(16).toString("hex") + "." + ext;
        await fs.promises.writeFile(path.join(dataDir, "posters", name), raw);
        return send(200, {
          customPosterUrl: "/posters/" + name,
          posterUrl: "/posters/" + name,
        });
      }
      const m = p.match(
        /^\/api\/series\/([a-f\d]+)(?:\/(refresh|progress|resolve|qualities|watched|continue))?$/,
      );
      if (m) {
        const s = db.items.find((x) => x.id === m[1]);
        if (!s) return send(404, { error: "عنوان پیدا نشد" });
        if (m[2] === "refresh" && req.method === "POST") {
          if (s.kind === "webdav" && !metadataProviders[s.metadataProvider]) return send(200,{ok:true});
          if (s.importState !== "RUNNING")
            refresh(s).catch((e) => console.error("iTV import:", e.message));
          return send(200, { ok: true });
        }
        if (
          (m[2] === "resolve" || m[2] === "qualities") &&
          req.method === "POST" &&
          tv
        ) {
          if (!s.episodes.some((e) => e.sourceEpisodeId === body.episodeId))
            throw Error("قسمت پیدا نشد");
          if (s.kind === "webdav") {
            if (m[2] === "qualities") return send(200,{items:[]});
            const episode = s.episodes.find(e => e.sourceEpisodeId === body.episodeId);
            if (!episode.mediaUrl) throw Error("لینک فایل این قسمت را در پنل WebDAV وارد کنید");
            const remote = await webdav.open(episode.mediaUrl,s.webdav,"bytes=0-0");
            const status = remote.statusCode;
            remote.destroy();
            if (![200,206].includes(status)) throw Error(webdav.errorMessage(status));
            return send(200,{url:`http://127.0.0.1:${server.address().port}/media/${s.id}/${episode.sourceEpisodeId}?ticket=${mediaTicket}&v=${crypto.randomBytes(8).toString("hex")}`,
              type:"file",quality:"کیفیت اصلی فایل",variants:[]});
          }
          return send(
            200,
            m[2] === "qualities"
              ? { items: await catalogApi.qualities(s.kind, body.episodeId) }
              : await catalogApi.resolveHighest(s.kind, body.episodeId),
          );
        }
        if (m[2] === "continue" && req.method === "POST" && tv) {
          if (typeof body.hidden !== "boolean") throw Error("وضعیت نامعتبر است");
          s.continueHidden = body.hidden;
          await save();
          return send(200, {ok:true});
        }
        if (m[2] === "watched" && req.method === "POST" && tv) {
          const episode = s.episodes.find(e => e.sourceEpisodeId === body.episodeId);
          if (!episode) throw Error("قسمت پیدا نشد");
          if (typeof body.watched !== "boolean") throw Error("وضعیت نامعتبر است");
          episode.watched = body.watched;
          episode.position = 0;
          await save();
          return send(200, {ok:true, watched:episode.watched});
        }
        if (m[2] === "progress" && req.method === "POST" && tv) {
          const e = s.episodes.find(
            (e) => e.sourceEpisodeId === body.episodeId,
          );
          if (!e) throw Error("قسمت پیدا نشد");
          const pos = Math.max(0, Number(body.position) || 0),
            duration = Math.max(0, Number(body.duration) || 0);
          if (pos > 0) s.continueHidden = false;
          Object.assign(e, {
            ...rules.progress(pos, duration),
            lastPlayed: Date.now(),
          });
          await save();
          return send(200, { ok: true });
        }
        if (!m[2] && req.method === "DELETE") {
          if (s.importState === "RUNNING")
            return send(409, { error: "تا پایان دریافت صبر کنید" });
          db.items = db.items.filter((x) => x !== s);
          await save();
          return send(200, { ok: true });
        }
        if (!m[2] && req.method === "PATCH") {
          if (s.kind === "webdav") throw Error("عنوان WebDAV را از فرم مدیریت فایل‌ها ویرایش کنید");
          const src = body.url ? catalog.source(body.url) : null;
          const updates = { localTitle: String(body.localTitle || "").trim() };
          if ("customPosterUrl" in body)
            updates.customPosterUrl = imageValue(body.customPosterUrl);
          if ("customBackdropUrl" in body)
            updates.customBackdropUrl = imageValue(body.customBackdropUrl);
          Object.assign(s, src || {}, updates);
          await save();
          if (body.url && s.importState !== "RUNNING")
            refresh(s).catch((e) => console.error("iTV import:", e.message));
          return send(200, { ok: true });
        }
      }
      send(404, { error: "مسیر پیدا نشد" });
    } catch (e) {
      if (res.headersSent) {res.destroy();return;}
      send(e.code === "ENOENT" ? 404 : 400, { error: e.message });
    }
  });
  return {
    server,
    start: () =>
      new Promise((resolve, reject) => {
        server.once("error", reject);
        server.listen(port, host, () =>
          resolve({
            pin,
            url: address(),
            port: server.address().port,
            tvToken,
          }),
        );
      }),
    status: () => ({
      pin,
      url: address(),
      port: server.address()?.port,
      tvToken,
    }),
    save,
  };
}
module.exports = { createServer };
