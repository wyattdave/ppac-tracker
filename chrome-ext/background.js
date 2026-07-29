import {
  STORAGE_KEY,
  calculateMetrics,
  loadConfiguration,
  normalizeState,
  refreshState,
  visibleItems
} from "./core.js";

const DAILY_ALARM = "ppac-tracker-daily-summary";

chrome.runtime.onInstalled.addListener(() => {
  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
  scheduleNextSummary();
});

chrome.runtime.onStartup.addListener(() => {
  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
  scheduleNextSummary();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name !== DAILY_ALARM) return;
  runDailySummary()
    .catch((error) => showFailureBadge(error.message))
    .finally(scheduleNextSummary);
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === "settings-updated") {
    scheduleNextSummary();
  } else if (message?.type === "clear-badge") {
    chrome.action.setBadgeText({ text: "" });
    chrome.action.setTitle({ title: "Open PPAC Tracker" });
  }
});

async function scheduleNextSummary() {
  await chrome.alarms.clear(DAILY_ALARM);
  const state = await loadState();
  if (!state.preferences.notificationEnabled) return;
  const now = new Date();
  const next = new Date();
  next.setHours(
    Number(state.preferences.notificationHour ?? 8),
    Number(state.preferences.notificationMinute ?? 0),
    0,
    0,
  );
  if (next <= now) next.setDate(next.getDate() + 1);
  await chrome.alarms.create(DAILY_ALARM, { when: next.getTime() });
}

async function runDailySummary() {
  const state = await loadState();
  if (!state.preferences.notificationEnabled) return;
  const { sources } = await loadConfiguration(new URL("./", import.meta.url));
  const refreshed = await refreshState(state, sources, fetch);
  await chrome.storage.local.set({ [STORAGE_KEY]: refreshed.state });
  const items = visibleItems(refreshed.state, sources).filter((item) => !item.tracking.isHidden);
  const metrics = calculateMetrics(items);
  const changes = refreshed.result.hadCachedUpdates
    ? refreshed.result.newCount + refreshed.result.changedCount
    : 0;
  const allFailed = refreshed.result.sourceCount > 0
    && refreshed.result.failures.length === refreshed.result.sourceCount;
  const badgeText = allFailed ? "!" : changes > 0 ? compactCount(changes) : "•";
  const title = allFailed
    ? "PPAC Tracker could not refresh. Open the side panel for details."
    : `PPAC Tracker — Open: ${metrics.totalOpen}, last 7 days: ${metrics.totalLastSevenDays}, GA this week: ${metrics.totalGaThisWeek}${changes ? `, ${changes} new or changed` : ""}`;
  await chrome.action.setBadgeBackgroundColor({ color: allFailed ? "#ba1a1a" : "#6750a4" });
  await chrome.action.setBadgeText({ text: badgeText });
  await chrome.action.setTitle({ title });
}

async function showFailureBadge(message) {
  await chrome.action.setBadgeBackgroundColor({ color: "#ba1a1a" });
  await chrome.action.setBadgeText({ text: "!" });
  await chrome.action.setTitle({ title: `PPAC Tracker refresh failed: ${message}` });
}

async function loadState() {
  const stored = await chrome.storage.local.get(STORAGE_KEY);
  return normalizeState(stored[STORAGE_KEY]);
}

function compactCount(value) {
  if (value > 999) return "999+";
  return String(value);
}
