import {
  getSyncState,
  resetPlaceEnrichment,
  saveSyncState,
  setPlaceActive,
  upsertPlaces
} from "../../lib/database.js";
import { integerEnv, requiredEnv } from "../../lib/env.js";
import { createDeadline, NETWORK_TIMEOUT_MS } from "../../lib/fetch-policy.js";
import { json, methodNotAllowed, serverError, unauthorized } from "../../lib/http.js";
import { runWithSyncLease } from "../../lib/sync-lease.js";
import { fetchTourSyncPage } from "../../lib/tour-api.js";

function formatModifiedTime(value) {
  if (/^\d{8}$/.test(String(value ?? ""))) return String(value);
  if (/^\d{14}$/.test(String(value ?? ""))) return String(value).slice(0, 8);
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error("Invalid TourAPI catalog cursor");
  const parts = [
    date.getUTCFullYear(),
    date.getUTCMonth() + 1,
    date.getUTCDate()
  ];
  return parts
    .map((part, index) => index === 0 ? String(part) : String(part).padStart(2, "0"))
    .join("");
}

const defaultDependencies = {
  now: () => new Date(),
  getState: getSyncState,
  saveState: saveSyncState,
  fetchPage: fetchTourSyncPage,
  upsert: upsertPlaces,
  resetEnrichment: resetPlaceEnrichment,
  setActive: setPlaceActive,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline
};

export function createTourCatalogSyncHandler(dependencies = {}) {
  const deps = { ...defaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }

        const result = await deps.withLease({
          jobId: "tour_catalog_delta",
          run: () => runCatalogJob(deps)
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

async function runCatalogJob(deps) {
  const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
  let state;
  try {
    state = await deps.getState("tour_catalog_delta", { signal: deadline.signal });
    const now = deps.now();
    const maxPages = Math.min(integerEnv("TOUR_SYNC_MAX_PAGES", 10), 25);
    let page = Math.max(Number.parseInt(state.next_page, 10) || 1, 1);
    let cycleStartedAt = state.cycle_started_at ?? null;
    let sourceCursor = state.source_cursor ?? null;

    if (!sourceCursor) {
      const fullState = await deps.getState("tour_api", { signal: deadline.signal });
      sourceCursor = fullState.last_completed_at ?? new Date(now.getTime() - 86_400_000);
    }
    sourceCursor = formatModifiedTime(sourceCursor);
    if (!cycleStartedAt) cycleStartedAt = now.toISOString();

    if (page === 1 || !state.cycle_started_at || !state.source_cursor) {
      state = {
        ...state,
        id: "tour_catalog_delta",
        next_page: page,
        source_cursor: sourceCursor,
        cycle_started_at: cycleStartedAt
      };
      await deps.saveState(state, { signal: deadline.signal });
    }

    let processedPages = 0;
    let activePlaces = 0;
    let inactivePlaces = 0;
    let totalCount = state.total_count ?? 0;
    let completed = false;
    let nextPage = page;

    for (let index = 0; index < maxPages; index += 1) {
      if (!deadline.canStart(5_000)) break;
      const result = await deps.fetchPage({
        pageNo: page,
        numOfRows: 100,
        modifiedTime: sourceCursor,
        signal: deadline.signal
      });
      const activeRows = result.places.filter((place) => place.is_active === true);
      const inactiveRows = result.places.filter((place) => place.is_active === false);

      await deps.upsert(activeRows, { signal: deadline.signal });
      await Promise.all(
        activeRows.map((place) => deps.resetEnrichment(place.content_id, { signal: deadline.signal }))
      );
      await Promise.all(
        inactiveRows.map((place) =>
          deps.setActive(place.content_id, false, { signal: deadline.signal })
        )
      );

      processedPages += 1;
      activePlaces += activeRows.length;
      inactivePlaces += inactiveRows.length;
      totalCount = result.totalCount;
      const totalPages = Math.max(Math.ceil(totalCount / result.numOfRows), 1);
      completed = page >= totalPages || result.rawCount === 0;
      nextPage = completed ? 1 : page + 1;
      const completedCursor = completed ? formatModifiedTime(cycleStartedAt) : sourceCursor;

      state = {
        ...state,
        id: "tour_catalog_delta",
        next_page: nextPage,
        total_count: totalCount,
        last_processed_page: page,
        last_item_count: result.rawCount,
        last_error: null,
        last_completed_at: completed ? now.toISOString() : state.last_completed_at,
        source_cursor: completedCursor,
        cycle_started_at: completed ? null : cycleStartedAt
      };
      await deps.saveState(state, { signal: deadline.signal });

      if (completed) break;
      page = nextPage;
    }

    return {
      status: completed ? "completed" : "partial",
      processedPages,
      activePlaces,
      inactivePlaces,
      totalCount,
      nextPage,
      cursor: state.source_cursor
    };
  } catch (error) {
    try {
      const current = state ?? await deps.getState("tour_catalog_delta", { signal: deadline.signal });
      await deps.saveState(
        {
          ...current,
          id: "tour_catalog_delta",
          last_error: (error instanceof Error ? error.message : String(error)).slice(0, 500)
        },
        { signal: deadline.signal }
      );
    } catch {
      // 원래 오류 응답을 유지한다.
    }
    throw error;
  } finally {
    deadline.dispose();
  }
}

export default createTourCatalogSyncHandler();
