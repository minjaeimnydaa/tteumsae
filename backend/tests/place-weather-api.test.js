import test from "node:test";
import assert from "node:assert/strict";
import { createPlaceHandler } from "../api/places/[id].js";

const place = {
  content_id: "123",
  name: "경포대",
  category: "ATTRACTION",
  cat1: "A01",
  latitude: 37.79,
  longitude: 128.9,
  tags: []
};

test("기능이 켜지면 도착 예정시각의 야외 날씨를 장소 상세에 붙인다", async () => {
  process.env.KMA_WEATHER_ENABLED = "true";
  let arrival;
  const handler = createPlaceHandler({
    getPlace: async () => place,
    getCongestion: async () => null,
    getParking: async () => [],
    getWeather: async (_place, value) => {
      arrival = value;
      return { condition_label: "비 예상", temperature_c: 18 };
    }
  });
  try {
    const epoch = Date.parse("2026-09-08T01:20:00.000Z");
    const response = await handler.fetch(new Request(
      `https://example.test/api/places/123?atEpochMillis=${epoch}`
    ));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(arrival.toISOString(), "2026-09-08T01:20:00.000Z");
    assert.equal(body.data.weather_forecast.condition_label, "비 예상");
  } finally {
    delete process.env.KMA_WEATHER_ENABLED;
  }
});

test("날씨 공급자 실패는 기존 장소 상세를 실패시키지 않는다", async () => {
  process.env.KMA_WEATHER_ENABLED = "true";
  const handler = createPlaceHandler({
    getPlace: async () => place,
    getCongestion: async () => null,
    getParking: async () => [],
    getWeather: async () => { throw new Error("provider failed"); }
  });
  try {
    const response = await handler.fetch(new Request("https://example.test/api/places/123"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.data.name, "경포대");
    assert.equal(body.data.weather_forecast, undefined);
  } finally {
    delete process.env.KMA_WEATHER_ENABLED;
  }
});
