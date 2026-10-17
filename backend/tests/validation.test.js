import test from "node:test";
import assert from "node:assert/strict";
import {
  parseRecommendationRequest,
  parseRouteRequest
} from "../lib/validation.js";

const valid = {
  mode: "ON_THE_WAY",
  start: { latitude: 37.7519, longitude: 128.8761 },
  destination: { latitude: 37.7644, longitude: 128.8996 },
  deadlineMinutes: 90,
  safetyBufferMinutes: 15,
  transport: "WALK",
  categories: ["CAFE", "ATTRACTION"]
};

test("유효한 추천 요청을 정규화한다", () => {
  assert.deepEqual(parseRecommendationRequest(valid), valid);
  assert.equal(
    parseRecommendationRequest({ ...valid, deadlineMinutes: 1440 }).deadlineMinutes,
    1440
  );
  assert.throws(
    () => parseRecommendationRequest({ ...valid, deadlineMinutes: 1441 }),
    /15~1440/
  );
});

test("순수 여유시간을 받고 기존 전체시간과 동시에 입력하면 거부한다", () => {
  const { deadlineMinutes: _deadlineMinutes, ...withoutDeadline } = valid;
  const extraTime = { ...withoutDeadline, extraTimeMinutes: 60 };
  assert.deepEqual(parseRecommendationRequest(extraTime), extraTime);
  assert.throws(
    () => parseRecommendationRequest({ ...extraTime, deadlineMinutes: 90 }),
    /중 하나만/
  );
  assert.throws(
    () => parseRecommendationRequest(withoutDeadline),
    /중 하나만/
  );
  assert.throws(
    () => parseRecommendationRequest({ ...withoutDeadline, extraTimeMinutes: 1441 }),
    /15~1440/
  );
});

test("도착 마감 V1은 절대시각으로 서버 기준 남은 분과 고정 여유를 계산한다", () => {
  const nowEpochMillis = Date.parse("2026-08-28T03:00:00.000Z");
  const v1Request = {
    mode: "ON_THE_WAY",
    start: valid.start,
    destination: valid.destination,
    arrivalDeadlineEpochMillis: nowEpochMillis + 45 * 60_000,
    transport: "CAR",
    categories: [],
    timeModel: "ARRIVAL_DEADLINE_V1"
  };

  assert.deepEqual(
    parseRecommendationRequest(v1Request, { nowEpochMillis }),
    {
      ...v1Request,
      deadlineMinutes: 45,
      safetyBufferMinutes: 10
    }
  );
});

test("도착 마감 V1은 상대시간과 사용자 안전여유의 혼용을 거부한다", () => {
  const nowEpochMillis = Date.parse("2026-08-28T03:00:00.000Z");
  const base = {
    mode: "ON_THE_WAY",
    start: valid.start,
    destination: valid.destination,
    arrivalDeadlineEpochMillis: nowEpochMillis + 45 * 60_000,
    transport: "CAR",
    categories: [],
    timeModel: "ARRIVAL_DEADLINE_V1"
  };

  for (const forbidden of [
    { deadlineMinutes: 45 },
    { extraTimeMinutes: 45 },
    { safetyBufferMinutes: 10 }
  ]) {
    assert.throws(
      () => parseRecommendationRequest({ ...base, ...forbidden }, { nowEpochMillis }),
      /함께 입력할 수 없습니다/
    );
  }

  assert.throws(
    () => parseRecommendationRequest({ ...valid, timeModel: "UNKNOWN" }),
    /timeModel/
  );

  assert.throws(
    () => parseRecommendationRequest({
      ...valid,
      arrivalDeadlineEpochMillis: nowEpochMillis + 45 * 60_000
    }, { nowEpochMillis }),
    /ARRIVAL_DEADLINE_V1에서만/
  );
});

test("도착 마감 V1은 정확한 15분부터 24시간까지만 허용한다", () => {
  const nowEpochMillis = Date.parse("2026-08-28T03:00:00.000Z");
  const request = (arrivalDeadlineEpochMillis) => ({
    mode: "ON_THE_WAY",
    start: valid.start,
    destination: valid.destination,
    arrivalDeadlineEpochMillis,
    transport: "CAR",
    categories: [],
    timeModel: "ARRIVAL_DEADLINE_V1"
  });

  assert.equal(
    parseRecommendationRequest(
      request(nowEpochMillis + 15 * 60_000),
      { nowEpochMillis }
    ).deadlineMinutes,
    15
  );
  assert.equal(
    parseRecommendationRequest(
      request(nowEpochMillis + 24 * 60 * 60_000),
      { nowEpochMillis }
    ).deadlineMinutes,
    1440
  );
  assert.throws(
    () => parseRecommendationRequest(
      request(nowEpochMillis + 15 * 60_000 - 1),
      { nowEpochMillis }
    ),
    /15분 이상/
  );
  assert.throws(
    () => parseRecommendationRequest(
      request(nowEpochMillis + 24 * 60 * 60_000 + 1),
      { nowEpochMillis }
    ),
    /24시간 이내/
  );
  assert.throws(
    () => parseRecommendationRequest(request(nowEpochMillis - 1), { nowEpochMillis }),
    /15분 이상/
  );
  assert.throws(
    () => parseRecommendationRequest(request(1.5), { nowEpochMillis }),
    /정수/
  );
  assert.throws(
    () => parseRecommendationRequest(
      request(Number.MAX_SAFE_INTEGER + 1),
      { nowEpochMillis }
    ),
    /안전한 정수/
  );
});

test("안전 여유가 전체 시간보다 길면 거부한다", () => {
  assert.throws(
    () =>
      parseRecommendationRequest({
        ...valid,
        deadlineMinutes: 30,
        safetyBufferMinutes: 30
      }),
    /안전 여유시간/
  );
});

test("잘못된 좌표를 거부한다", () => {
  assert.throws(
    () => parseRecommendationRequest({ ...valid, start: { latitude: 200, longitude: 0 } }),
    /좌표/
  );
});

test("경로 요청은 경유지 0~5개만 허용한다", () => {
  const route = {
    start: valid.start,
    destination: valid.destination
  };
  assert.deepEqual(parseRouteRequest(route), { ...route, waypoints: [] });
  assert.equal(
    parseRouteRequest({
      ...route,
      waypoints: Array.from({ length: 5 }, () => valid.start)
    }).waypoints.length,
    5
  );
  assert.throws(
    () => parseRouteRequest({
      ...route,
      waypoints: Array.from({ length: 6 }, () => valid.start)
    }),
    /최대 5개/
  );
  assert.throws(
    () => parseRouteRequest({ ...route, waypoints: [{ latitude: 200, longitude: 0 }] }),
    /경유지 좌표/
  );
});
