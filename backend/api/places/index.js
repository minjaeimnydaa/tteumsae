import { listPlaces } from "../../lib/database.js";
import {
  badRequest,
  json,
  methodNotAllowed,
  serverError
} from "../../lib/http.js";
import { placeCategories } from "../../lib/validation.js";

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);

    try {
      const url = new URL(request.url);
      const category = url.searchParams.get("category")?.toUpperCase();
      if (category && !placeCategories.has(category)) {
        return badRequest("지원하지 않는 장소 유형입니다.");
      }
      const page = Math.max(Number.parseInt(url.searchParams.get("page") ?? "1", 10), 1);
      const pageSize = Math.min(
        Math.max(Number.parseInt(url.searchParams.get("pageSize") ?? "30", 10), 1),
        100
      );
      const places = await listPlaces({
        category,
        limit: pageSize,
        offset: (page - 1) * pageSize
      });

      return json({
        data: places,
        pagination: {
          page,
          pageSize,
          returned: places.length,
          hasMore: places.length === pageSize
        }
      });
    } catch (error) {
      return serverError(error);
    }
  }
};

