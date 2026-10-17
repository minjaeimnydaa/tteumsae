import test from "node:test";
import assert from "node:assert/strict";
import {
  MINIMUM_STAY_MINUTES,
  operationStatus,
  recommendPlaces,
  safetyLevel
} from "../lib/time-safe.js";

const place = {
  content_id: "1",
  name: "테스트 카페",
  category: "CAFE",
  latitude: 37.75,
  longitude: 128.88,
  default_stay_minutes: 30
};

const criteria = {
  mode: "NEARBY",
  start: { latitude: 37.75, longitude: 128.88 },
  destination: { latitude: 37.75, longitude: 128.88 },
  deadlineMinutes: 70,
  safetyBufferMinutes: 15,
  transport: "WALK",
  categories: []
};

function fixedRoute() {
  return {
    firstLegMinutes: 10,
    secondLegMinutes: 10,
    directMinutes: 1,
    detourMinutes: 19,
    provider: "ESTIMATE"
  };
}

test("안전 여유시간 이상인 장소만 추천한다", () => {
  const result = recommendPlaces(criteria, [place], fixedRoute);
  assert.equal(result.length, 1);
  assert.equal(result[0].totalMinutes, 50);
  assert.equal(result[0].marginMinutes, 20);

  const none = recommendPlaces(
    { ...criteria, deadlineMinutes: 60, safetyBufferMinutes: 15 },
    [place],
    fixedRoute
  );
  assert.equal(none.length, 0);
});

test("선택된 카테고리를 적용한다", () => {
  const result = recommendPlaces(
    { ...criteria, categories: ["CULTURE"] },
    [place],
    fixedRoute
  );
  assert.equal(result.length, 0);
});

test("여유시간에 따라 안전도를 부여한다", () => {
  assert.equal(safetyLevel(20), "COMFORTABLE");
  assert.equal(safetyLevel(10), "AVAILABLE");
  assert.equal(safetyLevel(9), "TIGHT");
});

test("도착 예상 시각에 닫힌 장소는 제외하고 정보가 없으면 확인 필요로 남긴다", () => {
  const monday = new Date("2026-08-10T01:00:00.000Z");
  const tuesdayOpen = new Date("2026-08-11T01:00:00.000Z");
  const tuesdayClosed = new Date("2026-08-11T11:00:00.000Z");
  const openPlace = {
    ...place,
    opening_hours: "09:00~18:00",
    closed_days: "매주 월요일"
  };

  assert.equal(operationStatus(openPlace, monday), "CLOSED");
  assert.equal(operationStatus(openPlace, tuesdayOpen), "OPEN");
  assert.equal(operationStatus(openPlace, tuesdayClosed), "CLOSED");
  assert.equal(recommendPlaces(criteria, [openPlace], fixedRoute, monday).length, 0);
  assert.equal(
    recommendPlaces(criteria, [place], fixedRoute, monday)[0].operationStatus,
    "UNKNOWN"
  );
});

test("도착 후 체류 중 문을 닫는 장소는 제외한다", () => {
  const closingPlace = {
    ...place,
    default_stay_minutes: 40,
    opening_hours: "09:00~18:00",
    closed_days: "연중무휴"
  };
  const enoughTime = { ...criteria, deadlineMinutes: 90 };

  assert.equal(
    recommendPlaces(
      enoughTime,
      [closingPlace],
      fixedRoute,
      new Date("2026-08-11T08:40:00.000Z")
    ).length,
    0
  );
  assert.equal(
    recommendPlaces(
      enoughTime,
      [closingPlace],
      fixedRoute,
      new Date("2026-08-11T08:10:00.000Z")
    )[0].operationStatus,
    "OPEN"
  );
});

test("정확 경로 도착이 KST 자정을 넘으면 끝난 축제를 다시 제외한다", () => {
  const festival = {
    ...place,
    category: "FESTIVAL",
    content_type_id: 15,
    event_start_date: "2026-08-28",
    event_end_date: "2026-08-28"
  };
  const crossingRoute = () => ({
    ...fixedRoute(),
    firstLegMinutes: 20
  });

  const result = recommendPlaces(
    { ...criteria, deadlineMinutes: 80 },
    [festival],
    crossingRoute,
    new Date("2026-08-28T14:50:00.000Z")
  );

  assert.deepEqual(result, []);
});

