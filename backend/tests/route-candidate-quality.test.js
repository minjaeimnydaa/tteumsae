import test from "node:test";
import assert from "node:assert/strict";
import { selectRouteCandidates } from "../lib/time-safe.js";
import { estimateRoute } from "../lib/routing.js";

const now = new Date("2026-09-13T03:00:00Z");
const criteria = {
  timeModel: "ARRIVAL_DEADLINE_V1",
  start: { latitude: 37.75, longitude: 128.88 },
  destination: { latitude: 37.78, longitude: 128.94 },
  transport: "CAR",
  categories: [],
  deadlineMinutes: 90,
  safetyBufferMinutes: 10
};

function place(id, category = "RESTAURANT", extra = {}) {
  return {
    content_id: id,
    category,
    latitude: 37.765,
    longitude: 128.91,
    default_stay_minutes: 30,
    ...extra
  };
}

const restaurants = Array.from({ length: 10 }, (_, index) => place(`food-${index}`));

test("V1은 비슷한 우회 범위 안에서 다른 방문 종류도 정확 경로 후보에 포함한다", () => {
  const nature = place("nature", "ATTRACTION", { latitude: 37.785 });
  const cafe = place("cafe", "CAFE", { latitude: 37.775 });
  const result = selectRouteCandidates(criteria, [...restaurants, nature, cafe], 8, now);

  assert.equal(result.length, 8);
  assert.equal(result[0].content_id, "food-0");
  assert.ok(result.some((item) => item.content_id === "nature"));
  assert.ok(result.some((item) => item.content_id === "cafe"));
  assert.equal(new Set(result.map((item) => item.content_id)).size, 8);
});

test("종류 다양성을 위해 최선보다 추정 우회 5분 넘게 먼 곳을 밀어넣지 않는다", () => {
  const far = place("far", "ATTRACTION", { latitude: 37.795, image_url: "https://example.com/far.jpg" });
  const closestRoute = estimateRoute(criteria.start, criteria.destination, restaurants[0], "CAR");
  const farRoute = estimateRoute(criteria.start, criteria.destination, far, "CAR");
  assert.ok(farRoute.detourMinutes > closestRoute.detourMinutes + 5);
  assert.deepEqual(
    selectRouteCandidates(criteria, [...restaurants, far], 8, now).map((item) => item.content_id),
    restaurants.slice(0, 8).map((item) => item.content_id)
  );
});

test("추정 이동 조건이 같으면 실제 표시할 정보가 있는 후보를 우선한다", () => {
  const informative = place("with-info", "RESTAURANT", {
    image_url: "https://example.com/place.jpg",
    overview: "해변 산책길 옆의 작은 식당",
    operating_info_status: "VERIFIED"
  });
  assert.equal(
    selectRouteCandidates(criteria, [...restaurants, informative], 1, now)[0].content_id,
    "with-info"
  );
});

test("명시한 관심 카테고리와 작은 후보 상한을 유지한다", () => {
  const nature = place("nature", "ATTRACTION");
  const filtered = selectRouteCandidates(
    { ...criteria, categories: ["RESTAURANT"] }, [...restaurants, nature], 2, now
  );
  assert.equal(filtered.length, 2);
  assert.ok(filtered.every((item) => item.category === "RESTAURANT"));
  assert.equal(selectRouteCandidates(criteria, [...restaurants, nature], 1, now).length, 1);
  assert.deepEqual(selectRouteCandidates(criteria, [], 8, now), []);
});

test("종료된 축제를 다양성 확보용으로 되살리지 않는다", () => {
  const expired = place("expired", "FESTIVAL", {
    content_type_id: 15,
    event_start_date: "2026-09-01",
    event_end_date: "2026-09-12"
  });
  assert.ok(selectRouteCandidates(criteria, [...restaurants, expired], 8, now)
    .every((item) => item.content_id !== "expired"));
});

test("legacy shortlist는 기존 거리와 체류 기준의 순서를 유지한다", () => {
  const informative = place("with-info", "CAFE", { image_url: "https://example.com/place.jpg" });
  const { timeModel: _timeModel, ...legacy } = criteria;
  assert.deepEqual(
    selectRouteCandidates(legacy, [...restaurants, informative], 8, now),
    restaurants.slice(0, 8)
  );
});
