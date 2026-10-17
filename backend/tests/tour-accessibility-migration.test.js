import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL("../migrations/010_tour_accessibility.sql", import.meta.url);

test("무장애 상세 migration은 원자 병합 RPC와 service-role 경계를 만든다", async () => {
  const sql = await readFile(migrationUrl, "utf8");

  assert.match(sql, /add column if not exists accessibility_synced_at timestamptz/i);
  assert.match(sql, /create or replace function public\.save_place_accessibility/i);
  assert.match(sql, /enrichment_raw\s*=\s*coalesce\(enrichment_raw/i);
  assert.match(sql, /jsonb_build_object\(\s*'accessibility'/i);
  assert.match(sql, /revoke execute on function public\.save_place_accessibility/i);
  assert.match(sql, /grant execute on function public\.save_place_accessibility[\s\S]*to service_role/i);
  assert.match(sql, /insert into public\.sync_state \(id\)[\s\S]*tour_accessibility/i);
});
