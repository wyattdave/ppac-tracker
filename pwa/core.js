export const STORAGE_KEY = "ppacTrackerState";

const MONTHS = {
  jan: 1,
  january: 1,
  feb: 2,
  february: 2,
  mar: 3,
  march: 3,
  apr: 4,
  april: 4,
  may: 5,
  jun: 6,
  june: 6,
  jul: 7,
  july: 7,
  aug: 8,
  august: 8,
  sep: 9,
  sept: 9,
  september: 9,
  oct: 10,
  october: 10,
  nov: 11,
  november: 11,
  dec: 12,
  december: 12,
};

const DISPLAY_PRODUCTS = {
  PowerApps: "Power Apps",
  PowerAutomate: "Power Automate",
  CopilotStudio: "Copilot Studio",
  AiBuilder: "AI Builder",
  PowerPages: "Power Pages",
};

export const STATUS_FILTERS = ["Open", "All", "New", "Changed", "Skipped", "Saved", "AI", "Hidden"];
export const RELEASE_STAGE_FILTERS = [
  "All Releases",
  "All Not Released",
  "In Early Access",
  "In Public Preview",
  "In GA",
  "GA this Week",
];
export const TIMELINE_SORTS = {
  lastUpdate: "Last Update",
  earlyAccess: "Early Access",
  publicPreview: "Public Preview",
  generalAvailability: "GA",
};

export function createDefaultState() {
  return {
    schemaVersion: 1,
    updates: [],
    tracking: {},
    events: [],
    lastSyncAt: 0,
    preferences: {
      products: {},
      theme: "light",
      notificationEnabled: true,
      notificationHour: 8,
      notificationMinute: 0,
      proxyUrl: "",
      readDates: [],
      completeDates: [],
      apiFailureDates: [],
      longestReadStreakDays: 0,
      longestReadStreakStartDate: null,
      longestCompleteStreakWeeks: 0,
      longestCompleteStreakStartDate: null,
      manualCompletedBadges: [],
      manualOpenBadges: [],
      announcedBadges: [],
      debug: {
        readDays: null,
        readStartDate: null,
        completeWeeks: null,
        completeStartDate: null,
        completedTasks: null,
      },
    },
  };
}

export function normalizeState(value) {
  const defaults = createDefaultState();
  if (!value || typeof value !== "object") return defaults;
  const preferences = value.preferences && typeof value.preferences === "object" ? value.preferences : {};
  const debug = preferences.debug && typeof preferences.debug === "object" ? preferences.debug : {};
  return {
    ...defaults,
    ...value,
    updates: Array.isArray(value.updates) ? value.updates : [],
    tracking: value.tracking && typeof value.tracking === "object" ? value.tracking : {},
    events: Array.isArray(value.events) ? value.events : [],
    preferences: {
      ...defaults.preferences,
      ...preferences,
      products: preferences.products && typeof preferences.products === "object" ? preferences.products : {},
      readDates: stringArray(preferences.readDates),
      completeDates: stringArray(preferences.completeDates),
      apiFailureDates: stringArray(preferences.apiFailureDates),
      manualCompletedBadges: stringArray(preferences.manualCompletedBadges),
      manualOpenBadges: stringArray(preferences.manualOpenBadges),
      announcedBadges: stringArray(preferences.announcedBadges),
      debug: { ...defaults.preferences.debug, ...debug },
    },
  };
}

export async function loadConfiguration(baseUrl = import.meta.url) {
  const sourceUrl = new URL("config/api.json", baseUrl);
  const rewardsUrl = new URL("config/rewards.json", baseUrl);
  const [sourceResponse, rewardResponse] = await Promise.all([fetch(sourceUrl), fetch(rewardsUrl)]);
  if (!sourceResponse.ok) throw new Error(`Could not load api.json (${sourceResponse.status})`);
  if (!rewardResponse.ok) throw new Error(`Could not load rewards.json (${rewardResponse.status})`);
  const rawSources = await sourceResponse.json();
  const rawRewards = await rewardResponse.json();
  return {
    sources: rawSources.map(normalizeSource).filter((source) => source.product && source.url),
    rewards: uniqueBy(
      rawRewards.map((reward) => ({
        type: text(reward.type).trim(),
        name: text(reward.name).trim(),
        description: text(reward.description).trim(),
      })),
      (reward) => `${reward.type}|${reward.name}|${reward.description}`,
    ),
  };
}

export function normalizeSource(raw) {
  const configuredProduct = text(raw.Product ?? raw.product).trim();
  return {
    product: DISPLAY_PRODUCTS[configuredProduct] ?? configuredProduct,
    version: text(raw.Version ?? raw.version).trim(),
    type: text(raw.Type ?? raw.type).trim(),
    schema: normalizedType(raw.Schema ?? raw.schema) === "m365" ? "M365" : "ReleasePlanner",
    url: text(raw.Url ?? raw.url).trim(),
  };
}

