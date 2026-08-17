import test from "node:test";
import assert from "node:assert/strict";
import {
  fetchKakaoRoute,
  fetchKakaoRoutes,
  parseKakaoRoute
} from "../lib/kakao-mobility.js";

const start = { latitude: 37.7519, longitude: 128.8761 };
const destination = { latitude: 37.7644, longitude: 128.8996 };
const place = {
  content_id: "100",
  latitude: 37.758,
  longitude: 128.887
};

const successPayload = {
  routes: [
    {
      result_code: 0,
      summary: { distance: 9400, duration: 1260 },
      sections: [
        { distance: 4200, duration: 601 },
        { distance: 5200, duration: 659 }
      ]
    }
  ]
};

test("카카오 응답을 구간별 분 단위 경로로 변환한다", () => {
  const route = parseKakaoRoute(successPayload, start, destination, place);

  assert.equal(route.firstLegMinutes, 11);
  assert.equal(route.secondLegMinutes, 11);
  assert.equal(route.totalDistanceMeters, 9400);
  assert.equal(route.provider, "KAKAO_MOBILITY");
});

test("자동차 길찾기 요청에 경유지와 REST API 키를 적용한다", async () => {
  let request;
  const route = await fetchKakaoRoute(start, destination, place, {
    apiKey: "test-key",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return {
        ok: true,
        async json() {
          return successPayload;
        }
      };
    }
  });

  const url = new URL(request.url);
  assert.equal(url.searchParams.get("waypoints"), "128.887,37.758");
  assert.equal(url.searchParams.get("priority"), "TIME");
  assert.equal(request.options.headers.authorization, "KakaoAK test-key");
  assert.equal(route.provider, "KAKAO_MOBILITY");
});

test("후보별 실패는 제외하고 성공한 경로만 반환한다", async () => {
  const places = [
    place,
    { ...place, content_id: "200", longitude: 128.888 }
  ];
  const result = await fetchKakaoRoutes(start, destination, places, {
    concurrency: 2,
    apiKey: "test-key",
    fetchImpl: async (url) => {
      if (new URL(url).searchParams.get("waypoints").startsWith("128.888")) {
        return { ok: false, status: 403 };
      }
      return {
        ok: true,
        async json() {
          return successPayload;
        }
      };
    }
  });

  assert.equal(result.routes.size, 1);
  assert.equal(result.routes.get("100").provider, "KAKAO_MOBILITY");
  assert.equal(result.failedCount, 1);
});
