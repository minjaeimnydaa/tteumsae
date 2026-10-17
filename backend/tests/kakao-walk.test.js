import test from "node:test";
import assert from "node:assert/strict";
import {
  fetchKakaoWalkRoute,
  parseKakaoWalkRoute
} from "../lib/kakao-walk.js";

const start = { latitude: 37.75, longitude: 128.87 };
const waypoint = { latitude: 37.751, longitude: 128.88 };
const destination = { latitude: 37.752, longitude: 128.89 };

function walkPayload({ waypointCount = 1 } = {}) {
  return {
    status: "OK",
    route: {
      properties: { totalDistance: 1_200, totalTime: 900 },
      legs: Array.from({ length: waypointCount + 1 }, (_, index) => ({
        properties: { distance: 600, time: index === 0 ? 300 : 600 },
        steps: [{
          path: {
            points: index === 0
              ? [[128.87, 37.75], [128.88, 37.751]]
              : [[128.88, 37.751], [128.89, 37.752]]
          }
        }]
      }))
    }
  };
}

test("카카오 도보 응답을 기존 추천 경로 계약으로 변환한다", () => {
  const route = parseKakaoWalkRoute(walkPayload(), start, destination, [waypoint]);

  assert.equal(route.provider, "KAKAO_MAP_WALK");
  assert.equal(route.durationMinutes, 15);
  assert.equal(route.distanceMeters, 1_200);
  assert.equal(route.firstLegMinutes, 5);
  assert.equal(route.secondLegMinutes, 10);
  assert.equal(route.firstLegDistanceMeters, 600);
  assert.deepEqual(route.path[0], start);
  assert.deepEqual(route.path.at(-1), destination);
});

test("카카오 도보 요청은 WGS84 좌표·경유지·편안한 길 옵션과 별도 예산을 사용한다", async () => {
  let requestUrl;
  let requestHeaders;
  let usage;
  const route = await fetchKakaoWalkRoute(start, destination, [waypoint], {
    apiKey: "rest-key",
    usageTracker: async (input) => {
      usage = input;
      return input.call();
    },
    fetchImpl: async (url, init) => {
      requestUrl = new URL(String(url));
      requestHeaders = init.headers;
      return Response.json(walkPayload());
    }
  });

  assert.equal(requestUrl.origin, "https://dapi.kakao.com");
  assert.equal(requestUrl.pathname, "/v2/routing/walk");
  assert.equal(requestUrl.searchParams.get("start_x"), "128.87");
  assert.equal(requestUrl.searchParams.get("start_y"), "37.75");
  assert.equal(requestUrl.searchParams.get("via_x"), "128.88");
  assert.equal(requestUrl.searchParams.get("via_y"), "37.751");
  assert.equal(requestUrl.searchParams.get("end_x"), "128.89");
  assert.equal(requestUrl.searchParams.get("end_y"), "37.752");
  assert.equal(requestUrl.searchParams.get("input_coord"), "WGS84");
  assert.equal(requestUrl.searchParams.get("output_coord"), "WGS84");
  assert.equal(requestUrl.searchParams.get("route_mode"), "ACCESSIBLE");
  assert.equal(requestHeaders.authorization, "KakaoAK rest-key");
  assert.equal(usage.provider, "KAKAO_LOCAL");
  assert.equal(usage.operation, "WALK_DIRECTIONS");
  assert.equal(usage.budgetLimit, 800);
  assert.equal(route.waypointCount, 1);
});

test("카카오 도보 비정상 status는 정상 경로로 해석하지 않는다", () => {
  assert.equal(
    parseKakaoWalkRoute({ status: "TOO_FAR_AWAY" }, start, destination),
    null
  );
});