export function sourceIsEnabled(source, state) {
  const configured = state.preferences.products[source.product];
  return typeof configured === "boolean" ? configured : normalizedType(source.type) === "powerplatform";
}

export async function fetchSource(source, fetcher = fetch) {
  const response = await fetcher(source.url, {
    method: "GET",
    headers: { Accept: "application/json, text/plain, */*" },
    cache: "no-store",
  });
  const body = (await response.text()).replace(/^[\uFEFF\s]+/, "");
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  let payload;
  try {
    payload = JSON.parse(body);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error.message}`);
  }
  if (source.schema === "M365") {
    return array(payload.value).map((item) => adaptM365Item(item, source));
  }
  return array(payload.results);
}

export async function diagnoseSource(source, fetcher = fetch) {
  let response;
  try {
    response = await fetcher(source.url, {
      method: "GET",
      headers: { Accept: "application/json, text/plain, */*" },
      cache: "no-store",
    });
    const rawBody = await response.text();
    const body = rawBody.replace(/^[\uFEFF\s]+/, "");
    let items = [];
    let errorMessage = "";
    try {
      const payload = JSON.parse(body);
      items = source.schema === "M365"
        ? array(payload.value).map((item) => adaptM365Item(item, source))
        : array(payload.results);
    } catch (error) {
      errorMessage = error.message;
    }
    return {
      sourceProduct: source.product,
      url: source.url,
      statusCode: response.status,
      contentType: response.headers.get("content-type") ?? "",
      bodyLength: rawBody.length,
      parsedCount: items.length,
      firstFeature: text(items[0]?.FeatureName),
      responsePreview: body.slice(0, 1500),
      errorMessage,
    };
  } catch (error) {
    return {
      sourceProduct: source.product,
      url: source.url,
      statusCode: response?.status ?? 0,
      contentType: response?.headers?.get("content-type") ?? "",
      bodyLength: 0,
      parsedCount: 0,
      firstFeature: "",
      responsePreview: "",
      errorMessage: humanFetchError(error),
    };
  }
}

export async function refreshState(inputState, sources, fetcher = fetch) {
  const state = normalizeState(inputState);
  const now = Date.now();
  const hadCachedUpdates = state.updates.length > 0;
  const updateMap = new Map(state.updates.map((update) => [update.id, update]));
  const events = [];
  const failures = [];
  let total = 0;
  let newCount = 0;
  let changedCount = 0;

  for (const source of sources.filter((candidate) => sourceIsEnabled(candidate, state))) {
    try {
      const items = await fetchSource(source, fetcher);
      const sourceExisting = source.schema === "M365"
        ? state.updates.filter((update) => update.sourceProduct === source.product)
        : [];
      const m365ByProductId = new Map(
        sourceExisting
          .filter((update) => isM365Adapted(update) && update.productId)
          .map((update) => [update.productId, update]),
      );
      const legacyByTitle = new Map();
      sourceExisting.filter((update) => !isM365Adapted(update)).forEach((update) => {
        const title = normalizedReleaseTitle(update.featureName);
        const matches = legacyByTitle.get(title) ?? [];
        matches.push(update);
        legacyByTitle.set(title, matches);
      });

      for (const dto of items) {
        const id = stableId(dto, source);
        const productId = text(dto.ProductId);
        let existing = updateMap.get(id);
        if (!existing && productId) existing = m365ByProductId.get(productId);
        if (!existing) {
          const legacyMatches = legacyByTitle.get(normalizedReleaseTitle(text(dto.FeatureName)));
          existing = legacyMatches?.shift();
        }
        const entity = toEntity(dto, source, existing, now);
        const isNewlyDetected = !existing;
        const isChangedNow = Boolean(existing && existing.contentHash !== entity.contentHash);
        if (isNewlyDetected) {
          newCount += 1;
          events.push(changeEvent(entity, "New", now));
        } else if (isChangedNow) {
          changedCount += 1;
          events.push(changeEvent(entity, "Changed", now));
        }
        updateMap.set(entity.id, entity);
      }
      total += items.length;
    } catch (error) {
      failures.push({ product: source.product, message: humanFetchError(error) });
    }
  }

  state.updates = [...updateMap.values()];
  state.events = [...events, ...state.events].slice(0, 200);
  if (total > 0 || failures.length === 0) state.lastSyncAt = now;
  if (failures.length) addDate(state.preferences.apiFailureDates, localToday());

  const noOpenTasksCompleted = completeStreaksWhenNoOpenTasks(state, sources);
  updateLongestStreaks(state);
  return {
    state,
    result: {
      total,
      newCount,
      changedCount,
      failures,
      syncedAt: now,
      sourceCount: sources.filter((source) => sourceIsEnabled(source, state)).length,
      hadCachedUpdates,
      noOpenTasksCompleted,
    },
  };
}

export function toEntity(dto, source, existing = null, now = Date.now()) {
  const contentHash = hashContent(dto, source);
  const contentChanged = Boolean(existing && existing.contentHash !== contentHash);
  const gitCommitDate = text(dto.GitCommitDate);
  const firstGitHubPushDate = text(dto.FirstGitHubPushDate);
  return {
    id: existing?.id ?? stableId(dto, source),
    snapshotId: text(dto.SnapshotId),
    releasePlanId: text(dto.ReleasePlanID),
    productId: text(dto.ProductId),
    product: text(dto.Product) || source.product,
    sourceProduct: source.product,
    sourceUrl: source.url,
    featureName: htmlToText(text(dto.FeatureName)) || "Untitled update",
    productArea: text(dto.ProductArea),
    enabledFor: text(dto.EnabledFor),
    earlyAccessDate: text(dto.EarlyAccessDate),
    publicPreviewDate: text(dto.PublicPreviewDate),
    gaDate: text(dto.GADate),
    releaseWaveName: text(dto.ReleaseWaveName),
    gaReleaseWaveName: text(dto.GAReleaseWaveName),
    gitCommitDate,
    firstGitHubPushDate,
    docsUrl: text(dto.DocsUrl),
    docUrl: text(dto.docUrl),
    docsName: text(dto.docsName),
    gaDateValue: text(dto.GADateValue),
    ppDateValue: text(dto.PPDateValue),
    eaDateValue: text(dto.EADateValue),
    gaStatus: text(dto.GAStatus),
    ppStatus: text(dto.PPStatus),
    eaStatus: text(dto.EAStatus),
    featureType: text(dto.FeatureType),
    featureCategory: text(dto.FeatureCategory),
    businessValue: text(dto.BusinessValue),
    featureDetails: text(dto.FeatureDetails),
    aiContribution: text(dto.AiContribution).toLowerCase() === "true",
    geos: array(dto.geos).map(text),
    createdOn: text(dto.Createdon),
    firstSeenAt: existing?.firstSeenAt ?? now,
    lastSeenAt: now,
    changedAt: contentChanged ? now : (existing?.changedAt ?? null),
    contentHash,
    isNew: !firstGitHubPushDate || gitCommitDate.trim().toLowerCase() === firstGitHubPushDate.trim().toLowerCase(),
    isChanged: contentChanged || Boolean(existing?.isChanged),
  };
}

export function stableId(dto, source) {
  return text(dto.ReleasePlanID)
    || text(dto.SnapshotId)
    || stableHash(`${source.product}:${text(dto.FeatureName)}`);
}

export function normalizedReleaseTitle(value) {
  return text(value)
    .replace(/^\s*Microsoft\s+Copilot\s+Studio\s*:\s*/i, "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
}

export function trackingFor(state, id) {
  return {
    releaseId: id,
    isComplete: false,
    isSkipped: false,
    isSaved: false,
    isHidden: false,
    priority: "Normal",
    notes: "",
    updatedAt: Date.now(),
    ...(state.tracking[id] ?? {}),
  };
}

export function setTaskStatus(state, id, status) {
  const tracking = trackingFor(state, id);
  const wasComplete = tracking.isComplete;
  if (status === "Complete") {
    tracking.isComplete = true;
    tracking.isSkipped = false;
    if (!wasComplete) {
      addDate(state.preferences.readDates, localToday());
      addDate(state.preferences.completeDates, localToday());
    }
  } else if (status === "Skipped") {
    tracking.isComplete = false;
    tracking.isSkipped = true;
  } else {
    tracking.isComplete = false;
    tracking.isSkipped = false;
  }
  tracking.updatedAt = Date.now();
  state.tracking[id] = tracking;
  updateLongestStreaks(state);
}

export function releasedTasksToComplete(state, includePublicPreview = false) {
  return state.updates.filter((update) => !trackingFor(state, update.id).isComplete
    && (matchesReleaseStage(update, "In GA")
      || (includePublicPreview && matchesReleaseStage(update, "In Public Preview"))));
}

export function completeReleasedTasks(state, includePublicPreview = false) {
  const updates = releasedTasksToComplete(state, includePublicPreview);
  updates.forEach((update) => setTaskStatus(state, update.id, "Complete"));
  return updates.length;
}

export function toggleTrackingFlag(state, id, flag) {
  const tracking = trackingFor(state, id);
  tracking[flag] = !tracking[flag];
  tracking.updatedAt = Date.now();
  state.tracking[id] = tracking;
}

export function recordTaskRead(state, date = localToday()) {
  addDate(state.preferences.readDates, date);
  updateLongestStreaks(state, date);
}

export function visibleItems(state, sources) {
  const enabledProducts = new Set(
    sources.filter((source) => sourceIsEnabled(source, state)).map((source) => source.product),
  );
  return state.updates
    .filter((update) => enabledProducts.has(update.sourceProduct))
    .map((update) => ({ update, tracking: trackingFor(state, update.id) }));
}

export function filterUpdateItems(items, controls) {
  const source = controls.statusFilter === "Hidden"
    ? items
    : items.filter((item) => !item.tracking.isHidden);
  return source
    .filter((item) => controls.product === "All" || item.update.product === controls.product)
    .filter((item) => matchesSearch(item, controls.searchQuery))
    .filter((item) => matchesReleaseStage(item.update, controls.releaseStageFilter))
    .filter((item) => matchesStatus(item, controls.statusFilter))
    .sort(defaultUpdateComparator);
}

export function timelineItems(items, controls) {
  return items
    .filter((item) => !item.tracking.isHidden)
    .filter((item) => controls.product === "All" || item.update.product === controls.product)
    .filter((item) => {
      if (!controls.timelineDateFilter) return true;
      const range = timelineDateRange(item.update, controls.timelineSort);
      return Boolean(range && range.endInclusive >= controls.timelineDateFilter);
    })
    .sort((left, right) => {
      const leftDate = timelineDateRange(left.update, controls.timelineSort)?.start ?? "0000-01-01";
      const rightDate = timelineDateRange(right.update, controls.timelineSort)?.start ?? "0000-01-01";
      return rightDate.localeCompare(leftDate) || left.update.featureName.localeCompare(right.update.featureName);
    });
}

export function calculateMetrics(items, today = localToday()) {
  const sevenDaysAgo = addDays(today, -7);
  const sevenDaysAhead = addDays(today, 7);
  return {
    totalOpen: items.filter((item) => !item.tracking.isComplete && !item.tracking.isSkipped).length,
    totalLastSevenDays: items.filter((item) => overlaps(
      parseReleaseDateRange(item.update.gitCommitDate),
      sevenDaysAgo,
      today,
    )).length,
    totalGaThisWeek: items.filter((item) => overlaps(
      firstReleaseDateRange(item.update, item.update.gaDateValue, item.update.gaDate),
      today,
      sevenDaysAhead,
    )).length,
  };
}

export function releaseDateRange(update) {
  return firstReleaseDateRange(
    update,
    update.gaDateValue,
    update.gaDate,
    update.ppDateValue,
    update.publicPreviewDate,
  );
}

export function timelineDateRange(update, sort) {
  if (sort === "earlyAccess") {
    return firstReleaseDateRange(update, update.eaDateValue, update.earlyAccessDate);
  }
  if (sort === "publicPreview") {
    return firstReleaseDateRange(update, update.ppDateValue, update.publicPreviewDate);
  }
  if (sort === "generalAvailability") {
    return firstReleaseDateRange(update, update.gaDateValue, update.gaDate);
  }
  return firstReleaseDateRange(update, update.gitCommitDate);
}

export function parseReleaseDateRange(rawValue, monthOnlyAnchor = null) {
  const value = text(rawValue).trim();
  if (!value || value === "-") return null;
  let year;
  let month;
  let day;
  let match = value.match(/^(\d{4})-(\d{2})-(\d{2})(?:T.*)?$/);
  if (match) {
    [, year, month, day] = match.map(Number);
    return exactRange(year, month, day);
  }
  match = value.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (match) {
    month = Number(match[1]);
    day = Number(match[2]);
    year = Number(match[3]);
    return exactRange(year, month, day);
  }
  match = value.match(/^([A-Za-z]+)\s+(\d{1,2}),?\s+(\d{4})$/);
  if (match && MONTHS[match[1].toLowerCase()]) {
    month = MONTHS[match[1].toLowerCase()];
    day = Number(match[2]);
    year = Number(match[3]);
    return exactRange(year, month, day);
  }
  match = value.match(/^(\d{4})-(\d{2})$/);
  if (match) {
    year = Number(match[1]);
    month = Number(match[2]);
    return monthRange(year, month, monthOnlyAnchor);
  }
  match = value.match(/^([A-Za-z]+)\s+(\d{4})$/);
  if (match && MONTHS[match[1].toLowerCase()]) {
    return monthRange(Number(match[2]), MONTHS[match[1].toLowerCase()], monthOnlyAnchor);
  }
  return null;
}

export function primaryDateLabel(update) {
  const range = firstReleaseDateRange(
    update,
    update.gaDateValue,
    update.gaDate,
    update.ppDateValue,
    update.publicPreviewDate,
  );
  return range ? displayRange(range) : "Date not announced";
}

export function timelineDateLabel(update, sort) {
  const labels = {
    lastUpdate: "Last update",
    earlyAccess: "Early access",
    publicPreview: "Public preview",
    generalAvailability: "GA",
  };
  const range = timelineDateRange(update, sort);
  return `${labels[sort] ?? labels.lastUpdate}: ${range ? displayRange(range) : "Not announced"}`;
}

export function releaseStatusLabel(update) {
  if (meaningful(update.gaStatus) && update.gaStatus.toLowerCase() !== "n/a") return `GA ${update.gaStatus}`;
  if (meaningful(update.ppStatus) && update.ppStatus.toLowerCase() !== "n/a") return `Preview ${update.ppStatus}`;
  if (meaningful(update.eaStatus) && update.eaStatus.toLowerCase() !== "n/a") return `EA ${update.eaStatus}`;
  return "Planned";
}

export function bestDocsUrl(update) {
  const docsUrl = text(update.docsUrl).trim();
  const docUrl = text(update.docUrl).trim();
  if (/^https?:\/\//i.test(docsUrl)) return docsUrl;
  if (docsUrl) return `https://learn.microsoft.com${docsUrl.startsWith("/") ? "" : "/"}${docsUrl}`;
  if (/^https?:\/\//i.test(docUrl)) return docUrl;
  return "";
}

export function htmlToText(value) {
  return text(value)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n")
    .replace(/<li>/gi, "• ")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, "\"")
    .replace(/&apos;|&#39;|&#x27;/gi, "'")
    .replace(/&#(\d+);/g, (entity, code) => decodeNumericEntity(entity, code, 10))
    .replace(/&#x([0-9a-f]+);/gi, (entity, code) => decodeNumericEntity(entity, code, 16))
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function getRewardModel(rewards, state, sources = []) {
  const performance = calculateRewardPerformance(state);
  const allCompleteCount = Object.values(state.tracking).filter((tracking) => tracking.isComplete).length;
  const actualCompleteCount = sources.length
    ? visibleItems(state, sources).filter((item) => item.tracking.isComplete).length
    : allCompleteCount;
  const override = numberOrNull(state.preferences.debug.completedTasks);
  const completedTasks = Math.max(override ?? 0, actualCompleteCount);
  const manualCompleted = new Set(state.preferences.manualCompletedBadges);
  const manualOpen = new Set(state.preferences.manualOpenBadges);
  const targets = uniqueBy(
    rewards.map(rewardTarget).filter(Boolean),
    (target) => `${target.kind}|${target.name}|${target.target}`,
  );
  const currentFor = (kind) => {
    if (kind === "read") return performance.readStreakDays;
    if (kind === "weeks") return performance.completeStreakWeeks;
    return completedTasks;
  };
  const badges = targets.map((target) => {
    const current = currentFor(target.kind);
    return {
      ...target,
      current,
      isComplete: manualCompleted.has(target.name) || (!manualOpen.has(target.name) && current >= target.target),
      assetName: `badges/${assetSegment(target.name)}.png`,
      progressLabel: `${Math.min(current, target.target)} / ${target.target} ${target.unit}`,
    };
  });
  const progress = ["read", "weeks", "tasks"].map((kind) => {
    const candidates = targets.filter((target) => target.kind === kind).sort((a, b) => a.target - b.target);
    const current = currentFor(kind);
    const target = candidates.find((candidate) => current < candidate.target) ?? candidates.at(-1);
    return target ? {
      ...target,
      current,
      fraction: target.target > 0 ? Math.min(1, current / target.target) : 0,
      progressLabel: `${Math.min(current, target.target)} / ${target.target} ${target.unit}`,
    } : null;
  }).filter(Boolean);
  return { ...performance, completedTasks, progress, badges };
}

export function calculateRewardPerformance(state, date = localToday()) {
  const prefs = state.preferences;
  const readDates = new Set(prefs.readDates);
  const completeDates = new Set(prefs.completeDates);
  const apiFailureDates = new Set(prefs.apiFailureDates);
  const debug = prefs.debug;
  const baseRead = currentReadStreak(date, readDates, apiFailureDates);
  const baseComplete = currentCompleteStreak(date, completeDates, apiFailureDates);
  const read = debugReadStreak(debug, date) ?? baseRead;
  const complete = debugCompleteStreak(debug, date) ?? baseComplete;
  const longestRead = read.count >= Number(prefs.longestReadStreakDays || 0)
    ? read
    : {
        count: Number(prefs.longestReadStreakDays || 0),
        startDate: prefs.longestReadStreakStartDate,
      };
  const longestComplete = complete.count >= Number(prefs.longestCompleteStreakWeeks || 0)
    ? complete
    : {
        count: Number(prefs.longestCompleteStreakWeeks || 0),
        startDate: prefs.longestCompleteStreakStartDate,
      };
  return {
    readStreakDays: read.count,
    completeStreakWeeks: complete.count,
    readStreakStartDate: read.startDate,
    completeStreakStartDate: complete.startDate,
    longestReadStreakDays: longestRead.count,
    longestReadStreakStartDate: longestRead.startDate,
    longestCompleteStreakWeeks: longestComplete.count,
    longestCompleteStreakStartDate: longestComplete.startDate,
  };
}

export function updateLongestStreaks(state, date = localToday()) {
  const performance = calculateRewardPerformance(state, date);
  state.preferences.longestReadStreakDays = performance.longestReadStreakDays;
  state.preferences.longestReadStreakStartDate = performance.longestReadStreakStartDate;
  state.preferences.longestCompleteStreakWeeks = performance.longestCompleteStreakWeeks;
  state.preferences.longestCompleteStreakStartDate = performance.longestCompleteStreakStartDate;
  return performance;
}

export function toggleManualBadge(state, name, isCurrentlyComplete) {
  const completed = new Set(state.preferences.manualCompletedBadges);
  const open = new Set(state.preferences.manualOpenBadges);
  if (isCurrentlyComplete) {
    completed.delete(name);
    open.add(name);
  } else {
    open.delete(name);
    completed.add(name);
  }
  state.preferences.manualCompletedBadges = [...completed];
  state.preferences.manualOpenBadges = [...open];
}

export function displayDate(dateKey) {
  if (!dateKey) return "-";
  const [year, month, day] = dateKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day))).replace(",", "");
}

export function displayMonth(dateKey) {
  if (!dateKey) return "Unscheduled";
  const [year, month] = dateKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, 1)));
}

