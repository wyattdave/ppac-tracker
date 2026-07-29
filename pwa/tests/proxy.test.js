import assert from "node:assert/strict";
import test from "node:test";
import worker from "../proxy/worker.js";

test("proxy rejects missing and non-Microsoft targets", async () => {
  const missing = await worker.fetch(new Request("https://proxy.example/"));
  assert.equal(missing.status, 400);

  const disallowed = await worker.fetch(new Request(
    `https://proxy.example/?url=${encodeURIComponent("https://example.com/feed.json")}`,
  ));
  assert.equal(disallowed.status, 403);
});

test("proxy forwards allow-listed HTTPS feeds and adds CORS", async () => {
  const originalFetch = globalThis.fetch;
  let forwarded;
  globalThis.fetch = async (url, options) => {
    forwarded = { url, options };
    return new Response('{"results":[]}', {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  try {
    const target = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=123";
    const response = await worker.fetch(new Request(
      `https://proxy.example/?url=${encodeURIComponent(target)}`,
    ));
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("access-control-allow-origin"), "*");
    assert.equal(forwarded.url, target);
    assert.equal(forwarded.options.redirect, "manual");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
