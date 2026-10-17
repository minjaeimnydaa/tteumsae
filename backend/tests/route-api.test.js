import test from "node:test";
import assert from "node:assert/strict";
import routeApi from "../api/route.js";

const start = { latitude: 37.7519, longitude: 128.8761 };
const destination = { latitude: 37.7644, longitude: 128.8996 };

test("경로 API가 카카오 요약과 기존 Android 호환 필드를 반환한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  globalThis.fetch = async (url, options) => {
    const requestUrl = String(url);
    if (requestUrl.endsWith("/rpc/reserve_provider_usage")) {
      return Response.json([{
        allowed: true,
        reserved_count: 1,
        remaining_count: 7_999
      }]);
    }
    if (requestUrl.endsWith("/rpc/record_provider_usage_result")) {
      return new Response(null, { status: 204 });
    }
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json({
        routes: [{
          result_code: 0,
          summary: { distance: 9400, duration: 1260, fare: { toll: 1800 } },
          sections: [{ distance: 9400, duration: 1260 }]
        }]
    });
  };

  try {
    const response = await routeApi.fetch(new Request("https://example.test/api/route", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ start, destination })
    }));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.data.durationMinutes, 21);
    assert.equal(body.data.distanceMeters, 9400);
    assert.equal(body.data.tollFare, 1800);
    assert.equal(body.data.totalDrivingMinutes, 21);
    assert.equal(body.data.provider, "KAKAO_MOBILITY");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("경로 API가 경유지 6개를 카카오 호출 전에 거부한다", async () => {
  const response = await routeApi.fetch(new Request("https://example.test/api/route", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      start,
      destination,
      waypoints: Array.from({ length: 6 }, () => start)
    })
  }));
  assert.equal(response.status, 400);
  assert.match((await response.json()).error.message, /최대 5개/);
});

test("경로 API는 일일 예산 거부 시 Kakao 호출 없이 503과 Retry-After를 반환한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalKakaoKey = process.env.KAKAO_REST_API_KEY;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  process.env.KAKAO_REST_API_KEY = "test-key";
  let kakaoCalls = 0;
  globalThis.fetch = async (url) => {
    if (String(url).endsWith("/rpc/reserve_provider_usage")) {
      return Response.json([{
        allowed: false,
        reserved_count: 8_000,
        remaining_count: 0
      }]);
    }
    kakaoCalls += 1;
    throw new Error("Kakao must not be called");
  };
  try {
    const response = await routeApi.fetch(new Request("https://example.test/api/route", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ start, destination })
    }));
    const body = await response.json();

    assert.equal(response.status, 503);
    assert.equal(body.error.code, "UPSTREAM_BUDGET_EXHAUSTED");
    assert.ok(Number(response.headers.get("retry-after")) > 0);
    assert.equal(kakaoCalls, 0);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
    if (originalKakaoKey === undefined) delete process.env.KAKAO_REST_API_KEY;
    else process.env.KAKAO_REST_API_KEY = originalKakaoKey;
  }
});
