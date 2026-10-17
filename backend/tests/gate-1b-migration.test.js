import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL(
  "../migrations/006_gate_1b_data_trust.sql",
  import.meta.url
);

async function readMigration() {
  return readFile(migrationUrl, "utf8").catch(() => "");
}

test("Gate 1-B migration은 일일 사용량을 상한 안에서만 원자 예약한다", async () => {
  const sql = await readMigration();

  assert.match(sql, /create table if not exists public\.provider_usage_daily/i);
  assert.match(sql, /primary key \(usage_date, provider, operation\)/i);
  assert.match(sql, /create or replace function public\.reserve_provider_usage/i);
  assert.match(sql, /on conflict \(usage_date, provider, operation\) do update/i);
  assert.match(
    sql,
    /provider_usage_daily\.reserved_count\s*\+\s*excluded\.reserved_count\s*<=\s*excluded\.budget_limit/i
  );
  assert.match(sql, /create or replace function public\.record_provider_usage_result/i);
});

test("Gate 1-B migration은 원본과 분리된 검수 overlay와 운영 집계를 선언한다", async () => {
  const sql = await readMigration();

  assert.match(sql, /create table if not exists public\.place_curations/i);
  assert.match(sql, /create or replace view public\.effective_places/i);
  assert.match(sql, /security_invoker\s*=\s*true/i);
  assert.match(sql, /create or replace function public\.get_gate_1b_ops_status/i);
  assert.match(sql, /set search_path = ''/i);
  assert.match(sql, /revoke execute[\s\S]+from public, anon, authenticated/i);
  assert.match(sql, /grant execute[\s\S]+to service_role/i);
});
