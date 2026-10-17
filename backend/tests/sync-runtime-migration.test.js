import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("동기화 lease migration은 원자 claim과 service-role 전용 권한을 선언한다", async () => {
  const sql = await readFile(
    new URL("../migrations/005_sync_runtime_safety.sql", import.meta.url),
    "utf8"
  );
  for (const column of [
    "lease_token",
    "lease_expires_at",
    "last_started_at",
    "last_finished_at",
    "last_status",
    "last_duration_ms",
    "last_run_summary"
  ]) {
    assert.match(sql, new RegExp(`add column if not exists ${column}`, "i"));
  }
  assert.match(sql, /create or replace function public\.claim_sync_job/i);
  assert.match(sql, /on conflict \(id\) do update/i);
  assert.match(sql, /lease_expires_at\s*<=\s*p_now/i);
  assert.match(sql, /create or replace function public\.finish_sync_job/i);
  assert.match(sql, /lease_token\s*=\s*p_token/i);
  assert.match(sql, /revoke execute[\s\S]*from public, anon, authenticated/i);
  assert.match(sql, /grant execute[\s\S]*to service_role/i);
});
