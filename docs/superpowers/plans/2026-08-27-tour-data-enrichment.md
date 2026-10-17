# TourAPI Data Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 도착 마감 추천이 영업 종료·축제 기간·카페 분류를 신뢰할 수 있도록 TourAPI 상세 데이터를 기본 원본과 분리해 영속화하고 시간 중요 데이터부터 단계적으로 동기화한다.

**Architecture:** `places.raw`는 `areaBasedList2` 기본 원본만 소유하고 추천·상세 화면이 쓰는 값은 정규화 컬럼과 별도 `enrichment_raw`에 저장한다. 동기화는 카탈로그, `detailIntro2`, 표현 상세, 축제로 분리하며 각 단계는 독립 성공 상태와 재시도 시각을 가진다. 추천 요청은 DB 데이터만 사용하고 TourAPI를 실시간 호출하지 않는다.

**Tech Stack:** Node.js 24.x ES modules/node:test, Vercel Functions/Cron (`maxDuration=60`), Supabase Postgres/PostgREST, TourAPI `KorService2`, npm

**Spec:** `docs/superpowers/specs/2026-08-27-tour-data-enrichment-design.md`

## Global Constraints

- 기준 브랜치는 `agent/new-route-flow-ui`, 계획 작성 기준 커밋은 `9b67544`다.
- 개발계정 TourAPI 일일 한도 1,000건 안에서 동작하며 사용자 추천 요청 중에는 TourAPI를 호출하지 않는다.
- `SAFETY_BUFFER_MINUTES=10`, `MINIMUM_STAY_MINUTES=15` 판정은 통계가 아니라 경로·영업시간만 사용한다.
- TourAPI 무응답·빈 데이터·부분 실패가 기존 정상 상세 값을 삭제해서는 안 된다.
- `A05020900`만 `CAFE`에 매핑하고 미확인 음식점은 `RESTAURANT`로 유지한다.
- `showflag="1"`은 활성, `showflag="0"`은 비표출로 fixture를 고정하되 운영 반영 전 실제 응답으로 확인한다.
- 현재 `TOUR_API_SERVICE_KEY`를 서버에서만 재사용하고 신규 비밀값을 Git이나 Android에 넣지 않는다.
- 사용자 소유 미추적 경로 `output/`, `tmp/`를 수정·삭제·커밋하지 않는다.
- 각 작업은 실패 테스트 → 최소 구현 → 집중 테스트 → 전체 백엔드 회귀 → 해당 파일만 커밋 순서로 진행한다.

## File Responsibility Map

| File | Responsibility |
|---|---|
| `backend/migrations/004_tour_enrichment.sql` | 상세 컬럼, 동기화 커서, 기존 `_tteumsae` 이전 |
| `backend/lib/tour-api.js` | TourAPI 호출과 기본/인트로/공통/미디어/축제 정규화 |
| `backend/lib/database.js` | 공개 장소 매핑, 단계별 상세 저장, 배치 대상·통계 조회 |
| `backend/lib/tour-sync.js` | 제한 동시성, 단계별 배치 실행, 성공·실패 집계 |
| `backend/api/cron/tour-sync.js` | 수동 전체 카탈로그 복구 동기화 |
| `backend/api/cron/tour-catalog-sync.js` | `areaBasedSyncList2` 증분 동기화 |
| `backend/api/cron/tour-intro-sync.js` | 운영시간·휴무일·행사 기간 우선 동기화 |
| `backend/api/cron/tour-presentation-sync.js` | 소개·홈페이지·이미지·반려동물 동기화 |
| `backend/api/cron/tour-festival-sync.js` | 현재·예정 강원 축제 기간 동기화 |
| `backend/api/cron/tour-status.js` | 인증된 운영 데이터 보강률 확인 |
| `backend/scripts/run-tour-intro-backfill.js` | 초기 운영시간 백필 반복 실행 |
| `backend/lib/time-safe.js` | 축제 유효기간과 영업 종료 추천 반영 |

