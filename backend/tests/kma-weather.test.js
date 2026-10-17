import test from "node:test";
import assert from "node:assert/strict";
import {
  fetchKmaShortForecast,
  isWeatherRelevantPlace,
  latestShortForecastIssue,
  normalizeWeatherForecast,
  resolvePlaceWeather,
  toKmaGrid,
  weatherForecastTarget
} from "../lib/kma-weather.js";

test("위경도를 기상청 5km 격자로 변환한다", () => {
  assert.deepEqual(toKmaGrid(37.7519, 128.8761), { nx: 92, ny: 132 });
});

test("발표 후 15분이 지난 최신 단기예보 기준시각을 고른다", () => {
  assert.deepEqual(latestShortForecastIssue(new Date("2026-09-07T00:20:00.000Z")), {
    baseDate: "20260907",
    baseTime: "0800",
    issuedAt: "2026-09-06T23:00:00.000Z"
  });
  assert.deepEqual(latestShortForecastIssue(new Date("2026-09-06T17:05:00.000Z")), {
    baseDate: "20260906",
    baseTime: "2300",
    issuedAt: "2026-09-06T14:00:00.000Z"
  });
});

test("도착 무렵의 다음 정시를 예보 목표로 사용한다", () => {
  assert.deepEqual(weatherForecastTarget(new Date("2026-09-08T01:20:00.000Z")), {
    forecastDate: "20260908",
    forecastTime: "1100",
    forecastAt: "2026-09-08T02:00:00.000Z"
  });
});

test("같은 예보시각의 기온·강수·하늘·바람을 하나로 정규화한다", () => {
  const row = normalizeWeatherForecast([
    { fcstDate: "20260908", fcstTime: "1100", category: "TMP", fcstValue: "18" },
    { fcstDate: "20260908", fcstTime: "1100", category: "POP", fcstValue: "70" },
    { fcstDate: "20260908", fcstTime: "1100", category: "PTY", fcstValue: "1" },
    { fcstDate: "20260908", fcstTime: "1100", category: "SKY", fcstValue: "4" },
    { fcstDate: "20260908", fcstTime: "1100", category: "WSD", fcstValue: "3.2" }
  ], {
    nx: 92,
    ny: 131,
    target: weatherForecastTarget(new Date("2026-09-08T01:20:00.000Z")),
    issuedAt: "2026-09-07T23:00:00.000Z",
    fetchedAt: "2026-09-07T23:20:00.000Z"
  });

  assert.equal(row.condition_label, "비 예상");
  assert.equal(row.temperature_c, 18);
  assert.equal(row.precipitation_probability, 70);
  assert.equal(row.wind_speed_mps, 3.2);
});

test("확인된 야외 장소만 날씨 대상으로 삼는다", () => {
  assert.equal(isWeatherRelevantPlace({ cat1: "A01", tags: [] }), true);
  assert.equal(isWeatherRelevantPlace({ cat1: "A02", tags: ["야외 활동"] }), true);
  assert.equal(isWeatherRelevantPlace({ cat1: "A01", tags: ["실내 활동"] }), false);
  assert.equal(isWeatherRelevantPlace({ category: "LEISURE", tags: [] }), false);
});

test("단기예보 요청은 공식 경로·격자·발표시각과 별도 예산을 사용한다", async () => {
  process.env.KMA_SHORT_FORECAST_SERVICE_KEY = "weather-key";
  process.env.KMA_WEATHER_DAILY_BUDGET = "1000";
  let requested;
  let tracked;
  const result = await fetchKmaShortForecast({ nx: 92, ny: 131 }, {
    now: new Date("2026-09-07T00:20:00.000Z"),
    fetchImpl: async (url) => {
      requested = new URL(String(url));
      return Response.json({ response: { header: { resultCode: "00" }, body: {
        items: { item: [] }, totalCount: 0
      } } });
    },
    usageTracker: async (input) => {
      tracked = input;
      return input.call();
    }
  });

  assert.equal(requested.pathname.endsWith("/getVilageFcst"), true);
  assert.equal(requested.searchParams.get("ServiceKey"), "weather-key");
  assert.equal(requested.searchParams.get("base_date"), "20260907");
  assert.equal(requested.searchParams.get("base_time"), "0800");
  assert.equal(requested.searchParams.get("nx"), "92");
  assert.equal(requested.searchParams.get("ny"), "131");
  assert.equal(tracked.provider, "KMA");
  assert.equal(tracked.operation, "VILAGE_FCST");
  assert.equal(tracked.budgetLimit, 1000);
  assert.deepEqual(result.items, []);
});

test("같은 발표의 격자 캐시를 우선하고 없으면 새 예보를 저장한다", async () => {
  const place = { latitude: 37.7519, longitude: 128.8761, cat1: "A01", tags: [] };
  const arrival = new Date("2026-09-08T01:20:00.000Z");
  const now = new Date("2026-09-07T00:20:00.000Z");
  const cached = { forecast_at: "2026-09-08T02:00:00.000Z", condition_label: "맑음",
    temperature_c: 20, issued_at: "2026-09-06T23:00:00.000Z",
    fetched_at: "2026-09-07T00:00:00.000Z" };
  let fetched = false;
  const fromCache = await resolvePlaceWeather(place, arrival, {
    now,
    loadCache: async () => cached,
    fetchForecast: async () => { fetched = true; },
    saveCache: async () => assert.fail("캐시가 있으면 저장하지 않습니다.")
  });
  assert.equal(fromCache.condition_label, "맑음");
  assert.equal(fetched, false);

  let saved;
  const fresh = await resolvePlaceWeather(place, arrival, {
    now,
    loadCache: async () => null,
    fetchForecast: async () => ({
      issue: latestShortForecastIssue(now),
      items: [
        { fcstDate: "20260908", fcstTime: "1100", category: "TMP", fcstValue: "18" },
        { fcstDate: "20260908", fcstTime: "1100", category: "POP", fcstValue: "10" },
        { fcstDate: "20260908", fcstTime: "1100", category: "PTY", fcstValue: "0" },
        { fcstDate: "20260908", fcstTime: "1100", category: "SKY", fcstValue: "1" }
      ]
    }),
    saveCache: async (row) => { saved = row; }
  });
  assert.equal(fresh.condition_label, "맑음");
  assert.equal(saved.nx, 92);
  assert.equal(saved.ny, 132);
});
