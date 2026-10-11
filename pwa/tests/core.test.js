import assert from "node:assert/strict";
import test from "node:test";
import {
  calculateMetrics,
  completeReleasedTasks,
  createDefaultState,
  fetchSource,
  filterUpdateItems,
  getRewardModel,
  normalizeSource,
  parseReleaseDateRange,
  refreshState,
  releasedTasksToComplete,
  setTaskStatus,
  sourceIsEnabled,
  timelineDateRange,
  toEntity,
  visibleItems,
} from "../core.js";

const legacySource = normalizeSource({
  Product: "PowerApps",
  Version: "2026-06",
  Type: "PowerPlatform",
  Schema: "ReleasePlanner",
  Url: "https://releaseplans.microsoft.com/example",
});

test("normalizes source names and defaults Power Platform products on", () => {
  const state = createDefaultState();
  assert.equal(legacySource.product, "Power Apps");
  assert.equal(sourceIsEnabled(legacySource, state), true);
  assert.equal(sourceIsEnabled({ ...legacySource, product: "Excel", type: "M365" }, state), false);
});

test("month-only dates use the first-seen day when it is in the month", () => {
  assert.deepEqual(parseReleaseDateRange("2026-08", "2026-08-17"), {
    start: "2026-08-17",
    endInclusive: "2026-08-17",
    isMonthOnly: true,
  });
  assert.equal(parseReleaseDateRange("Aug 17, 2026").start, "2026-08-17");
  assert.equal(parseReleaseDateRange("08/17/2026").start, "2026-08-17");
});

test("adapts M365 items into the common release shape", async () => {
  const source = {
    product: "Copilot Studio",
    version: "M365",
    type: "PowerPlatform",
    schema: "M365",
    url: "https://www.microsoft.com/example",
  };
  const fetcher = async () => new Response(JSON.stringify({
    value: [{
      id: 567894,
      title: "Microsoft Copilot Studio: Share autonomous agents",
      description: "A description",
      platforms: ["Web"],
      products: ["Microsoft Copilot Studio"],
      generalAvailabilityDate: "2027-01",
      previewAvailabilityDate: "2026-08",
      created: "2026-07-20T22:37:15Z",
      modified: "2026-07-21T10:00:00Z",
      status: "Launched",
      moreInfoUrls: ["https://learn.microsoft.com/example"],
    }],
  }), { status: 200, headers: { "content-type": "application/json" } });
  const item = (await fetchSource(source, fetcher))[0];
  assert.equal(item.ReleasePlanID, "m365:copilotstudio:567894");
  assert.equal(item.FeatureName, "Share autonomous agents");
  assert.equal(item.GAStatus, "Shipped");
  assert.equal(item.DocsUrl, "https://learn.microsoft.com/example");
});

test("refresh merges updates, detects later changes, and preserves tracking", async () => {
  const firstPayload = { results: [{
    SnapshotId: "release-1",
    ReleasePlanID: "release-1",
    FeatureName: "First feature",
    Product: "Power Apps",
    GitCommitDate: "2026-07-20",
    FirstGitHubPushDate: "2026-07-20",
    GADate: "08/17/2026",
    GAStatus: "Shipped",
  }] };
  const firstFetch = async () => new Response(JSON.stringify(firstPayload), { status: 200 });
  const first = await refreshState(createDefaultState(), [legacySource], firstFetch);
  assert.equal(first.result.newCount, 1);
  assert.equal(first.state.updates.length, 1);
  setTaskStatus(first.state, "release-1", "Complete");

  const secondPayload = structuredClone(firstPayload);
  secondPayload.results[0].FeatureDetails = "Changed details";
  const secondFetch = async () => new Response(JSON.stringify(secondPayload), { status: 200 });
  const second = await refreshState(first.state, [legacySource], secondFetch);
  assert.equal(second.result.changedCount, 1);
  assert.equal(second.state.updates[0].isChanged, true);
  assert.equal(second.state.tracking["release-1"].isComplete, true);
});