export function syncLabel(timestamp) {
  if (!timestamp) return "Not synced yet";
  return `Synced ${new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(timestamp))}`;
}

export function localToday() {
  const date = new Date();
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function addDays(dateKey, count) {
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + count));
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export function weekStart(dateKey) {
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const mondayOffset = (date.getUTCDay() + 6) % 7;
  return addDays(dateKey, -mondayOffset);
}

export function isScheduledForToday(update, today = localToday()) {
  const range = releaseDateRange(update);
  return Boolean(range && range.start <= today && range.endInclusive >= today);
}

function adaptM365Item(item, source) {
  const sourceKey = normalizedType(source.product) || "source";
  const sourceId = `m365:${sourceKey}:${item.id}`;
  const sourceTitle = text(item.title);
  const featureName = sourceTitle.replace(/^\s*Microsoft\s+Copilot\s+Studio\s*:\s*/i, "").trim() || sourceTitle;
  const urls = array(item.moreInfoUrls).map((url) => text(url).trim()).filter(Boolean);
  const docsUrl = urls.find((url) => /learn\.microsoft\.com/i.test(url))
    ?? urls.find((url) => /^https:\/\//i.test(url))
    ?? urls.find((url) => /^http:\/\//i.test(url))
    ?? urls[0]
    ?? "";
  const rawStatus = text(item.status).trim();
  const status = /^(launched|shipped)$/i.test(rawStatus) ? "Shipped" : rawStatus;
  const products = array(item.products).map(text);
  return {
    SnapshotId: sourceId,
    ReleasePlanID: sourceId,
    ProductId: text(item.id),
    FeatureName: featureName,
    ProductArea: array(item.platforms).join(", "),
    EnabledFor: "",
    EarlyAccessDate: text(item.previewAvailabilityDate),
    PublicPreviewDate: text(item.previewAvailabilityDate),
    GADate: text(item.generalAvailabilityDate),
    Product: source.product,
    GitCommitDate: isoDate(item.modified),
    FirstGitHubPushDate: isoDate(item.created),
    DocsUrl: docsUrl,
    EAStatus: status,
    GAStatus: status,
    PPStatus: status,
    FeatureType: array(item.releaseRings).join(", "),
    FeatureCategory: "M365 Planner",
    BusinessValue: "",
    FeatureDetails: text(item.description),
    AiContribution: String(
      /copilot/i.test(source.product) || products.some((product) => /copilot/i.test(product)),
    ),
    docsName: docsUrl ? featureName : "",
    Createdon: isoDate(item.created),
    geos: array(item.cloudInstances),
    SourceFingerprint: [
      text(item.description),
      array(item.cloudInstances).join(","),
      array(item.platforms).join(","),
      array(item.releaseRings).join(","),
      urls.join(","),
      products.join(","),
      text(item.generalAvailabilityDate),
      text(item.previewAvailabilityDate),
      text(item.created),
      rawStatus,
      text(item.modified),
    ].join("\u001f"),
  };
}

function hashContent(dto, source) {
  return stableHash([
    source.product,
    dto.SnapshotId,
    dto.ReleasePlanID,
    dto.FeatureName,
    dto.ProductArea,
    dto.EnabledFor,
    dto.EarlyAccessDate,
    dto.PublicPreviewDate,
    dto.GADate,
    dto.GAStatus,
    dto.PPStatus,
    dto.EAStatus,
    dto.GitCommitDate,
    dto.FirstGitHubPushDate,
    dto.DocsUrl,
    dto.docUrl,
    dto.BusinessValue,
    dto.FeatureDetails,
    dto.SourceFingerprint,
  ].map(text).join("|"));
}

function stableHash(value) {
  let first = 0x811c9dc5;
  let second = 0x9e3779b9;
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    first = Math.imul(first ^ code, 0x01000193) >>> 0;
    second = Math.imul(second ^ code, 0x85ebca6b) >>> 0;
  }
  return `${first.toString(16).padStart(8, "0")}${second.toString(16).padStart(8, "0")}`;
}

function isM365Adapted(update) {
  return /^m365:/i.test(update.releasePlanId) || /^m365:/i.test(update.snapshotId);
}

function changeEvent(update, type, now) {
  return {
    id: `${now}-${Math.random().toString(16).slice(2)}`,
    releaseId: update.id,
    type,
    title: update.featureName,
    product: update.product,
    createdAt: now,
  };
}

function matchesSearch(item, query) {
  const normalized = text(query).trim().toLowerCase();
  if (!normalized) return true;
  return [
    item.update.featureName,
    item.update.product,
    item.update.docsName,
    item.update.featureDetails,
    item.update.enabledFor,
    item.update.productArea,
  ].some((value) => text(value).toLowerCase().includes(normalized));
}

function matchesStatus(item, status) {
  if (status === "All") return true;
  if (status === "Open" || status === "Incomplete") return !item.tracking.isComplete && !item.tracking.isSkipped;
  if (status === "New") return item.update.isNew;
  if (status === "Changed") return item.update.isChanged;
  if (status === "Saved") return item.tracking.isSaved;
  if (status === "Skipped") return item.tracking.isSkipped;
  if (status === "AI") return item.update.aiContribution;
  if (status === "Hidden") return item.tracking.isHidden;
  return true;
}

function matchesReleaseStage(update, filter) {
  const ea = shipped(update.eaStatus);
  const pp = shipped(update.ppStatus);
  const ga = shipped(update.gaStatus);
  if (filter === "All Not Released") return !ga;
  if (filter === "In Early Access") return ea && !pp && !ga;
  if (filter === "In Public Preview") return pp && !ga;
  if (filter === "In GA") return ga;
  if (filter === "GA this Week") {
    const today = localToday();
    return overlaps(timelineDateRange(update, "generalAvailability"), today, addDays(today, 7));
  }
  return true;
}

function defaultUpdateComparator(left, right) {
  const leftDate = timelineDateRange(left.update, "lastUpdate")?.start ?? "0000-01-01";
  const rightDate = timelineDateRange(right.update, "lastUpdate")?.start ?? "0000-01-01";
  return rightDate.localeCompare(leftDate) || left.update.featureName.localeCompare(right.update.featureName);
}

function firstReleaseDateRange(update, ...values) {
  const anchor = dateFromTimestamp(update.firstSeenAt);
  for (const value of values) {
    const range = parseReleaseDateRange(value, anchor);
    if (range) return range;
  }
  return null;
}

function exactRange(year, month, day) {
  const candidate = new Date(Date.UTC(year, month - 1, day));
  if (
    candidate.getUTCFullYear() !== year
    || candidate.getUTCMonth() !== month - 1
    || candidate.getUTCDate() !== day
  ) return null;
  const key = `${year}-${pad(month)}-${pad(day)}`;
  return { start: key, endInclusive: key, isMonthOnly: false };
}

function monthRange(year, month, anchor) {
  if (month < 1 || month > 12) return null;
  const start = `${year}-${pad(month)}-01`;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const end = `${year}-${pad(month)}-${pad(lastDay)}`;
  const effective = !anchor || anchor < start ? start : anchor > end ? end : anchor;
  return { start: effective, endInclusive: effective, isMonthOnly: true };
}

function displayRange(range) {
  if (!range) return "-";
  if (!range.isMonthOnly) return displayDate(range.start);
  const [year, month] = range.start.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, 1)));
}

