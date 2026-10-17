# Gate 1-A Runtime Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vercel Hobby와 Supabase Free 환경에서 동기화 중복, 외부 요청 무한 대기, 추천 요청의 과도한 Kakao 경로 호출을 차단한다.

**Architecture:** 외부 HTTP 호출은 `fetch-policy.js`의 timeout/deadline으로 통일한다. Cron 소유권은 `sync_state`와 service-role 전용 PostgreSQL RPC가 원자적으로 관리하고, 두 Cron은 독립성을 유지한 채 UTC 실행 창을 4시간 분리한다. 추천 API는 전체 25초 deadline 안에서 최대 8개 후보만 정확 경로 계산한다.

**Tech Stack:** Node.js 24, native Fetch/AbortSignal, Vercel Functions/Cron, Supabase Postgres/PostgREST RPC, Node test runner, pnpm 11.19.0

**Spec:** `docs/superpowers/specs/2026-08-28-gate-1-runtime-safety-design.md`

## Global Constraints

- Vercel Hobby와 Supabase Free 구성을 유지하며 새 유료 서비스나 큐를 추가하지 않는다.
- Vercel Function `maxDuration=60`을 유지한다.
- 요청별 timeout은 Supabase 5초, Kakao Local 5초, Kakao Mobility 8초, TourAPI 8초다.
- 추천 전체 deadline은 25초, Cron 외부 작업 deadline은 50초다.
- Cron은 45초 이후(50초 deadline까지 5초 미만) 새 장소나 새 페이지를 시작하지 않는다.
- DB lease는 90초이며 동일 `jobId`의 살아 있는 lease를 덮어쓰지 않는다.
- 자동차 추천의 Kakao Mobility 후보 상한은 8개다.
- 공개 timeout 응답은 HTTP 504와 `UPSTREAM_TIMEOUT`; 기타 내부 오류는 기존 HTTP 500 계약을 유지한다.
- 기존 Android 요청·응답 필드는 제거하거나 의미를 바꾸지 않는다.
- 실제 키, 외부 응답 전문, service-role 값은 코드·테스트·로그에 넣지 않는다.
- 각 Task는 red 확인 → 최소 구현 → 관련 전체 검증 → 문서/필수 파일 목록 갱신 → 독립 커밋 순서로 끝낸다.
- 사용자 소유 미추적 `output/`, `tmp/`는 열거나 추가하거나 삭제하지 않는다.

---

### Task 1: Add the shared timeout and deadline policy

**Files:**
- Create: `backend/lib/fetch-policy.js`
- Create: `backend/tests/fetch-policy.test.js`
- Modify: `backend/lib/http.js`
- Test: `backend/tests/fetch-policy.test.js`

**Interfaces:**
- Produces: `NETWORK_TIMEOUT_MS`, `UpstreamTimeoutError`, `fetchWithTimeout(input, init, options)`, `createDeadline(timeoutMs, options)`.
- Produces: `serverError(error)` maps `error.code === "UPSTREAM_TIMEOUT"` to HTTP 504.
- Consumes: native `AbortController`, `AbortSignal.any`, and `setTimeout` available in Node.js 24.

- [x] **Step 1: Write failing timeout and response tests**

Create `backend/tests/fetch-policy.test.js` with deterministic abort-aware fakes:

```js
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
  const response = serverError(new UpstreamTimeoutError("SUPABASE"));
  assert.equal(response.status, 504);
  assert.deepEqual((await response.json()).error.code, "UPSTREAM_TIMEOUT");
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
```

- [x] **Step 2: Run the focused test and verify red**

Run:

```powershell
cd backend
node --test tests/fetch-policy.test.js
```

Expected: FAIL because `lib/fetch-policy.js` does not exist.

- [x] **Step 3: Implement the timeout policy**

Create `backend/lib/fetch-policy.js` with these exact exports and semantics:

```js
export const NETWORK_TIMEOUT_MS = Object.freeze({
  SUPABASE: 5_000,
  KAKAO_LOCAL: 5_000,
  KAKAO_MOBILITY: 8_000,
  TOUR_API: 8_000,
  RECOMMENDATION: 25_000,
  CRON: 50_000
});

export class UpstreamTimeoutError extends Error {
  constructor(provider) {
    super("외부 서비스 응답 시간이 초과되었습니다.");
    this.name = "UpstreamTimeoutError";
    this.code = "UPSTREAM_TIMEOUT";
    this.provider = provider;
  }
}

export async function fetchWithTimeout(
  input,
  init = {},
  { provider, timeoutMs, signal, fetchImpl = fetch, consume } = {}
) {
  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  const combinedSignal = signal
    ? AbortSignal.any([signal, timeoutSignal])
    : timeoutSignal;
  try {
    const response = await fetchImpl(input, { ...init, signal: combinedSignal });
    return consume ? await consume(response, combinedSignal) : response;
  } catch (error) {
    if (timeoutSignal.aborted && !signal?.aborted) {
      throw new UpstreamTimeoutError(provider);
    }
    throw error;
  }
}

export function createDeadline(timeoutMs, { now = () => Date.now() } = {}) {
  const startedAt = now();
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(new UpstreamTimeoutError("REQUEST_DEADLINE")),
    timeoutMs
  );
  return {
    signal: controller.signal,
    expiresAt: startedAt + timeoutMs,
    remainingMs: () => Math.max(0, startedAt + timeoutMs - now()),
    canStart: (minimumRemainingMs) =>
      startedAt + timeoutMs - now() >= minimumRemainingMs,
    dispose: () => clearTimeout(timer)
  };
}
```

Update `backend/lib/http.js` before the existing 500 branch:

```js
export function serverError(error) {
  const requestId = crypto.randomUUID();
  const message = error instanceof Error ? error.message : String(error);
  console.error(`[${requestId}] ${message}`);
  if (error?.code === "UPSTREAM_TIMEOUT") {
    return json({
      error: {
        code: "UPSTREAM_TIMEOUT",
        message: "외부 서비스 응답이 늦어 요청을 완료하지 못했습니다.",
        requestId
      }
    }, 504);
  }
  return json({
    error: {
      code: "INTERNAL_ERROR",
      message: "서버 처리 중 오류가 발생했습니다.",
      requestId
    }
  }, 500);
}
```

