# Gate 1-B Data Trust Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kakao 일일 호출 예산과 운영 지표를 영구 집계하고, 운영시간·축제 추천 판정을 보수적으로 만들며, 강릉 핵심 장소 100개의 출처 기반 검수 데이터를 운영 DB에 안전하게 적용한다.

**Architecture:** Supabase migration 006에 일별 사용량 aggregate, 원자 예약·결과 RPC, 수동 검수 오버레이, 유효 장소 view, 운영 집계 RPC를 둔다. 서버는 모든 Kakao Mobility 호출을 공통 usage tracker로 감싸고, 순수 운영시간·축제 판정 모듈을 추천 전후에 적용한다. TourAPI 원문은 보존하고 검수 CSV와 별도 테이블이 우선하도록 한다.

**Tech Stack:** Node.js 24 ESM, Vercel Functions, Supabase Postgres/PostgREST RPC, `node:test`, PowerShell, Android Gradle/JDK 17 회귀 검증

**Spec:** `docs/superpowers/specs/2026-08-28-gate-1b-data-trust-design.md`

## Execution Status — 2026-09-01

| Task | 상태 | 근거 |
|---|---|---|
| 1–7 | 완료 | schema, provider tracking, ops API, 보수적 시간·축제 판정, CSV 도구 커밋 |
| 8 | 완료 | `9ed9d53`, 강릉 검수 100행, validator와 데이터 계약 테스트 통과 |
| 9 local | 완료 | Backend 143/143, project check 88 files, Android 75/75·lint·assemble 성공 |
| 9 live | 완료 | migration 005~006, 예산 env, 100행 import, 인증 ops·수동/예약 주기 Cron smoke 완료 |

운영 적용 중 실제 DB에 migration 005가 빠져 006 ops RPC가 `sync_state.last_started_at`
부재로 실패했다. 005 미적용을 스키마와 함수 존재 여부로 확인한 뒤 005를 먼저 적용했고,
006 smoke를 재실행했다. Vercel Hobby 함수 12개 제한은 예약되지 않은 레거시
`tour-detail-sync`를 제거하고 함수 수 테스트를 추가해 해결했다.

운영 증거: 강릉 활성 474곳, 검수 100/100, 운영시간 VERIFIED 75, 주차 VERIFIED 71,
Mobility 예약/성공 10/10, 후보 배포 route/recommendations 성공, Production health와
curation 상세 성공. 인증된 `/api/ops/status`와 수동 Production catalog·intro Cron은
200을 반환했고 두 작업의 `completed` 요약이 영속화됐다. 수동 시각
`2026-08-28T04:08Z`와 별개로 catalog `2026-08-31T19:01:25.674Z`, intro
`2026-08-30T22:47:02.803Z` 후속 실행도 `completed`와 비어 있지 않은 실행 요약을
저장했다. Vercel의 활성 Production Cron과 두 UTC 스케줄도 함께 확인했다.

## Global Constraints

- Kakao Mobility 길찾기는 7,000건부터 경고하고 8,000건에서 외부 호출 전에 차단한다.
- hard stop은 환경변수로 낮출 수 있지만 공식 10,000건보다 높일 수 없다.
- API 사용량에는 좌표, 검색어, 사용자 식별자, API key, 외부 응답 전문을 저장하지 않는다.
- 쿼터·예산 소진 시 직선거리 추정 경로로 자동 대체하지 않는다.
- 운영시간을 완전히 해석하지 못하면 `OPEN`이 아니라 `UNKNOWN`이다.
- 축제는 KST 예정 방문일이 유효한 시작일·종료일 안에 있을 때만 추천한다.
- TourAPI 동기화는 검수 오버레이를 덮어쓰지 않는다.
- 첫 검수 배치는 활성 강릉 `sigungu_code=1` 장소 정확히 100개다.
- 새 DB Function은 빈 `search_path`, 완전 수식 객체명, service-role 전용 실행 권한을 사용한다.
- 공개 `/api/health` 계약은 바꾸지 않고 운영 상세는 `CRON_SECRET` Bearer 인증 뒤에 둔다.
- `output/`, `tmp/`는 사용자 소유 untracked 경로이므로 읽기·수정·추가하지 않는다.
- 구현 중 기존 `TteumsaeApp.kt` 구조나 Gate 2 Android 플로우를 변경하지 않는다.

---

## File Structure

### 새 파일

- `backend/migrations/006_gate_1b_data_trust.sql`: usage aggregate, 예약·결과·운영 집계 RPC, 검수 테이블, effective view
- `backend/lib/provider-usage.js`: KST 사용일, budget 정책, 원자 예약 wrapper, 결과 분류 오류
- `backend/lib/operating-hours.js`: 운영시간 정규화·파싱·방문 구간 판정
- `backend/lib/festival-eligibility.js`: 축제 날짜 완전성·예정 방문일 판정
- `backend/api/ops/status.js`: 인증된 Gate 1 운영 상태 조회
- `backend/lib/curation-csv.js`: 의존성 없는 RFC 4180 CSV 파싱·직렬화·행 검증
- `backend/scripts/export-place-curations.mjs`: 활성 강릉 후보 100개 export
- `backend/scripts/validate-place-curations.mjs`: CSV 단독 검증
- `backend/scripts/import-place-curations.mjs`: 검증 통과 CSV를 Supabase에 upsert
- `backend/data/gangneung-core-place-curations.csv`: 출처 검수를 완료한 핵심 장소 100개
- 대응하는 `backend/tests/*.test.js`

### 수정 파일

