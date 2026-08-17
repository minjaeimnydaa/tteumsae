import test from "node:test";
import assert from "node:assert/strict";
import { parseRecommendationRequest } from "../lib/validation.js";

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
