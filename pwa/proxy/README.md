# PPAC Tracker PWA feed proxy

The Microsoft release feeds do not currently send browser CORS headers. This small Cloudflare Worker adds CORS only for the two feed hosts used by PPAC Tracker:

- `releaseplans.microsoft.com`
- `www.microsoft.com`

It rejects other hosts and non-GET requests, so it is not an open general-purpose proxy.

## Deploy

1. Install Wrangler: `npm install --global wrangler`
2. Sign in: `wrangler login`
3. From this directory, run: `wrangler deploy`
4. Copy the resulting `https://...workers.dev/` URL.
5. In the installed PWA, open **Settings → PWA data access**, paste the URL, save it, and refresh.

You can deploy the same worker on another standards-compatible edge runtime, but the included `cf` caching option is Cloudflare-specific.
