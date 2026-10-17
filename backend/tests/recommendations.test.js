import test from "node:test";
import assert from "node:assert/strict";
import recommendationsApi from "../api/recommendations.js";

const start = { latitude: 37.75, longitude: 128.87 };
const destination = { latitude: 37.75, longitude: 128.9 };

function providerUsageResponse(url) {
  if (url.pathname.endsWith("/rpc/reserve_provider_usage")) {
    return Response.json([{
      allowed: true,
      reserved_count: 1,
      remaining_count: 7_999
    }]);
  }
  if (url.pathname.endsWith("/rpc/record_provider_usage_result")) {
    return new Response(null, { status: 204 });
  }
  return null;
}

test("순수 여유시간으로 자동차 추천의 유효 마감과 corridor를 계산한다", async () => {
  const originalFetch = globalThis.fetch;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify([
            {
              content_id: "100",
              source: "TOUR_API",
              name: "경로 옆 장소",
              category: "ATTRACTION",
              latitude: 37.7505,
              longitude: 128.885,
              default_stay_minutes: 20,
              raw: {}
            },
            {
              content_id: "200",
              source: "TOUR_API",
              name: "바운드 모서리 장소",
              category: "ATTRACTION",
              latitude: 37.762,
              longitude: 128.915,
              default_stay_minutes: 20,
              raw: {}
            }
          ]);
        }
      };
    }

    const waypointCount = parsed.searchParams.get("waypoints")?.split("|").length ?? 0;
    return {
      ok: true,
      async json() {
        return {
          routes: [{
            result_code: 0,
            summary: {
              distance: waypointCount ? 3500 : 3000,
              duration: waypointCount ? 720 : 600,
              fare: { toll: 0 }
            },
            sections: Array.from({ length: waypointCount + 1 }, () => ({
              distance: waypointCount ? 1750 : 3000,
              duration: waypointCount ? 360 : 600,
              roads: [{ vertexes: [128.87, 37.75, 128.9, 37.75] }]
            }))
          }]
        };
      }
    };
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          extraTimeMinutes: 45,
          safetyBufferMinutes: 15,
          transport: "CAR",
          categories: []
        })
      }
    ));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.meta.baseRoute.durationMinutes, 10);
    assert.equal(body.baseRoute.totalDrivingMinutes, 10);
    assert.equal(body.meta.baseRouteMinutes, 10);
    assert.equal(body.meta.extraTimeMinutes, 45);
    assert.equal(body.meta.effectiveDeadlineMinutes, 55);
    assert.equal(body.meta.safetyBufferMinutes, 15);
    assert.equal(body.meta.corridorRadiusMeters, 900);
    assert.equal(body.meta.candidateCount, 2);
    assert.equal(body.meta.corridorCandidateCount, 1);
    assert.equal(body.data.length, 1);
    assert.equal(body.data[0].route.directMinutes, 10);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("V1 추천은 절대 마감 기준 최대 체류와 계산 시각 메타를 반환한다", async () => {
  const originalFetch = globalThis.fetch;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  const beforeRequest = Date.now();
  const arrivalDeadlineEpochMillis = beforeRequest + 60 * 60_000 + 30_000;

  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return Response.json([{
        content_id: "v1-100",
        source: "TOUR_API",
        name: "도착 마감 후보",
        category: "ATTRACTION",
        latitude: 37.7505,
        longitude: 128.885,
        default_stay_minutes: 30,
        raw: {}
      }]);
    }

    const waypointCount = parsed.searchParams.get("waypoints")?.split("|").length ?? 0;
    return Response.json({
      routes: [{
        result_code: 0,
        summary: {
          distance: waypointCount ? 3_500 : 3_000,
          duration: waypointCount ? 720 : 600,
          fare: { toll: 0 }
        },
        sections: Array.from({ length: waypointCount + 1 }, () => ({
          distance: waypointCount ? 1_750 : 3_000,
          duration: waypointCount ? 360 : 600,
          roads: [{ vertexes: [128.87, 37.75, 128.9, 37.75] }]
        }))
      }]
    });
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-forwarded-for": "203.0.113.101"
        },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          arrivalDeadlineEpochMillis,
          timeModel: "ARRIVAL_DEADLINE_V1",
          transport: "CAR",
          categories: []
        })
      }
    ));
    const afterRequest = Date.now();
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(body.data.length, 1);
    assert.equal(body.data[0].minimumStayMinutes, 15);
    assert.equal(body.data[0].maximumStayMinutes, 35);
    assert.equal(
      body.data[0].latestDepartureEpochMillis,
      arrivalDeadlineEpochMillis - 16 * 60_000
    );
    assert.equal(Object.hasOwn(body.data[0], "stayMinutes"), false);
    assert.equal(body.meta.timeModel, "ARRIVAL_DEADLINE_V1");
    assert.equal(body.meta.arrivalDeadlineEpochMillis, arrivalDeadlineEpochMillis);
    assert.equal(body.meta.safetyBufferMinutes, 10);
    assert.equal(body.meta.minimumStayMinutes, 15);
    assert.equal(Object.hasOwn(body.meta, "extraTimeMinutes"), false);
    assert.ok(body.meta.calculatedAtEpochMillis >= beforeRequest);
    assert.ok(body.meta.calculatedAtEpochMillis <= afterRequest);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("자동차 추천은 Kakao 후보 경로를 최대 8개만 요청한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalRouteLimit = process.env.KAKAO_ROUTE_CANDIDATE_LIMIT;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  process.env.KAKAO_ROUTE_CANDIDATE_LIMIT = "999";
  const candidateRouteRequests = [];
  const seenSignals = [];
  const candidates = Array.from({ length: 12 }, (_, index) => ({
    content_id: String(100 + index),
    source: "TOUR_API",
    name: `후보 ${index}`,
    category: "ATTRACTION",
    latitude: 37.75,
    longitude: 128.875 + index * 0.001,
    default_stay_minutes: 20,
    raw: {}
  }));

  globalThis.fetch = async (url, options = {}) => {
    seenSignals.push(options.signal);
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify(candidates);
        }
      };
    }

    const waypoints = parsed.searchParams.get("waypoints");
    if (waypoints) candidateRouteRequests.push(parsed);
    const waypointCount = waypoints?.split("|").length ?? 0;
    return {
      ok: true,
      status: 200,
      async json() {
        return {
          routes: [{
            result_code: 0,
            summary: {
              distance: waypointCount ? 3500 : 3000,
              duration: waypointCount ? 720 : 600,
              fare: { toll: 0 }
            },
            sections: Array.from({ length: waypointCount + 1 }, () => ({
              distance: waypointCount ? 1750 : 3000,
              duration: waypointCount ? 360 : 600,
              roads: [{ vertexes: [128.87, 37.75, 128.9, 37.75] }]
            }))
          }]
        };
      }
    };
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          extraTimeMinutes: 90,
          safetyBufferMinutes: 10,
          transport: "CAR",
          categories: []
        })
      }
    ));
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(candidateRouteRequests.length, 8);
    assert.equal(payload.meta.routeCandidateCount, 8);
    assert.ok(seenSignals.every((signal) => signal instanceof AbortSignal));
  } finally {
    globalThis.fetch = originalFetch;
    if (originalRouteLimit === undefined) {
      delete process.env.KAKAO_ROUTE_CANDIDATE_LIMIT;
    } else {
      process.env.KAKAO_ROUTE_CANDIDATE_LIMIT = originalRouteLimit;
    }
  }
});

