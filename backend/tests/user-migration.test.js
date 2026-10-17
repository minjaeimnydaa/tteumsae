import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL("../migrations/003_user_accounts.sql", import.meta.url);

async function readMigration() {
  return readFile(migrationUrl, "utf8").catch(() => "");
}

test("사용자 테이블은 소유자 RLS와 최소 권한을 선언한다", async () => {
  const sql = await readMigration();

  assert.match(sql, /create table public\.profiles/i);
  assert.match(sql, /create table public\.user_saved_places/i);
  assert.equal((sql.match(/enable row level security/gi) ?? []).length, 2);
  assert.match(sql, /auth\.uid\(\) is not null/gi);
  assert.doesNotMatch(sql, /grant .* to anon/i);
  assert.match(sql, /references auth\.users\(id\) on delete cascade/i);
  assert.doesNotMatch(sql, /grant delete .*authenticated/i);
});

test("프로필 선택값과 저장 tombstone 계약을 제한한다", async () => {
  const sql = await readMigration();

  assert.match(sql, /'UNDER_20','TWENTIES','THIRTIES','FORTIES',[\s\S]*'FIFTIES','SIXTY_PLUS','PREFER_NOT_TO_SAY'/i);
  assert.match(sql, /'FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY'/i);
  assert.match(sql, /display_name[\s\S]*between 1 and 40/i);
  assert.match(sql, /avatar_url[\s\S]*<= 2048/i);
  assert.match(sql, /is_saved boolean not null/i);
  assert.match(sql, /saved_at timestamptz/i);
  assert.match(sql, /primary key \(user_id, place_id\)/i);
});
