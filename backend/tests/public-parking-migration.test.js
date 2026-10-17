import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("공영주차장 migration은 좌표 검색 인덱스와 서버 전용 권한을 만든다", async () => {
  const sql = await readFile(
    new URL("../migrations/011_public_parking_lots.sql", import.meta.url),
    "utf8"
  );
  assert.match(sql, /create table if not exists public\.public_parking_lots/i);
  assert.match(sql, /primary key/i);
  assert.match(sql, /create index[\s\S]*latitude[\s\S]*longitude/i);
  assert.match(sql, /alter table public\.public_parking_lots enable row level security/i);
  assert.match(sql, /revoke all on table public\.public_parking_lots from public, anon, authenticated/i);
  assert.match(sql, /grant select, insert, update, delete[\s\S]*to service_role/i);
  assert.match(sql, /values \('public_parking'\)/i);
});
