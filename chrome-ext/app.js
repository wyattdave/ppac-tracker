import {
  RELEASE_STAGE_FILTERS,
  STATUS_FILTERS,
  TIMELINE_SORTS,
  STORAGE_KEY,
  addDays,
  bestDocsUrl,
  calculateMetrics,
  diagnoseSource,
  displayDate,
  displayMonth,
  filterUpdateItems,
  getRewardModel,
  htmlToText,
  isScheduledForToday,
  loadConfiguration,
  localToday,
  normalizeState,
  parseReleaseDateRange,
  primaryDateLabel,
  recordTaskRead,
  refreshState,
  releaseStatusLabel,
  setTaskStatus,
  sourceIsEnabled,
  syncLabel,
  timelineDateLabel,
  timelineDateRange,
  timelineItems,
  toggleManualBadge,
  toggleTrackingFlag,
  trackingFor,
  updateLongestStreaks,
  visibleItems,
  weekStart,
} from "./core.js";

const platform = document.documentElement.dataset.platform || "pwa";
const isExtension = platform === "extension";
const storage = createStorage();
const elements = {
  shell: document.querySelector("#app-shell"),
  title: document.querySelector("#app-title"),
  sync: document.querySelector("#sync-label"),
  action: document.querySelector("#top-action"),
  progress: document.querySelector("#sync-progress"),
  error: document.querySelector("#error-banner"),
  content: document.querySelector("#app-content"),
  nav: document.querySelector("#bottom-nav"),
  toast: document.querySelector("#toast"),
  modal: document.querySelector("#modal-root"),
};

let state = normalizeState();
let sources = [];
let rewards = [];
let diagnostic = null;
let busy = false;
let debugEnabled = false;
let aboutTapCount = 0;
let achievement = null;
let installPrompt = null;
let toastTimer = null;
let pointerStart = null;
let suppressCardClickUntil = 0;

const ui = {
  screen: "Today",
  selectedUpdateId: null,
  showAllBadges: false,
  filtersVisible: false,
  product: "All",
  searchQuery: "",
  statusFilter: "Open",
  releaseStageFilter: "All Releases",
  timelineSort: "lastUpdate",
  timelineDateFilter: "",
};

main().catch((error) => {
  elements.error.hidden = false;
  elements.error.textContent = `App failed to start: ${error.message}`;
});

async function main() {
  ({ sources, rewards } = await loadConfiguration(new URL("./", import.meta.url)));
  state = normalizeState(await storage.load());
  updateLongestStreaks(state);
  applyTheme();
  initialiseAnnouncedBadges();
  handleDeepLink();
  bindEvents();
  render();

  if (isExtension) {
    chrome.runtime.sendMessage({ type: "clear-badge" }).catch(() => {});
    chrome.storage.onChanged.addListener((changes, areaName) => {
      if (areaName !== "local" || !changes[STORAGE_KEY]?.newValue || busy) return;
      state = normalizeState(changes[STORAGE_KEY].newValue);
      render();
    });
  } else if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("./service-worker.js").catch(() => {});
  }

  if (!state.updates.length) await refresh();
}

function bindEvents() {
  elements.content.addEventListener("click", handleClick);
  elements.action.addEventListener("click", handleClick);
  elements.nav.addEventListener("click", handleClick);
  elements.modal.addEventListener("click", handleClick);
  elements.content.addEventListener("change", handleChange);
  elements.content.addEventListener("input", handleInput);
  elements.content.addEventListener("pointerdown", handlePointerDown);
  elements.content.addEventListener("pointerup", handlePointerUp);
  elements.content.addEventListener("pointercancel", () => { pointerStart = null; });
  window.addEventListener("popstate", handleDeepLink);
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    installPrompt = event;
    render();
  });
}

