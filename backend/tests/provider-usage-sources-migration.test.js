import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL(
  "../migrations/012_provider_usage_sources.sql",
  import.meta.url
);

test("신규 날씨·공공데이터 공급자를 사용량 원장과 예약 RPC가 허용한다", async () => {
  const sql = await readFile(migrationUrl, "utf8").catch(() => "");

  assert.match(sql, /drop constraint if exists provider_usage_daily_provider_check/i);
  assert.match(sql, /'KMA'/i);
  assert.match(sql, /'PUBLIC_DATA'/i);
  assert.match(sql, /create or replace function public\.reserve_provider_usage/i);
  assert.match(sql, /grant execute[\s\S]+to service_role/i);
});