- `backend/lib/database.js`: effective place 읽기, usage RPC, ops RPC, curation export/import adapter
- `backend/lib/kakao-mobility.js`: 구조화 오류 body와 공통 usage tracker
- `backend/lib/kakao-local.js`: Local operation별 usage tracker
- `backend/lib/tour-api.js`: TourAPI operation별 usage tracker와 제한 오류 분류
- `backend/lib/time-safe.js`: 방문 구간 운영 판정과 축제 판정 연결
- `backend/lib/http.js`: budget/quota 503 응답
- `backend/api/recommendations.js`: 기준 시각 전달과 사전 축제 필터
- `backend/api/route.js`: 공통 Mobility budget 오류 계약 사용
- `backend/scripts/check-project.js`: migration 006, ops endpoint, curation 도구 필수 파일 검사
- `backend/package.json`: curation export·validate·import 명령
- `backend/.env.example`, `backend/README.md`
- `docs/05_API_AND_DATA.md`, `docs/06_ENVIRONMENT_AND_ACCESS.md`, `docs/07_BUILD_TEST_DEPLOY.md`
- `docs/08_QA_AND_KNOWN_ISSUES.md`, `docs/09_NEXT_VERSION_PLAN.md`, `docs/10_DECISION_LOG.md`

---

### Task 1: Supabase Gate 1-B schema and atomic usage contracts

**Files:**
- Create: `backend/migrations/006_gate_1b_data_trust.sql`
- Create: `backend/tests/gate-1b-migration.test.js`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**
- Produces: `reserve_provider_usage(text,text,date,integer,integer)` returning `allowed`, `reserved_count`, `remaining_count`
- Produces: `record_provider_usage_result(text,text,date,text,integer)` returning `void`
- Produces: `get_gate_1b_ops_status(date,integer,integer)` returning `jsonb`
- Produces: `public.provider_usage_daily`, `public.place_curations`, `public.effective_places`

- [ ] **Step 1: Write the failing migration contract test**

Create a test that reads migration 006 and asserts all security and schema contracts:

```js
import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sql = await readFile(new URL("../migrations/006_gate_1b_data_trust.sql", import.meta.url), "utf8");

test("migration 006 defines daily usage, curation overlay, and service-role RPCs", () => {
  assert.match(sql, /create table if not exists public\.provider_usage_daily/i);
  assert.match(sql, /primary key \(usage_date, provider, operation\)/i);
  assert.match(sql, /create table if not exists public\.place_curations/i);
  assert.match(sql, /create or replace view public\.effective_places/i);
  assert.match(sql, /create or replace function public\.reserve_provider_usage/i);
  assert.match(sql, /reserved_count \+ p_units <= p_budget_limit/i);
  assert.match(sql, /create or replace function public\.record_provider_usage_result/i);
  assert.match(sql, /create or replace function public\.get_gate_1b_ops_status/i);
  assert.match(sql, /set search_path = ''/i);
  assert.match(sql, /revoke execute[\s\S]+from public, anon, authenticated/i);
  assert.match(sql, /grant execute[\s\S]+to service_role/i);
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
Set-Location C:\app\tteumsae\backend
node --test tests/gate-1b-migration.test.js
```

Expected: FAIL with `ENOENT` for `006_gate_1b_data_trust.sql`.

- [ ] **Step 3: Add daily usage and curation tables**

Create migration 006 with these exact checks:

```sql
create table if not exists public.provider_usage_daily (
  usage_date date not null,
  provider text not null check (provider in ('KAKAO_MOBILITY', 'KAKAO_LOCAL', 'TOUR_API')),
  operation text not null check (length(operation) between 1 and 80),
  budget_limit integer check (budget_limit is null or budget_limit > 0),
  reserved_count integer not null default 0 check (reserved_count >= 0),
  success_count integer not null default 0 check (success_count >= 0),
  quota_error_count integer not null default 0 check (quota_error_count >= 0),
  server_error_count integer not null default 0 check (server_error_count >= 0),
  timeout_count integer not null default 0 check (timeout_count >= 0),
  other_error_count integer not null default 0 check (other_error_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (usage_date, provider, operation)
);

create table if not exists public.place_curations (
  content_id text primary key references public.places(content_id) on delete cascade,
  operating_info_status text not null check (operating_info_status in ('VERIFIED', 'UNKNOWN')),
  opening_hours text,
  closed_days text,
  last_admission text,
  admission_info_status text not null check (admission_info_status in ('VERIFIED', 'NOT_APPLICABLE', 'UNKNOWN')),
  parking_info text,
  parking_info_status text not null check (parking_info_status in ('VERIFIED', 'UNKNOWN')),
  source_urls jsonb not null check (jsonb_typeof(source_urls) = 'array'),
  source_checked_at timestamptz not null,
  reviewed_at timestamptz not null,
  review_note text,
  updated_at timestamptz not null default now(),
  check (operating_info_status <> 'VERIFIED' or opening_hours is not null),
  check (admission_info_status <> 'VERIFIED' or last_admission is not null),
  check (parking_info_status <> 'VERIFIED' or parking_info is not null),
  check (jsonb_array_length(source_urls) > 0)
);
```

Enable RLS on both tables. Do not add anon/authenticated policies.

- [ ] **Step 4: Add atomic reserve and result RPCs**

Implement reservation so a null budget records usage without a hard stop, while a non-null budget
never increments beyond the limit:

```sql
insert into public.provider_usage_daily (...)
values (...)
on conflict (usage_date, provider, operation) do update
set reserved_count = public.provider_usage_daily.reserved_count + excluded.reserved_count,
    budget_limit = excluded.budget_limit,
    updated_at = now()
where excluded.budget_limit is null
   or public.provider_usage_daily.reserved_count + excluded.reserved_count <= excluded.budget_limit
returning true, reserved_count,
  case when budget_limit is null then null else budget_limit - reserved_count end
into v_allowed, v_reserved, v_remaining;
```