async function handleClick(event) {
  const target = event.target.closest("[data-action]");
  if (!target) return;
  const action = target.dataset.action;
  if (action === "nav") {
    ui.screen = target.dataset.screen;
    ui.selectedUpdateId = null;
    ui.showAllBadges = false;
    if (ui.screen === "Updates") ui.filtersVisible = true;
    render();
    elements.content.scrollTo({ top: 0 });
  } else if (action === "back") {
    ui.selectedUpdateId = null;
    ui.showAllBadges = false;
    clearTaskUrl();
    render();
  } else if (action === "toggle-filters") {
    ui.filtersVisible = !ui.filtersVisible;
    render();
  } else if (action === "open-update") {
    if (Date.now() < suppressCardClickUntil) return;
    openUpdate(target.dataset.id);
  } else if (action === "filter-product") {
    ui.product = target.dataset.value;
    render();
  } else if (action === "filter-status") {
    ui.statusFilter = target.dataset.value;
    render();
  } else if (action === "filter-stage") {
    ui.releaseStageFilter = target.dataset.value;
    render();
  } else if (action === "timeline-sort") {
    ui.timelineSort = target.dataset.value;
    render();
  } else if (action === "clear-timeline-date") {
    ui.timelineDateFilter = "";
    render();
  } else if (action === "scroll-timeline") {
    document.querySelector(`#${cssEscape(target.dataset.target)}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
  } else if (action === "set-status") {
    await changeStatus(target.dataset.id, target.dataset.status);
  } else if (action === "toggle-saved") {
    await mutateState(() => toggleTrackingFlag(state, target.dataset.id, "isSaved"));
  } else if (action === "toggle-hidden") {
    await mutateState(() => toggleTrackingFlag(state, target.dataset.id, "isHidden"));
    ui.selectedUpdateId = null;
    clearTaskUrl();
    render();
  } else if (action === "share") {
    await shareUpdate(findUpdate(target.dataset.id));
  } else if (action === "email") {
    emailUpdate(findUpdate(target.dataset.id));
  } else if (action === "docs") {
    openExternal(target.dataset.url);
  } else if (action === "refresh") {
    await refresh();
  } else if (action === "diagnostic") {
    await runDiagnostic();
  } else if (action === "about-tap") {
    aboutTapCount += 1;
    if (aboutTapCount >= 20 && !debugEnabled) {
      debugEnabled = true;
      showToast("Reward debug controls unlocked.");
    }
    render();
  } else if (action === "show-badges") {
    ui.showAllBadges = true;
    render();
  } else if (action === "toggle-badge" && debugEnabled) {
    const model = rewardModel();
    const badge = model.badges.find((candidate) => candidate.name === target.dataset.name);
    if (badge) await mutateState(() => toggleManualBadge(state, badge.name, badge.isComplete), true);
  } else if (action === "dismiss-achievement") {
    achievement = null;
    renderModal();
  } else if (action === "apply-debug") {
    await applyDebugValues();
  } else if (action === "save-proxy") {
    const input = document.querySelector("#proxy-url");
    await mutateState(() => {
      state.preferences.proxyUrl = input?.value.trim() ?? "";
    });
    showToast(state.preferences.proxyUrl ? "PWA data proxy saved." : "PWA data proxy cleared.");
  } else if (action === "install-pwa" && installPrompt) {
    await installPrompt.prompt();
    installPrompt = null;
    render();
  } else if (action === "longest-read") {
    const model = rewardModel();
    showToast(model.longestReadStreakStartDate ? `Streak started ${displayDate(model.longestReadStreakStartDate)}` : "Streak start date is not available yet");
  } else if (action === "longest-complete") {
    const model = rewardModel();
    showToast(model.longestCompleteStreakStartDate ? `Streak started ${displayDate(model.longestCompleteStreakStartDate)}` : "Streak start date is not available yet");
  }
}

async function handleChange(event) {
  const target = event.target;
  if (target.matches("[data-product-toggle]")) {
    await mutateState(() => {
      state.preferences.products[target.dataset.productToggle] = target.checked;
      if (!target.checked && ui.product === target.dataset.productToggle) ui.product = "All";
    });
  } else if (target.matches("[data-theme]")) {
    await mutateState(() => {
      state.preferences.theme = target.value;
      applyTheme();
    });
  } else if (target.id === "timeline-date") {
    ui.timelineDateFilter = target.value;
    render();
  } else if (target.id === "detail-status") {
    await changeStatus(target.dataset.id, target.value);
  } else if (target.id === "notification-enabled") {
    await mutateState(() => {
      state.preferences.notificationEnabled = target.checked;
    });
    notifyBackgroundSettingsChanged();
  } else if (target.id === "notification-time") {
    const [hour, minute] = target.value.split(":").map(Number);
    await mutateState(() => {
      state.preferences.notificationHour = hour;
      state.preferences.notificationMinute = minute;
    });
    notifyBackgroundSettingsChanged();
  }
}

function handleInput(event) {
  if (event.target.id !== "search-updates") return;
  ui.searchQuery = event.target.value;
  render();
  const input = document.querySelector("#search-updates");
  input?.focus();
  input?.setSelectionRange(ui.searchQuery.length, ui.searchQuery.length);
}

function handlePointerDown(event) {
  const card = event.target.closest(".swipe-card");
  if (!card || event.pointerType === "mouse") return;
  pointerStart = { x: event.clientX, y: event.clientY, id: card.dataset.updateId };
}

async function handlePointerUp(event) {
  if (!pointerStart) return;
  const start = pointerStart;
  pointerStart = null;
  const dx = event.clientX - start.x;
  const dy = event.clientY - start.y;
  if (Math.abs(dx) < 72 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
  suppressCardClickUntil = Date.now() + 400;
  await changeStatus(start.id, dx > 0 ? "Skipped" : "Complete");
  showToast(dx > 0 ? "Marked skipped" : "Marked complete");
}

async function refresh() {
  if (busy) return;
  busy = true;
  diagnostic = null;
  render();
  const beforeBadges = completedBadgeNames();
  try {
    const refreshed = await refreshState(state, sources, feedFetcher);
    state = refreshed.state;
    await storage.save(state);
    announceNewBadges(beforeBadges);
    const { result } = refreshed;
    if (result.failures.length === result.sourceCount && result.sourceCount > 0) {
      showError(`Sync failed: ${result.failures.map((failure) => `${failure.product}: ${failure.message}`).join("; ")}`);
    } else if (result.failures.length) {
      showError(`Partial sync: ${result.failures.map((failure) => failure.product).join(", ")}`);
    } else {
      hideError();
      showToast(`Synced ${result.total} release updates.`);
    }
    if (result.noOpenTasksCompleted) {
      showToast("No open tasks: read and weekly streaks were marked complete.");
    }
  } catch (error) {
    showError(`Refresh failed: ${error.message}`);
  } finally {
    busy = false;
    render();
  }
}

async function runDiagnostic() {
  if (busy) return;
  const firstSource = sources.find((source) => sourceIsEnabled(source, state)) ?? sources[0];
  if (!firstSource) return;
  busy = true;
  render();
  diagnostic = await diagnoseSource(firstSource, feedFetcher);
  busy = false;
  render();
}

async function feedFetcher(url, options) {
  if (isExtension || !state.preferences.proxyUrl) return fetch(url, options);
  const configured = state.preferences.proxyUrl.trim();
  let proxied;
  if (configured.includes("{url}")) {
    proxied = configured.replace("{url}", encodeURIComponent(url));
  } else {
    const proxyUrl = new URL(configured);
    proxyUrl.searchParams.set("url", url);
    proxied = proxyUrl.toString();
  }
  return fetch(proxied, options);
}

async function changeStatus(id, status) {
  await mutateState(() => setTaskStatus(state, id, status), true);
}

async function mutateState(mutator, checkRewards = false) {
  const beforeBadges = checkRewards ? completedBadgeNames() : null;
  mutator();
  updateLongestStreaks(state);
  await storage.save(state);
  if (beforeBadges) announceNewBadges(beforeBadges);
  render();
}

function initialiseAnnouncedBadges() {
  const completed = completedBadgeNames();
  const announced = new Set(state.preferences.announcedBadges);
  completed.forEach((name) => announced.add(name));
  state.preferences.announcedBadges = [...announced];
  storage.save(state);
}

function announceNewBadges(beforeNames) {
  const after = completedBadgeNames();
  const announced = new Set(state.preferences.announcedBadges);
  const newName = [...after].find((name) => !beforeNames.has(name) && !announced.has(name));
  after.forEach((name) => announced.add(name));
  state.preferences.announcedBadges = [...announced];
  storage.save(state);
  if (newName) {
    achievement = rewardModel().badges.find((badge) => badge.name === newName) ?? null;
    renderModal();
  }
}

function completedBadgeNames() {
  return new Set(rewardModel().badges.filter((badge) => badge.isComplete).map((badge) => badge.name));
}

function rewardModel() {
  return getRewardModel(rewards, state, sources);
}

function render() {
  applyTheme();
  const items = visibleItems(state, sources);
  const selected = items.find((item) => item.update.id === ui.selectedUpdateId);
  if (ui.selectedUpdateId && !selected) ui.selectedUpdateId = null;
  elements.sync.textContent = syncLabel(state.lastSyncAt);
  elements.progress.hidden = !busy;
  renderTopAction(Boolean(selected));
  renderNavigation();

  if (selected) {
    elements.content.innerHTML = renderDetail(selected);
  } else if (ui.showAllBadges) {
    elements.content.innerHTML = renderAllBadges();
  } else if (ui.screen === "Updates") {
    elements.content.innerHTML = renderUpdates(items);
  } else if (ui.screen === "Timeline") {
    elements.content.innerHTML = renderTimeline(items);
  } else if (ui.screen === "Tracked") {
    elements.content.innerHTML = renderTracked(items);
  } else if (ui.screen === "Settings") {
    elements.content.innerHTML = renderSettings(items);
  } else {
    elements.content.innerHTML = renderToday(items);
  }
  renderModal();
}

function renderTopAction(hasSelected) {
  if (hasSelected || ui.showAllBadges) {
    elements.action.hidden = false;
    elements.action.dataset.action = "back";
    elements.action.setAttribute("aria-label", "Back");
    elements.action.innerHTML = icon("back");
  } else if (ui.screen === "Updates") {
    elements.action.hidden = false;
    elements.action.dataset.action = "toggle-filters";
    elements.action.setAttribute("aria-label", ui.filtersVisible ? "Hide filters" : "Show filters");
    elements.action.innerHTML = icon("filter");
  } else {
    elements.action.hidden = true;
    elements.action.removeAttribute("data-action");
    elements.action.innerHTML = "";
  }
}

function renderNavigation() {
  const screens = [
    ["Today", "today"],
    ["Updates", "updates"],
    ["Timeline", "timeline"],
    ["Tracked", "tracked"],
    ["Settings", "settings"],
  ];
  elements.nav.innerHTML = screens.map(([screen, image]) => `
    <button class="nav-item ${ui.screen === screen ? "selected" : ""}" type="button"
      data-action="nav" data-screen="${screen}" aria-label="${screen}" aria-current="${ui.screen === screen ? "page" : "false"}">
      ${icon(image)}
      <span>${screen}</span>
    </button>
  `).join("");
}

function renderToday(items) {
  const model = rewardModel();
  const today = items
    .filter((item) => !item.tracking.isHidden && isScheduledForToday(item.update))
    .sort((left, right) => Number(left.tracking.isComplete || left.tracking.isSkipped)
      - Number(right.tracking.isComplete || right.tracking.isSkipped)
      || left.update.featureName.localeCompare(right.update.featureName));
  const completed = model.badges.filter((badge) => badge.isComplete);
  return `
    <section class="screen flow">
      ${model.progress.length ? renderRewards(model) : ""}
      <h2 class="section-title">Today's updates</h2>
      ${today.length ? `<div class="card-list">${today.map(updateCard).join("")}</div>`
        : emptyState("No release updates are scheduled for today.")}
      <div class="section-heading-row">
        <h2 class="section-title">Badges</h2>
        <button class="text-button" type="button" data-action="show-badges">See all</button>
      </div>
      ${completed.length
        ? `<div class="badge-carousel">${completed.map(badgeTile).join("")}</div>`
        : emptyState("No badges earned yet.")}
    </section>
  `;
}

function renderRewards(model) {
  const colors = ["var(--primary)", "#0f766e", "#b45309"];
  return `
    <section class="flow rewards-section">
      <h2 class="section-title">Rewards</h2>
      <article class="card reward-card">
        <div class="reward-rings" aria-label="Reward progress">
          ${model.progress.slice(0, 3).map((reward, index) => `
            <div class="reward-ring ring-${index}" style="--ring-progress:${Math.round(reward.fraction * 360)}deg;--ring-color:${colors[index]}"></div>
          `).join("")}
          <div class="reward-ring-label"><span>Streaks</span><strong>${escapeHtml(model.progress[0]?.progressLabel ?? "No progress yet")}</strong></div>
        </div>
        <div class="reward-legend">
          ${model.progress.map((reward, index) => `
            <div class="legend-row">
              <span class="legend-dot" style="--dot-color:${colors[index]}"></span>
              <span class="legend-copy"><small>${escapeHtml(reward.category)}</small><span>${escapeHtml(reward.name)}</span></span>
              <strong>${escapeHtml(reward.progressLabel)}</strong>
            </div>
          `).join("")}
        </div>
      </article>
      <div class="metrics two">
        ${metricCard("Longest streak", `${model.longestReadStreakDays} days`, "longest-read")}
        ${metricCard("Longest complete", `${model.longestCompleteStreakWeeks} weeks`, "longest-complete")}
      </div>
    </section>
  `;
}

function renderUpdates(items) {
  const filtered = filterUpdateItems(items, {
    product: ui.product,
    searchQuery: ui.searchQuery,
    statusFilter: ui.statusFilter,
    releaseStageFilter: ui.releaseStageFilter,
  });
  const metrics = calculateMetrics(filtered);
  const products = availableProducts(items);
  return `
    <section class="screen updates-screen">
      <div class="updates-toolbar flow">
        <div class="metrics three">
          ${metricCard("Total Open", metrics.totalOpen)}
          ${metricCard("Total last 7 days", metrics.totalLastSevenDays)}
          ${metricCard("Total GA this week", metrics.totalGaThisWeek)}
        </div>
        ${ui.filtersVisible ? `
          <label class="field">
            <span>Search updates</span>
            <input id="search-updates" type="search" value="${escapeAttr(ui.searchQuery)}" autocomplete="off">
          </label>
          ${chipRow(["All", ...products], ui.product, "filter-product", "Product")}
          ${chipRow(RELEASE_STAGE_FILTERS, ui.releaseStageFilter, "filter-stage", "Release stage")}
          ${chipRow(STATUS_FILTERS, ui.statusFilter, "filter-status", "Status")}
        ` : ""}
      </div>
      <p class="swipe-hint">Swipe right to skip or left to complete.</p>
      ${filtered.length ? `<div class="card-list">${filtered.map((item) => updateCard(item, true)).join("")}</div>`
        : emptyState("No updates match these filters.")}
    </section>
  `;
}

function renderTimeline(items) {
  const products = availableProducts(items);
  const timeline = timelineItems(items, {
    product: ui.product,
    timelineSort: ui.timelineSort,
    timelineDateFilter: ui.timelineDateFilter,
  });
  const groups = groupBy(timeline, (item) => {
    const date = timelineDateRange(item.update, ui.timelineSort)?.start;
    return date ? displayMonth(date) : "Unscheduled";
  });
  const groupEntries = [...groups.entries()];
  return `
    <section class="screen timeline-screen">
      <div class="timeline-controls flow">
        ${chipRow(["All", ...products], ui.product, "filter-product", "Product")}
        ${chipRow(Object.keys(TIMELINE_SORTS), ui.timelineSort, "timeline-sort", "Timeline sort", TIMELINE_SORTS)}
        <div class="date-filter">
          <label class="field compact"><span>From date</span><input id="timeline-date" type="date" value="${escapeAttr(ui.timelineDateFilter)}"></label>
          ${ui.timelineDateFilter ? `<button type="button" class="text-button" data-action="clear-timeline-date">Clear</button>` : ""}
        </div>
      </div>
      <div class="timeline-layout">
        <aside class="timeline-scrub" aria-label="Timeline shortcuts">
          ${groupEntries.map(([month], index) => `
            <button type="button" data-action="scroll-timeline" data-target="timeline-${index}">${escapeHtml(shortMonthLabel(month))}</button>
          `).join("")}
        </aside>
        <div class="timeline-list">
          ${groupEntries.length ? groupEntries.map(([month, groupItems], index) => `
            <section id="timeline-${index}" class="timeline-month">
              <h2 class="section-title">${escapeHtml(month)}</h2>
              ${groupItems.map(timelineRow).join("")}
            </section>
          `).join("") : emptyState("No timeline items match these filters.")}
        </div>
      </div>
    </section>
  `;
}

function renderTracked(items) {
  const visible = items.filter((item) => !item.tracking.isHidden);
  const tracked = visible.filter((item) => item.tracking.isSaved);
  return `
    <section class="screen flow">
      <div class="metrics three">
        ${metricCard("Saved", visible.filter((item) => item.tracking.isSaved).length)}
        ${metricCard("Complete", visible.filter((item) => item.tracking.isComplete).length)}
        ${metricCard("Skipped", visible.filter((item) => item.tracking.isSkipped).length)}
      </div>
      ${tracked.length ? `<div class="card-list">${tracked.map(updateCard).join("")}</div>`
        : emptyState("Save updates to build your local tracker.")}
    </section>
  `;
}

function renderSettings(items) {
  const enabledCount = items.filter((item) => !item.tracking.isHidden).length;
  return `
    <section class="screen flow settings-screen">
      <article class="card settings-card">
        <h2>Data source</h2>
        <p>Microsoft public release planner API</p>
        <p class="secondary">${enabledCount} cached updates</p>
        <button class="primary-button" type="button" data-action="refresh" ${busy ? "disabled" : ""}>${busy ? "Refreshing" : "Refresh now"}</button>
      </article>
      ${isExtension ? renderNotificationSettings() : renderPwaDataSettings()}
      <article class="card settings-card">
        <h2>Products</h2>
        <div class="settings-list">
          ${sources.map((source) => `
            <label class="setting-row">
              <span><strong>${escapeHtml(source.product)}</strong><small>${escapeHtml(source.type || "No type")} · ${normalizedType(source.type) === "powerplatform" ? "default on" : "default off"}</small></span>
              <input class="switch" type="checkbox" data-product-toggle="${escapeAttr(source.product)}"
                ${sourceIsEnabled(source, state) ? "checked" : ""}>
            </label>
          `).join("")}
        </div>
      </article>
      <article class="card settings-card">
        <h2>Appearance</h2>
        <div class="segmented">
          <label><input type="radio" name="theme" data-theme value="light" ${state.preferences.theme === "light" ? "checked" : ""}><span>Light</span></label>
          <label><input type="radio" name="theme" data-theme value="dark" ${state.preferences.theme === "dark" ? "checked" : ""}><span>Dark</span></label>
        </div>
      </article>
      <article class="card settings-card">
        <h2>API diagnostic</h2>
        <p>Fetches the first enabled Microsoft source and shows the raw parse result.</p>
        <button class="primary-button" type="button" data-action="diagnostic" ${busy ? "disabled" : ""}>${busy ? "Checking" : "Run API diagnostic"}</button>
        ${diagnostic ? renderDiagnostic(diagnostic) : ""}
      </article>
      <article class="card settings-card about-card" data-action="about-tap">
        <h2>About</h2>
        <p>Created by David Wyatt, Published by Power DevBox. For more information visit:</p>
        ${externalLink("https://wyattdave.com")}
        ${externalLink("https://powerdevbox.com")}
        <p>For a more detailed view on your laptop we recommend checking out:</p>
        ${externalLink("https://releaseplans.net/")}
      </article>
      ${debugEnabled ? renderDebugSettings() : ""}
    </section>
  `;
}

function renderNotificationSettings() {
  const hour = String(state.preferences.notificationHour).padStart(2, "0");
  const minute = String(state.preferences.notificationMinute).padStart(2, "0");
  return `
    <article class="card settings-card">
      <h2>Icon notifications</h2>
      <label class="setting-row">
        <span><strong>Daily summary badge</strong><small>${state.preferences.notificationEnabled ? `Runs at ${hour}:${minute}` : "Turned off"}</small></span>
        <input id="notification-enabled" class="switch" type="checkbox" ${state.preferences.notificationEnabled ? "checked" : ""}>
      </label>
      ${state.preferences.notificationEnabled ? `
        <label class="field"><span>Summary time</span><input id="notification-time" type="time" value="${hour}:${minute}"></label>
      ` : ""}
      <p class="secondary">The toolbar icon shows a badge and summary tooltip. No popup notification is created.</p>
    </article>
  `;
}

function renderPwaDataSettings() {
  return `
    <article class="card settings-card">
      <h2>PWA data access</h2>
      <p>Microsoft’s feeds do not allow browser CORS requests. Deploy the proxy included in <code>pwa/proxy</code>, then paste its HTTPS URL here.</p>
      <label class="field">
        <span>Proxy URL</span>
        <input id="proxy-url" type="url" inputmode="url" placeholder="https://your-worker.workers.dev/" value="${escapeAttr(state.preferences.proxyUrl)}">
      </label>
      <button class="outline-button" type="button" data-action="save-proxy">Save proxy</button>
      <p class="secondary">Cached data and all tracking remain available offline. This PWA never requests notification permission.</p>
      ${installPrompt ? `<button class="primary-button" type="button" data-action="install-pwa">Install app</button>` : ""}
    </article>
  `;
}

function renderDiagnostic(value) {
  return `
    <dl class="detail-list diagnostic">
      ${detailTerm("Source", value.sourceProduct)}
      ${detailTerm("HTTP status", value.statusCode)}
      ${detailTerm("Content type", value.contentType || "Not provided")}
      ${detailTerm("Body length", value.bodyLength)}
      ${detailTerm("Parsed results", value.parsedCount)}
      ${detailTerm("First feature", value.firstFeature)}
      ${value.errorMessage ? detailTerm("Parse error", value.errorMessage) : ""}
    </dl>
    <h3>Response preview</h3>
    <pre>${escapeHtml(value.responsePreview || "No response body.")}</pre>
  `;
}

function renderDebugSettings() {
  const model = rewardModel();
  const debug = state.preferences.debug;
  return `
    <article class="card settings-card">
      <h2>Debug rewards</h2>
      ${numberField("debug-read-days", "Days read streak", debug.readDays ?? model.readStreakDays)}
      ${dateField("debug-read-date", "Read streak start", debug.readStartDate ?? model.readStreakStartDate ?? localToday())}
      ${numberField("debug-complete-weeks", "Weeks complete streak", debug.completeWeeks ?? model.completeStreakWeeks)}
      ${dateField("debug-complete-date", "Complete streak start", debug.completeStartDate ?? model.completeStreakStartDate ?? weekStart(localToday()))}
      ${numberField("debug-total-complete", "Total complete", debug.completedTasks ?? model.completedTasks)}
      <button class="primary-button" type="button" data-action="apply-debug">Apply debug values</button>
      <p class="secondary">Badge tapping is enabled until the app restarts.</p>
    </article>
  `;
}

async function applyDebugValues() {
  const value = (id) => document.querySelector(`#${id}`)?.value ?? "";
  await mutateState(() => {
    state.preferences.debug = {
      readDays: Math.max(0, Number(value("debug-read-days")) || 0),
      readStartDate: value("debug-read-date") || localToday(),
      completeWeeks: Math.max(0, Number(value("debug-complete-weeks")) || 0),
      completeStartDate: value("debug-complete-date") || weekStart(localToday()),
      completedTasks: Math.max(0, Number(value("debug-total-complete")) || 0),
    };
  }, true);
}

function renderAllBadges() {
  const model = rewardModel();
  return `
    <section class="screen badge-page flow">
      <h1>Badges</h1>
      <div class="badge-list">
        ${model.badges.map((badge) => `
          <article class="badge-row ${badge.isComplete ? "complete" : "locked"}"
            ${debugEnabled ? `data-action="toggle-badge" data-name="${escapeAttr(badge.name)}"` : ""}>
            <img src="${escapeAttr(badge.assetName)}" alt="">
            <span><strong>${escapeHtml(badge.name)}</strong><small>${escapeHtml(badge.description)}</small><em>${escapeHtml(badge.progressLabel)}</em></span>
          </article>
        `).join("")}
      </div>
    </section>
  `;
}

function renderDetail(item) {
  const { update, tracking } = item;
  const docsUrl = bestDocsUrl(update);
  return `
    <section class="screen detail-screen">
      <article class="detail-card">
        <p class="product-label">${escapeHtml(update.product)}</p>
        <h1>${escapeHtml(update.featureName)}</h1>
        <div class="pills">
          ${pill(releaseStatusLabel(update))}
          ${update.aiContribution ? pill("AI") : ""}
          ${tracking.isComplete ? pill("Complete") : ""}
          ${tracking.isSkipped ? pill("Skipped") : ""}
        </div>
        <dl class="detail-list">
          ${detailTerm("Area", update.productArea)}
          ${detailTerm("Date", primaryDateLabel(update))}
          ${detailTerm("EarlyAccessDate", rawDateLabel(update, update.eaDateValue, update.earlyAccessDate))}
          ${detailTerm("PublicPreviewDate", rawDateLabel(update, update.ppDateValue, update.publicPreviewDate))}
          ${detailTerm("GADate", rawDateLabel(update, update.gaDateValue, update.gaDate))}
          ${detailTerm("Enabled for", update.enabledFor)}
          ${detailTerm("Wave", update.gaReleaseWaveName || update.releaseWaveName)}
        </dl>
        <h2 class="section-title">Business value</h2>
        <p class="long-copy">${escapeHtml(htmlToText(update.businessValue) || "No summary provided.")}</p>
        <h2 class="section-title">Details</h2>
        <p class="long-copy">${escapeHtml(htmlToText(update.featureDetails) || "No details provided.")}</p>
        <label class="field">
          <span>Task status</span>
          <select id="detail-status" data-id="${escapeAttr(update.id)}">
            ${["Open", "Complete", "Skipped"].map((status) => `<option ${taskStatus(tracking) === status ? "selected" : ""}>${status}</option>`).join("")}
          </select>
        </label>
        <div class="button-grid">
          <button class="outline-button" type="button" data-action="toggle-saved" data-id="${escapeAttr(update.id)}">${tracking.isSaved ? "Unsave" : "Save"}</button>
          <button class="outline-button" type="button" data-action="share" data-id="${escapeAttr(update.id)}">Share</button>
          <button class="outline-button" type="button" data-action="docs" data-url="${escapeAttr(docsUrl)}" ${docsUrl ? "" : "disabled"}>Docs</button>
          <button class="outline-button" type="button" data-action="email" data-id="${escapeAttr(update.id)}">Email</button>
        </div>
        <button class="danger-text-button" type="button" data-action="toggle-hidden" data-id="${escapeAttr(update.id)}">${tracking.isHidden ? "Restore to lists" : "Hide from lists"}</button>
      </article>
    </section>
  `;
}

function updateCard(item, swipeable = false) {
  const { update, tracking } = item;
  const status = [
    pill(releaseStatusLabel(update)),
    update.isNew ? pill("New") : "",
    update.isChanged ? pill("Changed") : "",
    tracking.isComplete ? pill("Complete") : "",
    tracking.isSkipped ? pill("Skipped") : "",
    tracking.isSaved ? pill("Saved") : "",
  ].join("");
  return `
    <article class="card update-card ${swipeable ? "swipe-card" : ""}" role="button" tabindex="0"
      data-action="open-update" data-id="${escapeAttr(update.id)}" data-update-id="${escapeAttr(update.id)}">
      <div class="update-meta">
        <span class="product-name">${productDot(update.product)}${escapeHtml(update.product)}</span>
        <time>${escapeHtml(primaryDateLabel(update))}</time>
      </div>
      <h3>${escapeHtml(update.featureName)}</h3>
      <p>${escapeHtml(update.productArea || update.featureType || releaseStatusLabel(update))}</p>
      <div class="pills">${status}</div>
    </article>
  `;
}

function timelineRow(item) {
  return `
    <article class="timeline-row" role="button" tabindex="0" data-action="open-update" data-id="${escapeAttr(item.update.id)}">
      ${productDot(item.update.product)}
      <span>
        <small>${escapeHtml(timelineDateLabel(item.update, ui.timelineSort))}</small>
        <strong>${escapeHtml(item.update.featureName)}</strong>
        <em>${escapeHtml(item.update.product)}</em>
      </span>
    </article>
  `;
}

function badgeTile(badge) {
  return `
    <article class="badge-tile" ${debugEnabled ? `data-action="toggle-badge" data-name="${escapeAttr(badge.name)}"` : ""}>
      <img src="${escapeAttr(badge.assetName)}" alt="${escapeAttr(badge.name)}">
    </article>
  `;
}

function renderModal() {
  if (!achievement) {
    elements.modal.innerHTML = "";
    elements.modal.hidden = true;
    elements.shell.classList.remove("blurred");
    return;
  }
  elements.shell.classList.add("blurred");
  elements.modal.hidden = false;
  elements.modal.innerHTML = `
    <button class="achievement-overlay" type="button" data-action="dismiss-achievement" aria-label="Dismiss achievement">
      <img src="${escapeAttr(achievement.assetName)}" alt="${escapeAttr(achievement.name)}">
      <strong>You achieved ${escapeHtml(achievement.name)} for ${escapeHtml(achievement.description)}</strong>
    </button>
  `;
}

function openUpdate(id) {
  const update = findUpdate(id);
  if (!update) return;
  recordTaskRead(state);
  storage.save(state);
  ui.selectedUpdateId = id;
  setTaskUrl(id);
  render();
  elements.content.scrollTo({ top: 0 });
}

function findUpdate(id) {
  return state.updates.find((update) => update.id === id);
}

async function shareUpdate(update) {
  if (!update) return;
  const docs = bestDocsUrl(update);
  const appLink = taskLink(update.id);
  const shareText = `${update.featureName}\n${update.product}\nStatus: ${releaseStatusLabel(update)}\nDate: ${primaryDateLabel(update)}${docs ? `\nDocs: ${docs}` : ""}\n${appLink}`;
  if (navigator.share) {
    try {
      await navigator.share({ title: update.featureName, text: shareText, url: appLink });
      return;
    } catch (error) {
      if (error.name === "AbortError") return;
    }
  }
  try {
    await navigator.clipboard.writeText(shareText);
    showToast("Update copied to the clipboard.");
  } catch {
    showToast("Sharing is unavailable in this browser.");
  }
}

function emailUpdate(update) {
  if (!update) return;
  const docs = bestDocsUrl(update);
  const body = [
    update.featureName,
    update.product,
    `Status: ${releaseStatusLabel(update)}`,
    `Date: ${primaryDateLabel(update)}`,
    "",
    htmlToText(update.businessValue),
    "",
    htmlToText(update.featureDetails),
    docs ? `Docs: ${docs}` : "",
    `Open in PPAC Tracker: ${taskLink(update.id)}`,
  ].filter((line) => line !== "").join("\n");
  location.href = `mailto:?subject=${encodeURIComponent(update.featureName)}&body=${encodeURIComponent(body)}`;
}

function taskLink(id) {
  if (isExtension) return `https://wyattdave.com/ppac-tracker/task/${encodeURIComponent(id)}`;
  const url = new URL(location.href);
  url.search = "";
  url.hash = "";
  url.searchParams.set("task", id);
  return url.toString();
}

function setTaskUrl(id) {
  if (isExtension) return;
  const url = new URL(location.href);
  url.searchParams.set("task", id);
  history.pushState({}, "", url);
}

function clearTaskUrl() {
  if (isExtension) return;
  const url = new URL(location.href);
  url.searchParams.delete("task");
  history.pushState({}, "", url);
}

function handleDeepLink() {
  if (isExtension) return;
  const url = new URL(location.href);
  const requestedScreen = url.searchParams.get("screen");
  if (["Today", "Updates", "Timeline", "Tracked", "Settings"].includes(requestedScreen)) {
    ui.screen = requestedScreen;
  }
  const id = url.searchParams.get("task");
  if (id) {
    ui.screen = "Updates";
    ui.statusFilter = "All";
    ui.releaseStageFilter = "All Releases";
    ui.product = "All";
    ui.searchQuery = "";
    ui.selectedUpdateId = id;
  }
  if (sources.length) render();
}

function openExternal(url) {
  if (!/^https?:\/\//i.test(url)) return;
  if (isExtension) chrome.tabs.create({ url });
  else window.open(url, "_blank", "noopener,noreferrer");
}

function notifyBackgroundSettingsChanged() {
  if (isExtension) chrome.runtime.sendMessage({ type: "settings-updated" }).catch(() => {});
}

function applyTheme() {
  document.documentElement.dataset.theme = state.preferences.theme === "dark" ? "dark" : "light";
  document.querySelector('meta[name="theme-color"]')?.setAttribute(
    "content",
    state.preferences.theme === "dark" ? "#17121d" : "#6750a4",
  );
}

function showToast(message) {
  clearTimeout(toastTimer);
  elements.toast.textContent = message;
  elements.toast.hidden = false;
  toastTimer = setTimeout(() => { elements.toast.hidden = true; }, 3600);
}

function showError(message) {
  elements.error.textContent = message;
  elements.error.hidden = false;
}

function hideError() {
  elements.error.hidden = true;
  elements.error.textContent = "";
}

function createStorage() {
  if (isExtension) {
    return {
      async load() {
        return (await chrome.storage.local.get(STORAGE_KEY))[STORAGE_KEY];
      },
      async save(value) {
        await chrome.storage.local.set({ [STORAGE_KEY]: value });
      },
    };
  }
  return {
    async load() {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const request = database.transaction("state", "readonly").objectStore("state").get(STORAGE_KEY);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });
    },
    async save(value) {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const request = database.transaction("state", "readwrite").objectStore("state").put(value, STORAGE_KEY);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    },
  };
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("ppac-tracker", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("state");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function availableProducts(items) {
  return [...new Set(items.map((item) => item.update.product).filter(Boolean))].sort();
}

function rawDateLabel(update, ...values) {
  for (const value of values) {
    const range = parseReleaseDateRange(value, dateKeyFromTimestamp(update.firstSeenAt));
    if (range) return range.isMonthOnly ? shortDisplayMonth(range.start) : displayDate(range.start);
  }
  return "-";
}

function dateKeyFromTimestamp(timestamp) {
  const date = new Date(Number(timestamp) || Date.now());
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function shortDisplayMonth(dateKey) {
  const [year, month] = dateKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", { month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(year, month - 1, 1)));
}

function shortMonthLabel(value) {
  if (value === "Unscheduled") return "—";
  const [month, year] = value.split(" ");
  return `${month.slice(0, 3)} ${year.slice(-2)}`;
}

function metricCard(label, value, action = "") {
  const attributes = action ? `role="button" tabindex="0" data-action="${action}"` : "";
  return `<article class="metric-card" ${attributes}><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></article>`;
}

function chipRow(values, selected, action, label, labels = null) {
  return `
    <div class="chip-row" role="group" aria-label="${escapeAttr(label)}">
      ${values.map((value) => `
        <button type="button" class="chip ${selected === value ? "selected" : ""}"
          data-action="${action}" data-value="${escapeAttr(value)}">${escapeHtml(labels?.[value] ?? value)}</button>
      `).join("")}
    </div>
  `;
}

function pill(value) {
  return `<span class="pill">${escapeHtml(value)}</span>`;
}

function productDot(product) {
  return `<span class="product-dot" style="--product-color:${productColor(product)}" aria-hidden="true"></span>`;
}

function productColor(product) {
  if (/power apps/i.test(product)) return "#742774";
  if (/power automate/i.test(product)) return "#0066ff";
  if (/copilot/i.test(product)) return "#0f766e";
  if (/dataverse/i.test(product)) return "#9333ea";
  if (/power pages/i.test(product)) return "#b45309";
  return "#6750a4";
}

function detailTerm(label, value) {
  return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value || "-")}</dd></div>`;
}

function numberField(id, label, value) {
  return `<label class="field"><span>${escapeHtml(label)}</span><input id="${id}" type="number" min="0" inputmode="numeric" value="${escapeAttr(value)}"></label>`;
}

function dateField(id, label, value) {
  return `<label class="field"><span>${escapeHtml(label)}</span><input id="${id}" type="date" value="${escapeAttr(value)}"></label>`;
}

function externalLink(url) {
  return `<button type="button" class="inline-link" data-action="docs" data-url="${escapeAttr(url)}">${escapeHtml(url)}</button>`;
}

function taskStatus(tracking) {
  return tracking.isComplete ? "Complete" : tracking.isSkipped ? "Skipped" : "Open";
}

function emptyState(message) {
  return `<div class="empty-state">${escapeHtml(message)}</div>`;
}

function groupBy(values, key) {
  const groups = new Map();
  values.forEach((value) => {
    const groupKey = key(value);
    const group = groups.get(groupKey) ?? [];
    group.push(value);
    groups.set(groupKey, group);
  });
  return groups;
}

function normalizedType(value) {
  return String(value ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function cssEscape(value) {
  return globalThis.CSS?.escape ? CSS.escape(value) : String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
}

function icon(name) {
  const paths = {
    back: '<path d="M20 11H7.8l4.6-4.6L11 5l-7 7 7 7 1.4-1.4L7.8 13H20v-2Z"/>',
    filter: '<path d="M4 6h16v2H4V6Zm3 5h10v2H7v-2Zm3 5h4v2h-4v-2Z"/>',
    today: '<path d="M7 2h2v2h6V2h2v2h3v18H4V4h3V2Zm11 8H6v10h12V10ZM6 6v2h12V6H6Z"/>',
    updates: '<path d="M5 3h14v4H5V3Zm0 7h14v4H5v-4Zm0 7h14v4H5v-4ZM7 5v0h10v0H7Z"/>',
    timeline: '<path d="M11 2h2v5.1a5 5 0 1 1-2 0V2Zm1 7a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm-1 8h2v5h-2v-5Z"/>',
    tracked: '<path d="M6 2h12v20l-6-4-6 4V2Zm2 2v14.3l4-2.7 4 2.7V4H8Z"/>',
    settings: '<path d="m19.4 13 .1-1-.1-1 2-1.5-2-3.4-2.4 1a8 8 0 0 0-1.7-1L15 3.5h-4l-.4 2.6a8 8 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.5-.1 1 .1 1-2 1.5 2 3.4 2.4-1a8 8 0 0 0 1.7 1l.4 2.6h4l.4-2.6a8 8 0 0 0 1.7-1l2.4 1 2-3.4-2-1.5ZM12 15a3 3 0 1 1 0-6 3 3 0 0 1 0 6Z"/>',
  };
  return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] ?? paths.updates}</svg>`;
}