function overlaps(range, start, end) {
  return Boolean(range && range.endInclusive >= start && range.start <= end);
}

function rewardTarget(reward) {
  const normalized = reward.type.trim().toLowerCase();
  const numbers = [...reward.description.matchAll(/\d+/g)].map((match) => Number(match[0]));
  if (normalized.includes("read task") && /consecutive days/i.test(reward.description)) {
    const target = numbers.at(-1);
    return target ? rewardTargetValue("read", "Read streak", "days", reward, target) : null;
  }
  if (normalized.includes("complete task streak")) {
    const target = numbers.at(-1);
    return target ? rewardTargetValue("weeks", "Weekly complete streak", "weeks", reward, target) : null;
  }
  if (normalized.includes("complete task")) {
    const target = numbers[0];
    return target ? rewardTargetValue("tasks", "Completed tasks", "tasks", reward, target) : null;
  }
  return null;
}

function rewardTargetValue(kind, category, unit, reward, target) {
  return {
    kind,
    category,
    unit,
    name: reward.name,
    description: reward.description,
    target,
  };
}

function currentReadStreak(date, readDates, apiFailureDates) {
  const endDate = successfulReadDay(date, readDates, apiFailureDates) ? date : addDays(date, -1);
  let count = 0;
  let cursor = endDate;
  while (successfulReadDay(cursor, readDates, apiFailureDates)) {
    count += 1;
    cursor = addDays(cursor, -1);
  }
  return { count, startDate: count ? addDays(endDate, -(count - 1)) : null };
}