Validate `p_units` in `1..100`, validate non-empty provider/operation, and return the existing count
with `allowed=false` when the conditional upsert returns no row. `record_provider_usage_result`
accepts only `success`, `quota`, `server_error`, `timeout`, `other_error` and increments exactly one
column by `p_units`.

- [ ] **Step 5: Add effective view and operational aggregate RPC**

Define `effective_places` with `(security_invoker = true)` as `p.*` plus non-conflicting fields:

```sql
case
  when c.content_id is null then p.opening_hours
  when c.operating_info_status = 'VERIFIED' then c.opening_hours
  else null
end as effective_opening_hours,
case
  when c.content_id is null then p.closed_days
  when c.operating_info_status = 'VERIFIED' then c.closed_days
  else null
end as effective_closed_days,
c.last_admission as effective_last_admission,
c.parking_info as effective_parking_info,
case when c.content_id is null then 'TOUR_API' else 'CURATION' end as data_provenance,
c.operating_info_status,
c.admission_info_status,
c.parking_info_status,
c.reviewed_at
```

`get_gate_1b_ops_status` returns a JSON object with `usage`, `syncJobs`, and `dataQuality`.
`dataQuality` must include active total, effective operating-hours count, complete/incomplete/past
festival counts, and `sigungu_code=1` curation count against target 100.
Each Mobility usage item also returns `warning=true` at 7,000 or more reserved calls and
`blocked=true` when `reserved_count >= budget_limit`.

- [ ] **Step 6: Lock down privileges and extend project checks**

Revoke table/view access from `anon`, `authenticated`; grant service role required select/insert/update.
Revoke all three RPCs from public roles and grant only `service_role`. Add migration 006 and
`api/ops/status.js` to `requiredFiles`; add the endpoint only after Task 4 creates it, so during this
task add migration 006 now and add the endpoint assertion in Task 4.

- [ ] **Step 7: Run migration contract and project tests**

Run:

```powershell
Set-Location C:\app\tteumsae\backend
node --test tests/gate-1b-migration.test.js tests/sync-runtime-migration.test.js tests/user-migration.test.js
pnpm run check
```

Expected: all tests PASS and project check reports no secret-like 64-character values.

- [ ] **Step 8: Commit the schema**

```powershell
git add backend/migrations/006_gate_1b_data_trust.sql backend/tests/gate-1b-migration.test.js backend/scripts/check-project.js
git commit -m "feat: Gate 1-B 운영 데이터 스키마 추가"
```

---

### Task 2: Provider usage runtime and database adapters

**Files:**
- Create: `backend/lib/provider-usage.js`
- Create: `backend/tests/provider-usage.test.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/lib/env.js`

**Interfaces:**
- Consumes: Task 1 RPCs
- Produces: `kstUsageDate`, `secondsUntilNextKstMidnight`, `mobilityBudgetPolicy`
- Produces: `trackProviderCall({ provider, operation, budgetLimit, signal, call, now, reserve, record })`
- Produces: `ProviderBudgetExhaustedError`, `ProviderResponseError`, `classifyProviderResult`
- Produces DB adapters: `reserveProviderUsage`, `recordProviderUsageResult`, `getGate1bOpsStatus`

- [ ] **Step 1: Write failing date, budget, and classification tests**

Cover KST rollover, 8,000 hard stop, no-body-sensitive classification, and best-effort result recording:

```js
test("KST date and retry-after cross UTC day correctly", () => {
  const now = new Date("2026-08-28T14:59:30.000Z");
  assert.equal(kstUsageDate(now), "2026-08-28");
  assert.equal(secondsUntilNextKstMidnight(now), 30);
});

test("denied reservation throws before provider call", async () => {
  let called = 0;
  await assert.rejects(
    trackProviderCall({
      provider: "KAKAO_MOBILITY",
      operation: "DIRECTIONS",
      budgetLimit: 8_000,
      now: () => new Date("2026-08-28T00:00:00.000Z"),
      reserve: async () => ({ allowed: false, reservedCount: 8_000, remainingCount: 0 }),
      record: async () => {},
      call: async () => { called += 1; }
    }),
    { code: "UPSTREAM_BUDGET_EXHAUSTED" }
  );
  assert.equal(called, 0);
});
```

Also assert `classifyProviderResult({status:429})`, `{status:400, providerCode:-10}` => `quota`,
503 => `server_error`, `UPSTREAM_TIMEOUT` => `timeout`, and a generic parse error => `other_error`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
Set-Location C:\app\tteumsae\backend
node --test tests/provider-usage.test.js
```

Expected: FAIL because `provider-usage.js` does not exist.

- [ ] **Step 3: Add database RPC adapters**

Export exact adapter signatures from `database.js`:

```js
export async function reserveProviderUsage({
  provider, operation, usageDate, budgetLimit = null, units = 1, signal
})

export async function recordProviderUsageResult({
  provider, operation, usageDate, resultKind, units = 1, signal
})

