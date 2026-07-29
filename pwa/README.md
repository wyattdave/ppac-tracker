# PPAC Tracker PWA

This directory contains the dependency-free, Android-oriented Progressive Web App port. It uses the same vanilla HTML, CSS, JavaScript, feed adapters, phone layout, and local feature set as the Chrome side-panel extension.

## Run locally

Service workers require a secure origin. `localhost` is treated as secure for development:

```powershell
python -m http.server 8000 --directory pwa
```

Open `http://localhost:8000`, configure the feed proxy under **Settings → PWA data access**, then use Chrome’s **Install app** option.

## Feed proxy requirement

The Microsoft feed hosts currently omit browser CORS response headers. A normal PWA therefore cannot read their JSON directly, even though the endpoints are public. The allow-listed Cloudflare Worker in [`proxy/`](proxy/) resolves that platform restriction without adding a JavaScript framework or exposing a general-purpose proxy.

After the first successful refresh, the app shell and release data work offline. User tracking, settings, streaks, and badge state are stored in IndexedDB.

## Notifications

This PWA intentionally has no notification permission, push subscription, notification UI, or notification service-worker handler.

## Deploy

Host the contents of this directory on any HTTPS static host. Keep the relative paths intact so `manifest.webmanifest` and `service-worker.js` remain within the app scope.
