"use strict";
const Service = require("webos-service"),
  path = require("path");
const service = new Service("app.itv.webos.service");
const onTV = process.platform === "linux";
const runtime = require("./server").createServer({
  dataDir: onTV
    ? "/media/internal/app.itv.webos"
    : path.join(require("os").tmpdir(), "itv-webos-simulator"),
  appDir: onTV
    ? path.resolve(__dirname, "../../applications/app.itv.webos")
    : path.resolve(__dirname, "../app"),
  port: onTV ? 18196 : 0,
});
let ready;
const subscribers = new Map();
function start() {
  if (!ready)
    ready = runtime.start().catch((e) => {
      ready = null;
      throw e;
    });
  return ready;
}
service.register("start", (message) =>
  start().then(
    (state) => message.respond({ returnValue: true, ...state }),
    (e) => message.respond({ returnValue: false, errorText: e.message }),
  ),
);
service.register(
  "session",
  (message) => {
    start().then(
      () => {
        if (message.isSubscription)
          subscribers.set(message.uniqueToken, message);
        message.respond({
          returnValue: true,
          ...runtime.status(),
          subscribed: !!message.isSubscription,
        });
      },
      (e) => message.respond({ returnValue: false, errorText: e.message }),
    );
  },
  (message) => {
    subscribers.delete(message.uniqueToken);
    if (!subscribers.size) {
      runtime.save().finally(() => {
        runtime.server.close();
        ready = null;
      });
    }
  },
);
