import test from "node:test";
import assert from "node:assert/strict";
import * as tourApi from "../lib/tour-api.js";

const fixedNow = new Date("2026-08-28T00:00:00.000Z");
const untrackedUsage = async ({ call }) => call();

function restoreEnv(name, value) {
  if (value === undefined) delete process.env[name];
  else process.env[name] = value;
}

async function passthroughLease({ run }) {
  return run();
}

function activeDeadline() {
  return {
    signal: new AbortController().signal,
    canStart: () => true,
    dispose: () => {}
  };
}

test("증분 목록은 modifiedtime을 보내고 표출·비표출 항목을 모두 매핑한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalServiceKey = process.env.TOUR_API_SERVICE_KEY;
  process.env.TOUR_API_SERVICE_KEY = "service-key";
  let requestUrl;
  globalThis.fetch = async (url) => {
    requestUrl = new URL(String(url));
    return Response.json({
      response: {
        header: { resultCode: "0000", resultMsg: "OK" },
        body: {
          pageNo: 2,
          numOfRows: 50,
          totalCount: 2,
          items: {
            item: [
              {
                contentid: "active-1",
                contenttypeid: "12",
                title: "표출 관광지",
                areacode: "32",
                mapx: "128.87",
                mapy: "37.75",
                modifiedtime: "20260827123000",
                showflag: "1"
              },
              {
                contentid: "inactive-1",
                modifiedtime: "20260827124000",
                showflag: "0"
              }
            ]
          }
        }
      }
    });
  };

  try {
    const result = await tourApi.fetchTourSyncPage({
      pageNo: 2,
      numOfRows: 50,
      modifiedTime: "20260827",
      usageTracker: untrackedUsage
    });

    assert.equal(requestUrl.pathname.endsWith("/areaBasedSyncList2"), true);
    assert.equal(requestUrl.searchParams.get("modifiedtime"), "20260827");
    assert.equal(requestUrl.searchParams.get("areaCode"), "32");
    assert.equal(requestUrl.searchParams.get("lDongRegnCd"), "51");
    assert.equal(requestUrl.searchParams.get("arrange"), "C");
    assert.equal(requestUrl.searchParams.get("pageNo"), "2");
    assert.equal(requestUrl.searchParams.get("numOfRows"), "50");
    assert.deepEqual(
      result.places.map(({ content_id, is_active }) => ({ content_id, is_active })),
      [
        { content_id: "active-1", is_active: true },
        { content_id: "inactive-1", is_active: false }
      ]
    );
  } finally {
    globalThis.fetch = originalFetch;
    restoreEnv("TOUR_API_SERVICE_KEY", originalServiceKey);
  }
});

test("문서에 없는 showflag 값은 비표출로 추정하지 않는다", () => {
  const mapped = tourApi.mapTourSyncItem(
    {
      contentid: "unknown-1",
      contenttypeid: "12",
      title: "상태 미확인 관광지",
      mapx: "128.87",
      mapy: "37.75",
      showflag: "9"
    },
    "2026-08-28T00:00:00.000Z"
  );

  assert.equal(mapped, null);
});

test("카탈로그 변경은 상세 단계만 초기화하고 비표출은 기존 행만 비활성화한다", async () => {
  const { resetPlaceEnrichment, setPlaceActive } = await import("../lib/database.js");
  const originalFetch = globalThis.fetch;
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const requests = [];
  globalThis.fetch = async (url, init) => {
    requests.push({ url: String(url), body: JSON.parse(init.body) });
    return new Response(null, { status: 204 });
  };

  try {
    await resetPlaceEnrichment("active-1");
    await setPlaceActive("inactive-1", false);

    assert.match(requests[0].url, /places\?content_id=eq\.active-1$/);
    assert.deepEqual(requests[0].body, {
      intro_synced_at: null,
      common_synced_at: null,
      media_synced_at: null,
      info_synced_at: null,
      enrichment_attempts: 0,
      enrichment_last_error: null,
      next_enrichment_at: null
    });
    assert.match(requests[1].url, /places\?content_id=eq\.inactive-1$/);
    assert.deepEqual(requests[1].body, { is_active: false });
  } finally {
    globalThis.fetch = originalFetch;
    restoreEnv("SUPABASE_URL", originalUrl);
    restoreEnv("SUPABASE_SERVICE_ROLE_KEY", originalKey);
  }
});

