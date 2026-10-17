import test from "node:test";
import assert from "node:assert/strict";
import {
  fetchPublicParkingPage,
  mapPublicParkingLot,
  publicNearbyParkingLot
} from "../lib/public-parking.js";
import {
  createPublicParkingSyncHandler,
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

test("공영·강원·유효 좌표 주차장만 서버 저장 행으로 정규화한다", () => {
  const mapped = mapPublicParkingLot({
    prkplceNo: "P-1",
    prkplceNm: "경포 공영주차장",
    prkplceSe: "공영",
    prkplceType: "노외",
    rdnmadr: "강원특별자치도 강릉시 창해로 1",
    prkcmprt: "120",
    operDay: "평일+토요일+공휴일",
    weekdayOperOpenHhmm: "09:00",
    weekdayOperColseHhmm: "18:00",
    parkingchrgeInfo: "유료",
    basicTime: "30",
    basicCharge: "1000",
    institutionNm: "강릉시청",
    instt_code: "4200000",
    phoneNumber: "033-123-4567",
    latitude: "37.80",
    longitude: "128.90",
    pwdbsPpkZoneYn: "Y",
    referenceDate: "2026-07-22"
  }, "2026-09-07T00:00:00.000Z");

  assert.equal(mapped.source_id, "4200000:P-1");
  assert.equal(mapped.capacity, 120);
  assert.equal(mapped.accessible_parking, true);
  assert.equal(mapped.reference_date, "2026-07-22");
  assert.equal(mapPublicParkingLot({ ...mapped.raw, prkplceSe: "민영" }), null);
  assert.equal(mapPublicParkingLot({ ...mapped.raw, latitude: "" }), null);
  assert.equal(mapPublicParkingLot({ ...mapped.raw, rdnmadr: "서울특별시 중구" }), null);
});

test("전국주차장 표준 API는 공영·강릉 제공기관 필터와 공식 응답 루트를 사용한다", async () => {
  const originalKey = process.env.PUBLIC_PARKING_API_SERVICE_KEY;
  process.env.PUBLIC_PARKING_API_SERVICE_KEY = "parking-key";
  let requestUrl;
  try {
    const result = await fetchPublicParkingPage(2, 100, {
      usageTracker: untrackedUsage,
      fetchImpl: async (url) => {
        requestUrl = new URL(String(url));
        return Response.json({
          header: { resultCode: "00", resultMsg: "NORMAL SERVICE" },
          body: {
            pageNo: 2,
            numOfRows: 100,
            totalCount: 101,
            items: [{
              prkplceNo: "P-1",
              prkplceNm: "경포 공영주차장",
              prkplceSe: "공영",
              rdnmadr: "강원특별자치도 강릉시 창해로 1",
              latitude: "37.80",
              longitude: "128.90",
              insttCode: "4200000",
              insttNm: "강원특별자치도 강릉시"
            }]
          }
        });
      }
    });

    assert.equal(requestUrl.pathname, "/openapi/tn_pubr_prkplce_info_api");
    assert.equal(requestUrl.searchParams.get("prkplceSe"), "공영");
    assert.equal(requestUrl.searchParams.get("instt_nm"), "강원특별자치도 강릉시");
    assert.equal(requestUrl.searchParams.has("rdnmadr"), false);
    assert.equal(requestUrl.searchParams.get("type"), "json");
    assert.equal(result.totalCount, 101);
    assert.equal(result.rows.length, 1);
  } finally {
    if (originalKey === undefined) delete process.env.PUBLIC_PARKING_API_SERVICE_KEY;
    else process.env.PUBLIC_PARKING_API_SERVICE_KEY = originalKey;
  }
});

test("주변 주차장 공개값은 직선거리 근거와 읽기 쉬운 운영·요금 요약을 포함한다", () => {
  const result = publicNearbyParkingLot({
    source_id: "4200000:P-1",
    name: "경포 공영주차장",
    parking_type: "노외",
    address: "강원특별자치도 강릉시 창해로 1",
    capacity: 120,
    operation_days: "평일+토요일+공휴일",
    weekday_open: "09:00",
    weekday_close: "18:00",
    fee_info: "유료",
    basic_minutes: 30,
    basic_fee_won: 1000,
    accessible_parking: true,
    phone: "033-123-4567",
    reference_date: "2026-07-22",
    distance_meters: 245.4
  });

  assert.equal(result.distance_meters, 245);
  assert.equal(result.distance_basis, "STRAIGHT_LINE");
  assert.equal(result.fee_summary, "유료 · 기본 30분 1,000원");
  assert.equal(result.operation_summary, "평일+토요일+공휴일 · 평일 09:00~18:00");
  assert.equal(result.source, "전국주차장정보표준데이터");
});

test("주차장 stage는 독립 cursor로 다음 페이지를 저장한다", async () => {
  process.env.CRON_SECRET = "cron-secret";
  let jobId;
  const savedStates = [];
  const handler = createPublicParkingSyncHandler({
    withLease: async (options) => {
      jobId = options.jobId;
      return options.run();
    },
    deadlineFactory: activeDeadline,
    now: () => new Date("2026-09-07T00:00:00.000Z"),
    getState: async () => ({ id: "public_parking", next_page: 1 }),
    saveState: async (state) => savedStates.push(state),
    fetchPage: async () => ({
      pageNo: 1,
      numOfRows: 100,
      totalCount: 101,
      rawCount: 100,
      rows: [{ source_id: "1" }]
    }),
    upsert: async () => {}
  });

  const response = await handler.fetch(new Request("https://example.test", {
    headers: { authorization: "Bearer cron-secret" }
  }));
  const body = await response.json();
  assert.equal(jobId, "public_parking");
  assert.equal(body.status, "partial");
  assert.equal(body.nextPage, 2);
  assert.equal(savedStates.at(-1).next_page, 2);

  let routed = "";
  createTourEnrichmentSyncHandler({
    introHandler: { fetch: () => { routed = "intro"; } },
    presentationHandler: { fetch: () => { routed = "presentation"; } },
    congestionHandler: { fetch: () => { routed = "congestion"; } },
    accessibilityHandler: { fetch: () => { routed = "accessibility"; } },
    parkingHandler: { fetch: () => { routed = "parking"; } }
  }).fetch(new Request("https://example.test?stage=parking"));
  assert.equal(routed, "parking");
});
