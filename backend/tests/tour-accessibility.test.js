import test from "node:test";
import assert from "node:assert/strict";
import {
  fetchTourAccessibilityDetail,
  fetchTourAccessibilityPage,
  normalizeTourAccessibility
} from "../lib/tour-accessibility.js";
import { runAccessibilityBatch } from "../lib/tour-accessibility-sync.js";
import {
  createTourAccessibilitySyncHandler,
  createTourEnrichmentSyncHandler
} from "../api/cron/tour-intro-sync.js";

const untrackedUsage = async ({ call }) => call();

function activeDeadline() {
  return {
    signal: new AbortController().signal,
    canStart: () => true,
    dispose: () => {}
  };
}

test("무장애 상세를 의미를 추측하지 않는 공개 안내 항목으로 정규화한다", () => {
  const result = normalizeTourAccessibility({
    detail: {
      parking: "장애인 전용 주차구역 있음",
      route: "주출입구까지 턱 없는 접근로",
      publictransport: "저상버스 정류장 이용 가능",
      promotion: "안내책자 있음",
      wheelchair: "휠체어 대여 가능",
      restroom: "장애인 화장실 있음",
      elevator: "없음",
      audioguide: "<b>음성 안내기</b> 대여",
      lactationroom: "수유실 있음",
      empty: ""
    },
    syncedAt: "2026-09-07T00:00:00.000Z"
  });

  assert.deepEqual(result.items, [
    { title: "장애인 주차", description: "장애인 전용 주차구역 있음" },
    { title: "접근로", description: "주출입구까지 턱 없는 접근로" },
    { title: "대중교통", description: "저상버스 정류장 이용 가능" },
    { title: "홍보물", description: "안내책자 있음" },
    { title: "휠체어", description: "휠체어 대여 가능" },
    { title: "엘리베이터", description: "없음" },
    { title: "장애인 화장실", description: "장애인 화장실 있음" },
    { title: "음성 안내", description: "음성 안내기 대여" },
    { title: "수유실", description: "수유실 있음" }
  ]);
  assert.equal(result.syncedAt, "2026-09-07T00:00:00.000Z");
  assert.equal(result.raw.wheelchair, "휠체어 대여 가능");
});

test("무장애 목록은 강원도 KorWithService2 동기화 계약을 사용한다", async () => {
  const originalKey = process.env.TOUR_API_SERVICE_KEY;
  process.env.TOUR_API_SERVICE_KEY = "service-key";
  let requestUrl;
  try {
    const result = await fetchTourAccessibilityPage(2, 20, {
      usageTracker: untrackedUsage,
      fetchImpl: async (url) => {
        requestUrl = new URL(String(url));
        return Response.json({
          response: {
            header: { resultCode: "0000", resultMsg: "OK" },
            body: {
              pageNo: 2,
              numOfRows: 20,
              totalCount: 21,
              items: { item: [
                { contentid: "123", title: "테스트 장소", showflag: "1" },
                { contentid: "hidden", title: "비표출 장소", showflag: "0" }
              ] }
            }
          }
        });
      }
    });

    assert.equal(requestUrl.pathname.endsWith("/KorWithService2/areaBasedSyncList2"), true);
    assert.equal(requestUrl.searchParams.get("areaCode"), "32");
    assert.equal(requestUrl.searchParams.get("showflag"), "1");
    assert.equal(requestUrl.searchParams.get("pageNo"), "2");
    assert.equal(result.totalCount, 21);
    assert.equal(result.rawCount, 2);
    assert.deepEqual(result.items, [{
      contentid: "123",
      title: "테스트 장소",
      showflag: "1"
    }]);
  } finally {
    if (originalKey === undefined) delete process.env.TOUR_API_SERVICE_KEY;
    else process.env.TOUR_API_SERVICE_KEY = originalKey;
  }
});