export async function getGate1bOpsStatus({
  usageDate, sigunguCode = 1, curationTarget = 100, signal
})
```

Map PostgREST snake_case output to `{ allowed, reservedCount, remainingCount }` in one place.

- [ ] **Step 4: Implement budget policy and typed errors**

Use these immutable defaults:

```js
export const KAKAO_MOBILITY_OFFICIAL_DAILY_QUOTA = 10_000;
export const KAKAO_MOBILITY_DEFAULT_DAILY_BUDGET = 8_000;
export const KAKAO_MOBILITY_DEFAULT_WARNING = 7_000;
```

`mobilityBudgetPolicy()` reads `KAKAO_MOBILITY_DAILY_BUDGET` and
`KAKAO_MOBILITY_DAILY_WARNING`, clamps budget to `1..10000`, and clamps warning to
`1..budget`. `ProviderBudgetExhaustedError` has code `UPSTREAM_BUDGET_EXHAUSTED`, provider,
operation, and `retryAfterSeconds`; it never includes coordinates or the API key.

- [ ] **Step 5: Implement tracked call ordering**

`trackProviderCall` must execute:

```text
usageDate = KST date(now)
reservation = await reserve(...)
if denied: throw ProviderBudgetExhaustedError before call()
try: value = await call(); record(success) best-effort; return value
catch: record(classify(error)) best-effort; rethrow original error
```

Reservation failures are not best-effort: if the DB cannot enforce the hard budget, fail closed.
Result-recording failures emit one sanitized `console.error` line and must not replace the provider
result or original provider error.

- [ ] **Step 6: Run focused and database regression tests**

```powershell
node --test tests/provider-usage.test.js tests/database.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit the runtime boundary**

```powershell
git add backend/lib/provider-usage.js backend/lib/database.js backend/lib/env.js backend/tests/provider-usage.test.js backend/tests/database.test.js
git commit -m "feat: 공급자 일일 호출 예산 경계 추가"
```

---

### Task 3: Kakao and TourAPI instrumentation with safe quota errors

**Files:**
- Modify: `backend/lib/kakao-mobility.js`
- Modify: `backend/lib/kakao-local.js`
- Modify: `backend/lib/tour-api.js`
- Modify: `backend/lib/http.js`
- Modify: `backend/tests/kakao-mobility.test.js`
- Modify: `backend/tests/kakao-local.test.js`
- Modify: `backend/tests/tour-api.test.js`
- Modify: `backend/tests/recommendations.test.js`
- Modify: `backend/tests/route-api.test.js`

**Interfaces:**
- Consumes: `trackProviderCall`, `mobilityBudgetPolicy`, `ProviderResponseError`
- Produces: every Kakao Mobility directions request uses `KAKAO_MOBILITY/DIRECTIONS`
- Produces: public 503 contracts for internal budget and upstream quota exhaustion

- [ ] **Step 1: Add failing Kakao error-body tests**

In `kakao-mobility.test.js`, pass a no-op usage tracker and return these responses:

```js
const usageTracker = async ({ call }) => call();
const quota400 = new Response(JSON.stringify({ code: -10, msg: "quota exceeded" }), {
  status: 400,
  headers: { "content-type": "application/json" }
});
await assert.rejects(
  fetchKakaoRoute(start, destination, [], { fetchImpl: async () => quota400, usageTracker }),
  (error) => error.code === "UPSTREAM_QUOTA_EXHAUSTED" && error.providerCode === -10
);
```

Add equivalent HTTP 429 and 503 assertions. Assert the thrown message does not contain origin,
destination, authorization header, or response `msg`.

- [ ] **Step 2: Add failing hard-stop API tests**

Extend each endpoint test's existing global `fetch` stub to recognize
`/rest/v1/rpc/reserve_provider_usage` before any Kakao URL and return this denied RPC payload:

```js
return new Response(JSON.stringify([{
  allowed: false,
  reserved_count: 8_000,
  remaining_count: 0
}]), { status: 200, headers: { "content-type": "application/json" } });
```

