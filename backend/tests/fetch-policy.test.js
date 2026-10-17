import test from "node:test";
import assert from "node:assert/strict";
import {
  NETWORK_TIMEOUT_MS,
  UpstreamTimeoutError,
  createDeadline,
  fetchWithTimeout
} from "../lib/fetch-policy.js";
import { serverError } from "../lib/http.js";

function neverCompletes(_url, { signal }) {
  return new Promise((_resolve, reject) => {
    signal.addEventListener("abort", () => reject(signal.reason), { once: true });
  });
}

test("provider timeout은 fetch를 중단하고 정규화된 오류를 낸다", async () => {
  await assert.rejects(
    fetchWithTimeout("https://example.test", {}, {
      provider: "TOUR_API",
      timeoutMs: 5,
      fetchImpl: neverCompletes
    }),
    (error) => error instanceof UpstreamTimeoutError && error.provider === "TOUR_API"
  );
});

test("호출자 abort는 upstream timeout으로 오분류하지 않는다", async () => {
  const controller = new AbortController();
  const running = fetchWithTimeout("https://example.test", {}, {
    provider: "KAKAO_LOCAL",
    timeoutMs: 1_000,
    signal: controller.signal,
    fetchImpl: neverCompletes
  });
  controller.abort(new Error("caller cancelled"));
  await assert.rejects(running, /caller cancelled/);
});

test("응답 body 소비도 같은 timeout 안에서 중단된다", async () => {
  await assert.rejects(
    fetchWithTimeout("https://example.test", {}, {
      provider: "SUPABASE",
      timeoutMs: 5,
      fetchImpl: async () => new Response("{}"),
      consume: async (_response, signal) => new Promise((_resolve, reject) => {
        signal.addEventListener("abort", () => reject(signal.reason), { once: true });
      })
    }),
    (error) => error instanceof UpstreamTimeoutError && error.provider === "SUPABASE"
  );
});

test("deadline은 남은 시간과 시작 가능 여부를 계산한다", () => {
  let now = 1_000;
  const deadline = createDeadline(25_000, { now: () => now });
  assert.equal(deadline.remainingMs(), 25_000);
  now = 21_001;
  assert.equal(deadline.canStart(5_000), false);
  deadline.dispose();
});

test("공개 timeout 응답은 세부정보 없이 504다", async () => {
  const originalConsoleError = console.error;
  console.error = () => {};
  try {
    const response = serverError(new UpstreamTimeoutError("SUPABASE"));
    const body = await response.json();
    assert.equal(response.status, 504);
    assert.equal(body.error.code, "UPSTREAM_TIMEOUT");
    assert.equal(Object.hasOwn(body.error, "provider"), false);
    assert.doesNotMatch(JSON.stringify(body), /SUPABASE/);
  } finally {
    console.error = originalConsoleError;
  }
});

test("provider 오류는 비밀값 없이 운영 진단 메타데이터를 구조화해 기록한다", () => {
  const logs = [];
  const originalConsoleError = console.error;
  console.error = (value) => logs.push(String(value));
  try {
    const error = Object.assign(new Error("외부 서비스가 요청을 처리하지 못했습니다."), {
      code: "UPSTREAM_ERROR",
      provider: "TOUR_API",
      status: 401,
      providerCode: "20"
    });
    serverError(error);
    const logged = JSON.parse(logs[0]);
    assert.equal(logged.level, "error");
    assert.equal(logged.message, "외부 서비스가 요청을 처리하지 못했습니다.");
    assert.equal(logged.code, "UPSTREAM_ERROR");
    assert.equal(logged.provider, "TOUR_API");
    assert.equal(logged.status, 401);
    assert.equal(logged.providerCode, "20");
    assert.equal(typeof logged.requestId, "string");
    assert.equal(Object.hasOwn(logged, "stack"), false);
  } finally {
    console.error = originalConsoleError;
  }
});

test("운영 timeout 상수는 설계값을 유지한다", () => {
  assert.deepEqual(NETWORK_TIMEOUT_MS, {
    SUPABASE: 5_000,
    KAKAO_LOCAL: 5_000,
    KAKAO_MOBILITY: 8_000,
    TOUR_API: 8_000,
    RECOMMENDATION: 25_000,
    CRON: 50_000
  });
});
