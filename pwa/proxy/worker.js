const ALLOWED_HOSTS = new Set([
  "releaseplans.microsoft.com",
  "www.microsoft.com"
]);

export default {
  async fetch(request) {
    const requestUrl = new URL(request.url);
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Accept, Content-Type",
      "Access-Control-Max-Age": "86400",
      "Vary": "Origin"
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }
    if (request.method !== "GET") {
      return jsonResponse({ error: "Only GET is allowed." }, 405, corsHeaders);
    }

    const targetValue = requestUrl.searchParams.get("url");
    if (!targetValue) {
      return jsonResponse({ error: "Pass a Microsoft feed URL in the url query parameter." }, 400, corsHeaders);
    }

    let target;
    try {
      target = new URL(targetValue);
    } catch {
      return jsonResponse({ error: "The url query parameter is invalid." }, 400, corsHeaders);
    }
    if (
      target.protocol !== "https:"
      || !ALLOWED_HOSTS.has(target.hostname)
      || target.username
      || target.password
      || (target.port && target.port !== "443")
    ) {
      return jsonResponse({ error: "That host is not allowed." }, 403, corsHeaders);
    }

    const upstream = await fetch(target.toString(), {
      method: "GET",
      redirect: "manual",
      headers: {
        "Accept": "application/json, text/plain, */*",
        "User-Agent": "PPAC-Tracker-PWA-Proxy/1.0"
      },
      cf: {
        cacheEverything: true,
        cacheTtl: 1800
      }
    });
    const headers = new Headers(corsHeaders);
    headers.set("Content-Type", upstream.headers.get("Content-Type") || "application/json; charset=utf-8");
    headers.set("Cache-Control", "public, max-age=900");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers
    });
  }
};

function jsonResponse(value, status, headers) {
  const responseHeaders = new Headers(headers);
  responseHeaders.set("Content-Type", "application/json; charset=utf-8");
  return new Response(JSON.stringify(value), { status, headers: responseHeaders });
}