test("카탈로그 첫 페이지는 주기 시작을 먼저 저장하고 완료 후에만 커서를 승격한다", async () => {
  const { createTourCatalogSyncHandler } = await import("../api/cron/tour-catalog-sync.js");
  const originalSecret = process.env.CRON_SECRET;
  const originalMaxPages = process.env.TOUR_SYNC_MAX_PAGES;
  process.env.CRON_SECRET = "cron-secret";
  process.env.TOUR_SYNC_MAX_PAGES = "10";
  const savedStates = [];
  const upserted = [];
  const reset = [];
  const activeChanges = [];
  let fetchOptions;

  const handler = createTourCatalogSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    now: () => new Date(fixedNow),
    getState: async (id) => {
      if (id === "tour_catalog_delta") {
        return { id, next_page: 1, source_cursor: null, cycle_started_at: null };
      }
      return { id, last_completed_at: "2026-08-27T01:00:00.000Z" };
    },
    saveState: async (state) => savedStates.push(structuredClone(state)),
    fetchPage: async (options) => {
      fetchOptions = options;
      return {
        pageNo: 1,
        numOfRows: 100,
        totalCount: 2,
        rawCount: 2,
        places: [
          { content_id: "active-1", is_active: true, name: "갱신 장소" },
          { content_id: "inactive-1", is_active: false }
        ]
      };
    },
    upsert: async (rows) => upserted.push(...rows),
    resetEnrichment: async (contentId) => reset.push(contentId),
    setActive: async (contentId, active) => activeChanges.push({ contentId, active })
  });

  try {
    const response = await handler.fetch(
      new Request("https://example.test/api/cron/tour-catalog-sync", {
        headers: { authorization: "Bearer cron-secret" }
      })
    );

    assert.equal(response.status, 200);
    assert.equal((await response.json()).status, "completed");
    assert.equal(fetchOptions.modifiedTime, "20260827");
    assert.equal(fetchOptions.pageNo, 1);
    assert.equal(savedStates[0].cycle_started_at, "2026-08-28T00:00:00.000Z");
    assert.equal(savedStates[0].source_cursor, "20260827");
    assert.deepEqual(upserted.map(({ content_id }) => content_id), ["active-1"]);
    assert.deepEqual(reset, ["active-1"]);
    assert.deepEqual(activeChanges, [{ contentId: "inactive-1", active: false }]);
    const completed = savedStates.at(-1);
    assert.equal(completed.next_page, 1);
    assert.equal(completed.source_cursor, "20260828");
    assert.equal(completed.cycle_started_at, null);
  } finally {
    restoreEnv("CRON_SECRET", originalSecret);
    restoreEnv("TOUR_SYNC_MAX_PAGES", originalMaxPages);
  }
});

test("부분 처리 재시작은 기존 주기와 커서를 유지하고 다음 페이지만 저장한다", async () => {
  const { createTourCatalogSyncHandler } = await import("../api/cron/tour-catalog-sync.js");
  const originalSecret = process.env.CRON_SECRET;
  const originalMaxPages = process.env.TOUR_SYNC_MAX_PAGES;
  process.env.CRON_SECRET = "cron-secret";
  process.env.TOUR_SYNC_MAX_PAGES = "1";
  const savedStates = [];
  let fetchOptions;
  const state = {
    id: "tour_catalog_delta",
    next_page: 2,
    source_cursor: "20260826",
    cycle_started_at: "2026-08-27T00:00:00.000Z"
  };
  const handler = createTourCatalogSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    now: () => new Date(fixedNow),
    getState: async () => state,
    saveState: async (saved) => savedStates.push(structuredClone(saved)),
    fetchPage: async (options) => {
      fetchOptions = options;
      return {
        pageNo: 2,
        numOfRows: 100,
        totalCount: 300,
        rawCount: 100,
        places: []
      };
    },
    upsert: async () => {},
    resetEnrichment: async () => {},
    setActive: async () => {}
  });

  try {
    const response = await handler.fetch(
      new Request("https://example.test/api/cron/tour-catalog-sync", {
        headers: { authorization: "Bearer cron-secret" }
      })
    );

    assert.equal((await response.json()).status, "partial");
    assert.equal(fetchOptions.modifiedTime, "20260826");
    assert.equal(fetchOptions.pageNo, 2);
    assert.equal(savedStates.length, 1);
    assert.equal(savedStates[0].next_page, 3);
    assert.equal(savedStates[0].source_cursor, "20260826");
    assert.equal(savedStates[0].cycle_started_at, "2026-08-27T00:00:00.000Z");
  } finally {
    restoreEnv("CRON_SECRET", originalSecret);
    restoreEnv("TOUR_SYNC_MAX_PAGES", originalMaxPages);
  }
});

