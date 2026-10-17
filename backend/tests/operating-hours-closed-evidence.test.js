import test from "node:test";
import assert from "node:assert/strict";
import { evaluateOperatingWindow } from "../lib/operating-hours.js";

const place = { opening_hours: "10:00~18:00", closed_days: "월요일 / 1월 1일 / 설날·추석 당일" };
test("휴무일 일부가 불명확해도 명확한 운영시간 밖이면 닫힘으로 판단한다", () => {
  assert.equal(evaluateOperatingWindow(place, {
    arrival: new Date("2026-09-12T17:00:00Z"),
    departure: new Date("2026-09-12T17:15:00Z")
  }).status, "CLOSED");
});
test("운영시간 안이어도 휴무가 불명확하면 열림을 확정하지 않는다", () => {
  assert.equal(evaluateOperatingWindow(place, {
    arrival: new Date("2026-09-13T03:00:00Z"),
    departure: new Date("2026-09-13T03:15:00Z")
  }).status, "UNKNOWN");
});

test("휴무가 불명확해도 명확한 입장 마감 이후 도착은 닫힘이다", () => {
  assert.equal(evaluateOperatingWindow({ ...place, last_admission: "17:30" }, {
    arrival: new Date("2026-09-13T08:40:00Z"),
    departure: new Date("2026-09-13T08:55:00Z")
  }).status, "CLOSED");
});

test("자정 넘는 운영은 이전 날 구간을 적용하되 휴무 불확실성은 유지한다", () => {
  const overnight = { ...place, opening_hours: "22:00~02:00" };
  assert.equal(evaluateOperatingWindow(overnight, {
    arrival: new Date("2026-09-12T16:00:00Z"),
    departure: new Date("2026-09-12T16:15:00Z")
  }).status, "UNKNOWN");
  assert.equal(evaluateOperatingWindow(overnight, {
    arrival: new Date("2026-09-12T16:55:00Z"),
    departure: new Date("2026-09-12T17:10:00Z")
  }).status, "CLOSED");
});

test("시간표 자체가 계절별이거나 입장 마감이 불명확하면 CLOSED를 추정하지 않는다", () => {
  for (const ambiguous of [
    { ...place, opening_hours: "하절기 10:00~18:00 / 동절기 10:00~17:00" },
    { ...place, last_admission: "당일 문의" }
  ]) {
    assert.equal(evaluateOperatingWindow(ambiguous, {
      arrival: new Date("2026-09-12T17:00:00Z"),
      departure: new Date("2026-09-12T17:15:00Z")
    }).status, "UNKNOWN");
  }
});
