import test from "node:test";
import assert from "node:assert/strict";
import {
  isFestival,
  isFestivalVisitEligible
} from "../lib/festival-eligibility.js";

const arrival = new Date("2026-08-28T01:00:00.000Z");

function festival(start, end) {
  return {
    content_type_id: 15,
    category: "FESTIVAL",
    event_start_date: start,
    event_end_date: end
  };
}

test("축제 여부는 content type 또는 category로 판정한다", () => {
  assert.equal(isFestival({ content_type_id: 15, category: "ATTRACTION" }), true);
  assert.equal(isFestival({ content_type_id: 12, category: "FESTIVAL" }), true);
  assert.equal(isFestival({ content_type_id: 12, category: "ATTRACTION" }), false);
});

test("KST 예정 방문일이 시작일과 종료일 사이인 축제만 허용한다", () => {
  assert.equal(isFestivalVisitEligible(festival("2026-08-20", "2026-08-31"), arrival), true);
  assert.equal(isFestivalVisitEligible(festival("2026-08-01", "2026-08-27"), arrival), false);
  assert.equal(isFestivalVisitEligible(festival("2026-08-29", "2026-08-31"), arrival), false);
});

test("날짜 누락·형식 오류·역전 축제는 추천하지 않는다", () => {
  assert.equal(isFestivalVisitEligible(festival(null, "2026-08-31"), arrival), false);
  assert.equal(isFestivalVisitEligible(festival("2026-08-20", null), arrival), false);
  assert.equal(isFestivalVisitEligible(festival("2026-02-30", "2026-08-31"), arrival), false);
  assert.equal(isFestivalVisitEligible(festival("2026-08-31", "2026-08-20"), arrival), false);
});

test("비축제 장소는 행사 날짜가 없어도 유지한다", () => {
  assert.equal(
    isFestivalVisitEligible({ content_type_id: 12, category: "ATTRACTION" }, arrival),
    true
  );
});

test("UTC 날짜가 같아도 KST 자정 전후의 행사일을 구분한다", () => {
  const oneDayFestival = festival("2026-08-29", "2026-08-29");
  assert.equal(
    isFestivalVisitEligible(oneDayFestival, new Date("2026-08-28T14:59:59.000Z")),
    false
  );
  assert.equal(
    isFestivalVisitEligible(oneDayFestival, new Date("2026-08-28T15:00:00.000Z")),
    true
  );
});
