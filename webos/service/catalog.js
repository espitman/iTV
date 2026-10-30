"use strict";
const https = require("https");
const BASE = "https://gateway.telewebion.net";
function source(raw) {
  const u = new URL(
    raw.trim().replace(/[۰-۹]/g, (c) => String("۰۱۲۳۴۵۶۷۸۹".indexOf(c))),
  );
  if (
    u.protocol !== "https:" ||
    !["telewebion.net", "www.telewebion.net", "telewebion.ir", "www.telewebion.ir"].includes(u.hostname) ||
    u.username ||
    u.password ||
    (u.port && u.port !== "443")
  )
    throw Error("فقط نشانی معتبر https تلوبیون مجاز است");
  const m = u.pathname.match(
    /^\/(program|product)\/(0x[\da-f]+)(?:\/archive)?\/?$/i,
  );
  if (!m) throw Error("مسیر برنامه یا محصول نامعتبر است");
  return {
    kind: m[1].toLowerCase(),
    sourceId: m[2].toLowerCase(),
    pageUrl: `https://telewebion.net/${m[1].toLowerCase()}/${m[2].toLowerCase()}`,
  };
}
function get(url, asText = false, redirects = 0, headers = {}) {
  return new Promise((resolve, reject) => {
    const r = https.get(
      url,
      {
        headers: { Accept: "application/json", "User-Agent": "iTV/0.31 webOS", ...headers },
      },
      (res) => {
        if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
          res.resume();
          try {
            const next = new URL(res.headers.location, url);
            if (
              redirects >= 4 ||
              next.protocol !== "https:" ||
              next.username ||
              next.password ||
              !(
                next.hostname === "telewebion.net" ||
                next.hostname.endsWith(".telewebion.net") ||
                next.hostname === "telewebion.ir" ||
                next.hostname.endsWith(".telewebion.ir")
              )
            )
              throw Error("تغییر مسیر منبع نامعتبر است");
            resolve(get(next.href, asText, redirects + 1));
          } catch (e) {
            reject(e);
          }
          return;
        }
        res.on("error", reject);
        let s = "";
        res.on("data", (b) => {
          s += b;
          if (s.length > 16 * 1024 * 1024)
            r.destroy(Error("پاسخ بیش از حد بزرگ است"));
        });
        res.on("end", () => {
          try {
            if (res.statusCode !== 200)
              throw Error("خطای منبع: " + res.statusCode);
            resolve(asText ? { text: s, url } : JSON.parse(s));
          } catch (e) {
            reject(e);
          }
        });
      },
    );
    r.setTimeout(20000, () => r.destroy(Error("مهلت اتصال تمام شد")));
    r.on("error", reject);
  });
}
const text = (o, ...keys) => {
  for (const k of keys)
    if (o && o[k] != null && String(o[k]).trim() && o[k] !== "null")
      return String(o[k]).trim();
  return "";
};
function image(raw, folder = "vodBannerImages") {
  if (!raw) return null;
  if (/^https:\/\//.test(raw)) return raw;
  if (raw.startsWith("/sites/default"))
    return BASE + raw.split("/").map(encodeURIComponent).join("/");
  return /^[\w-]{1,256}$/.test(raw)
    ? `https://static.telewebion.net/${folder}/${raw}/default`
    : null;
}
function pic(c, episode = false) {
  const m = c.media || {};
  for (const k of episode
    ? [
        "special_image_part",
        "horizontal_small_poster",
        "horizontal_big_poster",
        "vertical_poster",
      ]
    : [
        "vertical_poster",
        "main_small_poster",
        "horizontal_big_poster",
        "main_big_poster",
      ]) {
    const p = image(m[k]);
    if (p) return p;
  }
  return image(text(c, "image", "cover", "poster"));
}
function episode(c, kind, season, index) {
  const title = text(
    c,
    "persian_title",
    "special_title",
    "title",
    "Title",
    "name",
  );
  const explicit = text(
    c,
    "episode",
    "display_number",
    "episode_no",
    "episode_number",
    "EpisodeNo",
    "number",
  );
  const number = (explicit || title)
    .replace(/[۰-۹]/g, (c) => String("۰۱۲۳۴۵۶۷۸۹".indexOf(c)))
    .replace(/[٠-٩]/g, (c) => String("٠١٢٣٤٥٦٧٨٩".indexOf(c)))
    .match(/\d+/)?.[0];
  return {
    displayNumber: number || null,
    sourceEpisodeId: text(
      c,
      ...(kind === "program"
        ? ["EpisodeID", "episode_id", "id"]
        : ["content_id", "contentId", "id"]),
    ),
    title: text(c, "persian_title", "special_title", "title", "Title", "name"),
    imageUrl:
      kind === "program"
        ? image(text(c, "image", "Image"), "episodeImages")
        : pic(c, true),
    seasonNumber: season,
    sourceOrder: index,
    ...markers(c),
  };
}
function markers(c) {
  const out = {};
  for (const k of [
    "first_titraje_start",
    "first_titraje_end",
    "last_titraje_start",
  ])
    out[k] = Math.max(0, Number((c.titraje || {})[k] || c[k]) || 0);
  return out;
}
async function content(id, fetch = get) {
  const r = await fetch(
    `${BASE}/kandoo/vod/content/get-content?content_id=${id}&q=abc`,
  );
  const c = r.body?.content?.[0];
  if (!c) throw Error("پاسخ محصول خالی است");
  return c;
}
async function preview(raw) {
  const s = source(raw);
  if (s.kind === "program") {
    const r = await get(
      `${BASE}/kandoo/program/getEpisodesByProgramID/?ProgramID=${s.sourceId}&First=100&Offset=0`,
    );
    const p = r.body?.queryProgram?.[0];
    if (!p) throw Error("پاسخ برنامه ناقص است");
    return {
      ...s,
      kindLabel: "برنامه",
      seasonCount: 1,
      title: text(p, "title", "Title", "name", "program_title"),
      posterUrl: image(text(p, "image", "Image"), "programImages"),
      isMovie: false,
      seasons: [1],
    };
  }
  let c = await content(s.sourceId);
  if (c.content_type === "SERIALPARTS") {
    s.sourceId = c.serial?.content_id;
    if (!/^0x[\da-f]+$/i.test(s.sourceId))
      throw Error("شناسه سریال نامعتبر است");
    c = await content(s.sourceId);
    s.pageUrl = `https://telewebion.net/product/${s.sourceId}`;
  }
  return {
    ...s,
    kindLabel: c.content_type === "MOVIE" ? "فیلم سینمایی" : "سریال",
    seasonCount: (c.sorted_seasons || []).length,
    title: text(c, "persian_title", "title", "name"),
    posterUrl: pic(c),
    backdropUrl: image(
      text(
        c.media,
        "main_big_poster",
        "horizontal_big_poster",
        "horizontal_small_poster",
      ),
    ),
    description: text(c, "story", "description", "summary"),
    isMovie: c.content_type === "MOVIE",
    durationMinutes: Number(c.duration) || 0,
    seasons: (c.sorted_seasons || []).map(
      (v, i) => Number(typeof v === "object" ? v.season || v.id : v) || i + 1,
    ),
    credits: [
      "director",
      "writer",
      "actors",
      "producer",
      "cameraman",
      "editor",
      "composer",
    ]
      .flatMap((k) =>
        (c[k] || []).map((p) => ({
          name: p.person?.title,
          role: p.role?.title || k,
          imageUrl: image(p.person?.image),
        })),
      )
      .filter((p) => p.name),
  };
}
async function episodes(meta, onPage = async () => {}) {
  if (meta.isMovie)
    return [episode(await content(meta.sourceId), "product", 1, 0)];
  const out = [];
  for (const season of meta.kind === "program" ? [1] : meta.seasons) {
    for (let offset = 0; offset < 100000; offset += 100) {
      const url =
        meta.kind === "program"
          ? `${BASE}/kandoo/program/getEpisodesByProgramID/?ProgramID=${meta.sourceId}&First=100&Offset=${offset}`
          : `${BASE}/kandoo/vod/content/get-serial?content_id=${meta.sourceId}&season=${season}&first=100&offset=${offset}`;
      const r = await get(url);
      let rows;
      if (meta.kind === "program") rows = r.body?.queryProgram?.[0]?.episodes;
      else {
        const c = r.body?.content;
        rows = (Array.isArray(c) ? c[0] : c)?.serial_parts;
      }
      if (!Array.isArray(rows)) throw Error("پاسخ فصل ناقص است");
      out.push(
        ...rows
          .map((c, i) => episode(c, meta.kind, season, offset + i))
          .filter((e) => e.sourceEpisodeId),
      );
      await onPage(out.slice());
      if (rows.length < 100) break;
    }
  }
  return out.sort(
    (a, b) =>
      a.seasonNumber - b.seasonNumber ||
      (Number(a.displayNumber) || Infinity) -
        (Number(b.displayNumber) || Infinity) ||
      a.sourceOrder - b.sourceOrder,
  );
}
async function resolve(kind, id, fetch = get) {
  let c, url;
  if (!/^0x[\da-f]+$/i.test(id)) throw Error("شناسه نامعتبر است");
  if (kind === "program") {
    const r = await fetch(
      `${BASE}/kandoo/episode/getEpisodeDetail/?EpisodeId=${id}`,
    );
    c = r.body?.queryEpisode?.[0];
    if (!c || c.enable === false) throw Error("قسمت در دسترس نیست");
    const channel = c.channel?.descriptor;
    if (!/^[\w-]+$/.test(channel || "")) throw Error("کانال نامعتبر است");
    url = `https://cdna.telewebion.net/${channel}/episode/${id}/playlist.m3u8`;
  } else {
    c = await content(id, fetch);
    url = c.stream?.telewebion;
    if (typeof url !== "string" || !url.trim()) {
      // The public player obtains FREE VOD sources through Aparatchi,
      // using the content token returned by the same public metadata API.
      if (c.content_status !== "FREE" || !c.content_token || c.enable === false)
        throw Error("این قسمت از مسیر پخش عمومی تلوبیون در دسترس نیست");
      const response = await fetch(
        `https://rozaneh.telewebion.ir/aparatchi/api/v1/stream/${id}`,
        false, 0, { content: c.content_token });
      url = response.body?.source?.find(s =>
        ["application/x-mpegURL", "application/vnd.apple.mpegurl"].includes(s.type))?.src;
      if (typeof url !== "string" || !url.trim())
        throw Error("تلوبیون لینک پخش معتبری برای این قسمت برنگرداند");
    }
  }
  const u = new URL(url);
  if (
    u.protocol !== "https:" ||
    !(
      u.hostname === "telewebion.net" || u.hostname.endsWith(".telewebion.net") ||
      u.hostname === "telewebion.ir" || u.hostname.endsWith(".telewebion.ir")
    ) ||
    u.username ||
    u.password
  )
    throw Error("استریم نامعتبر است");
  return { url, ...markers(c) };
}
function parseVariants(text, base) {
  if (!text.trimStart().startsWith("#EXTM3U"))
    throw Error("پاسخ فهرست پخش نامعتبر است");
  const lines = text.split(/\r?\n/),
    out = [];
  for (let i = 0; i < lines.length; i++)
    if (lines[i].startsWith("#EXT-X-STREAM-INF:")) {
      const height = Number(lines[i].match(/RESOLUTION=\d+x(\d+)/)?.[1]) || 0;
      const bandwidth =
        Number(lines[i].match(/(?:^|[:,])BANDWIDTH=(\d+)/)?.[1]) || 0;
      let j = i + 1;
      while (lines[j]?.startsWith("#") || lines[j] === "") j++;
      if (!lines[j]) continue;
      const url = new URL(lines[j].trim(), base);
      if (
        url.protocol === "https:" &&
        (url.hostname === "telewebion.net" ||
          url.hostname.endsWith(".telewebion.net") ||
          url.hostname === "telewebion.ir" || url.hostname.endsWith(".telewebion.ir")) &&
        !url.username &&
        !url.password
      )
        out.push({
          height,
          bandwidth,
          label: height ? height + "p" : Math.round(bandwidth / 1000) + " kbps",
          url: url.href,
        });
    }
  return out;
}
async function resolveHighest(kind, id) {
  const stream = await resolve(kind, id),
    playlist = await get(stream.url, true);
  const variants = parseVariants(playlist.text, playlist.url),
    best = require("./rules").highest(variants);
  if (!best && playlist.text.includes("#EXT-X-STREAM-INF:"))
    throw Error("کیفیت معتبر برای پخش پیدا نشد");
  return {
    ...stream,
    masterUrl: stream.url,
    url: best?.url || playlist.url,
    quality: best?.label || "بالاترین کیفیت منبع",
    variants,
  };
}
async function qualities(kind, id) {
  const stream = await resolve(kind, id),
    playlist = await get(stream.url, true);
  return parseVariants(playlist.text, playlist.url);
}

module.exports = {
  source,
  image,
  markers,
  preview,
  episodes,
  resolve,
  qualities,
  resolveHighest,
  parseVariants,
};
