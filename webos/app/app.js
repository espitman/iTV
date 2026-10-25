"use strict";
const root = document.querySelector("#app"),
  modal = document.querySelector("#modal");
const state = {
  items: [],
  route: "home",
  id: null,
  season: null,
  query: "",
  base: "",
  token: "",
  session: null,
  player: null,
};
const fa = (n) => String(n ?? "").replace(/\d/g, (d) => "۰۱۲۳۴۵۶۷۸۹"[d]);
const esc = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const asset = (s) => (s?.startsWith("/") ? state.base + s : s || "");
function toast(s) {
  const el = document.querySelector("#toast");
  el.textContent = s;
  el.style.display = "block";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.style.display = "none"), 5000);
}
async function api(url, body, method, timeoutMs = 25000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const r = await fetch(state.base + url, {
      signal: controller.signal,
      method: method || (body ? "POST" : "GET"),
      headers: {
        "X-ITV-Token": state.token,
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const d = await r.json();
    if (!r.ok) throw Error(d.error || "خطای اتصال");
    return d;
  } finally {
    clearTimeout(timeout);
  }
}
function button(label, action, attrs = "") {
  return `<button data-action="${action}" ${attrs}>${esc(label)}</button>`;
}
function logo() {
  return '<b class="logo" aria-label="iTV"><i>i</i><span>TV</span></b>';
}
function iconButton(label, action, name, attrs = "") {
  return `<button data-action="${action}" aria-label="${esc(label)}" title="${esc(label)}" class="icon-button" ${attrs}>${ITVIcons[name] || ""}</button>`;
}
function header() {
  if (state.route === "detail")
    return `<header class="plain-header">${logo()}</header>`;
  if (["movies", "series", "settings"].includes(state.route))
    return `<header class="catalog-header">${logo()}<div>${button("بازگشت", "home", 'class="back"')}<h1>${state.route === "settings" ? "اتصال به پنل مدیریت" : state.route === "movies" ? "فیلم‌ها" : "سریال‌ها"}</h1>${state.route !== "settings" ? `<small>${fa(state.items.filter((s) => s.isMovie === (state.route === "movies")).length)} عنوان</small>` : ""}</div></header>`;
  return `<header>${logo()}<nav>${[
    ["خانه", "home"],
    ["سریال‌ها", "series"],
    ["فیلم‌ها", "movies"],
    ["تنظیمات", "settings"],
    ["جست‌وجو", "search"],
  ]
    .map(([label, route]) =>
      button(label, route, `class="${state.route === route ? "active" : ""}"`),
    )
    .join("")}</nav></header>`;
}
function card(s, episode) {
  const e = episode;
  const number = e && (e.displayNumber ||
    (ITVRules.ordered(s.episodes).filter(row => row.seasonNumber === e.seasonNumber)
      .findIndex(row => row.sourceEpisodeId === e.sourceEpisodeId) + 1));
  const title = e ? `قسمت ${fa(number)}` : s.title;
  const image = asset(e ? e.imageUrl || s.posterUrl : s.posterUrl);
  const position = e?.position || 0,
    duration = e?.duration || 1;
  return `<button class="poster ${e ? "wide" : ""}" aria-label="${esc(fa(title))}" data-action="${e ? "play" : "detail"}" data-id="${s.id}" ${e ? `data-episode="${esc(e.sourceEpisodeId)}"` : ""}><img src="${esc(image)}" alt="" onload="this.classList.add('loaded')" onerror="this.style.visibility='hidden';this.parentElement.querySelector('span')?.classList.remove('poster-caption')"><span class="${!e && image ? "poster-caption" : ""}">${esc(fa(title))}</span>${e?.watched ? `<small class="watched">${ITVIcons.watched_check}</small>` : ""}${position ? `<i class="progress" style="width:${Math.min(100, (position / duration) * 100)}%"></i>` : ""}</button>`;
}
const selected = (s) => ITVRules.select(s.episodes);
function hero(s, detail = false) {
  const ep = selected(s),
    url = asset(s.backdropUrl || ep?.imageUrl || s.posterUrl),
    playLabel = ep?.position > 10 ? "ادامه تماشا" : detail ? "تماشا" : "تماشا";
  const meta = s.isMovie
    ? `فیلم سینمایی · ${fa(s.durationMinutes)} دقیقه`
    : `سریال · ${fa(s.seasonCount || 1)} فصل · ${fa(s.episodes.length)} قسمت`;
  const play = `<button class="primary" data-action="play" data-id="${s.id}">${detail ? ITVIcons.play_tiny : ""}<span>${playLabel}</span>${!detail ? ITVIcons.play_tiny : ""}</button>`;
  return `<section class="hero ${detail ? "detail" : ""}"><div class="hero-art" style="background-image:url(&quot;${esc(url)}&quot;)"><div class="hero-wash"></div><div class="hero-bottom"></div><div class="hero-right"></div></div><div class="hero-copy"><h1>${esc(fa(s.title))}</h1>${detail ? `<div class="detail-meta">${meta}</div>${s.description ? `<p>${esc(fa(s.description))}</p>` : ""}` : `<div class="meta"><small>${s.isMovie ? "فیلم سینمایی" : "سریال"}</small><small>${fa(s.seasonCount || 1)} فصل</small><small>${fa(s.episodes.length)} قسمت</small></div>`}<div class="actions">${!detail ? button("جزئیات", "detail", `data-id="${s.id}"`) : ""}${play}</div></div></section>`;
}
function section(title, html, cls = "") {
  return `<section class="section ${cls === "credits" ? "credits-section" : ""}"><h2>${esc(title)}</h2><div class="shelf ${cls}">${html}</div></section>`;
}
function render() {
  if (state.player) return;
  root.dataset.route = state.route;
  let html = header();
  const s = state.items.find((s) => s.id === state.id);
  if (state.route === "home") {
    const continuing = ITVRules.continueWatching(state.items)
      .map(s => ({s, e: selected(s)}));
    const featured =
      state.items.find((s) => s.id === state.featured) ||
      continuing[0]?.s ||
      state.items[0];
    if (featured) html += hero(featured);
    if (continuing.length)
      html +=
        '<div class="continue-strip">' +
        section(
          "ادامهٔ تماشا",
          continuing.map(({ s, e }) => card(s, e)).join(""),
        ) +
        "</div>";
    html += `<div class="home-library ${continuing.length ? "has-continue" : ""}">`;
    for (const [title, movie] of [
      ["سریال‌ها", false],
      ["فیلم‌ها", true],
    ]) {
      const rows = state.items.filter((s) => s.isMovie === movie);
      if (rows.length)
        html += section(
          title,
          rows
            .slice(0, 8)
            .map((s) => card(s))
            .join("") +
            button("نمایش همه", movie ? "movies" : "series", 'class="more"'),
        );
    }
    html += "</div>";
    if (!featured)
      html += `<div class="empty"><h1>به iTV خوش آمدید</h1><p>از پنل مدیریت، فیلم و سریال به کتابخانه اضافه کنید.</p>${button("اتصال به پنل مدیریت", "settings", 'class="primary"')}</div>`;
  } else if (state.route === "detail" && s) {
    html += hero(s, true);
    if (s.importState === "ERROR")
      html += `<p class="section error">${esc(s.importError)} ${button("تلاش دوباره", "refresh", `data-id="${s.id}"`)}</p>`;
    const seasons = [...new Set(s.episodes.map((e) => e.seasonNumber))];
    if (!seasons.includes(state.season)) state.season = seasons[0];
    if (!s.isMovie) {
      html += `<div class="episode-section"><div class="section chips">${seasons.map((n) => button("فصل " + fa(n), "season", `data-season="${n}" class="${n === state.season ? "active" : ""}"`)).join("")}</div>`;
      html +=
        section(
          "قسمت‌ها",
          s.episodes
            .filter((e) => e.seasonNumber === state.season)
            .map((e) => card(s, e))
            .join(""),
          "episodes",
        ) + "</div>";
    }
    if (s.credits?.length)
      html += section(
        "عوامل",
        s.credits
          .map(
            (p) =>
              `<div class="poster" tabindex="0" role="group" aria-label="${esc(p.name)}"><img src="${esc(asset(p.imageUrl))}" alt=""><span>${esc(p.name)}</span><small>${esc(p.role)}</small></div>`,
          )
          .join(""),
        "credits",
      );
  } else if (state.route === "settings") {
    html += `<div class="settings-stage"><section class="settings"><div class="settings-status"><span><i></i>پنل مدیریت فعال است</span><small>iTV webOS 0.44.0</small></div><div class="settings-body"><div class="settings-fields"><label>نشانی پنل مدیریت</label><a>${esc(state.session?.url || state.base)}</a><label>کد اتصال</label><div class="pin">${fa(
      state.session?.pin || "----",
    )
      .split("")
      .map((d) => `<b>${d}</b>`)
      .join(
        "",
      )}</div></div><div class="qr-plate"><div id="qr"></div><small>اسکن برای بازکردن پنل</small></div></div><p class="settings-help">گوشی یا لپ‌تاپ را به شبکهٔ تلویزیون وصل کنید و نشانی بالا را در مرورگر باز کنید.</p></section></div>`;
  } else {
    if (state.route === "search")
      html +=
        '<input class="search" id="search" placeholder="جست‌وجوی فیلم و سریال در کتابخانه" value="' +
        esc(state.query) +
        '">';
    const rows = state.items.filter((s) =>
      state.route === "search"
        ? s.title.includes(state.query)
        : s.isMovie === (state.route === "movies"),
    );
    html += `${state.route === "search" ? '<section class="results-heading">نتایج جست‌وجو</section>' : ""}<div class="grid">${rows.map((s) => card(s)).join("")}</div>`;
    if (!rows.length) html += '<div class="empty muted">عنوانی پیدا نشد</div>';
  }
  const focus = document.activeElement?.dataset;
  root.innerHTML = html;
  if (state.route === "settings" && state.session?.url)
    document.querySelector("#qr").innerHTML = ITVQR.toSvg(state.session.url);
  let target = focus?.action
    ? [...root.querySelectorAll("button")].find(
        (b) =>
          b.dataset.action === focus.action &&
          b.dataset.id === focus.id &&
          b.dataset.episode === focus.episode,
      )
    : null;
  (
    target ||
    (state.route === "search"
      ? root.querySelector("input")
      : root.querySelector("button.primary") || root.querySelector("button"))
  )?.focus();
}
async function reload() {
  state.items = (await api("/api/series")).items
    .reverse()
    .map((s) => ({ ...s, episodes: ITVRules.ordered(s.episodes) }));
}
async function go(route, id) {
  if (state.player) await stop();
  state.route = route;
  state.id = id;
  root.scrollTop = 0;
  render();
}
root.addEventListener("focusin", (e) => {
  const b = e.target.closest(".poster[data-id]");
  if (state.route === "home" && b) {
    const s = state.items.find((s) => s.id === b.dataset.id);
    if (s) {
      state.featured = s.id;
      const old = root.querySelector(".hero");
      if (old) {
        const box = document.createElement("div");
        box.innerHTML = hero(s);
        old.replaceWith(box.firstElementChild);
      }
    }
  }
});
root.addEventListener("input", (e) => {
  if (e.target.id === "search") {
    state.query = e.target.value;
    const start = e.target.selectionStart;
    render();
    const input = document.querySelector("#search");
    input.focus();
    input.setSelectionRange(start, start);
  }
});
let episodePress = null, suppressEpisodeClickUntil = 0;
async function toggleEpisodeWatched(b) {
  const s = state.items.find(s => s.id === b.dataset.id);
  const e = s?.episodes.find(e => e.sourceEpisodeId === b.dataset.episode);
  if (!e) return;
  const watched = !e.watched;
  await api(`/api/series/${s.id}/watched`, {episodeId:e.sourceEpisodeId, watched});
  e.watched = watched;
  e.position = 0;
  render();
  toast(watched ? "دیده‌شده" : "دیده‌نشده");
}
function startEpisodePress(b, kind) {
  if (episodePress) return;
  const press = episodePress = {b, kind, long:false};
  press.timer = setTimeout(() => {
    if (episodePress !== press) return;
    press.long = true;
    suppressEpisodeClickUntil = Date.now() + 1200;
    toggleEpisodeWatched(b).catch(err => toast(err.message));
  }, 600);
}
function endEpisodePress(cancel = false) {
  const press = episodePress;
  if (!press) return;
  clearTimeout(press.timer);
  episodePress = null;
  if (press.long) suppressEpisodeClickUntil = Date.now() + 800;
  else if (press.kind === "key" && !cancel) press.b.click();
}
root.addEventListener("pointerdown", e => {
  const b = e.target.closest('[data-action="play"][data-episode]');
  if (b && !state.player && !modal.open) startEpisodePress(b, "pointer");
});
root.addEventListener("pointerup", () => endEpisodePress());
root.addEventListener("pointercancel", () => endEpisodePress(true));
root.addEventListener("pointerleave", () => endEpisodePress(true));
root.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-action]");
  if (!b) return;
  if (b.dataset.episode && Date.now() < suppressEpisodeClickUntil) {
    e.preventDefault(); return;
  }
  try {
    const a = b.dataset.action;
    if (["home", "series", "movies", "search", "settings"].includes(a))
      return go(a);
    if (a === "detail") return go(a, b.dataset.id);
    if (a === "season") {
      state.season = Number(b.dataset.season);
      return render();
    }
    if (a === "refresh") {
      await api(`/api/series/${b.dataset.id}/refresh`, {});
      return toast("دریافت اطلاعات آغاز شد");
    }
    if (a === "play") {
      const s = state.items.find((s) => s.id == b.dataset.id);
      const ep = b.dataset.episode
        ? s.episodes.find((e) => e.sourceEpisodeId === b.dataset.episode)
        : selected(s);
      if (!ep) return toast("قسمت‌ها هنوز آماده نشده‌اند");
      await play(s, ep);
    } else if (state.player) await playerAction(a, b);
  } catch (e) {
    toast(e.message);
  }
});
function clock(s) {
  s = Math.max(0, Math.floor(s || 0));
  return fa(
    `${Math.floor(s / 3600) ? Math.floor(s / 3600) + ":" : ""}${String(Math.floor(s / 60) % 60).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`,
  );
}
async function askResume(position, s, e) {
  return new Promise((resolve) => {
    let count = 5;
    modal.className = "resume-dialog";
    modal.style.backgroundImage = `linear-gradient(#080f14aa,#080f14aa),url("${asset(s.backdropUrl || e.imageUrl)}")`;
    modal.innerHTML = `<div class="resume-card"><small>• ادامهٔ تماشا</small><h2>${esc(fa(s.title))}</h2><p>${esc(fa(e.title))} · از ${clock(position)} ادامه می‌دهید؟</p><div class="actions">${button("ادامه ۵", "resume", 'id="resume-count" class="primary"')}${button("از ابتدا", "restart")}</div></div>`;
    modal.showModal();
    modal.querySelector("button").focus();
    const finish = (value) => {
      clearInterval(timer);
      modal.close();
      modal.className = "";
      modal.style.backgroundImage = "";
      modal.onclick = null;
      modal.oncancel = null;
      resolve(value);
    };
    const timer = setInterval(() => {
      if (--count === 0) finish(true);
      else
        document.querySelector("#resume-count").textContent =
          "ادامه " + fa(count);
    }, 1000);
    modal.onclick = (e) => {
      const a = e.target.closest("button")?.dataset.action;
      if (a) finish(a === "resume");
    };
    modal.oncancel = (e) => {
      e.preventDefault();
      finish(true);
    };
  });
}
function playerLoading(p, message = "در حال آماده‌سازی پخش…", failed = false) {
  if (state.player !== p) return;
  if (p.loading && !failed) return;
  clearTimeout(p.loadingTimer);
  p.waitPosition = p.video.currentTime;
  p.loading = true;
  p.failed = failed;
  const buffer = document.querySelector("#buffer");
  buffer.hidden = false;
  buffer.innerHTML = failed
    ? `<p>${esc(message)}</p>${button("تلاش دوباره", "retry")}${button("بازگشت", "back")}`
    : '<div class="buffer-spinner" role="progressbar" aria-label="در حال بارگذاری"></div>';
  document.querySelector(".controls").classList.add("hidden");
  document.querySelector(".pause-info").hidden = true;
  document.querySelector("#skip").hidden = true;
  document.querySelector("#next").hidden = true;
  buffer.querySelector("button")?.focus();
  if (!failed) p.loadingTimer = setTimeout(() => {
    if (state.player !== p || !p.loading) return;
    playerLoading(p, "اتصال کند است؛ پخش آماده نشد. دوباره تلاش کنید.", true);
  }, 30000);
}
function playerReady(p) {
  if (state.player !== p || p.video.seeking || p.video.readyState < 3) return;
  clearTimeout(p.loadingTimer);
  p.loading = false;
  p.failed = false;
  document.querySelector("#buffer").hidden = true;
  if (p.controls) document.querySelector(".controls").classList.remove("hidden");
  if (document.activeElement?.closest("#buffer")) {
    const target = document.querySelector(p.controls ? "#seek" : ".player");
    target.tabIndex = -1;
    target.focus();
  }
}
async function play(s, e) {
  if (s.kind === "webdav" && !e.mediaUrl) { toast("لینک فایل این قسمت را در پنل WebDAV وارد کنید"); return; }
  if (state.player) await stop(false);
  let resume = e.position > 10 ? await askResume(e.position, s, e) : false;
  if (resume === null) {
    render();
    return;
  }
  const p = {
    s,
    e,
    video: null,
    controls: true,
    wantPlay: true,
    cancelled: false,
    next: null,
    markers: {},
    timer: null,
    saveTimer: null,
    hideTimer: null,
  };
  state.player = p;
  root.dataset.route = "player";
  root.innerHTML = `<div class="player"><video id="video" playsinline></video><div id="seek-left" class="seek-feedback left" hidden></div><div id="seek-right" class="seek-feedback right" hidden></div><div class="loading" id="buffer" style="position:absolute;inset:0">در حال آماده‌سازی پخش…</div><button class="skip" id="skip" data-action="skip" hidden>رد کردن تیتراژ</button><div class="next" id="next" hidden></div><div class="pause-info" hidden><img src="${esc(asset(s.posterUrl))}" alt=""><div><h1>${esc(s.title)}</h1><p>${esc(e.title)}</p></div></div><div class="controls"><div class="transport-panel"><div class="timeline-labels"><span id="position" class="time">۰۰:۰۰</span><h2>${esc(fa(s.title))} — ${esc(fa(e.title))}</h2><span id="duration" class="time">۰۰:۰۰</span></div><input id="seek" type="range" min="0" max="100" value="0" aria-label="زمان پخش"><div class="transport-actions"><div class="transport-left">${iconButton("از ابتدا", "restart", "restart")}${iconButton("۱۰ ثانیه عقب", "rewind", "rewind")}${iconButton("توقف / پخش", "toggle", "pause", 'id="toggle-play"')}${iconButton("۱۰ ثانیه جلو", "forward", "forward")}${iconButton("قسمت بعد", "next", "next")}</div><div class="transport-right">${iconButton("کیفیت پخش", "quality", "quality")}${iconButton("سرعت پخش", "speed", "speed")}${iconButton("پرش به زمان", "jump", "jump")}</div></div></div></div></div>`;
  p.video = document.querySelector("#video");
  let resumeApplied = false;
  p.video.addEventListener("loadedmetadata", () => {
    if (state.player !== p) return;
    if (resume && !resumeApplied)
      p.video.currentTime = Math.min(
        e.position,
        Math.max(0, p.video.duration - 10),
      );
    resumeApplied = true;
    document.querySelector("#duration").textContent = clock(p.video.duration);
  });
  p.video.addEventListener("pause", () => {
    if (state.player !== p || p.loading) return;
    p.wantPlay = false;
    const info = document.querySelector(".pause-info");
    if (info) info.hidden = false;
    document.querySelector("#toggle-play").innerHTML = ITVIcons.play;
    showControls();
  });
  p.video.addEventListener("playing", () => {
    if (state.player !== p) return;
    p.wantPlay = true;
    clearTimeout(p.loadingTimer);
    p.loading = false;
    p.failed = false;
    if (p.controls) document.querySelector(".controls").classList.remove("hidden");
    document.querySelector("#buffer").hidden = true;
    if (document.activeElement?.closest("#buffer")) document.querySelector("#seek").focus();
    document.querySelector(".pause-info").hidden = true;
    document.querySelector("#toggle-play").innerHTML = ITVIcons.pause;
    if (!p.started) {
      p.started = true;
      showControls();
      document.querySelector("#seek").focus();
    }
  });
  p.video.addEventListener("waiting", () => {
    if (state.player !== p || p.failed) return;
    playerLoading(p, "در حال دریافت ویدیو…");
  });
  p.video.addEventListener("error", () => {
    if (state.player !== p) return;
    playerLoading(p, "پخش این ویدیو ممکن نشد", true);
  });
  p.video.addEventListener("canplay", () => playerReady(p));
  p.video.addEventListener("seeked", () => {
    if (state.player !== p) return;
    playerReady(p);
    if (p.wantPlay) p.video.play().catch(() => {
      if (state.player === p) playerLoading(p, "پخش ادامه پیدا نکرد؛ دوباره تلاش کنید.", true);
    });
  });
  p.video.addEventListener("timeupdate", () => {
    if (state.player !== p) return;
    if (p.loading && !p.video.paused && !p.video.seeking &&
        Math.abs(p.video.currentTime - p.waitPosition) > 0.1) playerReady(p);
    updatePlayer();
  });
  p.video.addEventListener("ended", () => {
    if (state.player !== p) return;
    if (!p.cancelled && nextEpisode())
      play(s, nextEpisode()).catch((e) => toast(e.message));
    else
      stop()
        .then(render)
        .catch((e) => toast(e.message));
  });
  document.querySelector("#seek").onchange = (e) => {
    p.video.currentTime = (p.video.duration * Number(e.target.value)) / 100;
    showControls();
  };
  playerLoading(p);
  try {
    const stream = await api(`/api/series/${s.id}/resolve`, {
      episodeId: e.sourceEpisodeId,
    });
    if (state.player !== p || p.failed) return;
    p.markers = stream;
    p.master = stream.masterUrl || stream.url;
    p.highestUrl = stream.url;
    p.quality = stream.quality;
    p.qualities = stream.variants || [];
    if (stream.type !== "file" && !p.video.canPlayType("application/vnd.apple.mpegurl") &&
        window.Hls && Hls.isSupported()) {
      p.hls = new Hls({startLevel: 0, maxBufferLength: 30});
      p.hls.on(Hls.Events.ERROR, (_, data) => {
        if (state.player !== p || !data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) p.hls.startLoad();
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) p.hls.recoverMediaError();
        else playerLoading(p, "دریافت ویدیو متوقف شد؛ دوباره تلاش کنید.", true);
      });
      await new Promise((resolve, reject) => {
        p.hls.on(Hls.Events.MANIFEST_PARSED, resolve);
        p.hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal) reject(Error("دریافت ویدیو کامل نشد: " + data.details));
        });
        p.hls.attachMedia(p.video);
        p.hls.loadSource(stream.url);
      });
      if (state.player !== p) return;
    } else p.video.src = stream.url;
    await p.video.play();
    if (state.player !== p) return;

    p.saveTimer = setInterval(
      () => persist().catch((e) => toast(e.message)),
      18000,
    );
  } catch (err) {
    if (state.player === p) {
      console.error("iTV playback:", err.name, err.message);
      const message = err.name === "AbortError"
        ? "دریافت لینک پخش بیش از حد طول کشید؛ دوباره تلاش کنید."
        : err.name === "NotSupportedError"
          ? "این محیط از قالب پخش ویدیو پشتیبانی نمی‌کند."
          : err.message || "پخش آماده نشد؛ دوباره تلاش کنید.";
      playerLoading(p, message, true);
    }
  }
}
function nextEpisode() {
  const p = state.player;
  if (!p) return null;
  return p.s.episodes[p.s.episodes.indexOf(p.e) + 1];
}
async function persist(p = state.player) {
  if (!p || !Number.isFinite(p.video.duration)) return;
  Object.assign(p.e, ITVRules.progress(p.video.currentTime, p.video.duration), {
    lastPlayed: Date.now(),
  });
  await api(
    `/api/series/${p.s.id}/progress`,
    {
      episodeId: p.e.sourceEpisodeId,
      position: p.video.currentTime,
      duration: p.video.duration,
    },
    undefined,
    4000,
  );
}
async function stop(redraw = true) {
  const p = state.player;
  if (!p) return;
  clearTimeout(p.loadingTimer);
  clearInterval(p.saveTimer);
  clearTimeout(p.hideTimer);
  clearInterval(p.timer);
  clearTimeout(p.seekTimer);
  clearTimeout(p.feedbackTimer);
  if (Number.isFinite(p.seekTarget)) p.video.currentTime = p.seekTarget;
  // Snapshot progress before releasing the video, but never hold navigation on I/O.
  const saved = persist(p).catch(() => {});
  state.player = null;
  p.video.pause();
  p.hls?.destroy();
  p.video.removeAttribute("src");
  p.video.load();
  if (redraw) render();
  saved.then(async () => {
    const route = state.route,
      id = state.id;
    await reload().catch(() => {});
    if (redraw && !state.player && route === state.route && id === state.id)
      render();
  });
}
function hideControls() {
  const p = state.player;
  if (!p || modal.open || p.loading) return;
  clearTimeout(p.hideTimer);
  p.controls = false;
  document.querySelector(".controls")?.classList.add("hidden");
  if (document.activeElement?.closest(".controls")) {
    const surface = document.querySelector(".player");
    surface.tabIndex = -1;
    surface.focus({ preventScroll: true });
  }
  updatePlayer();
}
function isBackKey(e) {
  return (
    e.keyCode === 461 ||
    ["Escape", "BrowserBack", "GoBack"].includes(e.key) ||
    (e.key === "Backspace" && document.activeElement?.tagName !== "INPUT")
  );
}
function handleBack() {
  if (modal.open) {
    const cancel = new Event("cancel", { cancelable: true });
    modal.dispatchEvent(cancel);
    if (modal.open && !cancel.defaultPrevented) modal.close();
    return;
  }
  const p = state.player;
  if (p) {
    if (p.loading) { stop().catch((err) => toast(err.message)); return; }
    if (document.activeElement?.closest("#next")) {
      p.cancelled = true;
      updatePlayer();
      if (p.controls) document.querySelector("#toggle-play")?.focus();
      else {
        const surface = document.querySelector(".player");
        surface.tabIndex = -1;
        surface.focus();
      }
      return;
    }
    if (p.controls) {
      hideControls();
      return;
    }
    stop().catch((e) => toast(e.message));
    return;
  }
  if (state.route !== "home") {
    go("home");
    return;
  }
  modal.className = "";
  modal.oncancel = null;
  modal.onkeydown = null;
  modal.innerHTML = `<h2>خروج از iTV؟</h2><div class="actions">${button("خروج", "exit")}${button("لغو", "cancel")}</div>`;
  modal.showModal();
  modal.querySelector("button").focus();
  modal.onclick = (e) => {
    const a = e.target.closest("button")?.dataset.action;
    if (a === "exit") window.close();
    if (a) modal.close();
  };
}
function showControls() {
  const p = state.player;
  if (!p) return;
  p.burst = null;
  document.querySelector(".controls").classList.remove("hidden");
  p.controls = true;
  updatePlayer();
  clearTimeout(p.hideTimer);
  p.hideTimer = setTimeout(() => {
    if (state.player === p) hideControls();
  }, 8000);
}
function updatePlayer() {
  const p = state.player;
  if (!p) return;
  const v = p.video,
    t = Number.isFinite(p.seekTarget) ? p.seekTarget : v.currentTime,
    d = v.duration;
  document.querySelector("#position").textContent = clock(t);
  if (Number.isFinite(d)) {
    const seek = document.querySelector("#seek");
    seek.value = (t / d) * 100;
    seek.style.setProperty("--played", (t / d) * 100 + "%");
  }
  const m = p.markers;
  const intro = ITVRules.intro(t, m);
  const outro = ITVRules.outro(t, m, d);
  const skip = document.querySelector("#skip");
  const skipWasShown = !skip.hidden;
  skip.hidden =
    !(intro || outro) ||
    modal.open ||
    !document.querySelector("#buffer").hidden ||
    p.controls ||
    v.paused ||
    (d - t < 10 && nextEpisode() && !p.cancelled);
  skip.textContent = intro ? "رد کردن تیتراژ" : "رد کردن تیتراژ پایانی";
  p.skipTo = intro ? m.first_titraje_end : d;
  if (
    !skip.hidden &&
    !skipWasShown &&
    !document.activeElement?.closest("#next")
  )
    skip.focus();
  if (skip.hidden && document.activeElement === skip) {
    const surface = document.querySelector(".player");
    surface.tabIndex = -1;
    surface.focus();
  }
  const n = nextEpisode(),
    card = document.querySelector("#next");
  const focusedAction =
    document.activeElement?.closest("#next button")?.dataset.action;
  if (n && !v.paused && !p.cancelled && d - t <= 60 && d - t > 0) {
    card.hidden = false;
    card.innerHTML = `<small class="next-label">${d - t < 10 ? "پخش خودکار تا " + fa(Math.ceil(d - t)) + " ثانیه" : "قسمت بعد"}</small><div class="next-info"><img src="${esc(asset(n.imageUrl))}" alt=""><p>${esc(fa(n.title))}</p></div><div class="actions">${button("پخش قسمت بعد", "next", 'class="primary"')}${button("لغو", "cancel-next")}</div>`;
    if (focusedAction)
      card.querySelector(`[data-action="${focusedAction}"]`)?.focus();
    if (d - t < 10 && !p.nextLocked) {
      p.nextLocked = true;
      card.querySelector("button")?.focus();
    }
  } else {
    card.hidden = true;
    p.nextLocked = false;
  }
}
async function playerAction(a, b) {
  const p = state.player,
    v = p.video;
  if (a === "back") return stop();
  if (a === "retry") return play(p.s, p.e);
  if (p.loading) return;
  showControls();
  if (a === "speed")
    return choose(
      "سرعت پخش",
      [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2].map((n) => ({
        label: fa(n) + "×",
        value: n,
      })),
      (n) => {
        v.playbackRate = Number(n);
      },
    );
  if (a === "quality")
    return choose(
      "کیفیت پخش",
      [
        {
          label: "بالاترین کیفیت (خودکار) · " + fa(p.quality || ""),
          value: "highest",
        },
      ],
      () => {},
    );
  if (a === "jump") return jump();
  if (a === "back") return stop();
  if (a === "toggle") {
    p.wantPlay = v.paused;
    return v.paused ? v.play() : v.pause();
  }
  if (a === "rewind" || a === "forward")
    v.currentTime = Math.max(
      0,
      Math.min(
        v.duration || Infinity,
        v.currentTime + (a === "forward" ? 10 : -10),
      ),
    );
  if (a === "skip") v.currentTime = p.skipTo;
  if (a === "restart") v.currentTime = 0;
  if (a === "cancel-next") {
    p.cancelled = true;
    updatePlayer();
  }
  if (a === "next") {
    const n = nextEpisode();
    if (n) return play(p.s, n);
    toast("قسمت بعدی موجود نیست");
  }
  if (a === "retry") return play(p.s, p.e);
}
function choose(title, options, apply) {
  const previous = document.activeElement;
  modal.className = title === "سرعت پخش" ? "speed-menu" : "quality-menu";
  modal.innerHTML = `<h2>${esc(title)}</h2><div class="choices">${options.map((o) => button(o.label, "choose", `data-value="${o.value}"`)).join("")}</div>${button("بازگشت", "close")}`;
  modal.showModal();
  modal.querySelector("button").focus();
  modal.onclick = (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    if (b.dataset.action === "choose") apply(b.dataset.value);
    modal.close();
    previous?.focus();
    showControls();
  };
  modal.oncancel = () => {
    previous?.focus();
    showControls();
  };
}
function jump() {
  const p = state.player,
    value = Math.floor(p.video.currentTime),
    units = [Math.floor(value / 3600), Math.floor(value / 60) % 60, value % 60];
  modal.innerHTML = `<h2>پرش به زمان</h2><p class="muted">با بالا و پایین مقدار را تغییر دهید.</p><div class="actions time" dir="ltr">${["ساعت", "دقیقه", "ثانیه"].map((label, i) => button(fa(String(units[i]).padStart(2, "0")), "time-unit", `data-unit="${i}" aria-label="${label}"`)).join("<span>:</span>")}</div><div class="actions">${button("برو", "jump-go")}${button("لغو", "close")}</div>`;
  modal.showModal();
  modal.querySelector("button").focus();
  modal.onkeydown = (e) => {
    const b = e.target.closest("[data-unit]");
    if (b && ["ArrowUp", "ArrowDown"].includes(e.key)) {
      e.preventDefault();
      e.stopPropagation();
      const i = Number(b.dataset.unit),
        max = i === 0 ? 99 : 59;
      units[i] =
        (units[i] + (e.key === "ArrowUp" ? 1 : -1) + max + 1) % (max + 1);
      b.textContent = fa(String(units[i]).padStart(2, "0"));
    }
  };
  const close = () => {
    modal.onkeydown = null;
    modal.close();
    (
      document.querySelector("#toggle-play") ||
      document.querySelector("button.primary")
    )?.focus();
    showControls();
  };
  modal.onclick = (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    const a = b.dataset.action;
    if (a === "time-unit") {
      const i = Number(b.dataset.unit);
      units[i] = (units[i] + 1) % (i === 0 ? 100 : 60);
      b.textContent = fa(String(units[i]).padStart(2, "0"));
      return;
    }
    if (a === "jump-go")
      p.video.currentTime = ITVRules.seek(
        0,
        units[0] * 3600 + units[1] * 60 + units[2],
        p.video.duration,
      );
    close();
  };
  modal.oncancel = () => {
    modal.onkeydown = null;
    (
      document.querySelector("#toggle-play") ||
      document.querySelector("button.primary")
    )?.focus();
    showControls();
  };
}
function spatial(key) {
  const container = modal.open ? modal : root;
  const candidates = [
    ...container.querySelectorAll('button,input,select,[tabindex="0"]'),
  ].filter((e) => !e.disabled && e.getClientRects().length);
  const current = document.activeElement;
  if (!candidates.includes(current)) {
    candidates[0]?.focus();
    return;
  }
  const r = current.getBoundingClientRect(),
    x = r.x + r.width / 2,
    y = r.y + r.height / 2;
  const horizontal = key === "ArrowLeft" || key === "ArrowRight",
    sign = key === "ArrowLeft" || key === "ArrowUp" ? -1 : 1;
  let best = null,
    score = Infinity;
  for (const c of candidates) {
    if (c === current) continue;
    const a = c.getBoundingClientRect(),
      dx = a.x + a.width / 2 - x,
      dy = a.y + a.height / 2 - y,
      along = (horizontal ? dx : dy) * sign,
      cross = Math.abs(horizontal ? dy : dx);
    if (along < 4) continue;
    const cost = along + cross * 4;
    if (cost < score) {
      score = cost;
      best = c;
    }
  }
  if (best) {
    best.focus();
    best.scrollIntoView({ block: "nearest", inline: "nearest" });
  }
}
function commitTimelineSeek(p) {
  clearTimeout(p.seekTimer);
  if (state.player !== p || !Number.isFinite(p.seekTarget)) return;
  const target = p.seekTarget;
  p.seekTarget = null;
  p.video.currentTime = target;
}
function timelineSeek(e) {
  const p = state.player;
  const forward = e.keyCode === 39 || e.key === "ArrowRight";
  p.seekTarget = ITVRules.seek(
    Number.isFinite(p.seekTarget) ? p.seekTarget : p.video.currentTime,
    forward ? 10 : -10, p.video.duration);
  showControls();
  clearTimeout(p.seekTimer);
  p.seekTimer = setTimeout(() => commitTimelineSeek(p), 500);
}
function quickSeek(e, media = false) {
  const p = state.player,
    now = Date.now(),
    code = e.keyCode;
  if (held.code !== code || now - held.last > 1500)
    held = { code, start: now, last: now };
  else held.last = now;
  const forward = code === 417 || code === 39 || e.key === "ArrowRight",
    step = ITVRules.seekStep(now - held.start, media) * (forward ? 1 : -1);
  if (!p.burst || p.burst.code !== code || now - p.burst.last > 1500)
    p.burst = {
      code,
      origin: p.video.currentTime,
      target: p.video.currentTime,
      last: now,
    };
  p.burst.target = ITVRules.seek(p.burst.target, step, p.video.duration);
  p.burst.last = now;
  p.video.currentTime = p.burst.target;
  const delta = p.burst.target - p.burst.origin;
  const left = document.querySelector("#seek-left"),
    right = document.querySelector("#seek-right");
  left.hidden = delta >= 0;
  right.hidden = delta <= 0;
  const feedback = delta > 0 ? right : left;
  feedback.textContent =
    (delta > 0 ? "+" : "−") + fa(Math.round(Math.abs(delta)));
  clearTimeout(p.feedbackTimer);
  p.feedbackTimer = setTimeout(() => {
    if (state.player === p) {
      left.hidden = true;
      right.hidden = true;
      p.burst = null;
    }
  }, 1500);
}
let held = { code: null, start: 0, last: 0 };
document.addEventListener("keydown", (e) => {
  const code = e.keyCode,
    key = ({37:"ArrowLeft",38:"ArrowUp",39:"ArrowRight",40:"ArrowDown",13:"Enter"})[code] || e.key;
  if (!state.player && !modal.open && key === "Enter") {
    const b = document.activeElement?.closest('[data-action="play"][data-episode]');
    if (b) { e.preventDefault(); startEpisodePress(b, "key"); return; }
  }
  if (isBackKey(e)) {
    e.preventDefault();
    e.stopImmediatePropagation();
    if (!e.repeat) handleBack();
    return;
  }
  if (modal.open) {
    if (key.startsWith("Arrow") && document.activeElement.tagName !== "INPUT") {
      e.preventDefault();
      spatial(key);
    }
    return;
  }
  if (state.player) {
    const p = state.player;
    if (p.loading) {
      if (p.started && !p.failed && Number.isFinite(p.video.duration) &&
          (["ArrowLeft", "ArrowRight"].includes(key) || [412,417].includes(code))) {
        e.preventDefault();
        if (document.activeElement === document.querySelector("#seek")) timelineSeek(e);
        else quickSeek(e, [412,417].includes(code));
        return;
      }
      if (key.startsWith("Arrow")) {
        e.preventDefault();
        const buttons = [...document.querySelector("#buffer").querySelectorAll("button")];
        const index = buttons.indexOf(document.activeElement);
        const step = ["ArrowLeft", "ArrowUp"].includes(key) ? -1 : 1;
        buttons[(index + step + buttons.length) % buttons.length]?.focus();
      } else if (key === "Enter") {
        e.preventDefault();
        const action = document.activeElement?.dataset.action;
        if (!action || !p.failed) return;
        playerAction(action).catch((err) => toast(err.message));
      } else e.preventDefault();
      return;
    }
    if (key === "ArrowUp" || key === "ArrowDown") {
      e.preventDefault();
      const seek = document.querySelector("#seek");
      if (!p.controls) {
        if (key === "ArrowUp") {
          showControls();
          document.querySelector("#seek")?.focus();
        }
      } else if (key === "ArrowUp") {
        if (document.activeElement !== seek) p.controlFocus = document.activeElement;
        showControls();
        seek.focus();
      } else if (document.activeElement === seek) {
        showControls();
        (p.controlFocus || document.querySelector("#toggle-play"))?.focus();
      } else hideControls();
      return;
    }
    if (p.controls && ["ArrowLeft", "ArrowRight"].includes(key) &&
        document.activeElement !== document.querySelector("#seek")) {
      const buttons = [...document.querySelector(".transport-actions").querySelectorAll("button")];
      const index = buttons.indexOf(document.activeElement);
      if (index >= 0) {
        e.preventDefault();
        showControls();
        buttons[Math.max(0, Math.min(buttons.length - 1,
          index + (key === "ArrowRight" ? 1 : -1)))]?.focus();
        return;
      }
    }
    if ([415, 19, 10252, 179].includes(code) || key === " ") {
      e.preventDefault();
      playerAction("toggle").catch((e) => toast(e.message));
      return;
    }
    const horizontal = ["ArrowLeft", "ArrowRight"].includes(key),
      mediaSeek = [412, 417].includes(code);
    const nextShown = !document.querySelector("#next").hidden;
    const nextFocus = !!document.activeElement?.closest("#next");
    const locked =
      nextShown && p.video.duration - p.video.currentTime < 10 && !p.cancelled;
    const direction = key.startsWith("Arrow");
    if (
      direction &&
      nextShown &&
      (nextFocus || (key === "ArrowUp" && p.controls) || locked)
    ) {
      e.preventDefault();
      if (!nextFocus)
        document.querySelector('#next [data-action="next"]')?.focus();
      else if (horizontal) {
        const action = document.activeElement?.dataset.action;
        document
          .querySelector(
            `#next [data-action="${action === "next" ? "cancel-next" : "next"}"]`,
          )
          ?.focus();
      } else if (key === "ArrowDown" && !locked) {
        showControls();
        document.querySelector("#toggle-play")?.focus();
      }
      return;
    }
    const ready =
      Number.isFinite(p.video.duration) &&
      p.video.duration > 0 &&
      document.querySelector("#buffer").hidden;
    if ((direction || mediaSeek) && !ready) {
      e.preventDefault();
      return;
    }
    if (!p.controls && ready && key === "ArrowDown" && nextShown) {
      e.preventDefault();
      document.querySelector('#next [data-action="next"]')?.focus();
      return;
    }
    if ((horizontal || mediaSeek) && ready && !modal.open && !p.controls) {
      e.preventDefault();
      quickSeek(e, mediaSeek);
      return;
    }
    if (horizontal && document.activeElement?.id === "seek") {
      e.preventDefault();
      timelineSeek(e);
      return;
    }
    if (mediaSeek && ready) {
      e.preventDefault();
      p.video.currentTime = ITVRules.seek(
        p.video.currentTime,
        code === 417 ? 10 : -10,
        p.video.duration,
      );
      if (p.controls) showControls();
      return;
    }
    if (
      !p.controls &&
      key === "Enter" &&
      document.activeElement?.closest("#skip,#next")
    )
      return;
    if (!p.controls) {
      showControls();
      document.querySelector("#seek")?.focus();
      e.preventDefault();
      return;
    }
    showControls();
  }
  if (
    key.startsWith("Arrow") &&
    !["INPUT", "SELECT"].includes(document.activeElement.tagName)
  ) {
    e.preventDefault();
    spatial(key);
  }
});
document.addEventListener("keyup", (e) => {
  if ((e.key === "Enter" || e.keyCode === 13) && episodePress?.kind === "key") {
    e.preventDefault(); endEpisodePress(); return;
  }
  if ([37,39].includes(e.keyCode) || ["ArrowLeft","ArrowRight"].includes(e.key)) {
    if (state.player && Number.isFinite(state.player.seekTarget)) {
      e.preventDefault();
      commitTimelineSeek(state.player);
    }
  }
  if (isBackKey(e)) {
    e.preventDefault();
    e.stopImmediatePropagation();
  }
  held = { code: null, start: 0 };
});
document.addEventListener("visibilitychange", () => {
  if (document.hidden && state.player) {
    state.player.video.pause();
    persist().catch(() => {});
  }
});
async function connect() {
  root.innerHTML =
    '<div class="loading"><h1>iTV</h1><p>در حال اتصال به کتابخانه…</p></div>';
  if (window.PalmServiceBridge) {
    const bridge = new PalmServiceBridge();
    window.itvBridge = bridge;
    const s = await new Promise((resolve, reject) => {
      bridge.onservicecallback = (raw) => {
        try {
          const d = JSON.parse(raw);
          if (!d.returnValue) reject(Error(d.errorText));
          else resolve(d);
        } catch (e) {
          reject(e);
        }
      };
      bridge.call(
        "luna://app.itv.webos.service/session",
        JSON.stringify({ subscribe: true }),
      );
    });
    state.session = s;
    state.base = "http://127.0.0.1:" + s.port;
    state.token = s.tvToken;
  } else {
    state.base = location.origin;
    const token = new URLSearchParams(location.search).get("token");
    if (!token)
      throw Error("برای پیش‌نمایش، نشانی دارای توکن اجرای محلی را باز کنید");
    state.token = token;
    state.session = {
      url: location.origin,
      pin: new URLSearchParams(location.search).get("pin"),
    };
  }
  await reload();
  render();
  setInterval(async () => {
    if (!state.player) {
      const previous = JSON.stringify(state.items);
      try {
        await reload();
        if (JSON.stringify(state.items) !== previous) render();
      } catch (e) {
        toast(e.message);
      }
    }
  }, 5000);
}
connect().catch((e) => {
  root.innerHTML = `<div class="empty"><h1>اتصال به کتابخانه برقرار نشد</h1><p class="error">${esc(e.message)}</p><button onclick="location.reload()">تلاش دوباره</button></div>`;
  root.querySelector("button")?.focus();
});