test("고비용 추천 요청을 IP별 분당 12회로 제한한다", async () => {
  const headers = { "x-forwarded-for": "203.0.113.42" };
  for (let count = 0; count < 12; count += 1) {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      { method: "POST", headers }
    ));
    assert.equal(response.status, 400);
  }

  const limited = await recommendationsApi.fetch(new Request(
    "https://example.test/api/recommendations",
    { method: "POST", headers }
  ));
  assert.equal(limited.status, 429);
  assert.equal((await limited.json()).error.code, "RATE_LIMITED");
  assert.ok(Number(limited.headers.get("retry-after")) >= 1);
});

test("근처 자동차 탐색은 출발지와 같은 목적지의 기본 경로를 요청하지 않는다", async () => {
  const originalFetch = globalThis.fetch;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  let directRouteRequestCount = 0;
  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify([{
            content_id: "100",
            source: "TOUR_API",
            name: "근처 장소",
            category: "ATTRACTION",
            latitude: 37.751,
            longitude: 128.871,
            default_stay_minutes: 20,
            raw: {}
          }]);
        }
      };
    }

    const waypointCount = parsed.searchParams.get("waypoints")?.split("|").length ?? 0;
    if (waypointCount === 0) directRouteRequestCount += 1;
    return {
      ok: true,
      async json() {
        return {
          routes: [{
            result_code: 0,
            summary: { distance: 2000, duration: 600, fare: { toll: 0 } },
            sections: Array.from({ length: waypointCount + 1 }, () => ({
              distance: 1000,
              duration: 300
            }))
          }]
        };
      }
    };
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "NEARBY",
          start,
          destination: start,
          deadlineMinutes: 90,
          safetyBufferMinutes: 15,
          transport: "CAR",
          categories: []
        })
      }
    ));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(directRouteRequestCount, 0);
    assert.equal(body.baseRoute, undefined);
    assert.equal(body.data.length, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("날짜가 유효한 축제만 Kakao 후보 경로를 요청한다", async () => {
  const originalFetch = globalThis.fetch;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  const candidateRouteRequests = [];
  const festivals = [
    {
      content_id: "active",
      source: "TOUR_API",
      name: "진행 축제",
      category: "FESTIVAL",
      content_type_id: 15,
      event_start_date: "2000-01-01",
      event_end_date: "2999-12-31",
      latitude: 37.75,
      longitude: 128.88,
      default_stay_minutes: 20
    },
    {
      content_id: "past",
      source: "TOUR_API",
      name: "지난 축제",
      category: "FESTIVAL",
      content_type_id: 15,
      event_start_date: "2000-01-01",
      event_end_date: "2000-01-02",
      latitude: 37.75,
      longitude: 128.881,
      default_stay_minutes: 20
    },
    {
      content_id: "future",
      source: "TOUR_API",
      name: "미래 축제",
      category: "FESTIVAL",
      content_type_id: 15,
      event_start_date: "2999-01-01",
      event_end_date: "2999-12-31",
      latitude: 37.75,
      longitude: 128.882,
      default_stay_minutes: 20
    },
    {
      content_id: "incomplete",
      source: "TOUR_API",
      name: "날짜 누락 축제",
      category: "FESTIVAL",
      content_type_id: 15,
      event_start_date: null,
      event_end_date: "2999-12-31",
      latitude: 37.75,
      longitude: 128.883,
      default_stay_minutes: 20
    }
  ];

  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") return Response.json(festivals);

    const waypoints = parsed.searchParams.get("waypoints");
    if (waypoints) candidateRouteRequests.push(waypoints);
    const waypointCount = waypoints?.split("|").length ?? 0;
    return Response.json({
      routes: [{
        result_code: 0,
        summary: {
          distance: waypointCount ? 3_500 : 3_000,
          duration: waypointCount ? 720 : 600,
          fare: { toll: 0 }
        },
        sections: Array.from({ length: waypointCount + 1 }, () => ({
          distance: waypointCount ? 1_750 : 3_000,
          duration: waypointCount ? 360 : 600,
          roads: [{ vertexes: [128.87, 37.75, 128.9, 37.75] }]
        }))
      }]
    });
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          extraTimeMinutes: 90,
          safetyBufferMinutes: 10,
          transport: "CAR",
          categories: []
        })
      }
    ));
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(body.meta.corridorCandidateCount, 4);
    assert.equal(body.meta.routeCandidateCount, 1);
    assert.equal(candidateRouteRequests.length, 1);
    assert.match(candidateRouteRequests[0], /^128\.88,37\.75$/);
    assert.deepEqual(body.data.map((item) => item.place.content_id), ["active"]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("활성화된 도보 추천은 카카오 실제 경로와 경로선을 사용한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalEnabled = process.env.KAKAO_WALK_ROUTE_ENABLED;
  process.env.KAKAO_WALK_ROUTE_ENABLED = "true";
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  const walkRequests = [];

  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return Response.json([{
        content_id: "walk-1",
        source: "TOUR_API",
        name: "도보 후보",
        category: "ATTRACTION",
        latitude: 37.751,
        longitude: 128.88,
        default_stay_minutes: 20
      }]);
    }
    if (parsed.pathname === "/v2/routing/walk") {
      walkRequests.push(parsed);
      const hasWaypoint = parsed.searchParams.has("via_x");
      return Response.json({
        status: "OK",
        route: {
          properties: {
            totalDistance: hasWaypoint ? 1_200 : 1_000,
            totalTime: hasWaypoint ? 720 : 600
          },
          legs: Array.from({ length: hasWaypoint ? 2 : 1 }, (_, index) => ({
            properties: {
              distance: hasWaypoint ? 600 : 1_000,
              time: hasWaypoint ? 360 : 600
            },
            steps: [{
              path: {
                points: index === 0
                  ? [[128.87, 37.75], [128.88, 37.751]]
                  : [[128.88, 37.751], [128.9, 37.75]]
              }
            }]
          }))
        }
      });
    }
    throw new Error(`unexpected request ${parsed}`);
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          arrivalDeadlineEpochMillis: Date.now() + 70 * 60_000,
          timeModel: "ARRIVAL_DEADLINE_V1",
          transport: "WALK",
          categories: []
        })
      }
    ));
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(walkRequests.length, 2);
    assert.equal(body.meta.routeProvider, "KAKAO_MAP_WALK");
    assert.equal(body.meta.baseRouteMinutes, 10);
    assert.equal(body.meta.warning, undefined);
    assert.equal(body.data[0].route.provider, "KAKAO_MAP_WALK");
    assert.equal(body.data[0].route.firstLegMinutes, 6);
    assert.ok(body.data[0].route.path.length >= 3);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalEnabled === undefined) delete process.env.KAKAO_WALK_ROUTE_ENABLED;
    else process.env.KAKAO_WALK_ROUTE_ENABLED = originalEnabled;
  }
});