Do not log provider URLs, response bodies, or keys.

- [x] **Step 4: Run focused and full Backend tests**

Run:

```powershell
node --test tests/fetch-policy.test.js
pnpm test
```

Expected: focused tests PASS and the existing 69 Backend tests still PASS.

- [x] **Step 5: Commit**

```powershell
git add backend/lib/fetch-policy.js backend/lib/http.js backend/tests/fetch-policy.test.js
git commit -m "feat: 외부 요청 timeout 정책 추가"
```

---

### Task 2: Add atomic sync lease migration and RPC adapters

**Files:**
- Create: `backend/migrations/005_sync_runtime_safety.sql`
- Create: `backend/tests/sync-runtime-migration.test.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/tests/database.test.js`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**
- Consumes: `fetchWithTimeout` and `NETWORK_TIMEOUT_MS.SUPABASE` from Task 1.
- Produces: `claimSyncJob({ jobId, token, now, leaseSeconds, signal }) -> Promise<boolean>`.
- Produces: `finishSyncJob({ jobId, token, status, summary, finishedAt, signal }) -> Promise<boolean>`.
- Produces: `claim_sync_job(p_id text, p_token text, p_now timestamptz, p_lease_seconds integer) -> boolean`.
- Produces: `finish_sync_job(p_id text, p_token text, p_status text, p_summary jsonb, p_finished_at timestamptz) -> boolean`.

- [x] **Step 1: Write the failing migration contract test**

Create `backend/tests/sync-runtime-migration.test.js`:

```js
import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("동기화 lease migration은 원자 claim과 service-role 전용 권한을 선언한다", async () => {
  const sql = await readFile(
    new URL("../migrations/005_sync_runtime_safety.sql", import.meta.url),
    "utf8"
  );
  for (const column of [
    "lease_token", "lease_expires_at", "last_started_at", "last_finished_at",
    "last_status", "last_duration_ms", "last_run_summary"
  ]) assert.match(sql, new RegExp(`add column if not exists ${column}`, "i"));
  assert.match(sql, /create or replace function public\.claim_sync_job/i);
  assert.match(sql, /on conflict \(id\) do update/i);
  assert.match(sql, /lease_expires_at\s*<=\s*p_now/i);
  assert.match(sql, /create or replace function public\.finish_sync_job/i);
  assert.match(sql, /lease_token\s*=\s*p_token/i);
  assert.match(sql, /revoke execute[\s\S]*from public, anon, authenticated/i);
  assert.match(sql, /grant execute[\s\S]*to service_role/i);
});
```

Add `claimSyncJob` and `finishSyncJob` to the existing import from `database.js`, then append these tests to `backend/tests/database.test.js`:

```js
test("동기화 작업 claim은 service-role RPC에 원자 lease 인자를 보낸다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json(true);
  };
  try {
    const claimed = await claimSyncJob({
      jobId: "tour_intro",
      token: "run-1",
      now: "2026-08-28T00:00:00.000Z",
      leaseSeconds: 90
    });
    assert.match(request.url, /\/rest\/v1\/rpc\/claim_sync_job$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_id: "tour_intro",
      p_token: "run-1",
      p_now: "2026-08-28T00:00:00.000Z",
      p_lease_seconds: 90
    });
    assert.equal(claimed, true);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});

test("동기화 작업 finish는 소유 token과 결과 요약을 보낸다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const originalFetch = globalThis.fetch;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return Response.json(false);
  };
  try {
    const finished = await finishSyncJob({
      jobId: "tour_intro",
      token: "run-1",
      status: "partial",
      summary: { processed: 4, failed: 1 },
      finishedAt: "2026-08-28T00:00:30.000Z"
    });
    assert.match(request.url, /\/rest\/v1\/rpc\/finish_sync_job$/);
    assert.deepEqual(JSON.parse(request.init.body), {
      p_id: "tour_intro",
      p_token: "run-1",
      p_status: "partial",
      p_summary: { processed: 4, failed: 1 },
      p_finished_at: "2026-08-28T00:00:30.000Z"
    });
    assert.equal(finished, false);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalKey;
  }
});
```

- [x] **Step 2: Run the focused tests and verify red**

Run:

```powershell
node --test tests/sync-runtime-migration.test.js tests/database.test.js
```

Expected: FAIL because migration 005 and the two database exports do not exist.

- [x] **Step 3: Write migration 005**

Create `backend/migrations/005_sync_runtime_safety.sql` with:

```sql
alter table public.sync_state
  add column if not exists lease_token text,
  add column if not exists lease_expires_at timestamptz,
  add column if not exists last_started_at timestamptz,
  add column if not exists last_finished_at timestamptz,
  add column if not exists last_status text,
  add column if not exists last_duration_ms bigint,
  add column if not exists last_run_summary jsonb not null default '{}'::jsonb;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'sync_state_last_status_check'
  ) then
    alter table public.sync_state add constraint sync_state_last_status_check
      check (last_status is null or last_status in ('completed', 'partial', 'failed'));
  end if;
end $$;

create or replace function public.claim_sync_job(
  p_id text, p_token text, p_now timestamptz, p_lease_seconds integer
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  claimed boolean;
begin
  if p_id is null or p_id = '' or p_token is null or p_token = '' then
    raise exception 'job id and token are required';
  end if;
  if p_lease_seconds < 1 or p_lease_seconds > 300 then
    raise exception 'lease seconds must be between 1 and 300';
  end if;
  insert into public.sync_state (
    id, lease_token, lease_expires_at, last_started_at, updated_at
  ) values (
    p_id, p_token, p_now + make_interval(secs => p_lease_seconds), p_now, p_now
  )
  on conflict (id) do update set
    lease_token = excluded.lease_token,
    lease_expires_at = excluded.lease_expires_at,
    last_started_at = excluded.last_started_at,
    updated_at = excluded.updated_at
  where public.sync_state.lease_token is null
     or public.sync_state.lease_expires_at is null
     or public.sync_state.lease_expires_at <= p_now
  returning true into claimed;
  return coalesce(claimed, false);
end;
$$;

create or replace function public.finish_sync_job(
  p_id text, p_token text, p_status text, p_summary jsonb, p_finished_at timestamptz
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  affected integer;
begin
  if p_status not in ('completed', 'partial', 'failed') then
    raise exception 'invalid sync status';
  end if;
  update public.sync_state
  set lease_token = null,
      lease_expires_at = null,
      last_finished_at = p_finished_at,
      last_status = p_status,
      last_duration_ms = greatest(
        0,
        floor(extract(epoch from (p_finished_at - last_started_at)) * 1000)::bigint
      ),
      last_run_summary = coalesce(p_summary, '{}'::jsonb),
      updated_at = p_finished_at
  where id = p_id and lease_token = p_token;
  get diagnostics affected = row_count;
  return affected = 1;
end;
$$;

revoke execute on function public.claim_sync_job(text, text, timestamptz, integer)
  from public, anon, authenticated;
revoke execute on function public.finish_sync_job(text, text, text, jsonb, timestamptz)
  from public, anon, authenticated;
grant execute on function public.claim_sync_job(text, text, timestamptz, integer)
  to service_role;
grant execute on function public.finish_sync_job(text, text, text, jsonb, timestamptz)
  to service_role;
```