test("filters, metrics, and timeline use the common entity model", () => {
  const state = createDefaultState();
  const update = toEntity({
    SnapshotId: "release-1",
    FeatureName: "AI feature",
    Product: "Power Apps",
    GitCommitDate: "2026-07-28",
    FirstGitHubPushDate: "2026-07-27",
    GADate: "2026-08",
    GAStatus: "Shipped",
    AiContribution: "true",
  }, legacySource, null, Date.parse("2026-07-29T10:00:00Z"));
  state.updates = [update];
  const items = visibleItems(state, [legacySource]);
  assert.equal(filterUpdateItems(items, {
    product: "All",
    searchQuery: "AI",
    statusFilter: "AI",
    releaseStageFilter: "In GA",
  }).length, 1);
  assert.equal(calculateMetrics(items, "2026-07-29").totalLastSevenDays, 1);
  assert.equal(timelineDateRange(update, "generalAvailability").start, "2026-08-01");
});

test("decodes HTML entities in feed titles", () => {
  const update = toEntity({
    SnapshotId: "release-entity-title",
    FeatureName: "Discover what&#39;s new",
    Product: "Power Apps",
  }, legacySource, null, Date.now());
  assert.equal(update.featureName, "Discover what's new");
});

test("rewards reflect completion tracking and debug streak values", () => {
  const state = createDefaultState();
  state.updates = [toEntity({
    SnapshotId: "release-1",
    FeatureName: "Feature",
    Product: "Power Apps",
  }, legacySource, null, Date.now())];
  setTaskStatus(state, "release-1", "Complete");
  state.preferences.debug.readDays = 7;
  state.preferences.debug.readStartDate = new Date().toISOString().slice(0, 10);
  const rewards = [
    { type: "read task streak", name: "7 day streak", description: "Opened a task for 7 consecutive days" },
    { type: "complete task", name: "1 task", description: "Complete 1 task" },
  ];
  const model = getRewardModel(rewards, state, [legacySource]);
  assert.equal(model.completedTasks, 1);
  assert.equal(model.badges.find((badge) => badge.name === "1 task").isComplete, true);
});

function bulkCompletionState() {
  const state = createDefaultState();
  const releases = [
    { SnapshotId: "ga", GAStatus: "Shipped", GADate: "2099-01-01" },
    { SnapshotId: "preview", PPStatus: "Shipped", GAStatus: "Planned" },
    { SnapshotId: "early", EAStatus: "Shipped" },
    { SnapshotId: "planned", GADate: "2000-01-01", PublicPreviewDate: "2000-01-01", GAStatus: "Planned", PPStatus: "Planned" },
    { SnapshotId: "hidden", GAStatus: "Shipped" },
    { SnapshotId: "disabled", Product: "Excel", GAStatus: "Shipped" },
    { SnapshotId: "skipped", GAStatus: "Shipped" },
    { SnapshotId: "completed", GAStatus: "Shipped" },
  ];
  state.updates = releases.map((release) => toEntity(release, legacySource, null, Date.now()));
  state.preferences.products[legacySource.product] = false;
  state.tracking.hidden = { isHidden: true, isSaved: true, notes: "Keep this note" };
  setTaskStatus(state, "skipped", "Skipped");
  setTaskStatus(state, "completed", "Complete");
  return state;
}

test("bulk GA completion includes all cached released tasks and preserves tracking", () => {
  const state = bulkCompletionState();
  const completedTracking = structuredClone(state.tracking.completed);
  assert.deepEqual(releasedTasksToComplete(state).map((update) => update.id), ["ga", "hidden", "disabled", "skipped"]);
  assert.equal(completeReleasedTasks(state), 4);
  assert.equal(state.tracking.skipped.isSkipped, false);
  assert.equal(state.tracking.hidden.isHidden, true);
  assert.equal(state.tracking.hidden.isSaved, true);
  assert.equal(state.tracking.hidden.notes, "Keep this note");
  assert.deepEqual(state.tracking.completed, completedTracking);
  assert.equal(state.tracking.preview, undefined);
  assert.equal(state.tracking.early, undefined);
  assert.equal(state.tracking.planned, undefined);
  assert.equal(completeReleasedTasks(state), 0);
});

test("bulk preview completion includes GA and shipped public previews only", () => {
  const state = bulkCompletionState();
  assert.equal(completeReleasedTasks(state, true), 5);
  assert.equal(state.tracking.preview.isComplete, true);
  assert.equal(state.tracking.ga.isComplete, true);
  assert.equal(state.tracking.early, undefined);
  assert.equal(state.tracking.planned, undefined);
  assert.equal(state.preferences.completeDates.length, 1);
  assert.equal(completeReleasedTasks(state, true), 0);
});
