# PPAC Tracker web ports

The Chrome extension and PWA use identical `app.js`, `core.js`, and `styles.css` files. Both are framework-free and self-contained in their respective directories.

## Feature mapping

| Android feature | Chrome side panel | Android PWA |
| --- | --- | --- |
| Legacy Release Planner and M365 feeds | Direct fetch through host permissions | Fetch through the included allow-listed proxy |
| Local release cache | `chrome.storage.local` | IndexedDB |
| Today, Updates, Timeline, Tracked, Settings | Yes | Yes |
| Search and product/status/stage filters | Yes | Yes |
| Timeline sort, date filter, and month scrubber | Yes | Yes |
| Complete, Skip, Save, Hide/Restore | Yes, including touch swipe | Yes, including touch swipe |
| Share, email, docs links | Web Share/clipboard, `mailto:`, browser tab | Web Share/clipboard, `mailto:`, browser tab |
| Product settings and light/dark theme | Yes | Yes |
| Read/complete streaks and badges | Yes | Yes |
| Badge achievement overlay and hidden reward debug controls | Yes | Yes |
| API diagnostic | Yes | Yes |
| Task deep links | Shares the canonical Android/web task link; internal detail navigation | `?task=<id>` |
| Daily notification | Toolbar icon badge and tooltip only | Deliberately omitted |
| Offline app shell and cached data | Packaged with extension | Service worker plus IndexedDB |

## Platform limits

- **PWA feed CORS:** `releaseplans.microsoft.com` and `www.microsoft.com/releasecommunications` currently do not grant cross-origin browser reads. The PWA must use a same-origin proxy or the included Cloudflare Worker. This cannot be solved inside client-side JavaScript or a service worker.
- **PWA background timing:** the PWA does not schedule background refresh. Reliable periodic background execution is not available consistently on Android browsers, and this port intentionally has no notifications. Refresh runs on first use when the cache is empty and when the user selects **Refresh now**.
- **Local data migration:** Android Room/SharedPreferences, Chrome storage, and PWA IndexedDB are separate stores. Existing native-app tracking state is not automatically imported or synchronized between ports.
- **Extension link launching:** Chrome extensions cannot register themselves as handlers for ordinary `https://` links. The extension shares the existing canonical `wyattdave.com/ppac-tracker/task/...` link, but opening that link does not automatically open the side panel.
- **Backup:** browser/extension storage persistence is controlled by Chrome and the operating system; it is not Android’s native app backup/device-transfer contract.
- **Sharing:** the Web Share API is used when available; the fallback copies formatted text. Email uses the device’s registered `mailto:` handler.
- **Extension alarms:** Chrome alarms are best-effort and can be delayed while the browser/device is suspended. The next local-time alarm is recalculated after each run to handle daylight-saving changes.

## Chrome extension setup

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Select **Load unpacked** and choose `chrome-ext/`.
4. Pin PPAC Tracker if desired, then click its toolbar icon to open the side panel.

The extension asks only for storage, alarms, side-panel support, and host access to the two Microsoft feed hosts.

## PWA setup

1. Deploy the Worker in `pwa/proxy/` and copy its HTTPS URL.
2. Serve `pwa/` from an HTTPS static host.
3. Open the PWA, paste the Worker URL under **Settings → PWA data access**, and save.
4. Select **Refresh now**, then install the app from Chrome.

For local testing:

```powershell
python -m http.server 8000 --directory pwa
```

Then visit `http://localhost:8000`.

## Validation

The dependency-free Node suite exercises both feed schemas, date handling, change detection, filters, metrics, tracking, rewards, and file parity:

```powershell
npm test --prefix pwa
```
