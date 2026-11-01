const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const providers = fs.readdirSync(root).filter((name) => name.endsWith(".js")).sort();
assert.equal(providers.length, 17);
for (const name of providers) {
  const plugin = require(path.join(root, name));
  assert.equal(typeof plugin.getStreams, "function", `${name}: missing getStreams`);
  assert.equal(typeof plugin.onSettings, "function", `${name}: missing onSettings`);
  assert.ok(Array.isArray(plugin.onSettings()), `${name}: invalid settings layout`);
}
console.log(`Provider contract passed for ${providers.length} Nuvio scrapers.`);
