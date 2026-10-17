import test from "node:test";
import assert from "node:assert/strict";
import {
  lookupKakaoRegion,
  parseKakaoPlaces,
  parseKakaoRegion,
  searchKakaoPlaces
} from "../lib/kakao-local.js";

const payload = {
  documents: [
    {
      id: "100",
      place_name: "강릉역",
      road_address_name: "강원특별자치도 강릉시 용지로 176",
      address_name: "강원특별자치도 강릉시 교동 118",
      category_name: "교통 > 기차역",
      x: "128.8991",
      y: "37.7640",
      place_url: "https://place.map.kakao.com/100"
    }
  ]
};
const untrackedUsage = async ({ call }) => call();

test("카카오 로컬 장소를 앱용 좌표 모델로 변환한다", () => {
  const places = parseKakaoPlaces(payload);

  assert.equal(places.length, 1);
  assert.equal(places[0].name, "강릉역");
  assert.equal(places[0].longitude, 128.8991);
  assert.equal(places[0].latitude, 37.764);
});

test("현재 좌표가 있으면 거리순 반경 검색을 사용한다", async () => {
  let requestedUrl;
  const places = await searchKakaoPlaces("강릉역", {
    latitude: 37.75,
    longitude: 128.88,
    apiKey: "test-key",
    usageTracker: untrackedUsage,
    fetchImpl: async (url, options) => {
      requestedUrl = { url, options };
      return {
        ok: true,
        async json() {
          return payload;
        }
      };
    }
  });

  const url = new URL(requestedUrl.url);
  assert.equal(url.searchParams.get("sort"), "distance");
  assert.equal(url.searchParams.get("radius"), "20000");
  assert.equal(requestedUrl.options.headers.authorization, "KakaoAK test-key");
  assert.equal(places.length, 1);
});

test("좌표 행정구역이 강원특별자치도인지 판정한다", async () => {
  const regionPayload = {
    documents: [{
      region_type: "H",
      address_name: "강원특별자치도 강릉시 중앙동",
      region_1depth_name: "강원특별자치도"
    }]
  };

  assert.equal(parseKakaoRegion(regionPayload).isGangwon, true);
  const region = await lookupKakaoRegion(37.75, 128.88, {
    apiKey: "test-key",
    usageTracker: untrackedUsage,
    fetchImpl: async (_url, options) => {
      assert.equal(options.headers.authorization, "KakaoAK test-key");
      return { ok: true, async json() { return regionPayload; } };
    }
  });
  assert.equal(region.address, "강원특별자치도 강릉시 중앙동");
});

test("Kakao Local 검색·행정구역 요청은 caller signal과 timeout을 결합한다", async () => {
  const controller = new AbortController();
  const signals = [];
  const fetchImpl = async (_url, options) => {
    signals.push(options.signal);
    return Response.json({ documents: [] });
  };

  await searchKakaoPlaces("강릉역", {
    apiKey: "test-key",
    signal: controller.signal,
    usageTracker: untrackedUsage,
    fetchImpl
  });
  await lookupKakaoRegion(37.75, 128.88, {
    apiKey: "test-key",
    signal: controller.signal,
    usageTracker: untrackedUsage,
    fetchImpl
  });

  assert.equal(signals.length, 2);
  assert.ok(signals.every((signal) => signal instanceof AbortSignal));
});

test("Kakao Local 검색과 지역 조회는 서로 다른 operation으로 계측한다", async () => {
  const operations = [];
  const usageTracker = async (input) => {
    operations.push(`${input.provider}/${input.operation}`);
    return input.call();
  };
  const fetchImpl = async (url) => {
    const isRegion = new URL(url).pathname.includes("coord2regioncode");
    return Response.json(isRegion
      ? { documents: [{ region_type: "H", region_1depth_name: "강원특별자치도" }] }
      : { documents: [] });
  };

  await searchKakaoPlaces("강릉역", { apiKey: "key", fetchImpl, usageTracker });
  await lookupKakaoRegion(37.75, 128.88, { apiKey: "key", fetchImpl, usageTracker });

  assert.deepEqual(operations, [
    "KAKAO_LOCAL/KEYWORD_SEARCH",
    "KAKAO_LOCAL/REGION"
  ]);
});
