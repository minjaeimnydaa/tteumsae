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
    fetchImpl: async (_url, options) => {
      assert.equal(options.headers.authorization, "KakaoAK test-key");
      return { ok: true, async json() { return regionPayload; } };
    }
  });
  assert.equal(region.address, "강원특별자치도 강릉시 중앙동");
});