test("무장애 상세는 content id를 포함한 detailWithTour2 계약을 사용한다", async () => {
  const originalKey = process.env.TOUR_API_SERVICE_KEY;
  process.env.TOUR_API_SERVICE_KEY = "service-key";
  let requestUrl;
  try {
    const detail = await fetchTourAccessibilityDetail("123", {
      usageTracker: untrackedUsage,
      fetchImpl: async (url) => {
        requestUrl = new URL(String(url));
        return Response.json({
          response: {
            header: { resultCode: "0000", resultMsg: "OK" },
            body: { items: { item: { contentid: "123", wheelchair: "가능" } } }
          }
        });
      }
    });

    assert.equal(requestUrl.pathname.endsWith("/KorWithService2/detailWithTour2"), true);
    assert.equal(requestUrl.searchParams.get("contentId"), "123");
    assert.equal(detail.wheelchair, "가능");
  } finally {
    if (originalKey === undefined) delete process.env.TOUR_API_SERVICE_KEY;
    else process.env.TOUR_API_SERVICE_KEY = originalKey;
  }
});

test("무장애 배치는 일부 장소가 DB에 없어도 나머지를 저장한다", async () => {
  const saved = [];
  const result = await runAccessibilityBatch({
    items: [{ contentid: "1" }, { contentid: "2" }, { contentid: "3" }],
    syncedAt: "2026-09-07T00:00:00.000Z",
    concurrency: 2,
    fetchDetail: async (contentId) => {
      if (contentId === "3") throw new Error("timeout");
      return contentId === "1" ? { wheelchair: "가능" } : null;
    },
    saveAccessibility: async (contentId, enrichment) => {
      saved.push({ contentId, enrichment });
      return contentId === "1";
    }
  });

  assert.deepEqual(result, {
    processed: 3,
    deferred: 0,
    updated: 1,
    empty: 0,
    unmatched: 1,
    failed: 1
  });
  assert.deepEqual(saved.map(({ contentId }) => contentId).sort(), ["1", "2"]);
});

test("무장애 stage는 독립 lease와 페이지 cursor를 사용한다", async () => {
  process.env.CRON_SECRET = "cron-secret";
  const savedStates = [];
  let jobId;
  const handler = createTourAccessibilitySyncHandler({
    withLease: async (options) => {
      jobId = options.jobId;
      return options.run();
    },
    deadlineFactory: activeDeadline,
    now: () => new Date("2026-09-07T00:00:00.000Z"),
    getState: async () => ({ id: "tour_accessibility", next_page: 2 }),
    saveState: async (state) => savedStates.push(state),
    fetchPage: async () => ({
      pageNo: 2,
      numOfRows: 20,
      totalCount: 21,
      rawCount: 1,
      items: [{ contentid: "123" }]
    }),
    runBatch: async () => ({
      processed: 1,
      deferred: 0,
      updated: 1,
      empty: 0,
      unmatched: 0,
      failed: 0
    })
  });

  assert.equal((await handler.fetch(new Request("https://example.test", {
    method: "POST"
  }))).status, 405);
  assert.equal((await handler.fetch(new Request("https://example.test"))).status, 401);
  const response = await handler.fetch(new Request("https://example.test", {
    headers: { authorization: "Bearer cron-secret" }
  }));
  assert.equal(response.status, 200);
  assert.equal(jobId, "tour_accessibility");
  assert.equal((await response.json()).status, "completed");
  assert.equal(savedStates.at(-1).next_page, 1);

  let routed = "";
  createTourEnrichmentSyncHandler({
    introHandler: { fetch: () => { routed = "intro"; } },
    presentationHandler: { fetch: () => { routed = "presentation"; } },
    congestionHandler: { fetch: () => { routed = "congestion"; } },
    accessibilityHandler: { fetch: () => { routed = "accessibility"; } }
  }).fetch(new Request("https://example.test?stage=accessibility"));
  assert.equal(routed, "accessibility");
});