---

### Task 1: Persist enrichment outside `places.raw`

**Files:**

- Create: `backend/migrations/004_tour_enrichment.sql`
- Modify: `backend/lib/database.js`
- Modify: `backend/tests/database.test.js`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**

- Consumes: 기존 `listPlaces`, `getPlace`, `databaseRequest`.
- Produces: 공개 장소의 `cat1`, `cat2`, `cat3`, `opening_hours`, `closed_days`, `event_start_date`, `event_end_date`, `overview`, `homepage_url`, `image_urls`, `tags`.

- [ ] **Step 1: Write the failing public mapping test**

`backend/tests/database.test.js`에 PostgREST가 정규화 컬럼과 충돌하는 레거시 `raw._tteumsae`를 함께 반환해도 정규화 컬럼만 공개되는 테스트를 추가한다.

```js
test("정규화된 TourAPI 상세 컬럼을 공개하고 raw는 숨긴다", async () => {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json([{
    content_id: "123", source: "TOUR_API", name: "테스트 카페",
    category: "CAFE", content_type_id: 39, area_code: 32,
    latitude: 37.75, longitude: 128.87, default_stay_minutes: 40,
    cat1: "A05", cat2: "A0502", cat3: "A05020900",
    opening_hours: "09:00~18:00", closed_days: "매주 월요일",
    image_urls: ["https://example.com/1.jpg"], tags: ["주차 가능"],
    raw: { _tteumsae: { openingHours: "00:00~01:00" } }
  }]);
  try {
    const [place] = await listPlaces({ limit: 1 });
    assert.equal(place.opening_hours, "09:00~18:00");
    assert.equal(place.cat3, "A05020900");
    assert.equal("raw" in place, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
```

- [ ] **Step 2: Run focused test and confirm RED**

Run `node --test tests/database.test.js` from `backend/`. Expect failure because `publicColumns` still reads `raw._tteumsae`.

- [ ] **Step 3: Add migration `004_tour_enrichment.sql`**

Add `cat1`, `cat2`, `cat3`, `opening_hours`, `closed_days`, `event_start_date date`, `event_end_date date`, `overview`, `homepage_url`, `image_urls jsonb default []`, `tags text[] default {}`, `enrichment_raw jsonb default {}`, `intro_synced_at`, `common_synced_at`, `media_synced_at`, `enrichment_attempts`, `enrichment_last_error`, and `next_enrichment_at` to `places`. Add `source_cursor` and `cycle_started_at` to `sync_state`.

Before deleting only the `_tteumsae` member from `raw`, copy legacy values:

```sql
update public.places
set opening_hours = coalesce(opening_hours, raw #>> '{_tteumsae,openingHours}'),
    closed_days = coalesce(closed_days, raw #>> '{_tteumsae,closedDays}'),
    image_urls = case when jsonb_typeof(raw #> '{_tteumsae,imageUrls}') = 'array'
      then raw #> '{_tteumsae,imageUrls}' else image_urls end,
    tags = case when jsonb_typeof(raw #> '{_tteumsae,tags}') = 'array'
      then array(select jsonb_array_elements_text(raw #> '{_tteumsae,tags}'))
      else tags end,
    enrichment_raw = enrichment_raw || jsonb_build_object('legacy', raw -> '_tteumsae')
where raw ? '_tteumsae';

update public.places set raw = raw - '_tteumsae' where raw ? '_tteumsae';
```

Add partial indexes for active rows missing `intro_synced_at` and active festivals by `event_end_date`. Insert sync state IDs `tour_catalog_delta`, `tour_intro`, `tour_presentation`, `tour_festival` idempotently.

- [ ] **Step 4: Switch public mapping to normalized columns**

Remove `raw` from `publicColumns`; select the normalized fields. `toPublicPlace` defensively destructures and discards a returned `raw` value, then supplies empty-array fallbacks for malformed `image_urls` and `tags`.

