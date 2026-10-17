import test from "node:test";
import assert from "node:assert/strict";
import { evaluateOperatingWindow } from "../lib/operating-hours.js";

function evaluate(place, arrival, departure) {
  return evaluateOperatingWindow(place, {
    arrival: new Date(arrival),
    departure: new Date(departure),
    timeZone: "Asia/Seoul"
  });
}

test("평일과 주말의 서로 다른 운영시간을 해당 KST 요일에 적용한다", () => {
  const place = {
    opening_hours: "평일 09:00~18:00 / 주말 10:00~17:00",
    closed_days: "연중무휴"
  };

  assert.equal(
    evaluate(place, "2026-08-28T01:00:00Z", "2026-08-28T02:00:00Z").status,
    "OPEN"
  );
  assert.equal(
    evaluate(place, "2026-08-29T00:30:00Z", "2026-08-29T01:30:00Z").status,
    "CLOSED"
  );
  assert.equal(
    evaluate(place, "2026-08-29T01:00:00Z", "2026-08-29T07:30:00Z").status,
    "OPEN"
  );
});

test("24시간 운영도 명확한 정기 휴무일에는 닫힘이다", () => {
  const place = { opening_hours: "24시간", closed_days: "매주 월요일" };

  assert.equal(
    evaluate(place, "2026-08-31T01:00:00Z", "2026-08-31T02:00:00Z").status,
    "CLOSED"
  );
  assert.equal(
    evaluate(place, "2026-09-01T01:00:00Z", "2026-09-01T02:00:00Z").status,
    "OPEN"
  );
});

test("입장 마감은 도착에만 적용하고 영업 종료는 예상 출발에 적용한다", () => {
  const place = {
    opening_hours: "09:00~18:00",
    closed_days: "연중무휴",
    last_admission: "17:30"
  };

  assert.equal(
    evaluate(place, "2026-08-28T08:30:00Z", "2026-08-28T08:50:00Z").status,
    "OPEN"
  );
  assert.equal(
    evaluate(place, "2026-08-28T08:31:00Z", "2026-08-28T08:50:00Z").status,
    "CLOSED"
  );
  assert.equal(
    evaluate(place, "2026-08-28T08:00:00Z", "2026-08-28T09:01:00Z").status,
    "CLOSED"
  );
});

test("종료 N분 전 입장 마감을 명확한 종료 시각에서 계산한다", () => {
  const place = {
    opening_hours: "09:00~18:00",
    closed_days: "연중무휴",
    last_admission: "종료 30분 전"
  };

  assert.equal(
    evaluate(place, "2026-08-28T08:30:00Z", "2026-08-28T08:45:00Z").status,
    "OPEN"
  );
  assert.equal(
    evaluate(place, "2026-08-28T08:31:00Z", "2026-08-28T08:45:00Z").status,
    "CLOSED"
  );
});

test("자정을 넘는 운영 구간 안의 방문을 허용한다", () => {
  const place = { opening_hours: "22:00~02:00", closed_days: "연중무휴" };

  assert.equal(
    evaluate(place, "2026-08-28T14:00:00Z", "2026-08-28T16:00:00Z").status,
    "OPEN"
  );
  assert.equal(
    evaluate(place, "2026-08-28T16:30:00Z", "2026-08-28T17:30:00Z").status,
    "CLOSED"
  );
});

test("계절·공휴일·상충 문구는 일부 시간 범위가 있어도 UNKNOWN이다", () => {
  const cases = [
    {
      opening_hours: "하절기 09:00~18:00 / 동절기 10:00~17:00",
      closed_days: "연중무휴"
    },
    {
      opening_hours: "09:00~18:00",
      closed_days: "공휴일 휴무"
    },
    {
      opening_hours: "평일 09:00~18:00 / 월요일 10:00~17:00",
      closed_days: "연중무휴"
    }
  ];

  for (const place of cases) {
    assert.equal(
      evaluate(place, "2026-08-31T01:00:00Z", "2026-08-31T02:00:00Z").status,
      "UNKNOWN"
    );
  }
});

test("운영시간이 없으면 휴무로 추정하지 않고 UNKNOWN이다", () => {
  assert.equal(
    evaluate({}, "2026-08-28T01:00:00Z", "2026-08-28T02:00:00Z").status,
    "UNKNOWN"
  );
});
