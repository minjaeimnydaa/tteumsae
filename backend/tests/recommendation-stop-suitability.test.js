import test from "node:test";
import assert from "node:assert/strict";
import { recommendPlaces, selectRouteCandidates } from "../lib/time-safe.js";
import { mapTourItem } from "../lib/tour-api.js";

const now = new Date("2026-09-13T03:00:00+09:00");
const criteria = {
  mode: "ON_THE_WAY",
  transport: "CAR",
  start: { latitude: 37.7722617595376, longitude: 128.948275405137 },
  destination: { latitude: 37.7950741626953, longitude: 128.896636344738 },
  categories: ["ATTRACTION"],
  deadlineMinutes: 180,
  safetyBufferMinutes: 10,
  timeModel: "ARRIVAL_DEADLINE_V1",
  arrivalDeadlineEpochMillis: now.getTime() + 180 * 60_000
};

function place(name, extra = {}) {
  return {
    content_id: name,
    source: "TOUR_API",
    name,
    category: "ATTRACTION",
    content_type_id: 12,
    cat1: null,
    cat2: null,
    cat3: null,
    latitude: 37.785,
    longitude: 128.911,
    default_stay_minutes: 60,
    opening_hours: null,
    overview: null,
    ...extra
  };
}

const ancillaryNames = [
  "강문해변화장실",
  "경포호수 공중화장실",
  "오죽헌박물관 주차장",
  "강릉종합사회복지관",
  "교1동 행정복지센터",
  "경포동 주민센터",
  "대한노인회 강릉시지회"
];
const tourismNames = [
  "경포호수광장",
  "순포해변",
  "순포습지",
  "주차장미술관",
  "화장실문화전시관 해우재",
  "옛 주차장(미술관)",
  "주민센터 옆 작은 책방"
];

function route() {
  return { firstLegMinutes: 10, secondLegMinutes: 5, directMinutes: 12, detourMinutes: 3 };
}

test("실제 원본 type12 부대·행정시설은 정밀 경로 슬롯을 차지하지 않는다", () => {
  const candidates = [...ancillaryNames.map((name) => place(name)), place("순포해변")];
  assert.deepEqual(
    selectRouteCandidates(criteria, candidates, 8, now).map((item) => item.name),
    ["순포해변"]
  );
});

test("최종 V1 추천에서도 부대시설은 경로 계산 전에 제외한다", () => {
  const visited = [];
  const result = recommendPlaces(
    criteria,
    [...ancillaryNames.map((name) => place(name)), place("경포호수광장")],
    (_start, _destination, item) => { visited.push(item.name); return route(); },
    now
  );
  assert.deepEqual(visited, ["경포호수광장"]);
  assert.deepEqual(result.map((item) => item.place.name), ["경포호수광장"]);
  assert.equal(result[0].operationStatus, "UNKNOWN");
});

test("부대시설 단어를 포함한 실제 관광지·문화 전환시설 이름은 유지한다", () => {
  const candidates = tourismNames.map((name) => place(name));
  assert.deepEqual(
    recommendPlaces(criteria, candidates, route, now).map((item) => item.place.name),
    tourismNames
  );
  assert.equal(selectRouteCandidates(criteria, candidates, 8, now).length, tourismNames.length);
});

test("이름 공백은 정규화하되 사진·검수 상태가 시설명 제외를 뒤집지 않는다", () => {
  const toilet = place("  강문해변 화장실  ", {
    image_url: "https://example.com/toilet.jpg",
    operating_info_status: "VERIFIED"
  });
  assert.deepEqual(recommendPlaces(criteria, [toilet], route, now), []);
});

test("카테고리 오분류와 legacy 도보에서도 동일한 최소 적합성 기준을 지킨다", () => {
  const { timeModel: _timeModel, arrivalDeadlineEpochMillis: _deadline, ...legacy } = criteria;
  const allCategories = { ...legacy, mode: "NEARBY", transport: "WALK", categories: [] };
  const candidates = [place("관광지 공중화장실", { category: "CULTURE", content_type_id: 14 }), place("순포습지")];
  assert.deepEqual(
    recommendPlaces(allCategories, candidates, route, now).map((item) => item.place.name),
    ["순포습지"]
  );
  assert.deepEqual(selectRouteCandidates(allCategories, candidates, 8, now).map((item) => item.name), ["순포습지"]);
});

test("이름 누락을 임의의 시설 오분류로 추정하지 않는다", () => {
  for (const name of [null, undefined, ""]) {
    assert.equal(recommendPlaces(criteria, [place(name)], route, now).length, 1);
  }
});

test("원본 카탈로그·좌표는 수정하지 않아 검색과 주차 상세에 계속 쓸 수 있다", () => {
  const raw = {
    contentid: "3547899", contenttypeid: "12", title: "강문해변화장실",
    mapy: "37.785", mapx: "128.911"
  };
  const catalogItem = Object.freeze(mapTourItem(raw, "2026-09-13T00:00:00Z"));
  const before = JSON.stringify(catalogItem);
  assert.equal(catalogItem.name, "강문해변화장실");
  assert.equal(catalogItem.is_active, true);
  assert.deepEqual(recommendPlaces(criteria, [catalogItem], route, now), []);
  assert.equal(JSON.stringify(catalogItem), before);
});