- [ ] **Step 5: Register and verify**

Add migration 004 to `check-project.js`, then run:

```powershell
node --test tests/database.test.js
npm test
npm run check
```

Expected: all pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/migrations/004_tour_enrichment.sql backend/lib/database.js backend/tests/database.test.js backend/scripts/check-project.js
git commit -m "feat: TourAPI 상세 데이터 영속 컬럼 분리"
```

### Task 2: Normalize category, intro, common, and media data

**Files:**

- Modify: `backend/lib/tour-api.js`
- Modify: `backend/tests/tour-api.test.js`

**Interfaces:**

- Produces `normalizeTourIntro({ contentTypeId, intro, syncedAt })`.
- Produces `normalizeTourCommon({ common, syncedAt })`.
- Produces `normalizeTourMedia({ contentTypeId, intro, images, pet, syncedAt })`.
- `mapTourItem` additionally produces `cat1`, `cat2`, `cat3` and maps `A05020900` to `CAFE`.

- [ ] **Step 1: Add failing literal-fixture tests**

```js
test("TourAPI 카페 소분류만 CAFE로 매핑한다", () => {
  const base = { contentid: "1", contenttypeid: "39", title: "테스트",
    areacode: "32", mapx: "128.87", mapy: "37.75", cat1: "A05", cat2: "A0502" };
  assert.equal(mapTourItem({ ...base, cat3: "A05020900" }).category, "CAFE");
  assert.equal(mapTourItem({ ...base, cat3: "A05020100" }).category, "RESTAURANT");
  assert.equal(mapTourItem({ ...base, cat3: "" }).category, "RESTAURANT");
});

test("인트로에서 영업시간과 행사 기간을 분리한다", () => {
  const result = normalizeTourIntro({ contentTypeId: 15, intro: {
    playtime: "10:00~18:00<br>", eventstartdate: "20260820", eventenddate: "20260831"
  }, syncedAt: "2026-08-27T00:00:00.000Z" });
  assert.equal(result.openingHours, "10:00~18:00");
  assert.equal(result.eventStartDate, "2026-08-20");
  assert.equal(result.eventEndDate, "2026-08-31");
});