Set the test Supabase URL/service key environment variables, count any request whose host is
`apis-navi.kakaomobility.com`, and assert HTTP 503, `UPSTREAM_BUDGET_EXHAUSTED`, numeric
`Retry-After`, and Kakao request count zero. Restore all environment variables and global `fetch` in
`finally`.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
node --test tests/kakao-mobility.test.js tests/recommendations.test.js tests/route-api.test.js
```

Expected: quota response is a generic error and budget error maps to HTTP 500 before implementation.

- [ ] **Step 4: Read bounded error bodies and create safe response errors**

For Kakao non-OK responses, consume at most the parsed `code` and status. Do not retain `msg` or raw
body. Create `ProviderResponseError` with `{ provider, status, providerCode }`; assign code
`UPSTREAM_QUOTA_EXHAUSTED` when status is 429 or providerCode is `-10`. Preserve a valid upstream
`Retry-After` header; otherwise set `retryAfterSeconds` to the next KST midnight for quota errors.

- [ ] **Step 5: Wrap every Mobility request at the lowest shared boundary**

Extend `fetchKakaoRoute` options:

```js
{
  apiKey = requiredEnv("KAKAO_REST_API_KEY"),
  signal,
  fetchImpl = fetch,
  usageTracker = trackProviderCall,
  now
}
```

Call `usageTracker({ provider:"KAKAO_MOBILITY", operation:"DIRECTIONS",
budgetLimit:mobilityBudgetPolicy().budgetLimit, signal, now, call })`. Propagate `usageTracker` and
`now` through `fetchKakaoRoutes`. Candidate workers must immediately rethrow errors whose code is
`UPSTREAM_BUDGET_EXHAUSTED` or `UPSTREAM_QUOTA_EXHAUSTED` instead of counting them as an ordinary
candidate failure.

- [ ] **Step 6: Add observation-only Local and TourAPI tracking**

Wrap Kakao Local keyword and region calls as `KAKAO_LOCAL/KEYWORD_SEARCH` and
`KAKAO_LOCAL/REGION`, with `budgetLimit:null`. Wrap the common TourAPI request boundary using
operation names already supplied to the helper and `budgetLimit:null`. Recognize TourAPI official
daily-limit code `22` as `UPSTREAM_QUOTA_EXHAUSTED` without storing the result message.

- [ ] **Step 7: Map budget and quota errors to sanitized 503 responses**

Add branches before the generic 500 branch in `serverError`:

```js
if (error?.code === "UPSTREAM_BUDGET_EXHAUSTED" ||
    error?.code === "UPSTREAM_QUOTA_EXHAUSTED") {
  return json({ error: { code: error.code, message: "오늘 경로 조회 한도에 도달했습니다.", requestId } },
    503,
    error.retryAfterSeconds ? { "retry-after": String(error.retryAfterSeconds) } : {});
}
```

Keep `UPSTREAM_TIMEOUT` as 504 and all other errors as 500.

- [ ] **Step 8: Run provider and endpoint tests**

```powershell
node --test tests/provider-usage.test.js tests/kakao-mobility.test.js tests/kakao-local.test.js tests/tour-api.test.js tests/recommendations.test.js tests/route-api.test.js
```

Expected: PASS; existing candidate limit remains 8.

- [ ] **Step 9: Commit provider integration**

```powershell
git add backend/lib/kakao-mobility.js backend/lib/kakao-local.js backend/lib/tour-api.js backend/lib/http.js backend/tests/kakao-mobility.test.js backend/tests/kakao-local.test.js backend/tests/tour-api.test.js backend/tests/recommendations.test.js backend/tests/route-api.test.js
git commit -m "feat: Kakao 호출량과 공급자 오류 기록"
```

---

### Task 4: Authenticated Gate 1 operations status

**Files:**
- Create: `backend/api/ops/status.js`
- Create: `backend/tests/ops-status.test.js`
- Modify: `backend/scripts/check-project.js`
- Modify: `backend/lib/database.js`

**Interfaces:**
- Consumes: `getGate1bOpsStatus`, `kstUsageDate`
- Produces: `createOpsStatusHandler(dependencies)` and default `GET /api/ops/status`

- [ ] **Step 1: Write failing method, auth, and response tests**

Use injected dependencies so no real Supabase call occurs:

```js
const handler = createOpsStatusHandler({
  secret: () => "cron-secret",
  now: () => new Date("2026-08-28T00:00:00.000Z"),
  getStatus: async (input) => ({ usageDate: input.usageDate, usage: [], syncJobs: [], dataQuality: {} })
});

assert.equal((await handler.fetch(new Request("https://x/api/ops/status"))).status, 401);
const ok = await handler.fetch(new Request("https://x/api/ops/status", {
  headers: { authorization: "Bearer cron-secret" }
}));
assert.equal(ok.status, 200);
assert.equal(ok.headers.get("cache-control"), "no-store");
```

Also assert POST => 405 and that unauthorized requests never call `getStatus`.

- [ ] **Step 2: Run focused test and verify RED**

```powershell
node --test tests/ops-status.test.js
```

Expected: FAIL because the endpoint does not exist.

- [ ] **Step 3: Implement the injectable endpoint**

`createOpsStatusHandler` defaults to `requiredEnv("CRON_SECRET")`, `new Date()`, and
`getGate1bOpsStatus`. It accepts GET only, authenticates before any DB access, passes KST usage date,
`sigunguCode:1`, `curationTarget:100`, and returns:

```json
{
  "status": "ok",
  "generatedAt": "2026-08-28T00:00:00.000Z",
  "usageDate": "2026-08-28",
  "usage": [],
  "syncJobs": [],
  "dataQuality": {}
}
```

- [ ] **Step 4: Add endpoint to project checks and test all API contracts**

```powershell
node --test tests/ops-status.test.js tests/places-api.test.js tests/account.test.js
pnpm run check
```

Expected: PASS and `api/ops/status.js` is included in required files.

- [ ] **Step 5: Commit the operator endpoint**

```powershell
git add backend/api/ops/status.js backend/tests/ops-status.test.js backend/scripts/check-project.js backend/lib/database.js
git commit -m "feat: Gate 1 운영 상태 API 추가"
```

---

### Task 5: Conservative operating-hours evaluation

**Files:**
- Create: `backend/lib/operating-hours.js`
- Create: `backend/tests/operating-hours.test.js`
- Modify: `backend/lib/time-safe.js`
- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/lib/database.js`

**Interfaces:**
- Produces: `evaluateOperatingWindow(place, { arrival, departure, timeZone })`
- Preserves: `operationStatus(place, instant)` as compatibility wrapper
- Consumes from effective view: `effective_opening_hours`, `effective_closed_days`, `effective_last_admission`

- [ ] **Step 1: Write the full RED decision table**

Tests must include:

```js
assert.deepEqual(
  evaluateOperatingWindow(
    { opening_hours: "평일 09:00~18:00 / 주말 10:00~17:00", closed_days: "연중무휴" },
    { arrival: new Date("2026-08-28T01:00:00Z"), departure: new Date("2026-08-28T02:00:00Z") }
  ).status,
  "OPEN"
);
```

