import test from "node:test";
import assert from "node:assert/strict";
import * as local from "../lib/kakao-local.js";

const roadDocument = {
  address_name: "강원특별자치도 강릉시 용지로 176",
  address_type: "ROAD_ADDR",
  x: "128.899627553491",
  y: "37.7645235587621",
  address: { address_name: "강원특별자치도 강릉시 교동 118" },
  road_address: { address_name: "강원특별자치도 강릉시 용지로 176" }
};
const placeDocument = {
  id: "8632588", place_name: "강릉역", x: roadDocument.x, y: roadDocument.y,
  road_address_name: roadDocument.address_name
};
const untrackedUsage = async ({ call }) => call();

function searchOptions(fetchImpl, extra = {}) {
  return { apiKey: "test-key", usageTracker: untrackedUsage, fetchImpl, ...extra };
}

test("도로명 주소는 장소 검색보다 주소 검색을 먼저 사용한다", async () => {
  const calls = [];
  const results = await local.searchKakaoPlaces("강릉시 용지로 176", searchOptions(async (input) => {
    const url = new URL(input);
    calls.push(url);
    return Response.json({ documents: url.pathname.includes("address.json") ? [roadDocument] : [placeDocument] });
  }));
  assert.equal(calls.length, 1);
  assert.match(calls[0].pathname, /address\.json$/);
  assert.equal(calls[0].searchParams.get("query"), "강릉시 용지로 176");
  assert.equal(calls[0].searchParams.get("analyze_type"), "exact");
  assert.equal(results[0].type, "ADDRESS");
  assert.equal(results[0].name, roadDocument.address_name);
});

test("지번과 건물 부번 주소도 주소 검색으로 처리한다", async () => {
  for (const query of ["전북 익산시 부송동 100", "강릉시 교동 118-2", "강릉시 성산면 구산리 산 10-2", "강릉시 용지로176", "서울 종로 1"]) {
    await local.searchKakaoPlaces(query, searchOptions(async (input) => {
      assert.match(new URL(input).pathname, /address\.json$/, query);
      return Response.json({ documents: [roadDocument] });
    }));
  }
});

test("숫자가 포함된 장소명과 출구 검색은 기존 키워드 검색을 유지한다", async () => {
  for (const query of ["강릉역", "카페 100", "국민체력100 익산체력인증센터", "365의원", "강릉역 2번출구", "종로 3가 맛집", "강릉시 용지로 176 주차장"]) {
    const results = await local.searchKakaoPlaces(query, searchOptions(async (input) => {
      assert.match(new URL(input).pathname, /keyword\.json$/, query);
      return Response.json({ documents: [placeDocument] });
    }));
    assert.equal(results[0].id, placeDocument.id);
  }
});

test("주소를 찾지 못하면 장소 검색을 한 번만 보조 조회한다", async () => {
  const paths = [];
  const results = await local.searchKakaoPlaces("강릉시 없는길 9999", searchOptions(async (input) => {
    const path = new URL(input).pathname;
    paths.push(path);
    return Response.json({ documents: path.includes("address.json") ? [] : [placeDocument] });
  }));
  assert.deepEqual(paths, ["/v2/local/search/address.json", "/v2/local/search/keyword.json"]);
  assert.equal(results[0].id, placeDocument.id);
});

test("주소 후보는 지번 보조 주소와 기존 필드를 유지하고 ID가 안정적이다", () => {
  const first = local.parseKakaoAddresses({ documents: [roadDocument] })[0];
  const second = local.parseKakaoAddresses({ documents: [{ ...roadDocument, address_type: "REGION_ADDR", address_name: roadDocument.address.address_name }] })[0];
  assert.match(first.id, /^address:[a-f0-9]+$/);
  assert.equal(first.id, second.id);
  assert.equal(first.address, roadDocument.road_address.address_name);
  assert.equal(first.secondaryAddress, roadDocument.address.address_name);
  assert.equal(first.category, "주소");
  assert.equal(first.kakaoMapUrl, "");
  assert.equal(first.latitude, Number(roadDocument.y));
  assert.equal(first.longitude, Number(roadDocument.x));
});

