import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import {
  importAndroidPublicConfig,
  initializeLocalConfig,
  inspectWorkspace
} from "../../scripts/workspace-doctor.mjs";

async function fixture() {
  const root = await mkdtemp(join(tmpdir(), "tteumsae-handoff-"));
  await mkdir(join(root, "android"), { recursive: true });
  await mkdir(join(root, "backend"), { recursive: true });
  await writeFile(
    join(root, "android", "local.properties.example"),
    "sdk.dir=C\\:\\\\Users\\\\YOUR_NAME\\\\Android\\\\Sdk\n" +
      "KAKAO_MAP_NATIVE_APP_KEY=YOUR_KAKAO_NATIVE_APP_KEY\n" +
      "SUPABASE_URL=\nSUPABASE_PUBLISHABLE_KEY=\n"
  );
  await writeFile(
    join(root, "backend", ".env.example"),
    "TOUR_API_SERVICE_KEY=\nKAKAO_REST_API_KEY=\n" +
      "SUPABASE_URL=https://YOUR_PROJECT.supabase.co\n" +
      "SUPABASE_SERVICE_ROLE_KEY=\nCRON_SECRET=\n"
  );
  return root;
}

test("초기 설정은 로컬 템플릿을 만들되 기존 비밀 설정을 덮어쓰지 않는다", async () => {
  const root = await fixture();
  try {
    const first = await initializeLocalConfig(root);
    assert.deepEqual(first.created.sort(), [
      "android/local.properties",
      "backend/.env.local"
    ]);

    await writeFile(join(root, "android", "local.properties"), "PRIVATE=keep-me\n");
    const second = await initializeLocalConfig(root);

    assert.deepEqual(second.created, []);
    assert.deepEqual(second.preserved.sort(), [
      "android/local.properties",
      "backend/.env.local"
    ]);
    assert.equal(
      await readFile(join(root, "android", "local.properties"), "utf8"),
      "PRIVATE=keep-me\n"
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("환경 진단은 빠진 키 이름만 알리고 비밀값 자체는 출력 모델에 넣지 않는다", async () => {
  const root = await fixture();
  try {
    await writeFile(
      join(root, "android", "local.properties"),
      "sdk.dir=C\\:\\\\Android\\\\Sdk\n" +
        "KAKAO_MAP_NATIVE_APP_KEY=native-secret-value\n" +
        "SUPABASE_URL=https://sample.supabase.co\n" +
        "SUPABASE_PUBLISHABLE_KEY=publishable-secret-value\n"
    );
    await writeFile(
      join(root, "backend", ".env.local"),
      "TOUR_API_SERVICE_KEY=tour-secret-value\n" +
        "KAKAO_REST_API_KEY=\n" +
        "SUPABASE_URL=https://sample.supabase.co\n" +
        "SUPABASE_SERVICE_ROLE_KEY=service-secret-value\n" +
        "CRON_SECRET=cron-secret-value\n"
    );

    const report = await inspectWorkspace(root, { nodeVersion: "24.19.0" });
    const serialized = JSON.stringify(report);

    assert.equal(report.node.status, "ready");
    assert.equal(report.android.status, "ready");
    assert.equal(report.backend.status, "needs_input");
    assert.deepEqual(report.backend.missing, ["KAKAO_REST_API_KEY"]);
    assert.doesNotMatch(serialized, /native-secret-value|tour-secret-value|service-secret-value/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("환경 진단은 Node 24가 아니면 작업 기준과 실제 버전을 구분한다", async () => {
  const root = await fixture();
  try {
    const report = await inspectWorkspace(root, { nodeVersion: "20.20.1" });

    assert.deepEqual(report.node, {
      status: "blocked",
      expected: "24.x",
      actual: "20.20.1"
    });
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("Android 공개 설정 import는 Supabase 공개값만 복사하고 서버 비밀은 배제한다", async () => {
  const root = await fixture();
  try {
    await writeFile(
      join(root, "android", "local.properties"),
      "sdk.dir=C\\:\\\\Android\\\\Sdk\n" +
        "KAKAO_MAP_NATIVE_APP_KEY=keep-native-key\n" +
        "SUPABASE_URL=\nSUPABASE_PUBLISHABLE_KEY=\n"
    );
    const source = join(root, "backend", ".env.production.local");
    await writeFile(
      source,
      "SUPABASE_URL=\"https://sample.supabase.co\"\n" +
        "SUPABASE_PUBLISHABLE_KEY='sb_publishable_sample'\n" +
        "SUPABASE_SERVICE_ROLE_KEY=must-never-reach-android\n"
    );

    const result = await importAndroidPublicConfig(root, source);
    const androidConfig = await readFile(
      join(root, "android", "local.properties"),
      "utf8"
    );

    assert.deepEqual(result.updated, [
      "SUPABASE_URL",
      "SUPABASE_PUBLISHABLE_KEY"
    ]);
    assert.match(androidConfig, /SUPABASE_URL=https:\/\/sample\.supabase\.co/);
    assert.match(androidConfig, /SUPABASE_PUBLISHABLE_KEY=sb_publishable_sample/);
    assert.match(androidConfig, /KAKAO_MAP_NATIVE_APP_KEY=keep-native-key/);
    assert.doesNotMatch(androidConfig, /SERVICE_ROLE|must-never-reach-android/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