- [x] **Step 4: Add database timeout and RPC adapters**

In `backend/lib/database.js`:

1. Import `fetchWithTimeout` and `NETWORK_TIMEOUT_MS`.
2. Extend `databaseRequest` options with `signal`.
3. Replace raw `fetch` with:

```js
const result = await fetchWithTimeout(`${baseUrl}/rest/v1/${path}`, {
  method,
  headers,
  body: body === undefined ? undefined : JSON.stringify(body)
}, {
  provider: "SUPABASE",
  timeoutMs: NETWORK_TIMEOUT_MS.SUPABASE,
  signal,
  consume: async (response) => ({
    status: response.status,
    ok: response.ok,
    text: response.status === 204 ? "" : await response.text()
  })
});
```

Use `result.ok`, `result.status`, and `result.text` in the existing error and JSON parsing branches.
This keeps body consumption inside the same 5-second timeout.

4. Add exact exports:

```js
export async function claimSyncJob({ jobId, token, now, leaseSeconds = 90, signal }) {
  return Boolean(await databaseRequest("rpc/claim_sync_job", {
    method: "POST",
    body: {
      p_id: jobId,
      p_token: token,
      p_now: now,
      p_lease_seconds: leaseSeconds
    },
    signal
  }));
}

export async function finishSyncJob({
  jobId, token, status, summary, finishedAt, signal
}) {
  return Boolean(await databaseRequest("rpc/finish_sync_job", {
    method: "POST",
    body: {
      p_id: jobId,
      p_token: token,
      p_status: status,
      p_summary: summary,
      p_finished_at: finishedAt
    },
    signal
  }));
}
```

Add an optional signal without breaking existing positional callers to every database function used
inside a request or Cron deadline:

```js
listPlaces({ limit, offset, category, sigunguCode, minLatitude, maxLatitude,
  minLongitude, maxLongitude, signal } = {})
getPlace(contentId, { signal } = {})
upsertPlaces(rows, { signal } = {})
listPlacesForEnrichment({ limit = 5, offset = 0, signal } = {})
updatePlaceEnrichment(place, enrichment, { signal } = {})
listPlacesForIntroSync({ limit = 20, now = new Date(), signal } = {})
savePlaceIntro(place, enrichment, { signal } = {})
recordPlaceEnrichmentFailure(place, error, now = new Date(), { signal } = {})
resetPlaceEnrichment(contentId, { signal } = {})
setPlaceActive(contentId, active, { signal } = {})
getSyncState(id = "tour_api", { signal } = {})
saveSyncState(state, { signal } = {})
```

Each function passes `signal` to its `databaseRequest` call. `requestPublicPlaceRows(query, signal)`
must reuse the same signal for the normalized-column request and legacy fallback.

Add migration 005 and `lib/fetch-policy.js` to `requiredFiles` in `backend/scripts/check-project.js`.

- [x] **Step 5: Run focused and full Backend verification**

Run:

```powershell
node --test tests/sync-runtime-migration.test.js tests/database.test.js
pnpm test
pnpm run check
```

Expected: focused tests PASS; all Backend tests and project check PASS.

- [x] **Step 6: Commit**

```powershell
git add backend/migrations/005_sync_runtime_safety.sql backend/lib/database.js backend/tests/sync-runtime-migration.test.js backend/tests/database.test.js backend/scripts/check-project.js
git commit -m "feat: 동기화 작업 DB lease 계약 추가"
```

---

### Task 3: Apply request timeout policy to every external provider

**Files:**
- Modify: `backend/lib/tour-api.js`
- Modify: `backend/lib/kakao-local.js`
- Modify: `backend/lib/kakao-mobility.js`
- Modify: `backend/lib/supabase-auth.js`
- Modify: `backend/tests/tour-api.test.js`
- Modify: `backend/tests/kakao-local.test.js`
- Modify: `backend/tests/kakao-mobility.test.js`
- Modify: `backend/tests/account.test.js`

**Interfaces:**
- Consumes: `fetchWithTimeout`, `NETWORK_TIMEOUT_MS`, and optional caller `signal` from Task 1.
- Produces: every provider function uses a provider timeout even when no overall signal is supplied.
- Preserves: `verifySupabaseUser(token, fetchImpl)` and `deleteSupabaseUser(userId, fetchImpl)` positional compatibility; optional signal is the third argument.

- [x] **Step 1: Add failing signal propagation tests**

Add the relevant function to each test file's import, then add one test per provider family:

```js
test("TourAPI 요청은 timeout signal을 전달한다", async () => {
  const originalKey = process.env.TOUR_API_SERVICE_KEY;
  process.env.TOUR_API_SERVICE_KEY = "service-key";
  try {
    const result = await tourApi.fetchTourCommon("123", {
      fetchImpl: async (_url, options) => {
        assert.ok(options.signal instanceof AbortSignal);
        return Response.json({
          response: {
            header: { resultCode: "0000", resultMsg: "OK" },
            body: { items: { item: [{ contentid: "123", overview: "소개" }] } }
          }
        });
      }
    });
    assert.equal(result.contentid, "123");
  } finally {
    if (originalKey === undefined) delete process.env.TOUR_API_SERVICE_KEY;
    else process.env.TOUR_API_SERVICE_KEY = originalKey;
  }
});

test("Kakao Local 요청은 caller signal과 timeout을 결합한다", async () => {
  const controller = new AbortController();
  await searchKakaoPlaces("강릉역", {
    apiKey: "test-key",
    signal: controller.signal,
    fetchImpl: async (_url, options) => {
      assert.ok(options.signal instanceof AbortSignal);
      return Response.json({ documents: [] });
    }
  });
});

test("Kakao Mobility 요청은 timeout signal을 전달한다", async () => {
  const route = await fetchKakaoRoute(start, destination, place, {
    apiKey: "test-key",
    fetchImpl: async (_url, options) => {
      assert.ok(options.signal instanceof AbortSignal);
      return Response.json(successPayload);
    }
  });
  assert.equal(route.provider, "KAKAO_MOBILITY");
});

test("Supabase 사용자 검증은 timeout signal을 전달한다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_PUBLISHABLE_KEY = "publishable-key";
  try {
    const user = await verifySupabaseUser("user-token", async (_url, options) => {
      assert.ok(options.signal instanceof AbortSignal);
      return Response.json({ id: "user-1" });
    });
    assert.deepEqual(user, { id: "user-1" });
  } finally {
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_PUBLISHABLE_KEY;
    else process.env.SUPABASE_PUBLISHABLE_KEY = originalKey;
  }
});
```

- [x] **Step 2: Run provider tests and verify red**

Run:

```powershell
node --test tests/tour-api.test.js tests/kakao-local.test.js tests/kakao-mobility.test.js tests/account.test.js
```

Expected: FAIL because raw provider fetches do not create timeout signals.

- [x] **Step 3: Replace raw provider fetches**

Apply these exact provider mappings:

```text
tour-api.js       -> provider "TOUR_API", timeout NETWORK_TIMEOUT_MS.TOUR_API
kakao-local.js    -> provider "KAKAO_LOCAL", timeout NETWORK_TIMEOUT_MS.KAKAO_LOCAL
kakao-mobility.js -> provider "KAKAO_MOBILITY", timeout NETWORK_TIMEOUT_MS.KAKAO_MOBILITY
supabase-auth.js  -> provider "SUPABASE", timeout NETWORK_TIMEOUT_MS.SUPABASE
```

Every provider must pass a `consume` callback so JSON parsing remains inside the timeout. For
JSON providers use this result shape and perform the existing status/payload validation afterward:

```js
consume: async (response) => ({
  ok: response.ok,
  status: response.status,
  payload: response.ok ? await response.json() : null
})
```

For Supabase 401/403, keep `status` even though `payload` is null. Do not call `response.json()` a
second time outside `fetchWithTimeout`.

Add `signal` beside `fetchImpl` in existing option objects. Use these signatures so callers remain compatible:

```js
fetchTourPage(pageNo, numOfRows = 100, { signal, fetchImpl = fetch } = {})
fetchTourIntro(contentId, contentTypeId, { signal, fetchImpl = fetch } = {})
fetchTourCommon(contentId, { signal, fetchImpl = fetch } = {})
fetchTourImages(contentId, { signal, fetchImpl = fetch } = {})
fetchPetTourDetail(contentId, { signal, fetchImpl = fetch } = {})
fetchTourSyncPage({ pageNo = 1, numOfRows = 100, modifiedTime, signal, fetchImpl = fetch } = {})
searchKakaoPlaces(query, { latitude, longitude, apiKey, signal, fetchImpl = fetch } = {})
lookupKakaoRegion(latitude, longitude, { apiKey, signal, fetchImpl = fetch } = {})
fetchKakaoRoute(start, destination, placeOrWaypoints = [], { apiKey, signal, fetchImpl = fetch } = {})
fetchKakaoRoutes(start, destination, places, { concurrency = 5, apiKey, signal, fetchImpl = fetch, baseRoute } = {})
verifySupabaseUser(token, fetchImpl = fetch, signal)
deleteSupabaseUser(userId, fetchImpl = fetch, signal)
```

Change the private TourAPI helper to
`fetchTourDetail(path, parameters, { signal, fetchImpl = fetch } = {})`, and have all four
detail wrappers forward their options object to it. `fetchTourPage` and `fetchTourSyncPage`
must also use their injected `fetchImpl`; no TourAPI path may retain a direct global `fetch`.

`fetchKakaoRoutes` must pass the same caller signal into every `fetchKakaoRoute` call. Do not swallow `UpstreamTimeoutError` when every candidate fails; preserve the current partial candidate behavior but let the existing all-failed branch throw.

- [x] **Step 4: Run provider and full Backend tests**

Run:

```powershell
node --test tests/tour-api.test.js tests/kakao-local.test.js tests/kakao-mobility.test.js tests/account.test.js
pnpm test
```

Expected: provider tests and the full suite PASS.

- [x] **Step 5: Commit**

```powershell
git add backend/lib/tour-api.js backend/lib/kakao-local.js backend/lib/kakao-mobility.js backend/lib/supabase-auth.js backend/tests/tour-api.test.js backend/tests/kakao-local.test.js backend/tests/kakao-mobility.test.js backend/tests/account.test.js
git commit -m "feat: 외부 provider 요청에 timeout 적용"
```

---

### Task 4: Add the reusable sync lease execution boundary

**Files:**
- Create: `backend/lib/sync-lease.js`
- Create: `backend/tests/sync-lease.test.js`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**
- Consumes: `claimSyncJob` and `finishSyncJob` from Task 2.
- Produces: `runWithSyncLease({ jobId, run, claim, finish, leaseSeconds, now, tokenFactory })`.
- `run()` returns an object whose `status` is `completed`, `partial`, or `idle`; `idle` is persisted as `completed`.
- Duplicate claim returns `{ status: "skipped", reason: "already_running" }` without calling `run` or `finish`.

