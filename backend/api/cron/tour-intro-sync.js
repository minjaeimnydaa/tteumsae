import {
  listPlacesForPresentationSync,
  listPlacesForIntroSync,
  listPlacesForCongestionMatch,
  getSyncState,
  recordPlaceEnrichmentFailure,
  savePlaceCommon,
  savePlaceInfo,
  savePlaceMedia,
  savePlaceIntro,
  savePlaceAccessibility,
  saveSyncState,
  upsertPublicParkingLots,
  upsertCongestionForecasts
} from "../../lib/database.js";
import { integerEnv, requiredEnv } from "../../lib/env.js";
import { createDeadline, NETWORK_TIMEOUT_MS } from "../../lib/fetch-policy.js";
import { json, methodNotAllowed, serverError, unauthorized } from "../../lib/http.js";
import { runWithSyncLease } from "../../lib/sync-lease.js";
import {
  fetchPetTourDetail,
  fetchTourCommon,
  fetchTourImages,
  fetchTourInfo,
  fetchTourIntro
} from "../../lib/tour-api.js";
import { runIntroBatch, runPresentationBatch } from "../../lib/tour-sync.js";
import { fetchTourCongestionPage } from "../../lib/tour-congestion.js";
import { runTourCongestionSync } from "../../lib/tour-congestion-sync.js";
import {
  fetchTourAccessibilityDetail,
  fetchTourAccessibilityPage
} from "../../lib/tour-accessibility.js";
import { runAccessibilityBatch } from "../../lib/tour-accessibility-sync.js";
import { fetchPublicParkingPage } from "../../lib/public-parking.js";

const defaultDependencies = {
  listPlaces: listPlacesForIntroSync,
  fetchIntro: fetchTourIntro,
  saveIntro: savePlaceIntro,
  recordFailure: recordPlaceEnrichmentFailure,
  runBatch: runIntroBatch,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline,
  now: () => new Date()
};

const presentationDefaultDependencies = {
  listPlaces: listPlacesForPresentationSync,
  fetchCommon: fetchTourCommon,
  fetchImages: fetchTourImages,
  fetchPet: fetchPetTourDetail,
  fetchInfo: fetchTourInfo,
  saveCommon: savePlaceCommon,
  saveMedia: savePlaceMedia,
  saveInfo: savePlaceInfo,
  recordFailure: recordPlaceEnrichmentFailure,
  runBatch: runPresentationBatch,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline,
  now: () => new Date()
};

const emptyPresentationResult = {
  status: "idle",
  processed: 0,
  deferred: 0,
  completed: 0,
  partial: 0,
  failed: 0,
  commonUpdated: 0,
  commonEmpty: 0,
  commonFailed: 0,
  mediaUpdated: 0,
  mediaEmpty: 0,
  mediaFailed: 0,
  infoUpdated: 0,
  infoEmpty: 0,
  infoFailed: 0
};

const congestionDefaultDependencies = {
  fetchPage: fetchTourCongestionPage,
  listPlaces: listPlacesForCongestionMatch,
  saveRows: upsertCongestionForecasts,
  runSync: runTourCongestionSync,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline,
  now: () => new Date()
};

const accessibilityDefaultDependencies = {
  getState: getSyncState,
  saveState: saveSyncState,
  fetchPage: fetchTourAccessibilityPage,
  fetchDetail: fetchTourAccessibilityDetail,
  saveAccessibility: savePlaceAccessibility,
  runBatch: runAccessibilityBatch,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline,
  now: () => new Date()
};

const parkingDefaultDependencies = {
  getState: getSyncState,
  saveState: saveSyncState,
  fetchPage: fetchPublicParkingPage,
  upsert: upsertPublicParkingLots,
  withLease: runWithSyncLease,
  deadlineFactory: createDeadline,
  now: () => new Date()
};

