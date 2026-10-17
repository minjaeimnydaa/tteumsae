import test from "node:test";
import assert from "node:assert/strict";
import {
  ProviderBudgetExhaustedError,
  ProviderResponseError,
  classifyProviderResult,
  kstUsageDate,
  mobilityBudgetPolicy,
  walkBudgetPolicy,
  secondsUntilNextKstMidnight,
  trackProviderCall
} from "../lib/provider-usage.js";

test("KST 사용일과 다음 자정 대기시간은 UTC 날짜와 독립적이다", () => {
  const beforeMidnight = new Date("2026-08-28T14:59:30.000Z");
  const afterMidnight = new Date("2026-08-28T15:00:00.000Z");

  assert.equal(kstUsageDate(beforeMidnight), "2026-08-28");
  assert.equal(secondsUntilNextKstMidnight(beforeMidnight), 30);
  assert.equal(kstUsageDate(afterMidnight), "2026-08-29");
  assert.equal(secondsUntilNextKstMidnight(afterMidnight), 86_400);
});

test("도보 경로는 공식 일일 1000건보다 낮은 700 경고·800 차단을 사용한다", () => {
  const originalBudget = process.env.KAKAO_WALK_DAILY_BUDGET;
  const originalWarning = process.env.KAKAO_WALK_DAILY_WARNING;
  try {
    delete process.env.KAKAO_WALK_DAILY_BUDGET;
    delete process.env.KAKAO_WALK_DAILY_WARNING;
    assert.deepEqual(walkBudgetPolicy(), {
      budgetLimit: 800,
      warningThreshold: 700
    });

    process.env.KAKAO_WALK_DAILY_BUDGET = "2000";
    process.env.KAKAO_WALK_DAILY_WARNING = "1500";
    assert.deepEqual(walkBudgetPolicy(), {
      budgetLimit: 1_000,
      warningThreshold: 1_000
    });
  } finally {
    if (originalBudget === undefined) delete process.env.KAKAO_WALK_DAILY_BUDGET;
    else process.env.KAKAO_WALK_DAILY_BUDGET = originalBudget;
    if (originalWarning === undefined) delete process.env.KAKAO_WALK_DAILY_WARNING;
    else process.env.KAKAO_WALK_DAILY_WARNING = originalWarning;
  }
});

test("Mobility 예산은 기본 7000 경고와 8000 차단이며 공식 쿼터를 넘지 않는다", () => {
  const originalBudget = process.env.KAKAO_MOBILITY_DAILY_BUDGET;
  const originalWarning = process.env.KAKAO_MOBILITY_DAILY_WARNING;
  try {
    delete process.env.KAKAO_MOBILITY_DAILY_BUDGET;
    delete process.env.KAKAO_MOBILITY_DAILY_WARNING;
    assert.deepEqual(mobilityBudgetPolicy(), {
      budgetLimit: 8_000,
      warningThreshold: 7_000
    });

    process.env.KAKAO_MOBILITY_DAILY_BUDGET = "12000";
    process.env.KAKAO_MOBILITY_DAILY_WARNING = "11000";
    assert.deepEqual(mobilityBudgetPolicy(), {
      budgetLimit: 10_000,
      warningThreshold: 10_000
    });
  } finally {
    if (originalBudget === undefined) delete process.env.KAKAO_MOBILITY_DAILY_BUDGET;
    else process.env.KAKAO_MOBILITY_DAILY_BUDGET = originalBudget;
    if (originalWarning === undefined) delete process.env.KAKAO_MOBILITY_DAILY_WARNING;
    else process.env.KAKAO_MOBILITY_DAILY_WARNING = originalWarning;
  }
});

test("거부된 예약은 공급자를 호출하거나 결과를 기록하지 않는다", async () => {
  let providerCalls = 0;
  let resultRecords = 0;

  await assert.rejects(
    trackProviderCall({
      provider: "KAKAO_MOBILITY",
      operation: "DIRECTIONS",
      budgetLimit: 8_000,
      now: () => new Date("2026-08-28T00:00:00.000Z"),
      reserve: async () => ({
        allowed: false,
        reservedCount: 8_000,
        remainingCount: 0
      }),
      record: async () => { resultRecords += 1; },
      call: async () => { providerCalls += 1; }
    }),
    (error) =>
      error instanceof ProviderBudgetExhaustedError &&
      error.code === "UPSTREAM_BUDGET_EXHAUSTED" &&
      error.retryAfterSeconds > 0
  );

  assert.equal(providerCalls, 0);
  assert.equal(resultRecords, 0);
});

test("승인된 호출은 성공 결과를 같은 KST 날짜에 기록한다", async () => {
  const records = [];
  const value = await trackProviderCall({
    provider: "KAKAO_LOCAL",
    operation: "REGION",
    budgetLimit: null,
    now: () => new Date("2026-08-28T15:00:00.000Z"),
    reserve: async (input) => {
      assert.equal(input.usageDate, "2026-08-29");
      return { allowed: true, reservedCount: 1, remainingCount: null };
    },
    record: async (input) => { records.push(input); },
    call: async () => ({ region: "강릉시" })
  });

  assert.deepEqual(value, { region: "강릉시" });
  assert.equal(records.length, 1);
  assert.equal(records[0].resultKind, "success");
  assert.equal(records[0].usageDate, "2026-08-29");
});

test("공급자 오류는 quota, 5xx, timeout, 기타로 구분한다", () => {
  assert.equal(
    classifyProviderResult(new ProviderResponseError("KAKAO_MOBILITY", 429)),
    "quota"
  );
  assert.equal(
    classifyProviderResult(new ProviderResponseError("KAKAO_MOBILITY", 400, -10)),
    "quota"
  );
  assert.equal(
    classifyProviderResult(new ProviderResponseError("KAKAO_MOBILITY", 503)),
    "server_error"
  );
  assert.equal(classifyProviderResult({ code: "UPSTREAM_TIMEOUT" }), "timeout");
  assert.equal(classifyProviderResult(new Error("invalid payload")), "other_error");
});

test("결과 기록 장애는 공급자의 성공 응답을 실패로 바꾸지 않는다", async () => {
  const originalError = console.error;
  const logs = [];
  console.error = (message) => logs.push(String(message));
  try {
    const value = await trackProviderCall({
      provider: "TOUR_API",
      operation: "detailIntro2",
      budgetLimit: null,
      reserve: async () => ({ allowed: true, reservedCount: 1, remainingCount: null }),
      record: async () => { throw new Error("db secret detail"); },
      call: async () => "ok"
    });

    assert.equal(value, "ok");
    assert.equal(logs.length, 1);
    assert.doesNotMatch(logs[0], /secret detail/);
  } finally {
    console.error = originalError;
  }
});