- [x] **Step 1: Write failing lease lifecycle tests**

Create `backend/tests/sync-lease.test.js` with these cases:

```js
import test from "node:test";
import assert from "node:assert/strict";
import { runWithSyncLease } from "../lib/sync-lease.js";

const fixedNow = () => new Date("2026-08-28T00:00:00.000Z");
const tokenFactory = () => "run-token";

test("살아 있는 lease가 있으면 작업과 finish를 호출하지 않는다", async () => {
  let ran = false;
  let finished = false;
  const result = await runWithSyncLease({
    jobId: "tour_intro",
    now: fixedNow,
    tokenFactory,
    claim: async () => false,
    finish: async () => { finished = true; },
    run: async () => { ran = true; }
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
  assert.equal(finishInput.token, "run-token");
  assert.equal(finishInput.status, "partial");
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
    run: async () => { throw Object.assign(new Error("secret detail"), { code: "UPSTREAM_TIMEOUT" }); }
  }), /secret detail/);
  assert.equal(finishes[0].status, "failed");
  assert.deepEqual(finishes[0].summary, { errorCode: "UPSTREAM_TIMEOUT" });
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
```

- [x] **Step 2: Run the focused test and verify red**

Run:

```powershell
node --test tests/sync-lease.test.js
```

Expected: FAIL because `lib/sync-lease.js` does not exist.

- [x] **Step 3: Implement the lease lifecycle**

Create `backend/lib/sync-lease.js`:

```js
import { claimSyncJob, finishSyncJob } from "./database.js";

function persistedStatus(status) {
  return status === "partial" ? "partial" : "completed";
}

export async function runWithSyncLease({
  jobId,
  run,
  claim = claimSyncJob,
  finish = finishSyncJob,
  leaseSeconds = 90,
  now = () => new Date(),
  tokenFactory = () => crypto.randomUUID()
}) {
  const token = tokenFactory();
  const startedAt = now();
  const claimed = await claim({
    jobId,
    token,
    now: startedAt.toISOString(),
    leaseSeconds
  });
  if (!claimed) return { status: "skipped", reason: "already_running" };

  let completionAttempted = false;
  try {
    const result = await run();
    completionAttempted = true;
    const finished = await finish({
      jobId,
      token,
      status: persistedStatus(result.status),
      summary: result,
      finishedAt: now().toISOString()
    });
    if (!finished) throw new Error("Sync lease ownership was lost before finish");
    return result;
  } catch (error) {
    if (!completionAttempted) {
      try {
        await finish({
          jobId,
          token,
          status: "failed",
          summary: { errorCode: error?.code ?? "INTERNAL_ERROR" },
          finishedAt: now().toISOString()
        });
      } catch {
        // The original error wins; an orphaned lease expires after 90 seconds.
      }
    }
    throw error;
  }
}
```

Add `lib/sync-lease.js` to `requiredFiles` in `backend/scripts/check-project.js`.

- [x] **Step 4: Run focused and full Backend verification**

Run:

```powershell
node --test tests/sync-lease.test.js
pnpm test
pnpm run check
```

Expected: all checks PASS.

- [x] **Step 5: Commit**

```powershell
git add backend/lib/sync-lease.js backend/tests/sync-lease.test.js backend/scripts/check-project.js
git commit -m "feat: 동기화 lease 실행 경계 추가"
```

---

### Task 5: Apply lease and deadlines to both production Cron jobs

**Files:**
- Modify: `backend/lib/tour-sync.js`
- Modify: `backend/api/cron/tour-catalog-sync.js`
- Modify: `backend/api/cron/tour-intro-sync.js`
- Modify: `backend/vercel.json`
- Modify: `backend/tests/tour-sync.test.js`
- Modify: `backend/tests/tour-catalog-sync.test.js`
- Modify: `backend/tests/policy-pages.test.js`

**Interfaces:**
- Consumes: `runWithSyncLease` from Task 4 and `createDeadline(50_000)` from Task 1.
- Produces: both handlers authenticate before claim and return HTTP 200 `skipped` on duplicate claim.
- Produces: `runIntroBatch` accepts `signal` and `canStart`; returns `{ processed, deferred, updated, empty, failed }`.
- Produces: catalog uses `jobId="tour_catalog_delta"`; intro uses `jobId="tour_intro"`.
- Produces: both jobs stop admitting new pages/places once less than 5 seconds remain (the 45-second admission boundary).

- [x] **Step 1: Write failing batch admission tests**

Extend `backend/tests/tour-sync.test.js`:

```js
test("intro 배치는 deadline 뒤 새 장소를 시작하지 않는다", async () => {
  const started = [];
  let admissions = 0;
  const result = await runIntroBatch({
    places: ["1", "2", "3"].map((content_id) => ({ content_id, content_type_id: 12 })),
    concurrency: 1,
    syncedAt: "2026-08-28T00:00:00.000Z",
    canStart: () => admissions++ < 1,
    signal: new AbortController().signal,
    fetchIntro: async (contentId) => { started.push(contentId); return null; },
    saveIntro: async () => {},
    recordFailure: async () => {}
  });
  assert.deepEqual(started, ["1"]);
  assert.deepEqual(result, {
    processed: 1, deferred: 2, updated: 0, empty: 1, failed: 0
  });
});
```

Update the two existing expected count objects to include `deferred: 0`.

- [x] **Step 2: Write failing Cron lease/deadline/schedule tests**

Add to the existing Cron test files:

```js
test("intro Cron 중복 실행은 장소를 읽지 않고 skipped다", async () => {
  const originalSecret = process.env.CRON_SECRET;
  process.env.CRON_SECRET = "cron-secret";
  let listed = false;
  const handler = createTourIntroSyncHandler({
    withLease: async () => ({ status: "skipped", reason: "already_running" }),
    listPlaces: async () => { listed = true; return []; }
  });
  try {
    const response = await handler.fetch(new Request(
      "https://example.test/api/cron/tour-intro-sync",
      { headers: { authorization: "Bearer cron-secret" } }
    ));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      status: "skipped",
      reason: "already_running"
    });
    assert.equal(listed, false);
  } finally {
    if (originalSecret === undefined) delete process.env.CRON_SECRET;
    else process.env.CRON_SECRET = originalSecret;
  }
});

test("카탈로그 Cron은 페이지 시작 여유가 없으면 partial이다", async () => {
  const originalSecret = process.env.CRON_SECRET;
  process.env.CRON_SECRET = "cron-secret";
  let fetched = false;
  const handler = createTourCatalogSyncHandler({
    withLease: async ({ run }) => run(),
    deadlineFactory: () => ({
      signal: new AbortController().signal,
      canStart: (minimumRemainingMs) => {
        assert.equal(minimumRemainingMs, 5_000);
        return false;
      },
      dispose: () => {}
    }),
    now: () => new Date("2026-08-28T00:00:00.000Z"),
    getState: async () => ({
      id: "tour_catalog_delta",
      next_page: 2,
      source_cursor: "20260826",
      cycle_started_at: "2026-08-27T00:00:00.000Z"
    }),
    saveState: async () => {},
    fetchPage: async () => { fetched = true; throw new Error("must not run"); },
    upsert: async () => {},
    resetEnrichment: async () => {},
    setActive: async () => {}
  });
  try {
    const response = await handler.fetch(new Request(
      "https://example.test/api/cron/tour-catalog-sync",
      { headers: { authorization: "Bearer cron-secret" } }
    ));
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(payload.status, "partial");
    assert.equal(payload.processedPages, 0);
    assert.equal(fetched, false);
  } finally {
    if (originalSecret === undefined) delete process.env.CRON_SECRET;
    else process.env.CRON_SECRET = originalSecret;
  }
});
```

Strengthen `backend/tests/policy-pages.test.js`:

```js
const catalogCron = config.crons.find((cron) => cron.path.endsWith("tour-catalog-sync"));
const introCron = config.crons.find((cron) => cron.path.endsWith("tour-intro-sync"));
assert.equal(catalogCron.schedule, "20 18 * * *");
assert.equal(introCron.schedule, "20 22 * * *");
```

- [x] **Step 3: Run focused tests and verify red**

Run:

```powershell
node --test tests/tour-sync.test.js tests/tour-catalog-sync.test.js tests/policy-pages.test.js
```

Expected: FAIL because batch admission, lease dependencies, and the 22:20 UTC schedule are absent.

- [x] **Step 4: Make `runIntroBatch` deadline-aware**

Change its inputs and counts:

```js
export async function runIntroBatch({
  places,
  fetchIntro,
  saveIntro,
  recordFailure,
  concurrency = 4,
  syncedAt,
  signal,
  canStart = () => true
}) {
  const queue = Array.isArray(places) ? places : [];
  const workerCount = Math.min(
    Math.max(Number.parseInt(concurrency, 10) || 1, 1),
    4,
    queue.length
  );
  const counts = { processed: 0, deferred: 0, updated: 0, empty: 0, failed: 0 };
  const failureTime = new Date(syncedAt);
  let nextIndex = 0;
  let recordError = null;

  async function worker() {
    while (nextIndex < queue.length) {
      if (!canStart()) return;
      const place = queue[nextIndex];
      nextIndex += 1;
      counts.processed += 1;
      try {
        const intro = await fetchIntro(
          place.content_id,
          place.content_type_id,
          { signal }
        );
        const enrichment = normalizeTourIntro({
          contentTypeId: place.content_type_id,
          intro,
          syncedAt
        });
        await saveIntro(place, enrichment, { signal });
        if (intro === null) counts.empty += 1;
        else counts.updated += 1;
      } catch (error) {
        counts.failed += 1;
        try {
          await recordFailure(place, error, failureTime);
        } catch (failureRecordError) {
          recordError ??= failureRecordError;
        }
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  counts.deferred = queue.length - counts.processed;
  if (recordError) throw recordError;
  return counts;
}
```

Keep concurrency at maximum 4. Increment `updated` for non-null intro saved successfully,
`empty` for null intro saved successfully, and `failed` only when fetch or save throws.

- [x] **Step 5: Wrap both handlers in lease and deadline boundaries**

For each handler:

1. Add default dependencies `withLease: runWithSyncLease` and `deadlineFactory: createDeadline`.
2. Authenticate before calling `withLease`.
3. Define a local `runCatalogJob()` or `runIntroJob()` closure containing the handler's current authorized work body, then call `withLease({ jobId, run: runCatalogJob })` or `withLease({ jobId, run: runIntroJob })`.
4. Create `const deadline = deps.deadlineFactory(NETWORK_TIMEOUT_MS.CRON)` inside `run` and always call `deadline.dispose()` in `finally`.
5. Pass `deadline.signal` to provider and DB calls.
   Use `getState(id, { signal })`, `saveState(state, { signal })`,
   `upsert(rows, { signal })`, `resetEnrichment(contentId, { signal })`,
   `setActive(contentId, active, { signal })`, and `listPlaces({ limit, now, signal })`.
6. Catalog checks `deadline.canStart(5_000)` before each page and returns `partial` if more work remains.
7. Intro passes `canStart: () => deadline.canStart(5_000)` and `signal` to `runIntroBatch`; it returns `partial` when `deferred > 0`, otherwise `completed` or existing `idle`.
8. Return `json(result)` after the lease helper completes.

Keep factory injection so existing tests can use:

```js
withLease: async ({ run }) => run(),
deadlineFactory: () => ({
  signal: new AbortController().signal,
  canStart: () => true,
  dispose: () => {}
})
```

The outer handler shape must be:

```js
try {
  const result = await deps.withLease({
    jobId: "tour_catalog_delta",
    run: runCatalogJob
  });
  return json(result);
} catch (error) {
  return serverError(error);
}
```

Use `jobId: "tour_intro"` and `run: runIntroJob` in the intro handler. `runCatalogJob`
and `runIntroJob` each own and dispose exactly one 50-second deadline.

Update `backend/vercel.json` intro schedule from `40 18 * * *` to `20 22 * * *`.

- [x] **Step 6: Run focused and full Backend verification**

Run:

