import { listPlaces } from "../lib/database.js";
import { integerEnv } from "../lib/env.js";
import {
  badRequest,
  json,
  methodNotAllowed,
  readJson,
  serverError
} from "../lib/http.js";
import { fetchKakaoRoutes } from "../lib/kakao-mobility.js";
import { createSearchBounds } from "../lib/routing.js";
import {
  recommendPlaces,
  selectRouteCandidates
} from "../lib/time-safe.js";
import { parseRecommendationRequest } from "../lib/validation.js";

export default {
  async fetch(request) {
    if (request.method !== "POST") return methodNotAllowed(["POST"]);

    let criteria;
    try {
      criteria = parseRecommendationRequest(await readJson(request));
    } catch (error) {
      return badRequest(
        error instanceof Error ? error.message : "요청 값이 올바르지 않습니다."
      );
    }

    try {
      const bounds = createSearchBounds(
        criteria.start,
        criteria.destination,
        criteria.transport
      );
      const candidates = await listPlaces({ ...bounds, limit: 500 });
      let recommendations;
      let routeProvider;
      let routeCandidateCount;
      let routeFailureCount = 0;
      let warning;

      if (criteria.transport === "CAR") {
        const routeLimit = Math.min(
          integerEnv("KAKAO_ROUTE_CANDIDATE_LIMIT", 20),
          20
        );
        const routeCandidates = selectRouteCandidates(
          criteria,
          candidates,
          routeLimit
        );
        const routeResult = await fetchKakaoRoutes(
          criteria.start,
          criteria.destination,
          routeCandidates
        );
        routeCandidateCount = routeCandidates.length;
        routeFailureCount = routeResult.failedCount;
        routeProvider = "KAKAO_MOBILITY";
        recommendations = recommendPlaces(
          criteria,
          routeCandidates,
          (_start, _destination, place) =>
            routeResult.routes.get(String(place.content_id))
        ).slice(0, 20);
      } else {
        routeProvider = "ESTIMATE";
        routeCandidateCount = candidates.length;
        warning =
          "도보 이동시간은 직선거리 기반 예상값이에요. 실제 길과 신호에 따라 더 오래 걸릴 수 있으니 여유 있게 출발해 주세요.";
        recommendations = recommendPlaces(criteria, candidates).slice(0, 20);
      }

      return json({
        data: recommendations,
        meta: {
          candidateCount: candidates.length,
          routeCandidateCount,
          routeFailureCount,
          recommendationCount: recommendations.length,
          routeProvider,
          ...(warning ? { warning } : {})
        }
      });
    } catch (error) {
      return serverError(error);
    }
  }
};
