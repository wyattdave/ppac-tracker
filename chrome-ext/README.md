# PPAC Tracker Chrome side-panel extension

This is a Manifest V3, dependency-free Chrome extension. Its UI and release-tracking logic are the same vanilla HTML, CSS, and JavaScript used by the PWA.

## Load locally

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Choose **Load unpacked**.
4. Select this `chrome-ext` directory.
5. Click the PPAC Tracker toolbar icon to open the side panel.

The extension requests host access only for the two public Microsoft feed hosts. Data, filters, tracking state, streaks, and settings are stored locally with `chrome.storage.local`.

## Icon notification

At the configured daily time the background service worker refreshes the enabled feeds. The toolbar icon shows:

- A number when new or changed items were found.
- A dot when the daily summary ran with no detected change.
- `!` when all enabled feeds failed.

Hovering the icon shows the release summary. Opening the side panel clears the badge. The extension does not create popup/system notifications.