```powershell
node --test tests/tour-sync.test.js tests/tour-catalog-sync.test.js tests/policy-pages.test.js
pnpm test
pnpm run check
```

Expected: focused and full checks PASS; both Cron tests prove auth-before-claim and duplicate skip.

- [x] **Step 7: Commit**

```powershell
git add backend/lib/tour-sync.js backend/api/cron/tour-catalog-sync.js backend/api/cron/tour-intro-sync.js backend/vercel.json backend/tests/tour-sync.test.js backend/tests/tour-catalog-sync.test.js backend/tests/policy-pages.test.js
git commit -m "feat: Cron 중복 실행과 작업 deadline 차단"
```

---

### Task 6: Cap Kakao candidates and apply request-wide API deadlines

**Files:**
- Modify: `backend/api/recommendations.js`
- Modify: `backend/api/route.js`
- Modify: `backend/tests/recommendations.test.js`
- Modify: `backend/tests/route-api.test.js`

**Interfaces:**
- Consumes: `createDeadline`, `NETWORK_TIMEOUT_MS.RECOMMENDATION`, provider signal support from Task 3.
- Produces: recommendation and route requests share a 25-second signal across their upstream calls.
- Produces: `listPlaces({ limit, offset, category, sigunguCode, minLatitude, maxLatitude, minLongitude, maxLongitude, signal })` passes caller cancellation into Supabase timeout.
- Produces: `KAKAO_ROUTE_CANDIDATE_LIMIT` effective range 1–8, default 8.

- [x] **Step 1: Write failing candidate cap and signal tests**

Add this regression to `backend/tests/recommendations.test.js`. It places all 12 candidates
on the mocked direct-route corridor, sets an intentionally excessive environment value, and
counts only Mobility requests that contain a waypoint:

```js
test("자동차 추천은 Kakao 후보 경로를 최대 8개만 요청한다", async () => {
  const originalFetch = globalThis.fetch;
  const originalRouteLimit = process.env.KAKAO_ROUTE_CANDIDATE_LIMIT;
  process.env.KAKAO_REST_API_KEY = "test-key";
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-key";
  process.env.KAKAO_ROUTE_CANDIDATE_LIMIT = "999";
  const candidateRouteRequests = [];
  const seenSignals = [];
  const candidates = Array.from({ length: 12 }, (_, index) => ({
    content_id: String(100 + index),
    source: "TOUR_API",
    name: `후보 ${index}`,
    category: "ATTRACTION",
    latitude: 37.75,
    longitude: 128.875 + index * 0.001,
    default_stay_minutes: 20,
    raw: {}
  }));

  globalThis.fetch = async (url, options = {}) => {
    seenSignals.push(options.signal);
    const parsed = new URL(url);
    if (parsed.hostname === "supabase.test") {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify(candidates);
        }
      };
    }

    const waypoints = parsed.searchParams.get("waypoints");
    if (waypoints) candidateRouteRequests.push(parsed);
    const waypointCount = waypoints?.split("|").length ?? 0;
    return {
      ok: true,
      status: 200,
      async json() {
        return {
          routes: [{
            result_code: 0,
            summary: {
              distance: waypointCount ? 3500 : 3000,
              duration: waypointCount ? 720 : 600,
              fare: { toll: 0 }
            },
            sections: Array.from({ length: waypointCount + 1 }, () => ({
              distance: waypointCount ? 1750 : 3000,
              duration: waypointCount ? 360 : 600,
              roads: [{ vertexes: [128.87, 37.75, 128.9, 37.75] }]
            }))
          }]
        };
      }
    };
  };

  try {
    const response = await recommendationsApi.fetch(new Request(
      "https://example.test/api/recommendations",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          mode: "ON_THE_WAY",
          start,
          destination,
          extraTimeMinutes: 90,
          safetyBufferMinutes: 10,
          transport: "CAR",
          categories: []
        })
      }
    ));
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(candidateRouteRequests.length, 8);
    assert.equal(payload.meta.routeCandidateCount, 8);
    assert.ok(seenSignals.every((signal) => signal instanceof AbortSignal));
  } finally {
    globalThis.fetch = originalFetch;
    if (originalRouteLimit === undefined) {
      delete process.env.KAKAO_ROUTE_CANDIDATE_LIMIT;
    } else {
      process.env.KAKAO_ROUTE_CANDIDATE_LIMIT = originalRouteLimit;
    }
  }
});
```

In the first `backend/tests/route-api.test.js` test, replace the Mobility mock signature
and assert that the handler propagated the overall request signal:

```js
globalThis.fetch = async (_url, options) => {
  assert.ok(options.signal instanceof AbortSignal);
  return {
    ok: true,
    async json() {
      return {
        routes: [{
          result_code: 0,
          summary: { distance: 9400, duration: 1260, fare: { toll: 1800 } },
          sections: [{ distance: 9400, duration: 1260 }]
        }]
      };
    }
  };
};
```

The public 504/no-provider-details regression belongs to Task 1's `http.test.js`; do not
duplicate it in these handler tests.

- [x] **Step 2: Run API tests and verify red**

Run:

```powershell
node --test tests/recommendations.test.js tests/route-api.test.js
```

Expected: FAIL because the current cap is 20 and handlers do not create an overall signal.

- [x] **Step 3: Propagate the 25-second deadline**

In both handlers, create `const deadline = createDeadline(NETWORK_TIMEOUT_MS.RECOMMENDATION)`
immediately before the first upstream call. Wrap the complete upstream calculation in `try/finally`
and call `deadline.dispose()` in the `finally` block. Validation and rate limiting stay outside this
deadline so invalid requests do not allocate timers.

Use the Task 2 `listPlaces` options object with `signal: deadline.signal`; do not add a second timeout wrapper in the handler.

In `recommendations.js`, pass the signal to:

```js
fetchKakaoRoute(criteria.start, criteria.destination, [], { signal: deadline.signal })
listPlaces({ ...bounds, limit: 500, signal: deadline.signal })
fetchKakaoRoutes(criteria.start, criteria.destination, routeCandidates, {
  baseRoute,
  signal: deadline.signal
})
```

