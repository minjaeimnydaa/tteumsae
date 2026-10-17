import { getSyncState, saveSyncState, upsertPlaces } from "../../lib/database.js";
import { integerEnv, requiredEnv } from "../../lib/env.js";
import {
  json,
  methodNotAllowed,
  serverError,
  unauthorized
} from "../../lib/http.js";
import { fetchTourPage } from "../../lib/tour-api.js";

function isAuthorized(request) {
  const expected = `Bearer ${requiredEnv("CRON_SECRET")}`;
  return request.headers.get("authorization") === expected;
}

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);
    try {
      if (!isAuthorized(request)) return unauthorized();

      const state = await getSyncState();
      const maxPages = Math.min(integerEnv("TOUR_SYNC_MAX_PAGES", 10), 25);
      let page = Math.max(state.next_page ?? 1, 1);
      let totalCount = state.total_count ?? 0;
      let processedPages = 0;
      let savedPlaces = 0;
      let completed = false;
      let resumePage = page;

      for (let index = 0; index < maxPages; index += 1) {
        const result = await fetchTourPage(page);
        await upsertPlaces(result.places);

        processedPages += 1;
        savedPlaces += result.places.length;
        totalCount = result.totalCount;
        const totalPages = Math.max(Math.ceil(totalCount / result.numOfRows), 1);
        completed = page >= totalPages || result.rawCount === 0;
        const nextPage = completed ? 1 : page + 1;
        resumePage = nextPage;

        await saveSyncState({
          next_page: nextPage,
          total_count: totalCount,
          last_processed_page: page,
          last_item_count: result.rawCount,
          last_error: null,
          last_completed_at: completed ? new Date().toISOString() : state.last_completed_at
        });

        if (completed) break;
        page = nextPage;
      }

      return json({
        status: completed ? "completed" : "partial",
        processedPages,
        savedPlaces,
        totalCount,
        nextPage: resumePage
      });
    } catch (error) {
      try {
        const state = await getSyncState();
        await saveSyncState({
          ...state,
          last_error: error instanceof Error ? error.message.slice(0, 500) : String(error)
        });
      } catch {
        // 원래 오류 응답을 유지한다.
      }
      return serverError(error);
    }
  }
};
