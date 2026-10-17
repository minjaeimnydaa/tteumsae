import test from "node:test";
import assert from "node:assert/strict";
import { runTourCongestionSync } from "../lib/tour-congestion-sync.js";
import {
  createTourCongestionSyncHandler,
  createTourEnrichmentSyncHandler
} from "../api/cron/tour-intro-sync.js";

const activeDeadline = () => ({
  signal: new AbortController().signal,
  canStart: () => true,
  dispose: () => {}
});

test("혼잡 예측 배치는 강릉 행만 exact match해 페이지별 저장한다", async () => {
  const saved = [];
  const result = await runTourCongestionSync({
    fetchedAt: "2026-09-07T00:00:00.000Z",
    listPlaces: async () => [{ content_id: "1", name: "경포대" }],
    fetchPage: async (pageNo) => ({
      pageNo,
      numOfRows: 2,
      totalCount: 3,
      items: pageNo === 1
        ? [
            { tAtsNm: "경포대", baseYmd: "20260908", cnctrRate: "72", areaCd: "51", signguCd: "51150" },
            { tAtsNm: "타지역", baseYmd: "20260908", cnctrRate: "50", areaCd: "51", signguCd: "51130" }
          ]
        : [{ tAtsNm: "미연결", baseYmd: "20260908", cnctrRate: "30", areaCd: "51", signguCd: "51150" }]
    }),
    saveRows: async (rows) => saved.push(rows)
  });

  assert.equal(result.status, "completed");
  assert.equal(result.processedPages, 2);
  assert.equal(result.sourceRows, 3);
  assert.equal(result.savedRows, 2);
  assert.equal(result.matchedRows, 1);
  assert.equal(result.unmatchedRows, 1);
  assert.deepEqual(saved.map((rows) => rows.length), [1, 1]);
});

test("혼잡 예측 배치는 deadline 이후 새 페이지를 시작하지 않는다", async () => {
  let admissions = 0;
  const result = await runTourCongestionSync({
    fetchedAt: "2026-09-07T00:00:00.000Z",
    listPlaces: async () => [],
    fetchPage: async (pageNo) => ({
      pageNo,
      numOfRows: 1,
      totalCount: 2,
      items: [{ tAtsNm: "미연결", baseYmd: "20260908", cnctrRate: "30", areaCd: "51", signguCd: "51150" }]
    }),
    saveRows: async () => {},
    canStart: () => admissions++ < 1
  });

  assert.equal(result.status, "partial");
  assert.equal(result.processedPages, 1);
  assert.equal(result.nextPage, 2);
});

test("혼잡 stage는 기존 Cron 함수 안에서 별도 lease로 실행된다", async () => {
  process.env.CRON_SECRET = "cron-secret";
  let jobId;
  const handler = createTourCongestionSyncHandler({
    withLease: async (options) => {
      jobId = options.jobId;
      return options.run();
    },
    deadlineFactory: activeDeadline,
    runSync: async ({ fetchedAt }) => ({ status: "completed", fetchedAt }),
    now: () => new Date("2026-09-07T00:00:00.000Z")
  });

  assert.equal((await handler.fetch(new Request("https://example.test", { method: "POST" }))).status, 405);
  assert.equal((await handler.fetch(new Request("https://example.test"))).status, 401);
  const response = await handler.fetch(new Request("https://example.test", {
    headers: { authorization: "Bearer cron-secret" }
  }));
  assert.equal(jobId, "tour_congestion");
  assert.equal((await response.json()).status, "completed");

  let routed = "";
  const router = createTourEnrichmentSyncHandler({
    introHandler: { fetch: () => { routed = "intro"; } },
    presentationHandler: { fetch: () => { routed = "presentation"; } },
    congestionHandler: { fetch: () => { routed = "congestion"; } }
  });
  router.fetch(new Request("https://example.test?stage=congestion"));
  assert.equal(routed, "congestion");
});
