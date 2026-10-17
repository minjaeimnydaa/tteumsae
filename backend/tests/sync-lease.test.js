import test from "node:test";
import assert from "node:assert/strict";
import { runWithSyncLease } from "../lib/sync-lease.js";

const fixedNow = () => new Date("2026-08-28T00:00:00.000Z");
const tokenFactory = () => "run-token";

test("살아 있는 lease가 있으면 작업과 finish를 호출하지 않는다", async () => {
  let claimInput;
  let ran = false;
  let finished = false;
  const result = await runWithSyncLease({
    jobId: "tour_intro",
    now: fixedNow,
    tokenFactory,
    claim: async (input) => { claimInput = input; return false; },
    finish: async () => { finished = true; },
    run: async () => { ran = true; }
  });
  assert.deepEqual(claimInput, {
    jobId: "tour_intro",
    token: "run-token",
    now: "2026-08-28T00:00:00.000Z",
    leaseSeconds: 90
  });
  assert.deepEqual(result, { status: "skipped", reason: "already_running" });
  assert.equal(ran, false);
  assert.equal(finished, false);
});

test("정상 결과는 같은 token으로 finish한다", async () => {
  let finishInput;
  const result = await runWithSyncLease({
    jobId: "tour_catalog_delta",
    now: fixedNow,
    tokenFactory,
    claim: async (input) => input.token === "run-token",
    finish: async (input) => { finishInput = input; return true; },
    run: async () => ({ status: "partial", processedPages: 2 })
  });
  assert.equal(result.status, "partial");
  assert.deepEqual(finishInput, {
    jobId: "tour_catalog_delta",
    token: "run-token",
    status: "partial",
    summary: result,
    finishedAt: "2026-08-28T00:00:00.000Z"
  });
});

test("idle 결과는 completed로 저장하고 원래 결과를 반환한다", async () => {
  let finishInput;
  const result = await runWithSyncLease({
    jobId: "tour_intro",
    now: fixedNow,
    tokenFactory,
    claim: async () => true,
    finish: async (input) => { finishInput = input; return true; },
    run: async () => ({ status: "idle", processed: 0 })
  });
  assert.deepEqual(result, { status: "idle", processed: 0 });
  assert.equal(finishInput.status, "completed");
  assert.deepEqual(finishInput.summary, result);
});

test("작업 오류는 failed로 기록하고 원래 오류를 다시 던진다", async () => {
  const finishes = [];
  await assert.rejects(runWithSyncLease({
    jobId: "tour_intro",
    now: fixedNow,
    tokenFactory,
    claim: async () => true,
    finish: async (input) => { finishes.push(input); return true; },
    run: async () => {
      throw Object.assign(new Error("secret detail"), { code: "UPSTREAM_TIMEOUT" });
    }
  }), /secret detail/);
  assert.equal(finishes[0].token, "run-token");
  assert.equal(finishes[0].status, "failed");
  assert.deepEqual(finishes[0].summary, { errorCode: "UPSTREAM_TIMEOUT" });
});

test("실패 기록도 실패하면 원래 작업 오류가 우선한다", async () => {
  const originalError = new Error("original job failure");
  await assert.rejects(
    runWithSyncLease({
      jobId: "tour_intro",
      now: fixedNow,
      tokenFactory,
      claim: async () => true,
      finish: async () => { throw new Error("finish failure"); },
      run: async () => { throw originalError; }
    }),
    (error) => error === originalError
  );
});

test("finish 소유권 실패를 성공으로 위장하지 않는다", async () => {
  let finishCalls = 0;
  await assert.rejects(runWithSyncLease({
    jobId: "tour_intro",
    now: fixedNow,
    tokenFactory,
    claim: async () => true,
    finish: async () => { finishCalls += 1; return false; },
    run: async () => ({ status: "completed", processed: 1 })
  }), /lease ownership/i);
  assert.equal(finishCalls, 1);
});
