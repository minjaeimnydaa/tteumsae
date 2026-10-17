import test from "node:test";
import assert from "node:assert/strict";
import { evaluateOperatingWindow } from "../lib/operating-hours.js";

// Exact public TourAPI text returned for 강릉올림픽뮤지엄 in the production smoke.
const museum = {
  opening_hours: "10:00 ~ 17:00 (입장마감 16:30)",
  closed_days: "매주 월요일, 법정공휴일 및 대체휴무일"
};

function evaluate(place, arrival, departure) {
  return evaluateOperatingWindow(place, {
    arrival: new Date(`2026-09-13T${arrival}:00+09:00`),
    departure: new Date(`2026-09-13T${departure}:00+09:00`)
  });
}

test("실제 박물관 괄호 입장마감 문구가 있어도 새벽에는 CLOSED다", () => {
  assert.deepEqual(evaluate(museum, "03:00", "03:15"), {
    status: "CLOSED", reason: "OUTSIDE_OPERATING_WINDOW"
  });
});

test("괄호 입장마감 이후 도착은 제외하고 마감 시각 도착은 허용한다", () => {
  assert.equal(evaluate(museum, "16:30", "16:45").status, "UNKNOWN");
  assert.deepEqual(evaluate(museum, "16:31", "16:45"), {
    status: "CLOSED", reason: "AFTER_LAST_ADMISSION"
  });
});

test("박물관 휴일 규칙은 UNKNOWN을 유지하되 명확한 17시 종료는 보존한다", () => {
  assert.deepEqual(evaluate(museum, "11:00", "12:00"), {
    status: "UNKNOWN",
    reason: "UNSUPPORTED_HOLIDAY",
    closesAtEpochMillis: Date.parse("2026-09-13T17:00:00+09:00")
  });
});

test("명확한 휴무 규칙에서는 괄호 입장마감과 운영 종료를 각각 적용한다", () => {
  const place = { ...museum, closed_days: "연중무휴" };
  assert.equal(evaluate(place, "16:30", "17:00").status, "OPEN");
  assert.equal(evaluate(place, "16:30", "17:01").status, "CLOSED");
});

test("괄호의 매표마감 종료 N분 전도 기존 상대 마감 규칙을 적용한다", () => {
  const place = {
    opening_hours: "10:00~17:00 ( 매표마감 운영 종료 30분 전 )",
    closed_days: "연중무휴"
  };
  assert.equal(evaluate(place, "16:30", "16:45").status, "OPEN");
  assert.equal(evaluate(place, "16:31", "16:45").status, "CLOSED");
});

test("임의 괄호·잘못된 시각·다중 마감·계절 시간표는 해석을 확장하지 않는다", () => {
  for (const opening_hours of [
    "10:00~17:00 (평일만)",
    "10:00~17:00 (입장마감 16:30, 평일만)",
    "10:00~17:00 (입장마감 16:30",
    "10:00~17:00 (입장마감 26:30)",
    "10:00~17:00 (입장마감 16:30) (매표마감 16:00)",
    "평일 10:00~17:00 (입장마감 16:30) / 주말 10:00~16:00 (입장마감 15:30)",
    "하절기 10:00~17:00 (입장마감 16:30) / 동절기 10:00~16:00",
    "매일 11:00~20:00 (16:00~17:00 브레이크 타임, 19:20 라스트 오더), 브레이크 타임은 탄력적 운영"
  ]) {
    assert.equal(
      evaluate({ opening_hours, closed_days: "연중무휴" }, "11:00", "11:15").status,
      "UNKNOWN",
      opening_hours
    );
  }
});
