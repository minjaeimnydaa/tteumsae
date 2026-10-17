import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("기상 캐시는 격자·예보시각별 서버 전용 데이터다", async () => {
  const sql = await readFile(new URL("../migrations/009_weather_forecast_cache.sql", import.meta.url), "utf8");
  assert.match(sql, /create table if not exists public\.weather_forecast_cache/i);
  assert.match(sql, /primary key \(nx, ny, forecast_at\)/i);
  assert.match(sql, /alter table public\.weather_forecast_cache enable row level security/i);
  assert.match(sql, /revoke all on table public\.weather_forecast_cache from public, anon, authenticated/i);
  assert.match(sql, /grant select, insert, update, delete on table public\.weather_forecast_cache to service_role/i);
});
