import assert from "node:assert/strict";
import test from "node:test";

import { applyTourResearch } from "../lib/curation-tour-research.js";

test("TourAPI 공식 인트로의 유형별 운영·휴무·주차 정보를 검수 행으로 옮긴다", () => {
  const [row] = applyTourResearch(
    [{ content_id: "1", name: "시설", category: "CULTURE" }],
    [{
      contentId: "1",
      contentTypeId: 14,
      intro: {
        usetimeculture: "09:00~18:00 (입장마감 17:30)",
        restdateculture: "매주 월요일",
        parkingculture: "전용 주차장 이용"
      },
      common: {
        homepage: '<a href="https://facility.example/info">공식 홈페이지</a>'
      }
    }],
    "2026-08-28T03:00:00.000Z"
  );

  assert.equal(row.operating_info_status, "VERIFIED");
  assert.equal(row.opening_hours, "09:00~18:00 (입장마감 17:30)");
  assert.equal(row.closed_days, "매주 월요일");
  assert.equal(row.last_admission, "17:30");
  assert.equal(row.admission_info_status, "VERIFIED");
  assert.equal(row.parking_info, "전용 주차장 이용");
  assert.equal(row.parking_info_status, "VERIFIED");
  assert.deepEqual(JSON.parse(row.source_urls), [
    "https://www.data.go.kr/data/15101578/openapi.do",
    "https://facility.example/info"
  ]);
});

test("확인값이 없으면 추정하지 않고 UNKNOWN이며 음식점 입장 마감만 해당 없음이다", () => {
  const rows = applyTourResearch(
    [
      { content_id: "1", name: "야외", category: "ATTRACTION" },
      { content_id: "2", name: "식당", category: "RESTAURANT" }
    ],
    [
      { contentId: "1", contentTypeId: 12, intro: null, common: {} },
      { contentId: "2", contentTypeId: 39, intro: {}, common: { homepage: "@handle" } }
    ],
    "2026-08-28T03:00:00.000Z"
  );

  assert.equal(rows[0].operating_info_status, "UNKNOWN");
  assert.equal(rows[0].admission_info_status, "UNKNOWN");
  assert.equal(rows[0].parking_info_status, "UNKNOWN");
  assert.equal(rows[1].admission_info_status, "NOT_APPLICABLE");
  assert.deepEqual(JSON.parse(rows[1].source_urls), [
    "https://www.data.go.kr/data/15101578/openapi.do"
  ]);
  assert.match(rows[0].review_note, /추정하지 않음/);
});

test("모든 후보는 동일 contentId의 공식 응답이 있어야 한다", () => {
  assert.throws(
    () => applyTourResearch(
      [{ content_id: "missing", name: "누락", category: "ATTRACTION" }],
      [],
      "2026-08-28T03:00:00.000Z"
    ),
    /missing/
  );
});