test("도보 경로 공급자 장애는 추천 전체 실패 대신 명시적 예상값으로 전환한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalEnabled = process.env.KAKAO_WALK_ROUTE_ENABLED;
  process.env.KAKAO_WALK_ROUTE_ENABLED = "true";
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";

  globalThis.fetch = async (url) => {
    const parsed = new URL(url);
    const usageResponse = providerUsageResponse(parsed);
    if (usageResponse) return usageResponse;
    if (parsed.hostname === "supabase.test") {
      return Response.json([{
        content_id: "walk-fallback",
        source: "TOUR_API",
        name: "예상 경로 후보",
        category: "ATTRACTION",
        latitude: 37.751,
        longitude: 128.88,
        default_stay_minutes: 20
      }]);
    }
    if (parsed.pathname === "/v2/routing/walk") {
      return Response.json({ status: "INTERNAL_ERROR" });
    }
    throw new Error(`unexpected request ${parsed}`);
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          arrivalDeadlineEpochMillis: Date.now() + 70 * 60_000,
          timeModel: "ARRIVAL_DEADLINE_V1",
          transport: "WALK",
          categories: []
        })
      }
    ));
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(body.meta.routeProvider, "ESTIMATE_FALLBACK");
    assert.match(body.meta.warning, /실제 도보 경로를 확인하지 못해/);
    assert.equal(body.data[0].route.provider, "ESTIMATE");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalEnabled === undefined) delete process.env.KAKAO_WALK_ROUTE_ENABLED;
    else process.env.KAKAO_WALK_ROUTE_ENABLED = originalEnabled;
  }
});