test("V1은 고정 여유 뒤 최소 15분부터 5분 단위 최대 체류를 계산한다", () => {
  const now = new Date("2026-08-11T01:00:00.000Z");
  const arrivalDeadlineEpochMillis = now.getTime() + 69 * 60_000;
  const v1Criteria = {
    ...criteria,
    timeModel: "ARRIVAL_DEADLINE_V1",
    deadlineMinutes: 69,
    safetyBufferMinutes: 10,
    arrivalDeadlineEpochMillis
  };

  const [result] = recommendPlaces(v1Criteria, [place], fixedRoute, now);

  assert.equal(MINIMUM_STAY_MINUTES, 15);
  assert.equal(result.minimumStayMinutes, 15);
  assert.equal(result.maximumStayMinutes, 35);
  assert.equal(
    result.latestDepartureEpochMillis,
    arrivalDeadlineEpochMillis - 20 * 60_000
  );
  assert.equal(Object.hasOwn(result, "stayMinutes"), false);

  const exactMinimum = recommendPlaces(
    {
      ...v1Criteria,
      deadlineMinutes: 45,
      arrivalDeadlineEpochMillis: now.getTime() + 45 * 60_000
    },
    [place],
    fixedRoute,
    now
  );
  assert.equal(exactMinimum[0].maximumStayMinutes, 15);

  const belowMinimum = recommendPlaces(
    {
      ...v1Criteria,
      deadlineMinutes: 44,
      arrivalDeadlineEpochMillis: now.getTime() + 44 * 60_000
    },
    [place],
    fixedRoute,
    now
  );
  assert.deepEqual(belowMinimum, []);
});

test("V1 최대 체류는 영업 종료 전 마지막 5분 경계로 제한된다", () => {
  const now = new Date("2026-08-11T08:00:00.000Z");
  const v1Criteria = {
    ...criteria,
    timeModel: "ARRIVAL_DEADLINE_V1",
    deadlineMinutes: 69,
    safetyBufferMinutes: 10,
    arrivalDeadlineEpochMillis: now.getTime() + 69 * 60_000
  };
  const closingPlace = {
    ...place,
    opening_hours: "09:00~17:30",
    closed_days: "연중무휴"
  };

  const [result] = recommendPlaces(v1Criteria, [closingPlace], fixedRoute, now);
  assert.equal(result.maximumStayMinutes, 20);
  assert.equal(result.operationStatus, "OPEN");

  const tooSoon = {
    ...closingPlace,
    opening_hours: "09:00~17:24"
  };
  assert.deepEqual(recommendPlaces(v1Criteria, [tooSoon], fixedRoute, now), []);
});

test("V1은 영업시간을 해석할 수 없는 후보도 확인 필요 상태로 유지한다", () => {
  const now = new Date("2026-08-11T01:00:00.000Z");
  const [result] = recommendPlaces(
    {
      ...criteria,
      timeModel: "ARRIVAL_DEADLINE_V1",
      deadlineMinutes: 69,
      safetyBufferMinutes: 10,
      arrivalDeadlineEpochMillis: now.getTime() + 69 * 60_000
    },
    [place],
    fixedRoute,
    now
  );

  assert.equal(result.operationStatus, "UNKNOWN");
  assert.equal(result.maximumStayMinutes, 35);
});

test("V1은 휴무가 불명확해도 확실한 폐점 전에 최대 체류를 제한한다", () => {
  const now = new Date("2026-09-13T08:00:00Z");
  const v1Criteria = {
    ...criteria,
    timeModel: "ARRIVAL_DEADLINE_V1",
    deadlineMinutes: 120,
    safetyBufferMinutes: 10,
    arrivalDeadlineEpochMillis: now.getTime() + 120 * 60_000
  };
  const closingPlace = {
    ...place,
    opening_hours: "10:00~18:00",
    closed_days: "월요일 / 설날·추석 당일"
  };

  const [result] = recommendPlaces(v1Criteria, [closingPlace], fixedRoute, now);
  assert.equal(result.maximumStayMinutes, 50);
  assert.equal(result.operationStatus, "UNKNOWN");
  assert.deepEqual(recommendPlaces(v1Criteria, [
    { ...closingPlace, opening_hours: "10:00~17:24" }
  ], fixedRoute, now), []);
});

test("legacy 추천은 기존 체류 필드를 유지하고 V1 필드를 노출하지 않는다", () => {
  const [result] = recommendPlaces(criteria, [place], fixedRoute);

  assert.equal(result.stayMinutes, 30);
  assert.equal(Object.hasOwn(result, "minimumStayMinutes"), false);
  assert.equal(Object.hasOwn(result, "maximumStayMinutes"), false);
  assert.equal(Object.hasOwn(result, "latestDepartureEpochMillis"), false);
});