Add exact cases for weekly Monday closure, 24 hours, arrival before opening, departure after closing,
explicit `입장 마감 17:30`, `종료 30분 전 입장 마감`, overnight `22:00~02:00`, unsupported
season wording, holiday exceptions, conflicting ranges, and missing hours. Unsupported/partial cases
must equal `UNKNOWN`, not `OPEN`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test tests/operating-hours.test.js tests/time-safe.test.js
```

Expected: FAIL because `operating-hours.js` is absent and current parser returns optimistic values.

- [ ] **Step 3: Implement normalization and parse result types**

Normalize HTML remnants and Unicode separators, then parse to an internal object:

```js
{
  complete: true,
  intervalsByDay: new Map([[0, [[600, 1020]]], ...]),
  closedWeekdays: new Set([1]),
  lastAdmissionByDay: new Map([[0, 990], ...]),
  reason: "PARSED"
}
```

Reject to `{ complete:false, reason:"UNSUPPORTED_SEASON" }` for season/month wording not fully
represented, `UNSUPPORTED_HOLIDAY` for holiday exceptions, and `AMBIGUOUS` for conflicting or
partial branches. Do not guess omitted weekend or weekday schedules.

- [ ] **Step 4: Implement visit-window evaluation**

Convert `arrival` and `departure` through `Intl.DateTimeFormat` with `Asia/Seoul`; do not rely on the
Vercel process timezone. Apply last admission only to arrival and closing time to departure. A visit
crossing a calendar day must be contained in a supported overnight interval; otherwise return
`UNKNOWN` or `CLOSED` according to the parsed schedule.

- [ ] **Step 5: Wire effective fields and preserve compatibility**

Map `effective_opening_hours` to public `opening_hours`, effective closed days to `closed_days`, and
effective last admission to `last_admission` in `toPublicPlace`. Update `recommendPlaces` to call
`evaluateOperatingWindow` once with exact arrival and `arrival + default_stay_minutes`, eliminating
the old double `operationStatus` logic. Keep the wrapper for existing callers and tests.

- [ ] **Step 6: Run parser and recommendation regressions**

```powershell
node --test tests/operating-hours.test.js tests/time-safe.test.js tests/recommendations.test.js tests/database.test.js
```

Expected: PASS; closed places remain excluded and uncertain places remain in results as `UNKNOWN`.

- [ ] **Step 7: Commit operating-hours hardening**

```powershell
git add backend/lib/operating-hours.js backend/lib/time-safe.js backend/lib/database.js backend/tests/operating-hours.test.js backend/tests/time-safe.test.js backend/tests/recommendations.test.js backend/tests/database.test.js
git commit -m "feat: 운영시간 방문 구간을 보수적으로 판정"
```

---

### Task 6: Festival eligibility before and after precise routing

**Files:**
- Create: `backend/lib/festival-eligibility.js`
- Create: `backend/tests/festival-eligibility.test.js`
- Modify: `backend/lib/time-safe.js`
- Modify: `backend/api/recommendations.js`
- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/tests/recommendations.test.js`

**Interfaces:**
- Produces: `isFestival(place)` and `isFestivalVisitEligible(place, arrival, timeZone="Asia/Seoul")`
- Changes: `selectRouteCandidates(criteria, places, limit=20, now=new Date())`
- Preserves: non-festival candidates unchanged

- [ ] **Step 1: Write failing eligibility matrix tests**

Use an arrival on `2026-08-28` KST and assert:

```text
2026-08-20..2026-08-31 => true
2026-08-01..2026-08-27 => false
2026-08-29..2026-08-31 => false
missing start or end => false
end before start => false
non-festival with no dates => true
```

Add a UTC instant on each side of KST midnight to prove timezone correctness.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test tests/festival-eligibility.test.js tests/recommendations.test.js
```

Expected: FAIL because no festival filter exists.

- [ ] **Step 3: Implement strict ISO date comparison**

Accept only DB-normalized `YYYY-MM-DD`. Convert the arrival instant to a KST `YYYY-MM-DD` string and
compare lexicographically after validating both event dates with a UTC round trip. Treat
`content_type_id===15` or `category==="FESTIVAL"` as a festival.

- [ ] **Step 4: Filter before Kakao candidate calls**

In `selectRouteCandidates`, calculate the existing estimated route first, derive
`estimatedArrival = now + firstLegMinutes`, then remove ineligible festivals before sorting and
slicing. Capture `const requestNow = new Date()` once in `recommendations.js` and pass the same value
to both `selectRouteCandidates(..., requestNow)` and `recommendPlaces(..., requestNow)`; do not call
`new Date()` separately for each candidate or for the precise recheck.

- [ ] **Step 5: Recheck with precise route arrival**

In `recommendPlaces`, after the exact route is found, return null when
`isFestivalVisitEligible(place, arrival)` is false. This catches a route crossing KST midnight after
the estimate.

- [ ] **Step 6: Verify no ineligible festival triggers a Kakao candidate request**

Add an endpoint test with one active and three ineligible festival rows. Assert only the active
festival's waypoint appears in `candidateRouteRequests`, while `meta.corridorCandidateCount` may
still include raw corridor rows and `meta.routeCandidateCount` contains only eligible routed rows.

- [ ] **Step 7: Run the festival regression set**

```powershell
node --test tests/festival-eligibility.test.js tests/time-safe.test.js tests/recommendations.test.js
```

Expected: PASS and no ineligible festival waypoint reaches Kakao.

- [ ] **Step 8: Commit the festival filter**

```powershell
git add backend/lib/festival-eligibility.js backend/lib/time-safe.js backend/api/recommendations.js backend/tests/festival-eligibility.test.js backend/tests/time-safe.test.js backend/tests/recommendations.test.js
git commit -m "feat: 유효 기간 밖 축제를 추천에서 제외"
```

---

### Task 7: Curation CSV tooling and safe Supabase import

**Files:**
- Create: `backend/lib/curation-csv.js`
- Create: `backend/scripts/export-place-curations.mjs`
- Create: `backend/scripts/validate-place-curations.mjs`
- Create: `backend/scripts/import-place-curations.mjs`
- Create: `backend/tests/curation-csv.test.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/package.json`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**
- Produces CSV columns: `content_id,name,category,operating_info_status,opening_hours,closed_days,last_admission,admission_info_status,parking_info,parking_info_status,source_urls,source_checked_at,reviewed_at,review_note`
- Produces: `parseCurationCsv`, `serializeCurationCsv`, `validateCurationRows(rows,{expectedCount})`
- Produces scripts: `pnpm curation:export`, `pnpm curation:validate`, `pnpm curation:import`

- [ ] **Step 1: Write failing CSV round-trip and validation tests**

Tests must prove quoted comma, quote escaping, embedded newline, CRLF, duplicate content ID, invalid
status, missing official URL, invalid timestamp, `VERIFIED` without value, and count mismatch.

```js
const csv = serializeCurationCsv([{ content_id:"1", name:"박물관, 본관", review_note:"첫째 줄\n둘째 줄", ...valid }]);
assert.equal(parseCurationCsv(csv)[0].name, "박물관, 본관");
assert.throws(() => validateCurationRows([valid, {...valid}], { expectedCount:2 }), /중복/);
```

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test tests/curation-csv.test.js
```

