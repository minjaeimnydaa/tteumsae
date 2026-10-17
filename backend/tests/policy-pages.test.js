import test from "node:test";
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";

async function readOrEmpty(path) {
  return readFile(new URL(path, import.meta.url), "utf8").catch(() => "");
}

test("개인정보처리방침은 서비스·운영자·처리 항목과 삭제 정책을 공개한다", async () => {
  const html = await readOrEmpty("../privacy.html");

  assert.match(html, /<!doctype html>/i);
  assert.match(html, /<html[^>]+lang="ko"/i);
  assert.match(html, /개인정보처리방침/);
  assert.match(html, /틈새|Tteumsae/);
  assert.match(html, /신홍/);
  assert.match(html, /mailto:godburgundy@gmail\.com/i);
  assert.match(html, /위치/);
  assert.match(html, /닉네임/);
  assert.match(html, /연령대/);
  assert.match(html, /성별/);
  assert.match(html, /보유|보관/);
  assert.match(html, /파기|삭제/);
  assert.match(html, /Supabase/);
  assert.match(html, /Vercel/);
  assert.match(html, /저장한 장소는 이 기기에만 저장/);
  assert.doesNotMatch(html, /저장한 장소 동기화/);
  assert.doesNotMatch(html, /<script\b/i);
  assert.doesNotMatch(html, /fonts\.(googleapis|gstatic)\.com|analytics|tracker/i);
});

test("계정 삭제 페이지는 앱 내부와 이메일 요청 경로를 모두 제공한다", async () => {
  const html = await readOrEmpty("../account-deletion.html");

  assert.match(html, /<!doctype html>/i);
  assert.match(html, /틈새|Tteumsae/);
  assert.match(html, /신홍/);
  assert.match(html, /설정[\s\S]*계정[\s\S]*계정 삭제/);
  assert.match(html, /mailto:godburgundy@gmail\.com/i);
  assert.match(html, /로그인 제공자/);
  assert.match(html, /프로필/);
  assert.match(html, /저장한 장소/);
  assert.match(html, /기기에만 저장한 장소는 계정 삭제로 삭제되지/);
  assert.match(html, /비밀번호|토큰/);
  assert.match(html, /요청하지/);
  assert.doesNotMatch(html, /<script\b/i);
});

test("Vercel은 기존 설정을 유지하면서 정책 페이지 clean URL을 제공한다", async () => {
  const config = JSON.parse(await readOrEmpty("../vercel.json"));

  assert.deepEqual(
    config.rewrites.find((rewrite) => rewrite.source === "/privacy"),
    { source: "/privacy", destination: "/privacy.html" }
  );
  assert.deepEqual(
    config.rewrites.find((rewrite) => rewrite.source === "/account-deletion"),
    { source: "/account-deletion", destination: "/account-deletion.html" }
  );
  assert.ok(config.rewrites.some((rewrite) => rewrite.source.includes("downloads")));
  assert.deepEqual(config.crons, [
    { path: "/api/cron/tour-catalog-sync", schedule: "20 18 * * *" },
    { path: "/api/cron/tour-intro-sync", schedule: "20 22 * * *" },
    {
      path: "/api/cron/tour-intro-sync?stage=presentation",
      schedule: "40 22 * * *"
    }
  ]);
});

test("Vercel Hobby 배포 함수는 12개를 넘지 않는다", async () => {
  const apiRoot = new URL("../api/", import.meta.url);
  const countFunctions = async (directory) => {
    const entries = await readdir(directory, { withFileTypes: true });
    let count = 0;
    for (const entry of entries) {
      if (entry.isDirectory()) {
        count += await countFunctions(new URL(`${entry.name}/`, directory));
      } else if (entry.name.endsWith(".js")) {
        count += 1;
      }
    }
    return count;
  };

  assert.ok(await countFunctions(apiRoot) <= 12);
});
