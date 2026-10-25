"use strict";
const { test } = require("node:test"),
  assert = require("node:assert/strict"),
  vm = require("node:vm"),
  fs = require("node:fs"),
  path = require("node:path");
function harness() {
  const handlers = {};
  let document;
  const elements = new Map();
  function el(name) {
    const classes = new Set();
    return {
      name,
      dataset: {},
      style: { setProperty() {} },
      classList: {
        add: (s) => classes.add(s),
        remove: (s) => classes.delete(s),
        contains: (s) => classes.has(s),
      },
      hidden: false,
      focus() {
        document.activeElement = this;
      },
      closest() {
        return null;
      },
      querySelector: (s) => get(s),
      querySelectorAll: () => [],
      getClientRects: () => [{}],
      set innerHTML(v) {
        this.html = v;
      },
      get innerHTML() {
        return this.html || "";
      },
    };
  }
  const get = (s) => {
    if (!elements.has(s)) elements.set(s, el(s));
    return elements.get(s);
  };
  const root = get("#app");
  root.addEventListener = () => {};
  const modal = get("#modal");
  modal.open = false;
  modal.close = () => (modal.open = false);
  modal.showModal = () => (modal.open = true);
  modal.dispatchEvent = (e) => {
    modal.oncancel?.(e);
    return !e.defaultPrevented;
  };
  document = {
    activeElement: el("body"),
    querySelector: get,
    addEventListener: (type, fn) => (handlers[type] = fn),
  };
  const context = vm.createContext({
    document,
    window: {},
    location: { origin: "http://localhost", search: "?token=test" },
    URLSearchParams,
    AbortController,
    Event,
    setTimeout: () => 1,
    clearTimeout() {},
    setInterval: () => 1,
    clearInterval() {},
    fetch: () => new Promise(() => {}),
    ITVRules: require("../app/rules"),
    ITVIcons: { play_tiny: "", watched_check: "" },
  });
  vm.runInContext(
    fs.readFileSync(path.join(__dirname, "../app/app.js"), "utf8"),
    context,
  );
  const run = (code) => vm.runInContext(code, context);
  const p = run(
    `(()=>{const e={sourceEpisodeId:'0x2',seasonNumber:1,title:'قسمت',sourceOrder:0};const s={id:'ab12',title:'نمونه',episodes:[e],isMovie:false};state.items=[s];state.route='detail';state.id=s.id;let pauses=0;const video={duration:100,currentTime:20,paused:false,pause(){this.pauses=(this.pauses||0)+1;},removeAttribute(){},load(){}};return state.player={s,e,video,controls:true,markers:{}};})()`,
  );
  function back(type = "keydown", repeat = false) {
    let prevented = false,
      stopped = false;
    handlers[type]({
      key: "Unidentified",
      keyCode: 461,
      repeat,
      preventDefault() {
        prevented = true;
      },
      stopImmediatePropagation() {
        stopped = true;
      },
    });
    assert.equal(prevented, true);
    assert.equal(stopped, true);
  }
  return { p, run, get, modal, document, back, handlers };
}
test("LG back hides visible controls, then exits immediately even when progress request hangs", () => {
  const h = harness();
  h.back();
  assert.equal(h.p.controls, false);
  assert.equal(h.get(".controls").classList.contains("hidden"), true);
  assert.equal(h.run("state.player!==null"), true);
  h.back();
  assert.equal(h.run("state.player"), null);
  assert.equal(h.p.video.pauses, 1);
  assert.equal(h.run("state.route"), "detail");
});
test("back with hidden controls exits instead of showing controls again", () => {
  const h = harness();
  h.p.controls = false;
  h.back();
  assert.equal(h.run("state.player"), null);
});
test("back dismisses open player menu before hiding controls", () => {
  const h = harness();
  h.modal.open = true;
  let cancelled = 0;
  h.modal.oncancel = () => cancelled++;
  h.back();
  assert.equal(cancelled, 1);
  assert.equal(h.modal.open, false);
  assert.equal(h.p.controls, true);
  assert.equal(h.run("state.player!==null"), true);
});
test("back cancels next episode focus before hiding transport", () => {
  const h = harness();
  h.document.activeElement = { closest: (s) => (s === "#next" ? {} : null) };
  h.back();
  assert.equal(h.p.cancelled, true);
  assert.equal(h.p.controls, true);
});
test("long press repeats and keyup are consumed without an extra navigation", () => {
  const h = harness();
  h.back();
  h.back("keydown", true);
  h.back("keyup");
  assert.equal(h.run("state.player!==null"), true);
  assert.equal(h.p.controls, false);
});
test("back on resume prompt continues playback instead of cancelling the prompt", async () => {
  const h = harness();
  const result = h.run(
    "askResume(20,state.items[0],state.items[0].episodes[0])",
  );
  h.back();
  assert.equal(await result, true);
  assert.equal(h.modal.open, false);
});

