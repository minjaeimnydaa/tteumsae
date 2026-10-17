import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";

function runVerifier(env) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ["scripts/verify-user-rls.js"], {
      cwd: new URL("../", import.meta.url),
      env,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8").on("data", (chunk) => { stdout += chunk; });
    child.stderr.setEncoding("utf8").on("data", (chunk) => { stderr += chunk; });
    child.once("error", reject);
    child.once("close", (code) => resolve({ code, stdout, stderr }));
  });
}

test("RLS 검증 설정이 없으면 누락 변수를 모두 밝히고 안전하게 건너뛴다", async () => {
  const env = { ...process.env };
  delete env.SUPABASE_TEST_URL;
  delete env.SUPABASE_TEST_PUBLISHABLE_KEY;
  delete env.SUPABASE_TEST_SERVICE_ROLE_KEY;

  const result = await runVerifier(env);

  assert.equal(result.code, 0);
  assert.equal(result.stderr, "");
  assert.match(result.stdout, /SKIPPED/);
  assert.match(result.stdout, /SUPABASE_TEST_URL/);
  assert.match(result.stdout, /SUPABASE_TEST_PUBLISHABLE_KEY/);
  assert.match(result.stdout, /SUPABASE_TEST_SERVICE_ROLE_KEY/);
});
