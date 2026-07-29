import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sharedFiles = ["app.js", "core.js", "styles.css"];
const copiedConfigFiles = ["config/api.json", "config/rewards.json"];

for (const file of [...sharedFiles, ...copiedConfigFiles]) {
  test(`${file} stays identical in both web ports`, async () => {
    const [pwa, extension] = await Promise.all([
      readFile(new URL(`../${file}`, import.meta.url)),
      readFile(new URL(`../../chrome-ext/${file}`, import.meta.url)),
    ]);
    assert.deepEqual(extension, pwa);
  });
}

test("web source and reward configuration match the repository originals", async () => {
  const [pwaSources, rootSources, pwaRewards, rootRewards] = await Promise.all([
    readFile(new URL("../config/api.json", import.meta.url)),
    readFile(new URL("../../api.json", import.meta.url)),
    readFile(new URL("../config/rewards.json", import.meta.url)),
    readFile(new URL("../../rewards.json", import.meta.url)),
  ]);
  assert.deepEqual(pwaSources, rootSources);
  assert.deepEqual(pwaRewards, rootRewards);
});
