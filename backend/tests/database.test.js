import test from "node:test";
import assert from "node:assert/strict";
import {
  claimSyncJob,
  finishSyncJob,
  getGate1bOpsStatus,
  getPlace,
  listNearbyPublicParkingLots,
  listGangneungCurationCandidates,
  listPlaces,
  recordProviderUsageResult,
  reserveProviderUsage,
  savePlaceAccessibility,
  upsertPlaceCurations
} from "../lib/database.js";

test("장소 반경 1km 안의 공영주차장을 직선거리순 최대 3곳으로 제한한다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let requestUrl;
  globalThis.fetch = async (url) => {
    requestUrl = new URL(String(url));
    return Response.json([
      { source_id: "far", name: "경계 밖", latitude: 37.82, longitude: 128.9 },
      { source_id: "second", name: "두 번째", latitude: 37.802, longitude: 128.9 },
      { source_id: "first", name: "첫 번째", latitude: 37.801, longitude: 128.9 }
    ]);
  };

  try {
    const rows = await listNearbyPublicParkingLots({ latitude: 37.8, longitude: 128.9 });
    assert.match(requestUrl.pathname, /\/public_parking_lots$/);
    assert.deepEqual(rows.map((row) => row.source_id), ["first", "second"]);
    assert.ok(rows.every((row) => row.distance_meters <= 1_000));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("장소 좌표가 없으면 주차장 DB를 조회하지 않는다", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => assert.fail("좌표가 없으면 DB 조회를 하지 않습니다.");
  try {
    assert.deepEqual(await listNearbyPublicParkingLots({ latitude: null, longitude: null }), []);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("장소 단건 조회만 반복 상세와 사진 권리 메타데이터를 공개하고 원문은 숨긴다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let requestUrl;
  globalThis.fetch = async (url) => {
    requestUrl = new URL(String(url));
    return Response.json([{
      content_id: "detail-1",
      source: "TOUR_API",
      name: "상세 장소",
      category: "CULTURE",
      content_type_id: 14,
      area_code: 32,
      latitude: 37.75,
      longitude: 128.87,
      default_stay_minutes: 60,
      image_urls: ["https://example.com/place.jpg"],
      tags: [],
      enrichment_raw: {
        info: [{ infoname: "원문" }],
        infoItems: [{ title: "이용 안내", description: "예약 불필요" }],
        accessibility: { wheelchair: "원문" },
        accessibilityItems: [{ title: "휠체어", description: "대여 가능" }],
        imageAttributions: [{
          image_url: "https://example.com/place.jpg",
          thumbnail_url: null,
          name: "장소 전경",
          copyright_type: "Type1",
          copyright_label: "공공누리 제1유형"
        }]
      }
    }]);
  };

  try {
    const place = await getPlace("detail-1");
    assert.match(requestUrl.searchParams.get("select"), /enrichment_raw/);
    assert.deepEqual(place.detail_items, [
      { title: "이용 안내", description: "예약 불필요" }
    ]);
    assert.deepEqual(place.accessibility_items, [
      { title: "휠체어", description: "대여 가능" }
    ]);
    assert.equal(place.image_attributions[0].copyright_label, "공공누리 제1유형");
    assert.equal("enrichment_raw" in place, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("무장애 상세 저장은 원자 병합 RPC에 정규화 값만 보낸다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json(true);
  };

  try {
    const matched = await savePlaceAccessibility("123", {
      raw: { wheelchair: "가능" },
      items: [{ title: "휠체어", description: "가능" }],
      syncedAt: "2026-09-07T00:00:00.000Z"
    });
    assert.equal(matched, true);
    assert.match(request.url, /\/rest\/v1\/rpc\/save_place_accessibility$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_content_id: "123",
      p_accessibility_raw: { wheelchair: "가능" },
      p_accessibility_items: [{ title: "휠체어", description: "가능" }],
      p_synced_at: "2026-09-07T00:00:00.000Z"
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("새 Supabase secret 키는 PostgREST Bearer 토큰으로 보내지 않는다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "sb_secret_server-only-key";
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json([]);
  };
  try {
    assert.deepEqual(await listPlaces(), []);
    assert.equal(request.init.headers.apikey, "sb_secret_server-only-key");
    assert.equal("authorization" in request.init.headers, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("정규화된 TourAPI 상세 컬럼을 공개하고 raw는 숨긴다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () =>
    Response.json([
      {
        content_id: "123",
        source: "TOUR_API",
        name: "테스트 카페",
        category: "CAFE",
        content_type_id: 39,
        area_code: 32,
        latitude: 37.75,
        longitude: 128.87,
        default_stay_minutes: 40,
        cat1: "A05",
        cat2: "A0502",
        cat3: "A05020900",
        opening_hours: "09:00~18:00",
        closed_days: "매주 월요일",
        image_urls: ["https://example.com/1.jpg"],
        tags: ["주차 가능"],
        raw: { _tteumsae: { openingHours: "00:00~01:00" } }
      }
    ]);
  try {
    const [place] = await listPlaces({ limit: 1 });
    assert.equal(place.opening_hours, "09:00~18:00");
    assert.equal(place.cat3, "A05020900");
    assert.equal("raw" in place, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("상세 컬럼 마이그레이션 전에는 레거시 장소 조회로 폴백한다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url) => {
    requests.push(String(url));
    if (requests.length === 1) {
      return Response.json(
        { code: "42703", message: "column places.cat1 does not exist" },
        { status: 400 }
      );
    }
    return Response.json([
      {
        content_id: "legacy-123",
        source: "TOUR_API",
        name: "기존 장소",
        category: "RESTAURANT",
        content_type_id: 39,
        area_code: 32,
        latitude: 37.75,
        longitude: 128.87,
        default_stay_minutes: 40,
        raw: {
          _tteumsae: {
            openingHours: "10:00~20:00",
            imageUrls: ["https://example.com/legacy.jpg"],
            tags: ["주차 가능"]
          }
        }
      }
    ]);
  };
  try {
    const [place] = await listPlaces({ limit: 1 });
    assert.equal(requests.length, 2);
    assert.match(requests[0], /cat1/);
    assert.match(requests[1], /raw/);
    assert.equal(place.opening_hours, "10:00~20:00");
    assert.deepEqual(place.image_urls, ["https://example.com/legacy.jpg"]);
    assert.deepEqual(place.tags, ["주차 가능"]);
    assert.equal("raw" in place, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("동기화 작업 claim은 service-role RPC에 원자 lease 인자를 보낸다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json(true);
  };
  try {
    const claimed = await claimSyncJob({
      jobId: "tour_intro",
      token: "run-1",
      now: "2026-08-28T00:00:00.000Z",
      leaseSeconds: 90
    });
    assert.match(request.url, /\/rest\/v1\/rpc\/claim_sync_job$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_id: "tour_intro",
      p_token: "run-1",
      p_now: "2026-08-28T00:00:00.000Z",
      p_lease_seconds: 90
    });
    assert.ok(request.init.signal instanceof AbortSignal);
    assert.equal(claimed, true);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("동기화 작업 finish는 소유 token과 결과 요약을 보낸다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json(false);
  };
  try {
    const finished = await finishSyncJob({
      jobId: "tour_intro",
      token: "run-1",
      status: "partial",
      summary: { processed: 4, failed: 1 },
      finishedAt: "2026-08-28T00:00:30.000Z"
    });
    assert.match(request.url, /\/rest\/v1\/rpc\/finish_sync_job$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_id: "tour_intro",
      p_token: "run-1",
      p_status: "partial",
      p_summary: { processed: 4, failed: 1 },
      p_finished_at: "2026-08-28T00:00:30.000Z"
    });
    assert.ok(request.init.signal instanceof AbortSignal);
    assert.equal(finished, false);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("검수 overlay의 유효 운영시간과 입장 마감을 원본보다 우선한다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json([{
    content_id: "curated-1",
    source: "TOUR_API",
    name: "검수 장소",
    category: "CULTURE",
    content_type_id: 14,
    area_code: 32,
    sigungu_code: 1,
    latitude: 37.75,
    longitude: 128.87,
    default_stay_minutes: 60,
    opening_hours: "00:00~01:00",
    closed_days: null,
    effective_opening_hours: "09:00~18:00",
    effective_closed_days: "매주 월요일",
    effective_last_admission: "17:30",
    effective_parking_info: "건물 주차장 이용",
    data_provenance: "CURATION",
    image_urls: [],
    tags: []
  }]);
  try {
    const [place] = await listPlaces({ limit: 1 });
    assert.equal(place.opening_hours, "09:00~18:00");
    assert.equal(place.closed_days, "매주 월요일");
    assert.equal(place.last_admission, "17:30");
    assert.equal(place.parking_info, "건물 주차장 이용");
    assert.equal(place.data_provenance, "CURATION");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("공급자 사용량 예약 RPC 결과를 서버 필드명으로 정규화한다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json([{
      allowed: true,
      reserved_count: 7_000,
      remaining_count: 1_000
    }]);
  };
  try {
    const reservation = await reserveProviderUsage({
      provider: "KAKAO_MOBILITY",
      operation: "DIRECTIONS",
      usageDate: "2026-08-28",
      budgetLimit: 8_000
    });
    assert.match(request.url, /\/rpc\/reserve_provider_usage$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_provider: "KAKAO_MOBILITY",
      p_operation: "DIRECTIONS",
      p_usage_date: "2026-08-28",
      p_budget_limit: 8_000,
      p_units: 1
    });
    assert.deepEqual(reservation, {
      allowed: true,
      reservedCount: 7_000,
      remainingCount: 1_000
    });
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("공급자 결과와 운영 상태는 대응 RPC 인자를 그대로 보낸다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const requests = [];
  globalThis.fetch = async (url, init) => {
    requests.push({ url: String(url), body: JSON.parse(init.body) });
    if (String(url).endsWith("record_provider_usage_result")) {
      return new Response(null, { status: 204 });
    }
    return Response.json({ usage: [], syncJobs: [], dataQuality: {} });
  };
  try {
    await recordProviderUsageResult({
      provider: "KAKAO_LOCAL",
      operation: "REGION",
      usageDate: "2026-08-28",
      resultKind: "success"
    });
    const status = await getGate1bOpsStatus({ usageDate: "2026-08-28" });

    assert.deepEqual(requests[0].body, {
      p_provider: "KAKAO_LOCAL",
      p_operation: "REGION",
      p_usage_date: "2026-08-28",
      p_result_kind: "success",
      p_units: 1
    });
    assert.deepEqual(requests[1].body, {
      p_usage_date: "2026-08-28",
      p_sigungu_code: 1,
      p_curation_target: 100
    });
    assert.deepEqual(status, { usage: [], syncJobs: [], dataQuality: {} });
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("강릉 검수 후보를 effective view에서 읽고 검수값을 50개씩 upsert한다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const requests = [];
  globalThis.fetch = async (url, init) => {
    requests.push({ url: String(url), init });
    if ((init?.method ?? "GET") === "GET") {
      return Response.json([{ content_id: "1", name: "후보", category: "CULTURE" }]);
    }
    return new Response(null, { status: 204 });
  };
  try {
    const candidates = await listGangneungCurationCandidates();
    await upsertPlaceCurations(
      Array.from({ length: 51 }, (_, index) => ({
        content_id: String(index + 1),
        operating_info_status: "UNKNOWN"
      }))
    );

    assert.equal(candidates.length, 1);
    assert.match(requests[0].url, /effective_places\?/);
    assert.match(requests[0].url, /sigungu_code=eq\.1/);
    assert.equal(requests.length, 3);
    assert.match(requests[1].url, /place_curations\?on_conflict=content_id$/);
    assert.equal(JSON.parse(requests[1].init.body).length, 50);
    assert.equal(JSON.parse(requests[2].init.body).length, 1);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("검수 view가 아직 배포되지 않았으면 places에서 강릉 후보를 읽는다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const requests = [];
  globalThis.fetch = async (url) => {
    requests.push(String(url));
    if (String(url).includes("/effective_places?")) {
      return Response.json({
        code: "PGRST205",
        message: "Could not find the table 'public.effective_places' in the schema cache"
      }, { status: 404 });
    }
    return Response.json([{ content_id: "legacy-1", name: "기존 장소", category: "CULTURE" }]);
  };
  try {
    const candidates = await listGangneungCurationCandidates();

    assert.deepEqual(candidates, [{
      content_id: "legacy-1",
      name: "기존 장소",
      category: "CULTURE"
    }]);
    assert.equal(requests.length, 2);
    assert.match(requests[0], /effective_places\?/);
    assert.match(requests[1], /places\?/);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});