test("hidden-control D-pad seeks immediately and never reveals transport", () => {
  const h = harness();
  h.p.controls = false;
  h.get("#buffer").hidden = true;
  h.get("#next").hidden = true;
  const event = { key: "ArrowRight", keyCode: 39, preventDefault() {} };
  h.handlers.keydown(event);
  assert.equal(h.p.video.currentTime, 30);
  assert.equal(h.p.controls, false);
  assert.equal(h.get("#seek-right").hidden, false);
  h.handlers.keydown(event);
  assert.equal(h.p.video.currentTime, 40);
  h.handlers.keydown({ ...event, key: "ArrowLeft", keyCode: 37 });
  assert.equal(h.p.video.currentTime, 30);
  assert.equal(h.p.controls, false);
});
test("visible controls do not turn left/right on an action button into a seek", () => {
  const h = harness();
  h.get("#buffer").hidden = true;
  h.get("#next").hidden = true;
  h.document.activeElement = h.get("#toggle-play");
  h.handlers.keydown({ key: "ArrowRight", keyCode: 39, preventDefault() {} });
  assert.equal(h.p.video.currentTime, 20);
  assert.equal(h.p.controls, true);
});
test("timeline arrows always step ten seconds rather than the browser percentage step", () => {
  const h = harness();
  h.get("#buffer").hidden = true;
  h.get("#next").hidden = true;
  h.get("#seek").id = "seek";
  h.document.activeElement = h.get("#seek");
  h.handlers.keydown({ key: "ArrowRight", keyCode: 39, preventDefault() {} });
  assert.equal(h.p.seekTarget, 30);
  assert.equal(h.p.video.currentTime, 20);
  h.handlers.keyup({key:"ArrowRight",keyCode:39,preventDefault(){}});
  assert.equal(h.p.video.currentTime, 30);
  assert.equal(h.p.controls, true);
});

test("back exits loading immediately while network never resolves", () => {
  const h = harness();
  h.p.loading = true;
  h.p.video.duration = NaN;
  h.back();
  assert.equal(h.run("state.player"), null);
  assert.equal(h.p.video.pauses, 1);
});
test("loading error actions remain reachable by LG arrows and Enter", () => {
  const h = harness();
  h.p.loading = true;
  h.p.failed = true;
  const retry = h.get("retry"), back = h.get("back");
  retry.dataset.action = "retry";
  back.dataset.action = "back";
  h.get("#buffer").querySelectorAll = () => [retry, back];
  retry.focus();
  const press = code => h.handlers.keydown({key:"Unidentified",keyCode:code,preventDefault(){}});
  press(39);
  assert.equal(h.document.activeElement, back);
  press(37);
  assert.equal(h.document.activeElement, retry);
  press(39);
  press(13);
  assert.equal(h.run("state.player"), null);
});

test("LG right key code seeks forward even with Unidentified key", () => {
  const h = harness();
  h.p.controls = false;
  h.get("#buffer").hidden = true;
  h.get("#next").hidden = true;
  h.handlers.keydown({key:"Unidentified", keyCode:39, preventDefault(){}});
  assert.equal(h.p.video.currentTime, 30);
});
test("native canplay recovery clears stale loading and restores transport", () => {
  const h = harness();
  h.p.loading = true;
  h.p.failed = true;
  h.p.video.readyState = 3;
  h.p.video.seeking = false;
  h.get(".controls").classList.add("hidden");
  h.run("playerReady(state.player)");
  assert.equal(h.p.loading, false);
  assert.equal(h.p.failed, false);
  assert.equal(h.get("#buffer").hidden, true);
  assert.equal(h.get(".controls").classList.contains("hidden"), false);
});
test("recovery does not dismiss buffering before native seek finishes", () => {
  const h = harness();
  h.p.loading = true;
  h.p.video.readyState = 3;
  h.p.video.seeking = true;
  h.run("playerReady(state.player)");
  assert.equal(h.p.loading, true);
});

test("held forward keeps adding ten immediately during seek buffering", () => {
  const h = harness();
  h.p.started = true;
  h.p.loading = true;
  h.p.controls = false;
  for (let i = 0; i < 4; i++) {
    h.handlers.keydown({key:"Unidentified", keyCode:39, repeat:i > 0, preventDefault(){}});
    assert.equal(h.p.video.currentTime, 30 + i * 10);
  }
});

test("opening focuses seek; down selects buttons; up returns; down on buttons closes", () => {
  const h = harness();
  h.p.controls = false;
  const press = code => h.handlers.keydown({key:"Unidentified",keyCode:code,preventDefault(){}});
  press(38);
  assert.equal(h.document.activeElement, h.get("#seek"));
  press(40);
  assert.equal(h.document.activeElement, h.get("#toggle-play"));
  assert.equal(h.p.controls,true);
  press(38);
  assert.equal(h.document.activeElement, h.get("#seek"));
  press(40);
  press(40);
  assert.equal(h.p.controls,false);
  assert.equal(h.p.video.currentTime,20);
});

test("visible transport reaches seek and every right-hand action", () => {
  const h = harness();
  const buttons = ["restart","rewind","toggle","forward","next","quality","speed","jump"].map(a=>h.get(a));
  h.get(".transport-actions").querySelectorAll = () => buttons;
  buttons[2].focus();
  const press = code => h.handlers.keydown({key:"Unidentified",keyCode:code,preventDefault(){}});
  press(38);
  assert.equal(h.document.activeElement, h.get("#seek"));
  press(40);
  assert.equal(h.document.activeElement, buttons[2]);
  for (let i=3;i<buttons.length;i++) {press(39);assert.equal(h.document.activeElement,buttons[i]);}
  assert.equal(h.p.video.currentTime,20);
});

test("timeline holds accumulate and reverse without repeatedly seeking video", () => {
  const h = harness();
  h.get("#buffer").hidden = true;
  h.get("#next").hidden = true;
  h.get("#seek").id = "seek";
  h.get("#seek").focus();
  for (let i=0;i<4;i++) h.handlers.keydown({key:"ArrowRight",keyCode:39,preventDefault(){}});
  assert.equal(h.p.seekTarget,60);
  assert.equal(h.p.video.currentTime,20);
  h.handlers.keydown({key:"ArrowLeft",keyCode:37,preventDefault(){}});
  assert.equal(h.p.seekTarget,50);
  h.handlers.keyup({key:"ArrowLeft",keyCode:37,preventDefault(){}});
  assert.equal(h.p.video.currentTime,50);
});
