import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migrationUrl = new URL("../migrations/001_initial.sql", import.meta.url);

test("장소와 동기화 상태는 서버 역할에만 명시적으로 쓰기 권한을 준다", async () => {
  const sql = await readFile(migrationUrl, "utf8");

  assert.match(
    sql,
    /revoke all on public\.places, public\.sync_state from anon, authenticated/i
  );
  assert.match(
    sql,
    /grant select, insert, update, delete on public\.places, public\.sync_state to service_role/i
  );
  assert.doesNotMatch(sql, /grant .* on public\.(places|sync_state) to (anon|authenticated)/i);
});
