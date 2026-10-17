import test from "node:test";
import assert from "node:assert/strict";
import {
  congestionLevel,
  fetchTourCongestionPage,
  normalizeCongestionItem,
  prepareCongestionRows,
  publicCongestionForecast
} from "../lib/tour-congestion.js";

const fetchedAt = "2026-09-07T00:00:00.000Z";

test("관광지 집중률은 날짜·상대값·표시 등급으로 정규화한다", () => {
  const row = normalizeCongestionItem({
    tAtsNm: " 경포대 ",
    baseYmd: "20260908",
    areaCd: "51",
    areaNm: "강원특별자치도",
    signguCd: "51150",
    signguNm: "강릉시",
    cnctrRate: "72.35"
  }, fetchedAt);

  assert.equal(row.source_name, "경포대");
  assert.equal(row.forecast_date, "2026-09-08");
  assert.equal(row.concentration_rate, 72.35);
  assert.equal(row.level, "HIGH");
  assert.deepEqual(congestionLevel(39.99), { code: "LOW", label: "한산 예상" });
  assert.deepEqual(congestionLevel(40), { code: "MODERATE", label: "보통 예상" });
  assert.deepEqual(congestionLevel(70), { code: "HIGH", label: "혼잡 예상" });
});

test("장소명 exact unique match만 content id를 연결한다", () => {
  const items = [
    { tAtsNm: "경 포 대", baseYmd: "20260908", cnctrRate: "30", areaCd: "51", signguCd: "51150" },
    { tAtsNm: "중복 장소", baseYmd: "20260908", cnctrRate: "50", areaCd: "51", signguCd: "51150" },
    { tAtsNm: "없는 장소", baseYmd: "20260908", cnctrRate: "80", areaCd: "51", signguCd: "51150" }
  ];
  const places = [
    { content_id: "1", name: "경포대", category: "ATTRACTION" },
    { content_id: "2", name: "중복 장소", category: "ATTRACTION" },
    { content_id: "3", name: "중복장소", category: "CULTURE" }
  ];

  const rows = prepareCongestionRows(items, places, fetchedAt);

  assert.deepEqual(rows.map((row) => [row.content_id, row.match_status]), [
    ["1", "MATCHED"],
    [null, "AMBIGUOUS"],
    [null, "UNMATCHED"]
  ]);
});

test("집중률 목록은 강릉 법정동 코드·페이지와 별도 키를 사용한다", async () => {
  let requestUrl;
  let usage;
  const result = await fetchTourCongestionPage(2, 9999, {
    apiKey: "congestion-key",
    usageTracker: async (input) => {
      usage = input;
      return input.call();
    },
    fetchImpl: async (url) => {
      requestUrl = new URL(String(url));
      return Response.json({
        response: {
          header: { resultCode: "0000", resultMsg: "OK" },
          body: {
            pageNo: 2,
            numOfRows: 1000,
            totalCount: 1001,
            items: { item: { tAtsNm: "경포대", baseYmd: "20260908", cnctrRate: "30" } }
          }
        }
      });
    }
  });

  assert.equal(requestUrl.pathname, "/B551011/TatsCnctrRateService/tatsCnctrRatedList");
  assert.equal(requestUrl.searchParams.get("serviceKey"), "congestion-key");
  assert.equal(requestUrl.searchParams.get("areaCd"), "51");
  assert.equal(requestUrl.searchParams.get("signguCd"), "51150");
  assert.equal(requestUrl.searchParams.get("pageNo"), "2");
  assert.equal(requestUrl.searchParams.get("numOfRows"), "1000");
  assert.equal(usage.provider, "TOUR_API");
  assert.equal(usage.operation, "tatsCnctrRatedList");
  assert.equal(usage.budgetLimit, 100);
  assert.equal(result.items.length, 1);
});

test("공개 집중률은 상대 예측 근거를 포함하고 원천 장소명은 숨긴다", () => {
  const result = publicCongestionForecast({
    forecast_date: "2026-09-08",
    concentration_rate: "72.35",
    fetched_at: fetchedAt,
    source_name: "원천 이름"
  });

  assert.equal(result.label, "혼잡 예상");
  assert.equal(result.concentration_rate, 72.35);
  assert.match(result.basis, /상대 예측값/);
  assert.equal("source_name" in result, false);
});
