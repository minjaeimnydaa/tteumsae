import test from "node:test";
import assert from "node:assert/strict";
import placeApi from "../api/places/[id].js";

test("장소 상세는 요청 시각 날짜의 매칭된 혼잡 예측만 공개한다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url) => {
    const parsed = new URL(String(url));
    requests.push(parsed);
    if (parsed.pathname.endsWith("/effective_places")) {
      return Response.json([{
        content_id: "123",
        source: "TOUR_API",
        name: "경포대",
        category: "ATTRACTION",
        content_type_id: 12,
        area_code: 32,
        sigungu_code: 1,
        latitude: 37.79,
        longitude: 128.9,
        default_stay_minutes: 60,
        image_urls: [],
        tags: [],
        enrichment_raw: {}
      }]);
    }
    if (parsed.pathname.endsWith("/tour_congestion_forecasts")) {
      return Response.json([{
        forecast_date: "2026-09-08",
        concentration_rate: 72.35,
        level: "HIGH",
        fetched_at: "2026-09-07T00:00:00.000Z"
      }]);
    }
    if (parsed.pathname.endsWith("/public_parking_lots")) {
      return Response.json([]);
    }
    throw new Error(`unexpected request ${parsed}`);
  };

  try {
    const atEpochMillis = Date.parse("2026-09-08T03:00:00.000Z");
    const response = await placeApi.fetch(new Request(
      `https://example.test/api/places/123?atEpochMillis=${atEpochMillis}`
    ));
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(requests.length, 3);
    assert.equal(
      requests[1].searchParams.get("forecast_date"),
      "eq.2026-09-08"
    );
    assert.equal(body.data.congestion_forecast.label, "혼잡 예상");
    assert.equal(body.data.congestion_forecast.concentration_rate, 72.35);
    assert.match(body.data.congestion_forecast.basis, /과거 최고 혼잡/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("혼잡 테이블 migration 전에도 기존 장소 상세는 정상 동작한다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    if (calls === 1) {
      return Response.json([{
        content_id: "123",
        source: "TOUR_API",
        name: "경포대",
        category: "ATTRACTION",
        content_type_id: 12,
        area_code: 32,
        sigungu_code: 1,
        latitude: 37.79,
        longitude: 128.9,
        default_stay_minutes: 60,
        image_urls: [],
        tags: [],
        enrichment_raw: {}
      }]);
    }
    const table = calls === 2 ? "tour_congestion_forecasts" : "public_parking_lots";
    return Response.json({
      code: "PGRST205",
      message: `Could not find ${table}`
    }, { status: 404 });
  };

  try {
    const response = await placeApi.fetch(new Request("https://example.test/api/places/123"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.data.congestion_forecast, undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("잘못된 혼잡 기준시각은 DB 조회 전에 거부한다", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => assert.fail("잘못된 입력은 DB를 조회하지 않습니다.");
  try {
    const response = await placeApi.fetch(
      new Request("https://example.test/api/places/123?atEpochMillis=invalid")
    );
    assert.equal(response.status, 400);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