function currentCompleteStreak(date, completeDates, apiFailureDates) {
  const completedWeeks = new Set([...completeDates, ...apiFailureDates].map(weekStart));
  const thisWeek = weekStart(date);
  const endWeek = completedWeeks.has(thisWeek) ? thisWeek : addDays(thisWeek, -7);
  let count = 0;
  let cursor = endWeek;
  while (completedWeeks.has(cursor)) {
    count += 1;
    cursor = addDays(cursor, -7);
  }
  return { count, startDate: count ? addDays(endWeek, -(count - 1) * 7) : null };
}

function debugReadStreak(debug, date) {
  const configuredDays = numberOrNull(debug.readDays);
  if (configuredDays === null) return null;
  const startDate = debug.readStartDate ?? addDays(date, -(configuredDays - 1));
  if (configuredDays <= 0 || startDate > date) return { count: 0, startDate: null };
  return { count: dayDifference(startDate, date) + 1, startDate };
}

function debugCompleteStreak(debug, date) {
  const configuredWeeks = numberOrNull(debug.completeWeeks);
  if (configuredWeeks === null) return null;
  const startDate = debug.completeStartDate ?? addDays(weekStart(date), -(configuredWeeks - 1) * 7);
  const startWeek = weekStart(startDate);
  const currentWeek = weekStart(date);
  if (configuredWeeks <= 0 || startWeek > currentWeek) return { count: 0, startDate: null };
  return { count: Math.floor(dayDifference(startWeek, currentWeek) / 7) + 1, startDate };
}