In `route.js`, pass `{ signal: deadline.signal }` as the fourth argument to `fetchKakaoRoute`.

The route call must be:

```js
const route = await fetchKakaoRoute(
  routeRequest.start,
  routeRequest.destination,
  routeRequest.waypoints,
  { signal: deadline.signal }
);
```

- [x] **Step 4: Enforce the candidate cap**

Replace the current limit calculation with:

```js
const routeLimit = Math.min(
  Math.max(integerEnv("KAKAO_ROUTE_CANDIDATE_LIMIT", 8), 1),
  8
);
```

Keep `recommendations.slice(0, 20)` for response compatibility; only exact route evaluation is capped.

- [x] **Step 5: Run API and full Backend verification**

Run:

```powershell
node --test tests/recommendations.test.js tests/route-api.test.js
pnpm test
pnpm run check
```

Expected: candidate requests are at most 8, signals are present, and all checks PASS.

- [x] **Step 6: Commit**

```powershell
git add backend/api/recommendations.js backend/api/route.js backend/tests/recommendations.test.js backend/tests/route-api.test.js
git commit -m "feat: 추천 경로 호출량과 전체 deadline 제한"
```

---

### Task 7: Update operations docs and close the local Gate 1-A checkpoint

**Files:**
- Modify: `backend/README.md`
- Modify: `backend/.env.example`
- Modify: `docs/07_BUILD_TEST_DEPLOY.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`
- Modify: `docs/10_DECISION_LOG.md` only if implementation differs from the approved decision
- Modify: this plan's checkboxes and execution record

**Interfaces:**
- Consumes: Tasks 1–6 commits and fresh verification output.
- Produces: current-state documentation that separates local completion, migration application, Preview verification, and Production Cron verification.

- [x] **Step 1: Update Backend configuration and deployment docs**

Record exact values:

```text
Supabase timeout: 5s
Kakao Local timeout: 5s
Kakao Mobility timeout: 8s
TourAPI timeout: 8s
Recommendation/route deadline: 25s
Cron deadline: 50s
Sync lease: 90s
KAKAO_ROUTE_CANDIDATE_LIMIT=8
Catalog Cron: 20 18 * * * UTC
Intro Cron: 20 22 * * * UTC
```

In the deployment guide, state the required order:

1. Test or empty Supabase applies migrations 001–005.
2. Two concurrent `claim_sync_job` calls prove one owner.
3. Production Supabase applies migration 005.
4. Preview is manually smoke-tested; Vercel does not run Preview Cron automatically.
5. Production is explicitly promoted and both UTC schedules are checked in the Vercel UI.

Do not mark migration or production verification complete without live evidence.

- [x] **Step 2: Update Gate status accurately**

In `docs/09_NEXT_VERSION_PLAN.md`, mark these complete only after code and local tests pass:

```text
Cron 시간 창 분리
DB claim/lease
provider timeout와 전체 deadline
Kakao 후보 상한 8
```

Leave daily API budget/429/5xx persistence, operating-hours coverage, parser hardening, festival filtering, and curated core-place review unchecked for Gate 1-B.

- [x] **Step 3: Run fresh full verification**

Run Backend with Node 24/pnpm 11.19.0:

```powershell
cd backend
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

Run Android regression:

```powershell
cd ..\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

Then:

```powershell
cd ..
git diff --check
git status --short
```

Expected:

- Backend tests: all pass, zero failures.
- Backend project check: pass with the new files included.
- Android tests: 75/75 unless this branch has deliberately added tests; zero failures.
- Android lint: zero errors.
- Debug APK exists at `android/app/build/outputs/apk/debug/app-debug.apk`.
- Only deliberate documentation/checklist edits and pre-existing `output/`, `tmp/` remain.

- [x] **Step 4: Record the local checkpoint evidence**

Append an execution record with:

```text
Task commit hashes
Backend exact pass/fail count
Project check file count
Android test count
Lint error count
APK SHA-256
Unverified external gates: migration 005 live apply, concurrent RPC proof,
Preview smoke, Production promotion, first scheduled Cron results
```

- [x] **Step 5: Commit docs and stop for review**

```powershell
git add backend/README.md backend/.env.example docs/07_BUILD_TEST_DEPLOY.md docs/08_QA_AND_KNOWN_ISSUES.md docs/09_NEXT_VERSION_PLAN.md docs/10_DECISION_LOG.md docs/superpowers/plans/2026-08-28-gate-1-runtime-safety.md
git commit -m "docs: Gate 1 런타임 안전 검증 결과 기록"
```

Stop before Gate 1-B or Gate 2. Report exact local evidence and the external migration/deployment gates; do not claim production completion.

## Execution record — 2026-08-28 local Gate 1-A checkpoint

### Task commits

| Task | Commit |
|---|---|
| Plan | `ac38b2d` |
| Task 1 — timeout policy | `f02be37` |
| Task 2 — DB lease contract | `45a1ad8` |
| Task 3 — provider timeout | `e43251b` |
| Task 4 — lease lifecycle | `1eb9f08` |
| Task 5 — Cron lease/deadline | `335a49e` |
| Task 6 — candidate cap/request deadline | `6ecd9c8` |

### Fresh local evidence

```text
Node: 24.19.0
pnpm: 11.19.0
Backend tests: 94 passed, 0 failed
Backend project check: 62 files passed
Android unit tests: 75 passed, 0 failed, 0 skipped
Android lint: 0 errors, 31 warnings
Android debug build: successful
APK: android/app/build/outputs/apk/debug/app-debug.apk
APK SHA-256: 843821A80816EF61613960C589A689530F81919C3F659F9BAA496A155F324E33
```

### Unverified external gates

- migration 005를 테스트·Production Supabase에 실제 적용
- 서로 다른 세션의 동시 `claim_sync_job` 호출로 단일 소유자 증명
- Preview API와 수동 Cron 스모크
- 검증 배포의 명시적 Production 승격
- Vercel UI의 두 UTC Cron 스케줄과 첫 예약 실행 결과 확인

로컬 Gate 1-A 구현과 회귀 검증만 완료했다. 위 외부 게이트의 실행 증거가 없으므로
Production 완료로 간주하지 않는다.
