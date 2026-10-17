import test from "node:test";
import assert from "node:assert/strict";
import { createPlaceHandler } from "../api/places/[id].js";

const place = { content_id: "123", name: "테스트 장소", latitude: 37.79, longitude: 128.9 };
const request = () => new Request("https://example.test/api/places/123");

for (const dependency of ["getCongestion", "getParking"]) {
  test(`${dependency} 일시 장애에도 기본 장소 상세를 제공한다`, async () => {
    const handler = createPlaceHandler({
      getPlace: async () => place,
      getCongestion: async () => null,
      getParking: async () => [],
      [dependency]: async () => { throw new Error("private provider response"); }
    });
    const response = await handler.fetch(request());
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.data.name, place.name);
    assert.equal(body.data.congestion_forecast, undefined);
    assert.equal(body.data.nearby_parking_lots, undefined);
    assert.ok(!JSON.stringify(body).includes("private provider response"));
  });
}

test("부가 조회가 함께 실패해도 상세는 유지하고 성공한 날씨는 보존한다", async () => {
  const previous = process.env.KMA_WEATHER_ENABLED;
  process.env.KMA_WEATHER_ENABLED = "true";
  try {
    const handler = createPlaceHandler({
      getPlace: async () => place,
      getCongestion: async () => { throw new Error("timeout"); },
      getParking: async () => { throw new Error("timeout"); },
      getWeather: async () => ({ temperature_c: 21 })
    });
    const response = await handler.fetch(request());
    assert.equal(response.status, 200);
    assert.equal((await response.json()).data.weather_forecast.temperature_c, 21);
  } finally {
    if (previous === undefined) delete process.env.KMA_WEATHER_ENABLED;
    else process.env.KMA_WEATHER_ENABLED = previous;
  }
});

test("기본 장소 조회의 실패는 부가 데이터 폴백으로 숨기지 않는다", async () => {
  const handler = createPlaceHandler({
    getPlace: async () => { throw new Error("database unavailable"); },
    getCongestion: async () => assert.fail("기본 조회 실패 뒤 부가 조회 금지"),
    getParking: async () => assert.fail("기본 조회 실패 뒤 부가 조회 금지")
  });
  assert.equal((await handler.fetch(request())).status, 500);
});
