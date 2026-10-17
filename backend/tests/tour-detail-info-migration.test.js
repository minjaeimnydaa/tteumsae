import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL("../migrations/007_tour_detail_info.sql", import.meta.url);

test("반복 상세 동기화 상태와 pending 인덱스를 추가한다", async () => {
  const sql = await readFile(migrationUrl, "utf8");

  assert.match(sql, /add column if not exists info_synced_at timestamptz/i);
  assert.match(sql, /places_info_pending_idx/i);
  assert.match(sql, /info_synced_at is null/i);
  assert.doesNotMatch(sql, /service_role_key|serviceKey|secret/i);
});