test("공통 상세의 소개와 홈페이지를 정규화한다", () => {
  const result = normalizeTourCommon({ common: {
    overview: "<p>바다 옆 <b>전시관</b></p>",
    homepage: '<a href="https://example.com/place">홈페이지</a>'
  }, syncedAt: "2026-08-27T00:00:00.000Z" });
  assert.equal(result.overview, "바다 옆 전시관");
  assert.equal(result.homepageUrl, "https://example.com/place");
});
```

- [ ] **Step 2: Run `node --test tests/tour-api.test.js` and confirm RED**

Expected: missing category fields and normalizer exports.

- [ ] **Step 3: Implement mapping helpers**

Use `CAFE_CAT3_CODES = new Set(["A05020900"])`. Parse only eight-digit Tour dates into `YYYY-MM-DD`; invalid values become `null`. Strip HTML and collapse whitespace. Homepage accepts only `http:`/`https:` and extracts anchor `href` before validation.

- [ ] **Step 4: Add `fetchTourCommon(contentId)`**

Call the current `KorService2/detailCommon2` contract with `contentId`, `numOfRows`, and
`pageNo`. The current operation returns `overview` and `homepage` without the legacy
`defaultYN`/`overviewYN` selection flags. Keep the shared response parser's
single-object/array/empty handling.

- [ ] **Step 5: Keep `normalizeTourEnrichment` as a compatibility wrapper**

Compose it from the new intro/media normalizers until Task 5 removes the old caller. This preserves existing tests between commits.

- [ ] **Step 6: Verify and commit**

```powershell
node --test tests/tour-api.test.js
npm test
npm run check
git add backend/lib/tour-api.js backend/tests/tour-api.test.js
git commit -m "feat: TourAPI 분류와 상세 정보 정규화"
```

### Task 3: Add the time-critical intro synchronization lane

**Files:**

- Create: `backend/lib/tour-sync.js`
- Create: `backend/api/cron/tour-intro-sync.js`
- Create: `backend/tests/tour-sync.test.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/.env.example`
- Modify: `backend/vercel.json`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**

- Produces `listPlacesForIntroSync({ limit, now })`, `savePlaceIntro(place, enrichment)`, `recordPlaceEnrichmentFailure(place, error, now)`.
- Produces `runIntroBatch({ places, fetchIntro, saveIntro, recordFailure, concurrency, syncedAt })` → `{ processed, updated, empty, failed }`.

- [ ] **Step 1: Add failing orchestration tests**

Use three literal places: one intro success, one `null` result, one rejected call. Assert successful and empty results are saved, the rejection is recorded, and the result is `{ processed: 3, updated: 1, empty: 1, failed: 1 }`. Add a deferred-promise counter proving active calls never exceed `concurrency=2`.

- [ ] **Step 2: Run `node --test tests/tour-sync.test.js` and confirm import failure**

- [ ] **Step 3: Implement stage-owned database writes**

`listPlacesForIntroSync` selects active, due rows with null `intro_synced_at`, null-first then `content_id`. `savePlaceIntro` PATCHes only opening/closed/event dates, intro tags, `enrichment_raw.intro`, `intro_synced_at`, and resets retry fields. It must save an explicit empty result as completed.

On failure, preserve current values, increment attempts, store at most 300 error characters, and set exponential retry `min(24h, 15m * 2^(attempts-1))`.

- [ ] **Step 4: Implement limited-concurrency workers**

Use a shared index consumed by at most `concurrency` async workers. Do not create one promise per entire catalog. Count `null` intro as `empty`, normalize it, and persist the successful no-data state.

- [ ] **Step 5: Add authenticated Cron endpoint**

Accept only `GET` with `Bearer CRON_SECRET`. Clamp `TOUR_INTRO_SYNC_BATCH_SIZE` to 1–40, default 20; clamp `TOUR_SYNC_CONCURRENCY` to 1–4, default 4. Return `idle` when no rows are due and otherwise return the batch counts.

- [ ] **Step 6: Register runtime values and Cron**

Add `TOUR_INTRO_SYNC_BATCH_SIZE=20`, `TOUR_SYNC_CONCURRENCY=4`. Register the file in
project check. Replace the existing detail Cron schedule with intro at `40 18 * * *` and
leave the legacy detail endpoint available only for manual compatibility. Vercel Hobby
Cron has up to 59 minutes of scheduling imprecision, so separate `18:40` and `19:00`
schedules cannot enforce intro-first ordering. Task 5 reintroduces the presentation Cron
after its selector requires completed intro state.

- [ ] **Step 7: Verify and commit**

```powershell
node --test tests/tour-sync.test.js tests/database.test.js tests/tour-api.test.js
npm test
npm run check
git add backend/lib/tour-sync.js backend/lib/database.js backend/api/cron/tour-intro-sync.js backend/tests/tour-sync.test.js backend/.env.example backend/vercel.json backend/scripts/check-project.js
git commit -m "feat: 운영시간 우선 TourAPI 동기화 추가"
```

### Task 4: Replace daily full scans with incremental catalog sync

**Files:**

- Modify: `backend/lib/tour-api.js`
- Modify: `backend/lib/database.js`
- Create: `backend/api/cron/tour-catalog-sync.js`
- Create: `backend/tests/tour-catalog-sync.test.js`
- Modify: `backend/vercel.json`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**

- Produces `fetchTourSyncPage({ pageNo, numOfRows, modifiedTime })`, `mapTourSyncItem(item, syncedAt)`.
- Produces `resetPlaceEnrichment(contentId)` and `setPlaceActive(contentId, active)`.

- [x] **Step 1: Add a failing sync fixture test**

Stub TourAPI with two items using `showflag: "1"` and `"0"`. Assert the request path ends in `/areaBasedSyncList2`, sends the current official `YYYYMMDD` contract as literal `modifiedtime=20260827`, and maps active values to true/false.

- [x] **Step 2: Run `node --test tests/tour-catalog-sync.test.js` and confirm RED**

- [x] **Step 3: Implement sync fetch and mapping**

Reuse the shared payload parser. Send legacy-compatible `areaCode=32` together with the current legal-district filter `lDongRegnCd=51`, plus `arrange=C`, pagination, and `modifiedtime` only when supplied. Keep inactive supported items so their existing DB rows can be disabled.

- [x] **Step 4: Implement safe cursor transitions**

Read `tour_catalog_delta`; choose its cursor, otherwise the completed `tour_api.last_completed_at`, otherwise 24 hours before now. Save `cycle_started_at` on page 1. Process at most `TOUR_SYNC_MAX_PAGES`. Active changed rows upsert base fields and clear the three stage timestamps; inactive rows only set an existing row inactive. On final page, promote cycle start to `source_cursor`, reset page to 1, and clear cycle start. Overlap is allowed; gaps are not.

- [x] **Step 5: Keep full sync for recovery only**

Leave `/api/cron/tour-sync` authenticated and callable, remove it from Vercel Cron, and schedule `/api/cron/tour-catalog-sync` at `20 18 * * *`.

- [x] **Step 6: Verify `showflag` against the current official operation contract before activation**

Do not log the key. The current official Swagger contract defines `1=표출`, `0=비표출`; if a later live response differs, update the literal fixture and mapper together before deploying.

- [x] **Step 7: Verify and commit**

```powershell
node --test tests/tour-catalog-sync.test.js tests/tour-api.test.js tests/database.test.js
npm test
npm run check
git add backend/lib/tour-api.js backend/lib/database.js backend/api/cron/tour-catalog-sync.js backend/tests/tour-catalog-sync.test.js backend/vercel.json backend/scripts/check-project.js docs/superpowers/plans/2026-08-27-tour-data-enrichment.md docs/superpowers/specs/2026-08-27-tour-data-enrichment-design.md
git commit -m "feat: TourAPI 카탈로그 증분 동기화"
```

### Task 5: Split presentation enrichment into independent stages

**Files:**

- Create: `backend/api/cron/tour-presentation-sync.js`
- Modify: `backend/lib/tour-sync.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/lib/tour-api.js`
- Modify: `backend/tests/tour-sync.test.js`
- Modify: `backend/tests/tour-api.test.js`
- Delete: `backend/api/cron/tour-detail-sync.js`
- Modify: `backend/.env.example`
- Modify: `backend/vercel.json`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**

- Produces `listPlacesForPresentationSync`, `savePlaceCommon`, `savePlaceMedia`, and `runPresentationBatch`.

- [ ] **Step 1: Add a failing partial-success test**

For one place, make `fetchCommon` reject while image succeeds and pet returns null. Assert common is not saved, media is saved once, and the batch reports one partial result. Add the inverse case so a media failure cannot erase common success.

- [ ] **Step 2: Run `node --test tests/tour-sync.test.js` and confirm RED**

- [ ] **Step 3: Add stage-specific writes**

`savePlaceCommon` owns only `overview`, `homepage_url`, `enrichment_raw.common`, `common_synced_at`. `savePlaceMedia` owns image fallback, `image_urls`, the union of existing and media tags, `enrichment_raw.images/pet`, `media_synced_at`. Neither changes intro fields.

- [ ] **Step 4: Implement presentation orchestration**

Select active places with completed intro and missing common or media stage. Skip completed stages. Call common independently; call image and pet together for media. Save each fulfilled stage even if the other fails.

- [ ] **Step 5: Replace old all-in-one endpoint**

Create `/api/cron/tour-presentation-sync`, update all references, then delete `tour-detail-sync.js`. Add `TOUR_PRESENTATION_SYNC_BATCH_SIZE=5`, clamp 1–10, and point the `0 19 * * *` Cron to the new endpoint.

- [ ] **Step 6: Remove compatibility wrapper**

When no caller imports `normalizeTourEnrichment`, delete it and its old test; retain direct intro/common/media tests.

- [ ] **Step 7: Verify and commit**

```powershell
node --test tests/tour-sync.test.js tests/tour-api.test.js tests/database.test.js
npm test
npm run check
git add backend/api/cron backend/lib/tour-sync.js backend/lib/database.js backend/lib/tour-api.js backend/tests/tour-sync.test.js backend/tests/tour-api.test.js backend/.env.example backend/vercel.json backend/scripts/check-project.js
git commit -m "feat: TourAPI 표현 상세 동기화 분리"
```

### Task 6: Sync active festivals and filter expired recommendations

**Files:**

- Create: `backend/api/cron/tour-festival-sync.js`
- Modify: `backend/lib/tour-api.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/lib/time-safe.js`
- Create: `backend/tests/tour-festival-sync.test.js`
- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/vercel.json`
- Modify: `backend/scripts/check-project.js`