Expected: FAIL because the module does not exist.

- [ ] **Step 3: Implement RFC 4180 parsing and deterministic serialization**

Use only Node built-ins. The parser is a character-state machine with `inQuotes`; `""` inside a
quoted field becomes `"`. Serialization always writes the fixed header order and quotes any field
containing comma, quote, CR, or LF. `source_urls` is a JSON array string inside one CSV field.

- [ ] **Step 4: Implement strict row validation**

`validateCurationRows` returns normalized DB rows or throws one combined error listing row numbers.
Require HTTPS source URLs, exact enum values, valid ISO instants, unique non-empty content IDs, and
the field requirements from migration 006. When `expectedCount:100`, require exactly 100 rows.

- [ ] **Step 5: Implement export candidate selection**

Add a DB adapter that reads active `effective_places` with `sigungu_code=1`. The export script sorts
within category by: has image, has overview, has intro sync, then name; it allocates category slots
without excluding categories with fewer candidates and fills remaining slots globally until exactly
100 unique IDs. Export rows carry the immutable ID/name/category and `UNKNOWN` statuses, empty
information fields, and no fake source URL; therefore validation intentionally fails until researched.

- [ ] **Step 6: Implement validate and import commands**

Add package scripts:

```json
"curation:export": "node scripts/export-place-curations.mjs",
"curation:validate": "node scripts/validate-place-curations.mjs",
"curation:import": "node scripts/import-place-curations.mjs"
```

Import must call `validateCurationRows(...,{expectedCount:100})`, verify all IDs exist as active
`sigungu_code=1` rows, then upsert `place_curations?on_conflict=content_id` in chunks of 50 with
`resolution=merge-duplicates,return=minimal`. Never read or print `SUPABASE_SERVICE_ROLE_KEY`.

`validate-place-curations.mjs --allow-partial` still requires exactly 100 unique immutable candidate
rows. It fully validates rows whose `reviewed_at` is present, prints the remaining row numbers, and
exits zero only for field errors in completed rows; normal mode requires all 100 reviewed rows and
exits nonzero for any omission.

- [ ] **Step 7: Run tooling tests and project checks**

```powershell
node --test tests/curation-csv.test.js tests/database.test.js
pnpm run check
```

Expected: PASS.

- [ ] **Step 8: Commit curation tooling**

```powershell
git add backend/lib/curation-csv.js backend/scripts/export-place-curations.mjs backend/scripts/validate-place-curations.mjs backend/scripts/import-place-curations.mjs backend/tests/curation-csv.test.js backend/lib/database.js backend/package.json backend/scripts/check-project.js
git commit -m "feat: 핵심 장소 검수 데이터 도구 추가"
```

---

### Task 8: Research and commit the 100-place Gangneung alpha dataset

**Files:**
- Create: `backend/data/gangneung-core-place-curations.csv`
- Modify: `backend/tests/curation-csv.test.js`

**Interfaces:**
- Consumes: Task 7 export and validator
- Produces: exactly 100 source-verified rows ready for Task 9 import

- [ ] **Step 1: Export a fresh 100-place candidate file from the connected Supabase project**

```powershell
Set-Location C:\app\tteumsae\backend
pnpm curation:export -- --output data/gangneung-core-place-curations.csv
```

Expected: 100 unique active Gangneung rows; `pnpm curation:validate` fails because research fields are
not yet verified.

- [ ] **Step 2: Verify rows 1–20 against current official sources**

For each row, use place-owned official website first, then Gangneung City or a public tourism source.
Record the exact hours/closed days/last admission/parking text, HTTPS source URL array, current
`source_checked_at`, and `reviewed_at`. If a field cannot be verified, set its status to `UNKNOWN` and
explain the checked sources in `review_note`; do not invent a value.

- [ ] **Step 3: Verify rows 21–40 with the same acceptance rules**

After the batch, run the validator with `--allow-partial` so completed rows pass field constraints and
unreviewed rows are reported by number. Fix every error in rows 21–40 before continuing.

- [ ] **Step 4: Verify rows 41–60 with the same acceptance rules**

Reject third-party blog, map review, and search-result snippets as sole `VERIFIED` evidence. A place
with no current primary/public source remains explicit `UNKNOWN`.

- [ ] **Step 5: Verify rows 61–80 with the same acceptance rules**

For seasonal or holiday exceptions, preserve the source wording in `review_note`; only place a simple
schedule in `opening_hours` when the parser can represent it without discarding an active exception.

- [ ] **Step 6: Verify rows 81–100 with the same acceptance rules**

For festivals, also cross-check stored event start/end dates in the source and note any mismatch;
festival date corrections remain in the TourAPI/raw-data workflow rather than the curation schedule
columns.

- [ ] **Step 7: Add an exact production-data contract test and validate all rows**

