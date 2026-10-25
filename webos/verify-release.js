"use strict";
const fs = require("fs"),
  path = require("path"),
  assert = require("assert/strict");
const pkg = require("./package.json"),
  app = require("./app/appinfo.json"),
  service = require("./service/package.json");
assert.equal(pkg.version, app.version);
assert.equal(pkg.versionName, app.version);
assert.equal(pkg.version, service.version);
assert.ok(Number.isInteger(pkg.versionCode) && pkg.versionCode > 0);
const dir = path.resolve(__dirname, "../release/webos");
for (const name of fs.existsSync(dir) ? fs.readdirSync(dir) : []) {
  if (!/^version-.*\.json$/.test(name)) continue;
  const old = JSON.parse(fs.readFileSync(path.join(dir, name), "utf8"));
  assert.ok(pkg.versionCode > old.versionCode, "Increase webOS versionCode");
  const a = pkg.version.split(".").map(Number),
    b = old.version.split(".").map(Number);
  assert.ok(
    a[0] > b[0] ||
      (a[0] === b[0] && (a[1] > b[1] || (a[1] === b[1] && a[2] > b[2]))),
    "Increase webOS version",
  );
}
