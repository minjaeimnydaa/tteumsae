import test from "node:test";
import assert from "node:assert/strict";
import { evaluateOperatingWindow } from "../lib/operating-hours.js";
import { recommendPlaces } from "../lib/time-safe.js";

const minute = 60_000;
const place = {
  content_id: "museum",
  category: "CULTURE",
  latitude: 37.75,
  longitude: 128.88,
  opening_hours: "10:00~18:00",
  closed_days: "연중무휴"
};
const route = () => ({ firstLegMinutes: 10, secondLegMinutes: 10, detourMinutes: 2 });

function recommend(candidate, now, deadlineMinutes = 120) {
  return recommendPlaces({
    categories: [],
    timeModel: "ARRIVAL_DEADLINE_V1",
    deadlineMinutes,
    safetyBufferMinutes: 10,
    arrivalDeadlineEpochMillis: now.getTime() + deadlineMinutes * minute
  }, [candidate], route, now);
}

test("명확한 폐점은 요청의 초·밀리초에 흔들리지 않는 절대 시각이다", () => {
  const assessment = evaluateOperatingWindow(place, {
    arrival: new Date("2026-09-13T08:10:27.987Z"),
    departure: new Date("2026-09-13T08:30:27.987Z")
  });
  assert.equal(assessment.closesAtEpochMillis, Date.parse("2026-09-13T09:00:00Z"));
});

test("V1 출발 마감은 확실한 폐점 이후로 안내하지 않는다", () => {
  const now = new Date("2026-09-13T08:00:00Z");
  const [result] = recommend(place, now);
  assert.equal(result.maximumStayMinutes, 50);
  assert.equal(result.latestDepartureEpochMillis, Date.parse("2026-09-13T09:00:00Z"));
});

test("휴무 불확실성은 유지하면서 명확한 폐점으로 출발 마감만 제한한다", () => {
  const [result] = recommend({ ...place, closed_days: "월요일 / 설·추석 당일" },
    new Date("2026-09-13T08:00:00Z"));
  assert.equal(result.operationStatus, "UNKNOWN");
  assert.equal(result.latestDepartureEpochMillis, Date.parse("2026-09-13T09:00:00Z"));
});

test("자정 넘는 운영의 실제 다음 날 폐점을 사용한다", () => {
  const [result] = recommend({ ...place, opening_hours: "22:00~02:00" },
    new Date("2026-09-13T16:00:00Z"));
  assert.equal(result.latestDepartureEpochMillis, Date.parse("2026-09-13T17:00:00Z"));
});

test("일찍 도착해야 하는 목적지 마감은 폐점보다 우선한다", () => {
  const now = new Date("2026-09-13T08:00:00Z");
  const [result] = recommend(place, now, 49);
  assert.equal(result.maximumStayMinutes, 15);
  assert.equal(result.latestDepartureEpochMillis, now.getTime() + 29 * minute);
  assert.ok(result.latestDepartureEpochMillis >
    now.getTime() + (10 + result.maximumStayMinutes) * minute);
});

test("알 수 없는 시간표는 최소 체류 반올림값으로 출발 마감을 줄이지 않는다", () => {
  for (const opening_hours of [null, "하절기 10:00~18:00 / 동절기 10:00~17:00"]) {
    const now = new Date("2026-09-13T08:00:00Z");
    const [result] = recommend({ ...place, opening_hours }, now, 49);
    assert.equal(result.operationStatus, "UNKNOWN");
    assert.equal(result.maximumStayMinutes, 15);
    assert.equal(result.latestDepartureEpochMillis, now.getTime() + 29 * minute);
    const responseAfterTenSeconds = now.getTime() + 10_000;
    assert.ok(result.latestDepartureEpochMillis - responseAfterTenSeconds - 10 * minute >= 15 * minute);
  }
});

test("폐점 경계에서 초 단위 잔여 시간을 올림해 최소 체류를 만들어내지 않는다", () => {
  const [result] = recommend(place, new Date("2026-09-13T08:00:27.987Z"));
  assert.equal(result.maximumStayMinutes, 45);
  assert.deepEqual(recommend(place, new Date("2026-09-13T08:35:00.001Z")), []);
});

test("24시간 표기는 자정을 실제 폐점 시각으로 생성하지 않는다", () => {
  const assessment = evaluateOperatingWindow({ ...place, opening_hours: "24시간" }, {
    arrival: new Date("2026-09-13T08:10:00Z"),
    departure: new Date("2026-09-13T08:30:00Z")
  });
  assert.equal(assessment.status, "OPEN");
  assert.equal(assessment.closesAtEpochMillis, undefined);
});