Extend `curation-csv.test.js` to load the committed file and assert:

```js
const rows = parseCurationCsv(await readFile(dataUrl, "utf8"));
const normalized = validateCurationRows(rows, { expectedCount: 100 });
assert.equal(new Set(normalized.map((row) => row.content_id)).size, 100);
assert.ok(normalized.every((row) => row.source_urls.length > 0));
```

Run:

```powershell
pnpm curation:validate -- --input data/gangneung-core-place-curations.csv
node --test tests/curation-csv.test.js
```

Expected: 100/100 valid, zero duplicates, zero missing required evidence.

- [ ] **Step 8: Commit the reviewed dataset**

```powershell
git add backend/data/gangneung-core-place-curations.csv backend/tests/curation-csv.test.js
git commit -m "data: 강릉 핵심 장소 100개 검수"
```

---

### Task 9: Operations docs, external application, and completion verification

**Files:**
- Modify: `backend/.env.example`
- Modify: `backend/README.md`
- Modify: `docs/05_API_AND_DATA.md`
- Modify: `docs/06_ENVIRONMENT_AND_ACCESS.md`
- Modify: `docs/07_BUILD_TEST_DEPLOY.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`
- Modify: `docs/10_DECISION_LOG.md`
- Modify: `docs/superpowers/plans/2026-08-28-gate-1b-data-trust.md`

**Interfaces:**
- Consumes: all prior tasks
- Produces: repeatable migration/deploy/import runbook and evidence-backed Gate 1 status

- [x] **Step 1: Add configuration and API contracts to docs**

Add:

```dotenv
KAKAO_MOBILITY_DAILY_WARNING=7000
KAKAO_MOBILITY_DAILY_BUDGET=8000
```

Document `/api/ops/status` Bearer auth, 503 budget/quota errors, `Retry-After`, KST reset, curation
commands, migration order, and rollback principle. Never paste actual secrets or live response bodies.

- [x] **Step 2: Document the production application order**

The runbook order is exact:

```text
1. Back up/export current places and sync_state metadata.
2. Apply migration 006 in Supabase SQL Editor.
3. Run migration smoke queries and confirm service-role-only RPC access.
4. Add/confirm Vercel budget environment variables.
5. Deploy Preview and test health, places, recommendation, route, ops auth.
6. Run curation validate, then import exactly 100 rows.
7. Confirm ops dataQuality.curation.reviewed=100.
8. Promote the verified deployment to Production.
```

Rollback is code rollback plus disabling the new ops endpoint; do not drop usage or curation tables,
because they are operational evidence and the old code ignores them.

- [x] **Step 3: Run the complete local backend suite**

```powershell
Set-Location C:\app\tteumsae\backend
pnpm test
pnpm run check
```

Expected: every backend test passes and project check includes all Gate 1-B files.

- [x] **Step 4: Run Android regression because API errors and place fields changed**

```powershell
Set-Location C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: unit tests PASS, lint reports zero errors, Debug APK assembles.

- [x] **Step 5: Apply external gates only with captured evidence**

Apply migration 006, configure Vercel, deploy, call authenticated ops status, and import the 100-row
CSV. Record timestamps and non-secret summaries in `docs/07_BUILD_TEST_DEPLOY.md`. If live access is
unavailable, leave these checkboxes and Gate 1 status unchecked and report the exact blocker.

- [x] **Step 6: Update Gate truth without overclaiming**

Mark each item in `docs/09_NEXT_VERSION_PLAN.md` complete only when its evidence exists:

```text
daily budget/429/5xx/Cron/coverage: code + migration + live ops response
hours parser: automated matrix green
festival filter: automated matrix green
100 places: validator 100/100 + successful DB import + ops count 100
```

Add the adopted 8,000/7,000 policy, conservative parser, overlay strategy, and Gangneung-first scope
to `docs/10_DECISION_LOG.md`.

- [x] **Step 7: Self-review the plan execution record and commit docs**

Search for unchecked items and unsupported completion claims:

```powershell
Set-Location C:\app\tteumsae
rg -n "Gate 1|\[ \]|완료|미완료" docs/09_NEXT_VERSION_PLAN.md docs/08_QA_AND_KNOWN_ISSUES.md docs/07_BUILD_TEST_DEPLOY.md docs/superpowers/plans/2026-08-28-gate-1b-data-trust.md
git diff --check
git status --short
```

Then commit only Gate 1-B documents and configuration:

```powershell
git add backend/.env.example backend/README.md docs/05_API_AND_DATA.md docs/06_ENVIRONMENT_AND_ACCESS.md docs/07_BUILD_TEST_DEPLOY.md docs/08_QA_AND_KNOWN_ISSUES.md docs/09_NEXT_VERSION_PLAN.md docs/10_DECISION_LOG.md docs/superpowers/plans/2026-08-28-gate-1b-data-trust.md
git commit -m "docs: Gate 1-B 운영 검증 결과 기록"
```

---

## Execution Order and Stop Conditions

Execute Tasks 1–7 in order. Task 8 requires current web/source verification and may take multiple
batches, but it must not be replaced by generated or inferred facts. Task 9 may apply production
changes only after all local tests and the 100-row validator pass.

Stop and report instead of guessing when:

- migration 006 conflicts with the live schema,
- the configured Supabase project differs from the documented Tteumsae project,
- fewer than 100 active Gangneung places exist,
- an official source cannot support a `VERIFIED` value,
- Vercel/Supabase authentication requires user interaction,
- a production smoke test changes data outside the curation import scope.

Do not stop merely because a test fails: use systematic debugging, fix the root cause, rerun the
focused test, then rerun the relevant regression set.