**Interfaces:**

- Produces `fetchFestivalPage({ eventStartDate, pageNo, numOfRows })` with `areaCode=32`.
- Produces `isPlaceDateEligible(place, at)` used before route probing and during final recommendation.

- [ ] **Step 1: Add failing date-boundary tests**

Pass an expired festival and an ending-today festival into `selectRouteCandidates(criteria, places, 20, now)`. Assert expired length 0 and ending-today length 1. Assert non-festivals and festivals with no dates preserve legacy behavior.

- [ ] **Step 2: Run `node --test tests/time-safe.test.js tests/tour-festival-sync.test.js` and confirm RED**

- [ ] **Step 3: Implement festival fetch and storage**

Call `searchFestival2` with KST today `yyyyMMdd`, `areaCode=32`, `arrange=A`, pagination. Map through `mapTourItem`, add event dates, and upsert without clearing intro/common/media. Do not delete festivals missing from one response.

- [ ] **Step 4: Filter before expensive routing**

```js
function seoulCalendarDate(at) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(at);
}

export function isPlaceDateEligible(place, at = new Date()) {
  if (place.category !== "FESTIVAL") return true;
  if (!place.event_start_date && !place.event_end_date) return true;
  const date = seoulCalendarDate(at);
  return (!place.event_start_date || place.event_start_date <= date) &&
    (!place.event_end_date || place.event_end_date >= date);
}
```