function completeStreaksWhenNoOpenTasks(state, sources) {
  const hasOpen = visibleItems(state, sources)
    .filter((item) => !item.tracking.isHidden)
    .some((item) => !item.tracking.isComplete && !item.tracking.isSkipped);
  if (hasOpen || !state.updates.length) return false;
  const today = localToday();
  const needsRead = !state.preferences.readDates.includes(today);
  const thisWeek = weekStart(today);
  const needsWeekly = !state.preferences.completeDates.some((date) => weekStart(date) === thisWeek);
  if (needsRead) addDate(state.preferences.readDates, today);
  if (needsWeekly) addDate(state.preferences.completeDates, today);
  return needsRead || needsWeekly;
}

function successfulReadDay(date, readDates, apiFailureDates) {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay();
  return readDates.has(date) || apiFailureDates.has(date) || day === 0 || day === 6;
}

function dayDifference(start, end) {
  return Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86_400_000);
}

function dateFromTimestamp(timestamp) {
  const date = new Date(Number(timestamp) || Date.now());
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function isoDate(value) {
  const trimmed = text(value).trim();
  return /^\d{4}-\d{2}-\d{2}/.test(trimmed) ? trimmed.slice(0, 10) : trimmed;
}

function normalizedType(value) {
  return text(value).toLowerCase().replace(/[^a-z0-9]/g, "");
}

function assetSegment(value) {
  return text(value).trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}

function meaningful(value) {
  const trimmed = text(value).trim();
  return trimmed && trimmed !== "-" ? trimmed : "";
}

function shipped(value) {
  return text(value).toLowerCase() === "shipped";
}

function humanFetchError(error) {
  const message = error?.message || String(error);
  if (/failed to fetch|networkerror|load failed/i.test(message)) {
    return "Network request blocked or unavailable. In the PWA, configure the included CORS proxy in Settings.";
  }
  return message;
}

function addDate(list, date) {
  if (!list.includes(date)) list.push(date);
}

function stringArray(value) {
  return Array.isArray(value) ? value.filter((item) => typeof item === "string") : [];
}

function array(value) {
  return Array.isArray(value) ? value : [];
}

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

function numberOrNull(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function uniqueBy(values, key) {
  const seen = new Set();
  return values.filter((value) => {
    const candidate = key(value);
    if (seen.has(candidate)) return false;
    seen.add(candidate);
    return true;
  });
}

function decodeNumericEntity(entity, value, radix) {
  const codePoint = Number.parseInt(value, radix);
  return Number.isInteger(codePoint) && codePoint >= 0 && codePoint <= 0x10ffff
    ? String.fromCodePoint(codePoint)
    : entity;
}

function pad(value) {
  return String(value).padStart(2, "0");
}