test("초기 증분 커서는 현재 시각보다 24시간 이전부터 겹쳐 조회한다", async () => {
  const { createTourCatalogSyncHandler } = await import("../api/cron/tour-catalog-sync.js");
  const originalSecret = process.env.CRON_SECRET;
  process.env.CRON_SECRET = "cron-secret";
  let modifiedTime;
  const handler = createTourCatalogSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    now: () => new Date(fixedNow),
    getState: async (id) => ({ id, next_page: 1 }),
    saveState: async () => {},
    fetchPage: async (options) => {
      modifiedTime = options.modifiedTime;
      return { pageNo: 1, numOfRows: 100, totalCount: 0, rawCount: 0, places: [] };
    },
    upsert: async () => {},
    resetEnrichment: async () => {},
    setActive: async () => {}
  });

  try {
    await handler.fetch(
      new Request("https://example.test/api/cron/tour-catalog-sync", {
        headers: { authorization: "Bearer cron-secret" }
      })
    );
    assert.equal(modifiedTime, "20260827");
  } finally {
    restoreEnv("CRON_SECRET", originalSecret);
  }
});

test("카탈로그 Cron은 이미 실행 중이면 작업 없이 skipped를 반환한다", async () => {
  const { createTourCatalogSyncHandler } = await import("../api/cron/tour-catalog-sync.js");
  process.env.CRON_SECRET = "cron-secret";
  let fetched = false;
  const handler = createTourCatalogSyncHandler({
    withLease: async ({ jobId }) => {
      assert.equal(jobId, "tour_catalog_delta");
      return { status: "skipped", reason: "already_running" };
    },
    deadlineFactory: activeDeadline,
    fetchPage: async () => {
      fetched = true;
    }
  });

  const response = await handler.fetch(
    new Request("https://example.test/api/cron/tour-catalog-sync", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "skipped", reason: "already_running" });
  assert.equal(fetched, false);
});

test("카탈로그 Cron은 deadline 여유가 없으면 새 페이지를 시작하지 않는다", async () => {
  const { createTourCatalogSyncHandler } = await import("../api/cron/tour-catalog-sync.js");
  process.env.CRON_SECRET = "cron-secret";
  let fetched = false;
  let minimumRemaining;
  const signal = new AbortController().signal;
  const handler = createTourCatalogSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: () => ({
      signal,
      canStart: (minimum) => {
        minimumRemaining = minimum;
        return false;
      },
      dispose: () => {}
    }),
    now: () => new Date(fixedNow),
    getState: async () => ({
      id: "tour_catalog_delta",
      next_page: 2,
      source_cursor: "20260827",
      cycle_started_at: "2026-08-28T00:00:00.000Z"
    }),
    saveState: async () => {},
    fetchPage: async () => {
      fetched = true;
    }
  });

  const response = await handler.fetch(
    new Request("https://example.test/api/cron/tour-catalog-sync", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.status, "partial");
  assert.equal(body.processedPages, 0);
  assert.equal(minimumRemaining, 5_000);
  assert.equal(fetched, false);
});
