import { badRequest, json, methodNotAllowed, serverError } from "../lib/http.js";
import { searchKakaoPlaces } from "../lib/kakao-local.js";

function optionalCoordinate(searchParams, name, min, max) {
  const raw = searchParams.get(name);
  if (raw === null || raw === "") return undefined;
  const value = Number.parseFloat(raw);
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new Error(`${name} 좌표가 올바르지 않습니다.`);
  }
  return value;
}

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);

    try {
      const url = new URL(request.url);
      const query = url.searchParams.get("q")?.trim() ?? "";
      if (query.length < 2 || query.length > 100) {
        return badRequest("검색어는 2~100자로 입력해주세요.");
      }
      const latitude = optionalCoordinate(
        url.searchParams,
        "latitude",
        -90,
        90
      );
      const longitude = optionalCoordinate(
        url.searchParams,
        "longitude",
        -180,
        180
      );
      if ((latitude === undefined) !== (longitude === undefined)) {
        return badRequest("latitude와 longitude는 함께 입력해야 합니다.");
      }

      const places = await searchKakaoPlaces(query, { latitude, longitude });
      return json({
        data: places,
        meta: {
          query,
          resultCount: places.length,
          provider: "KAKAO_LOCAL"
        }
      });
    } catch (error) {
      if (
        error instanceof Error &&
        (error.message.startsWith("latitude") ||
          error.message.startsWith("longitude"))
      ) {
        return badRequest(error.message);
      }
      return serverError(error);
    }
  }
};
