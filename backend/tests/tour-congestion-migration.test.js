import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("관광지 혼잡 예측은 원천 행과 보수적 장소 매칭을 서버 전용으로 저장한다", async () => {
  const sql = await readFile(
    new URL("../migrations/008_tour_congestion_forecasts.sql", import.meta.url),
    "utf8"
  );

  assert.match(sql, /create table if not exists public\.tour_congestion_forecasts/i);
  assert.match(sql, /concentration_rate numeric[\s\S]*between|concentration_rate >= 0/i);
  assert.match(sql, /content_id text references public\.places\(content_id\)/i);
  assert.match(sql, /MATCHED[\s\S]*AMBIGUOUS[\s\S]*UNMATCHED/i);
  assert.match(sql, /enable row level security/i);
  assert.match(sql, /revoke all[\s\S]*anon[\s\S]*authenticated/i);
  assert.match(sql, /grant select, insert, update, delete[\s\S]*service_role/i);
});
