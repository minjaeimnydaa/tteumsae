import { badRequest, json, methodNotAllowed, serverError } from "../lib/http.js";
import { lookupKakaoRegion } from "../lib/kakao-local.js";

function coordinate(searchParams, name, min, max) {
  const value = Number.parseFloat(searchParams.get(name) ?? "");
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new Error(`${name} 좌표가 올바르지 않습니다.`);
  }
  return value;
}

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);

    try {
      const parameters = new URL(request.url).searchParams;
      const latitude = coordinate(parameters, "latitude", -90, 90);
      const longitude = coordinate(parameters, "longitude", -180, 180);
      const region = await lookupKakaoRegion(latitude, longitude);
      if (!region) return badRequest("좌표의 행정구역을 찾지 못했습니다.");
      return json({ data: region });
    } catch (error) {
      if (error instanceof Error && error.message.endsWith("좌표가 올바르지 않습니다.")) {
        return badRequest(error.message);
      }
      return serverError(error);
    }
  }
};