Add optional `now` to `selectRouteCandidates`; filter there and in `recommendPlaces` without changing existing defaults.

- [ ] **Step 5: Add daily Cron**

Schedule authenticated `/api/cron/tour-festival-sync` at `10 19 * * *`, use `tour_festival` paging state, and return processed/upserted/next-page counts.

- [ ] **Step 6: Verify and commit**

```powershell
node --test tests/time-safe.test.js tests/tour-festival-sync.test.js tests/recommendations.test.js
npm test
npm run check
git add backend/api/cron/tour-festival-sync.js backend/lib/tour-api.js backend/lib/database.js backend/lib/time-safe.js backend/tests/tour-festival-sync.test.js backend/tests/time-safe.test.js backend/vercel.json backend/scripts/check-project.js
git commit -m "feat: 유효한 축제만 경유 추천에 반영"
```

### Task 7: Add operations, controlled backfill, and release verification

**Files:**

- Create: `backend/api/cron/tour-status.js`
- Create: `backend/scripts/run-tour-intro-backfill.js`
- Create: `backend/tests/tour-operations.test.js`
- Modify: `backend/lib/database.js`
- Modify: `backend/scripts/check-project.js`
- Modify: `backend/README.md`
- Modify: `docs/02_ARCHITECTURE.md`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/05_API_AND_DATA.md`
- Modify: `docs/06_ENVIRONMENT_AND_ACCESS.md`
- Modify: `docs/07_BUILD_TEST_DEPLOY.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/10_DECISION_LOG.md`

**Interfaces:**

- Produces authorized `GET /api/cron/tour-status`, `getTourDataCoverage()`, and `runBackfill({ invoke, maxCalls, delay })`.

- [ ] **Step 1: Add failing operations tests**

Assert status rejects missing/wrong Bearer and returns injected coverage. Assert backfill stops on `idle`, stops at `maxCalls`, and never retries 401/403.

```js
test("백필은 호출 예산을 넘지 않는다", async () => {
  let calls = 0;
  const result = await runBackfill({
    invoke: async () => { calls += 1; return { status: "partial", processed: 20 }; },
    maxCalls: 3,
    delay: async () => {}
  });
  assert.equal(calls, 3);
  assert.equal(result.reason, "budget_exhausted");
});
```

- [ ] **Step 2: Run `node --test tests/tour-operations.test.js` and confirm RED**

- [ ] **Step 3: Implement coverage counts**

Return active total, intro complete, known hours, common complete, media complete, intro due, and last completed timestamps for catalog/intro/presentation/festival. Production tests assert response shape, not sample counts.

- [ ] **Step 4: Implement controlled backfill**

Require `BACKEND_URL` and `CRON_SECRET`; default `TOUR_BACKFILL_MAX_CALLS=40`, `TOUR_BACKFILL_DELAY_MS=1500`. Print counts only, never headers or secrets. Stop on idle, budget exhaustion, or unauthorized. At 20 rows per call, one run is capped at 800 intro calls.

- [ ] **Step 5: Update operational docs**

Document migration order, Cron order, initial backfill, 1,000/day budget, authenticated status, recovery-only full sync, unknown-hours fallback, and future soft-ranking APIs.

- [ ] **Step 6: Run release verification**

```powershell
cd C:\app\tteumsae\backend
npm test
npm run check
```

- [ ] **Step 7: Apply production in safe order**

Apply migration 004; deploy Vercel; verify legacy details survived; run one intro batch; run one catalog page and prove intro values remain; run controlled backfill under budget; confirm no due row is silently abandoned; smoke-test known cafe, known/unknown hours, expired festival, legacy recommendation, and `/api/route`.

- [ ] **Step 8: Commit**

```powershell
git add backend/api/cron/tour-status.js backend/scripts/run-tour-intro-backfill.js backend/tests/tour-operations.test.js backend/lib/database.js backend/scripts/check-project.js backend/README.md docs
git commit -m "docs: TourAPI 보강 운영 절차와 검증 추가"
```

## Acceptance Checklist

- [ ] 기본 카탈로그 동기화 후에도 상세 보강 데이터가 유지된다.
- [ ] 활성 장소가 인트로 성공, 명시적 빈 데이터, 또는 재시도 예정 상태 중 하나를 가진다.
- [ ] 운영시간·휴무일이 도착 마감 최대 체류 계산에 사용된다.
- [ ] `A05020900` 장소만 `CAFE`로 분리되고 나머지 음식점은 회귀하지 않는다.
- [ ] 카탈로그 변경과 비표출 상태를 전체 재수집 없이 반영한다.
- [ ] 소개·홈페이지·이미지·반려동물 부분 실패가 서로의 성공 결과를 지우지 않는다.
- [ ] 종료된 축제는 Kakao 경로 후보가 되지 않고 날짜 미확인 장소는 기존 동작을 유지한다.
- [ ] 보강률, 실패, 커서, 마지막 성공 시각을 인증된 상태 API에서 확인할 수 있다.
- [ ] 전체 백엔드 테스트, 구조 검사, 운영 스모크 테스트가 통과한다.
- [ ] `output/`, `tmp/`, 비밀키가 어떤 커밋에도 포함되지 않는다.
