const path = require("path");
require("./server")
  .createServer({
    dataDir:
      process.env.ITV_DATA_DIR ||
      path.join(require("os").tmpdir(), "itv-webos-dev"),
    host: "127.0.0.1",
    port: Number(process.env.PORT) || 18196,
  })
  .start()
  .then((s) =>
    console.log(
      JSON.stringify({
        ...s,
        tvUrl:
          "http://127.0.0.1:" +
          s.port +
          "/tv/?token=" +
          s.tvToken +
          "&pin=" +
          s.pin,
      }),
    ),
  )
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  });