export function createTourIntroSyncHandler(dependencies = {}) {
  const deps = { ...defaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }

        const result = await deps.withLease({
          jobId: "tour_intro",
          run: async () => {
            const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
            try {
              const now = deps.now();
              const batchSize = Math.min(integerEnv("TOUR_INTRO_SYNC_BATCH_SIZE", 20), 40);
              const concurrency = Math.min(integerEnv("TOUR_SYNC_CONCURRENCY", 4), 4);
              const places = await deps.listPlaces({ limit: batchSize, now, signal: deadline.signal });
              if (places.length === 0) {
                return {
                  status: "idle",
                  processed: 0,
                  deferred: 0,
                  updated: 0,
                  empty: 0,
                  failed: 0
                };
              }

              const counts = await deps.runBatch({
                places,
                fetchIntro: deps.fetchIntro,
                saveIntro: deps.saveIntro,
                recordFailure: deps.recordFailure,
                concurrency,
                syncedAt: now.toISOString(),
                signal: deadline.signal,
                canStart: () => deadline.canStart(5_000)
              });
              return { status: counts.deferred > 0 ? "partial" : "completed", ...counts };
            } finally {
              deadline.dispose();
            }
          }
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export function createTourPresentationSyncHandler(dependencies = {}) {
  const deps = { ...presentationDefaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }

        const result = await deps.withLease({
          jobId: "tour_presentation",
          run: async () => {
            const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
            try {
              const now = deps.now();
              const batchSize = Math.min(
                integerEnv("TOUR_PRESENTATION_SYNC_BATCH_SIZE", 5),
                10
              );
              const concurrency = Math.min(integerEnv("TOUR_SYNC_CONCURRENCY", 4), 4);
              const places = await deps.listPlaces({
                limit: batchSize,
                now,
                signal: deadline.signal
              });
              if (places.length === 0) return { ...emptyPresentationResult };

              const counts = await deps.runBatch({
                places,
                fetchCommon: deps.fetchCommon,
                fetchImages: deps.fetchImages,
                fetchPet: deps.fetchPet,
                fetchInfo: deps.fetchInfo,
                saveCommon: deps.saveCommon,
                saveMedia: deps.saveMedia,
                saveInfo: deps.saveInfo,
                recordFailure: deps.recordFailure,
                concurrency,
                syncedAt: now.toISOString(),
                signal: deadline.signal,
                canStart: () => deadline.canStart(20_000)
              });
              const incomplete = counts.deferred > 0 || counts.partial > 0 || counts.failed > 0;
              return { status: incomplete ? "partial" : "completed", ...counts };
            } finally {
              deadline.dispose();
            }
          }
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export function createTourCongestionSyncHandler(dependencies = {}) {
  const deps = { ...congestionDefaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }
        const result = await deps.withLease({
          jobId: "tour_congestion",
          run: async () => {
            const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
            try {
              const now = deps.now();
              return deps.runSync({
                fetchPage: deps.fetchPage,
                listPlaces: deps.listPlaces,
                saveRows: deps.saveRows,
                pageLimit: Math.min(integerEnv("TOUR_CONGESTION_PAGE_LIMIT", 20), 20),
                fetchedAt: now.toISOString(),
                signal: deadline.signal,
                canStart: () => deadline.canStart(10_000)
              });
            } finally {
              deadline.dispose();
            }
          }
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export function createTourAccessibilitySyncHandler(dependencies = {}) {
  const deps = { ...accessibilityDefaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }
        const result = await deps.withLease({
          jobId: "tour_accessibility",
          run: async () => {
            const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
            try {
              const now = deps.now();
              const state = await deps.getState("tour_accessibility", {
                signal: deadline.signal
              });
              const page = Math.max(Number.parseInt(state.next_page, 10) || 1, 1);
              const pageSize = Math.min(
                Math.max(integerEnv("TOUR_ACCESSIBILITY_SYNC_BATCH_SIZE", 20), 1),
                40
              );
              const source = await deps.fetchPage(page, pageSize, {
                signal: deadline.signal
              });
              const counts = await deps.runBatch({
                items: source.items,
                fetchDetail: deps.fetchDetail,
                saveAccessibility: deps.saveAccessibility,
                concurrency: Math.min(integerEnv("TOUR_SYNC_CONCURRENCY", 4), 4),
                syncedAt: now.toISOString(),
                signal: deadline.signal,
                canStart: () => deadline.canStart(10_000)
              });
              const totalPages = Math.max(
                Math.ceil(source.totalCount / Math.max(source.numOfRows, 1)),
                1
              );
              const pageCompleted = counts.deferred === 0;
              const cycleCompleted = pageCompleted && (
                page >= totalPages || source.rawCount === 0
              );
              const nextPage = pageCompleted
                ? cycleCompleted ? 1 : page + 1
                : page;
              await deps.saveState({
                ...state,
                id: "tour_accessibility",
                next_page: nextPage,
                total_count: source.totalCount,
                last_processed_page: page,
                last_item_count: source.rawCount,
                last_error: null,
                last_completed_at: cycleCompleted && counts.failed === 0
                  ? now.toISOString()
                  : state.last_completed_at
              }, { signal: deadline.signal });
              const incomplete = counts.deferred > 0 || counts.failed > 0;
              return {
                status: incomplete ? "partial" : cycleCompleted ? "completed" : "partial",
                page,
                totalCount: source.totalCount,
                nextPage,
                ...counts
              };
            } finally {
              deadline.dispose();
            }
          }
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export function createPublicParkingSyncHandler(dependencies = {}) {
  const deps = { ...parkingDefaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        if (request.headers.get("authorization") !== `Bearer ${requiredEnv("CRON_SECRET")}`) {
          return unauthorized();
        }
        const result = await deps.withLease({
          jobId: "public_parking",
          run: async () => {
            const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON);
            try {
              const now = deps.now();
              const state = await deps.getState("public_parking", { signal: deadline.signal });
              const page = Math.max(Number.parseInt(state.next_page, 10) || 1, 1);
              const source = await deps.fetchPage(page, 100, { signal: deadline.signal });
              await deps.upsert(source.rows, { signal: deadline.signal });
              const totalPages = Math.max(
                Math.ceil(source.totalCount / Math.max(source.numOfRows, 1)),
                1
              );
              const completed = page >= totalPages || source.rawCount === 0;
              const nextPage = completed ? 1 : page + 1;
              await deps.saveState({
                ...state,
                id: "public_parking",
                next_page: nextPage,
                total_count: source.totalCount,
                last_processed_page: page,
                last_item_count: source.rawCount,
                last_error: null,
                last_completed_at: completed ? now.toISOString() : state.last_completed_at
              }, { signal: deadline.signal });
              return {
                status: completed ? "completed" : "partial",
                page,
                totalCount: source.totalCount,
                savedLots: source.rows.length,
                nextPage
              };
            } finally {
              deadline.dispose();
            }
          }
        });
        return json(result);
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export function createTourEnrichmentSyncHandler({
  introHandler = createTourIntroSyncHandler(),
  presentationHandler = createTourPresentationSyncHandler(),
  congestionHandler = createTourCongestionSyncHandler(),
  accessibilityHandler = createTourAccessibilitySyncHandler(),
  parkingHandler = createPublicParkingSyncHandler()
} = {}) {
  return {
    fetch(request) {
      const stage = new URL(request.url).searchParams.get("stage");
      if (stage === "presentation") return presentationHandler.fetch(request);
      if (stage === "congestion") return congestionHandler.fetch(request);
      if (stage === "accessibility") return accessibilityHandler.fetch(request);
      if (stage === "parking") return parkingHandler.fetch(request);
      return introHandler.fetch(request);
    }
  };
}

export default createTourEnrichmentSyncHandler();