test("도로명이 없는 지번 주소도 선택할 수 있다", () => {
  const item = local.parseKakaoAddresses({ documents: [{
    address_type: "REGION_ADDR", address_name: "강원특별자치도 강릉시 교동 118",
    x: "128.8996", y: "37.7645", road_address: null,
    address: { address_name: "강원특별자치도 강릉시 교동 118" }
  }] })[0];
  assert.equal(item.name, "강원특별자치도 강릉시 교동 118");
  assert.equal(item.address, item.name);
  assert.equal(item.secondaryAddress, "");
});

test("주소 결과의 중복을 제거하되 서로 다른 주소는 보존한다", () => {
  const results = local.parseKakaoAddresses({ documents: [
    roadDocument,
    { ...roadDocument, address_type: "REGION_ADDR" },
    { ...roadDocument, road_address: { address_name: "서울특별시 용산구 한강대로 405" } }
  ] });
  assert.equal(results.length, 2);
  assert.ok(results[0].address.startsWith("강원"));
  assert.ok(results[1].address.startsWith("서울"));
});

test("주소 후보의 잘못된 좌표와 지명 중심점은 제외한다", () => {
  const invalid = [
    { ...roadDocument, x: "" }, { ...roadDocument, y: null },
    { ...roadDocument, x: "128.8bad" }, { ...roadDocument, y: "NaN" },
    { ...roadDocument, y: "91" }, { ...roadDocument, x: "181" },
    { ...roadDocument, address_type: "ROAD" }, { ...roadDocument, address_type: "REGION" },
    { ...roadDocument, address_name: "", address: null, road_address: null }
  ];
  assert.deepEqual(local.parseKakaoAddresses({ documents: invalid }), []);
  assert.deepEqual(local.parseKakaoAddresses(null), []);
});

test("주소 API 오류는 잘못된 키워드 결과로 조용히 대체하지 않는다", async () => {
  let calls = 0;
  await assert.rejects(local.searchKakaoPlaces("강릉시 용지로 176", searchOptions(async () => {
    calls += 1;
    return Response.json({ message: "unavailable" }, { status: 503 });
  })), (error) => error.code === "UPSTREAM_ERROR");
  assert.equal(calls, 1);
});

test("주소 API 쿼터 오류는 기존 공급자 오류 계약을 유지한다", async () => {
  await assert.rejects(local.searchKakaoPlaces("강릉시 용지로 176", searchOptions(async () =>
    Response.json({ code: -10 }, { status: 429 })
  )), (error) => error.code === "UPSTREAM_QUOTA_EXHAUSTED");
});

test("주소 검색도 기존 키와 취소 신호 및 별도 호출량 operation을 사용한다", async () => {
  const controller = new AbortController();
  const operations = [];
  const results = await local.searchKakaoPlaces("강릉시 용지로 176", searchOptions(async (input, init) => {
    const url = new URL(input);
    assert.match(url.pathname, /address\.json$/);
    assert.equal(url.searchParams.has("x"), false);
    assert.equal(url.searchParams.has("radius"), false);
    assert.equal(init.headers.authorization, "KakaoAK test-key");
    assert.ok(init.signal instanceof AbortSignal);
    return Response.json({ documents: [roadDocument] });
  }, {
    signal: controller.signal, latitude: 37.5, longitude: 127,
    usageTracker: async (input) => { operations.push(`${input.provider}/${input.operation}`); return input.call(); }
  }));
  assert.equal(results.length, 1);
  assert.deepEqual(operations, ["KAKAO_LOCAL/ADDRESS_SEARCH"]);
});

test("공급자가 잘못된 주소 후보만 반환하면 보조 장소 검색을 사용한다", async () => {
  const paths = [];
  await local.searchKakaoPlaces("강릉시 용지로 176", searchOptions(async (input) => {
    const path = new URL(input).pathname;
    paths.push(path);
    return Response.json({ documents: path.includes("address.json") ? [{ ...roadDocument, address_type: "ROAD" }] : [] });
  }));
  assert.equal(paths.length, 2);
});
