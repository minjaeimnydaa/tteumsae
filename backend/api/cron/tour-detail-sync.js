import {
  getSyncState,
  listPlacesForEnrichment,
  saveSyncState,
  updatePlaceEnrichment
} from "../../lib/database.js";
import { integerEnv, requiredEnv } from "../../lib/env.js";
import {
  json,
  methodNotAllowed,
  serverError,
  unauthorized
} from "../../lib/http.js";
import {
  fetchPetTourDetail,
  fetchTourImages,
  fetchTourIntro,
  normalizeTourEnrichment
} from "../../lib/tour-api.js";

function isAuthorized(request) {
  return request.headers.get("authorization") ===
    `Bearer ${requiredEnv("CRON_SECRET")}`;
}

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);
    if (!isAuthorized(request)) return unauthorized();

    try {
      const state = await getSyncState("tour_details");
      const batchSize = Math.min(integerEnv("TOUR_DETAIL_SYNC_BATCH_SIZE", 10), 10);
      const page = Math.max(state.next_page ?? 1, 1);
      const places = await listPlacesForEnrichment({
        limit: batchSize,
        offset: (page - 1) * batchSize
      });
      let updated = 0;
      let failed = 0;

      for (const place of places) {
        const [introResult, imageResult, petResult] = await Promise.allSettled([
          fetchTourIntro(place.content_id, place.content_type_id),
          fetchTourImages(place.content_id),
          fetchPetTourDetail(place.content_id)
        ]);
        const allFailed = [introResult, imageResult, petResult]
          .every((result) => result.status === "rejected");
        if (allFailed) {
          failed += 1;
          continue;
        }

        const enrichment = normalizeTourEnrichment({
          contentTypeId: place.content_type_id,
          intro: introResult.status === "fulfilled" ? introResult.value : null,
          images: imageResult.status === "fulfilled" ? imageResult.value : [],
          pet: petResult.status === "fulfilled" ? petResult.value : null
        });
        await updatePlaceEnrichment(place, enrichment);
        updated += 1;
      }

      const completed = places.length < batchSize;
      await saveSyncState({
        ...state,
        id: "tour_details",
        next_page: completed ? 1 : page + 1,
        last_processed_page: page,
        last_item_count: places.length,
        last_error: failed > 0 ? `${failed} places failed` : null,
        last_completed_at: completed ? new Date().toISOString() : state.last_completed_at
      });

      return json({
        status: completed ? "completed" : "partial",
        page,
        processed: places.length,
        updated,
        failed,
        nextPage: completed ? 1 : page + 1
      });
    } catch (error) {
      return serverError(error);
    }
  }
};
