# Gate 2 Arrival Deadline Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 출발지·목적지·도착 마감을 한 화면에서 정하면 현재 교통 기준으로 최소 15분 이상 머물 수 있는 경유지 한 곳과 최대 체류시간·출발 권장시각을 제공한다.

**Architecture:** Backend는 `ARRIVAL_DEADLINE_V1` 요청의 절대 epoch를 서버 clock으로 검증하고 기존 `extraTimeMinutes` 요청과 분리해 계산한다. Android는 경로 입력·추천·한 곳 선택을 `RouteFlowViewModel + SavedStateHandle`로 옮기고, 수정하는 LOCATION/RESULTS 화면만 `ui/route`로 추출한다. 외부 카카오맵과 opt-in 로컬 알림은 화면 상태와 분리된 platform/reminder 경계에서 처리한다.

**Tech Stack:** Node.js 24 ES modules/node:test, Kotlin 2.3.20, Java 17, Android API 26–36, Jetpack Compose, Lifecycle 2.8.7, coroutines 1.9.0, AlarmManager, SharedPreferences, Kakao Mobility/Kakao Map

**Spec:** `docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md`

## Global Constraints

- 활성 제품 흐름은 `HOME → LOCATION → LOADING → RESULTS → DETAIL`이며 `CONDITIONS` 필수 단계를 제거한다.
- 첫 알파의 제품 경유지는 정확히 0~1곳이다. 두 번째 경유지 탐색·CTA·상태는 구현하지 않는다.
- `SAFETY_BUFFER_MINUTES=10`, `MINIMUM_STAY_MINUTES=15`, `DEPARTURE_REMINDER_LEAD_MINUTES=5`다.
- 신규 요청은 `arrivalDeadlineEpochMillis`만 보내며 `deadlineMinutes`, `extraTimeMinutes`, `safetyBufferMinutes`를 함께 보내지 않는다.
- 서버 `/api/route`의 0~5개 경유지와 `timeModel` 없는 기존 `extraTimeMinutes` 추천 요청은 호환을 유지한다.
- 사용자는 체류시간이나 안전여유를 입력하지 않는다. 지도 핀에는 추가 이동시간만 표시한다.
- `TteumsaeApp.kt`를 전면 재작성하지 않고 수정하는 LOCATION/RESULTS 책임부터 분리한다.
- 위치·알림 권한 거부는 추천과 외부 내비를 막지 않는다. 백그라운드 위치와 exact-alarm 권한은 추가하지 않는다.
- 새 동작은 실패하는 테스트를 먼저 확인한 뒤 최소 구현한다.
- Gate 1 작업공간의 `output/`, `tmp/`와 운영 비밀값을 복사·수정·커밋하지 않는다.

## File Responsibility Map

| File | Responsibility |
|---|---|
| `backend/lib/validation.js` | V1 절대 마감·금지 필드·15분/24시간 경계 검증 |
| `backend/lib/time-safe.js` | 한 곳 최대 체류·5분 내림·출발 권장시각 계산 |
| `backend/api/recommendations.js` | 서버 clock 고정, V1/legacy 분기와 응답 meta |
| `android/.../domain/route/ArrivalDeadlinePolicy.kt` | 시간 선택·표시·상수 순수 정책 |
| `android/.../domain/route/RouteFlowModels.kt` | 신규 입력·추천·한 곳 선택 도메인 타입 |
| `android/.../data/route/RouteGateway.kt` | ViewModel이 의존하는 추천·정확 경로 경계 |
| `android/.../data/TteumsaeApi.kt` | V1 JSON 직렬화·응답 파싱, legacy/route 호환 |
| `android/.../ui/route/RouteFlowUiState.kt` | 입력·로딩·결과·선택·오류 상태 |
| `android/.../ui/route/RouteFlowViewModel.kt` | 검색·선택·새로고침·SavedStateHandle 복원 |
| `android/.../ui/route/LocationScreen.kt` | 위치·도착 마감·선택 필터 입력 |
| `android/.../ui/route/ResultsScreen.kt` | 한 곳 선택·직행·외부 내비·새로고침 UI |
| `android/.../ui/route/RouteResultComponents.kt` | 핀/카드/선택 요약/빈 결과 컴포넌트 |
| `android/.../reminder/*` | 활성 여행 스냅샷과 고정 출발 알림 |
| `android/.../ui/TteumsaeApp.kt` | 상위 탭·DETAIL·화면 조립 |

---

### Task 1: Accept an absolute V1 deadline at the backend boundary

**Files:**
- Modify: `backend/tests/validation.test.js`
- Modify: `backend/lib/validation.js`

**Interfaces:**
- Consumes: raw recommendation JSON and injected `nowEpochMillis`
- Produces: `parseRecommendationRequest(value, { nowEpochMillis })` with internal `deadlineMinutes`, fixed buffer 10, and preserved absolute epoch

- [x] **Step 1: Write failing validation tests**

Add cases proving that V1 accepts only this shape:

```js
const parsed = parseRecommendationRequest({
  mode: "ON_THE_WAY",
  start: valid.start,
  destination: valid.destination,
  arrivalDeadlineEpochMillis: now + 45 * 60_000,
  timeModel: "ARRIVAL_DEADLINE_V1",
  transport: "CAR",
  categories: []
}, { nowEpochMillis: now });
assert.equal(parsed.deadlineMinutes, 45);
assert.equal(parsed.safetyBufferMinutes, 10);
```

Also assert rejection of missing/unsafe/float epochs, past values, 14m59s, and an exact delta over
`86_400_000` ms. A deadline exactly 24 hours away is valid, while 24 hours plus 1 ms is not.
Reject unknown models and V1 combined with `deadlineMinutes`, `extraTimeMinutes`, or
`safetyBufferMinutes`.

- [x] **Step 2: Run the focused test and verify RED**

Run: `node --test tests/validation.test.js` from `backend/`.

Expected: failures because the parser still requires relative minutes.

- [x] **Step 3: Implement the minimal parser branch**

Use:

```js
export const ARRIVAL_DEADLINE_TIME_MODEL = "ARRIVAL_DEADLINE_V1";
export const ARRIVAL_DEADLINE_SAFETY_BUFFER_MINUTES = 10;

const remainingWholeMinutes = Math.floor(
  (value.arrivalDeadlineEpochMillis - nowEpochMillis) / 60_000
);
```

V1 validates `15 <= remainingWholeMinutes <= 1440`, returns the absolute epoch plus internal `deadlineMinutes`, and never trusts a client buffer. Leave the legacy branch byte-compatible.

- [x] **Step 4: Run validation tests and verify GREEN**

Run: `node --test tests/validation.test.js`.

- [x] **Step 5: Commit**

```powershell
git add backend/lib/validation.js backend/tests/validation.test.js
git commit -m "feat: 절대 도착 마감 요청 검증"
```

---

### Task 2: Calculate V1 maximum stay and response metadata

**Files:**
- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/tests/recommendations.test.js`
- Modify: `backend/lib/time-safe.js`
- Modify: `backend/api/recommendations.js`

**Interfaces:**
- Consumes: Task 1 normalized V1 criteria and Kakao leg minutes
- Produces: `minimumStayMinutes`, `maximumStayMinutes`, `latestDepartureEpochMillis`, `route.detourMinutes`, and V1 meta

- [x] **Step 1: Write failing pure calculation tests**

Pin these formulas with injected `now`:

```js
raw = deadlineMinutes - firstLegMinutes - secondLegMinutes - 10;
maximumStayMinutes = Math.floor(raw / 5) * 5;
latestDepartureEpochMillis = arrivalDeadlineEpochMillis
  - (secondLegMinutes + 10) * 60_000;
```

Assert 15 minutes is included, 14 is excluded, route seconds already rounded to leg minutes stay conservative, 39 raw minutes becomes 35, and legacy results still expose `stayMinutes` without V1-only fields.

- [x] **Step 2: Add failing operating-window cap tests**

For a clearly parsed schedule, find the longest 5-minute departure window accepted by `evaluateOperatingWindow`. Cap maximum stay at that value; exclude if the cap is below 15. Keep `UNKNOWN` candidates and label them `operationStatus="UNKNOWN"`.

- [x] **Step 3: Run focused tests and verify RED**

Run: `node --test tests/time-safe.test.js tests/recommendations.test.js`.

- [x] **Step 4: Implement the V1 branch without changing legacy math**

Use `MINIMUM_STAY_MINUTES=15`, floor the available stay to a 5-minute boundary, evaluate departure at the capped stay, and select route candidates with 15 minutes instead of `default_stay_minutes` for V1.

- [x] **Step 5: Freeze request time before parsing and return meta**

`recommendations.js` creates `requestNow` before parsing and passes its epoch into Task 1. V1 meta contains:

```js
{
  timeModel: "ARRIVAL_DEADLINE_V1",
  calculatedAtEpochMillis: requestNow.getTime(),
  arrivalDeadlineEpochMillis: criteria.arrivalDeadlineEpochMillis,
  safetyBufferMinutes: 10,
  minimumStayMinutes: 15
}
```

Do not return client-relative `extraTimeMinutes` for V1.

- [x] **Step 6: Verify GREEN and full backend regression**

Run:

```powershell
node --test tests/validation.test.js tests/time-safe.test.js tests/recommendations.test.js
pnpm test
pnpm check
```

- [x] **Step 7: Commit**

```powershell
git add backend/lib/time-safe.js backend/api/recommendations.js backend/tests/time-safe.test.js backend/tests/recommendations.test.js
git commit -m "feat: 최대 체류시간 추천 계약 구현"
```

- [ ] **Step 8: Preview smoke before any Production promotion**

Deploy the committed Backend as a Vercel Preview. Verify one literal V1 request, one legacy
`extraTimeMinutes` request, a 14m59s rejection, unauthenticated ops 401, and health 200. Promote
that exact deployment only if every check passes, then repeat V1 and legacy smoke through
`tteumsae-backend-one.vercel.app`. Record only non-secret request summaries in the deployment doc.

2026-08-28 실행 기록: 새 Preview 두 건이 Vercel API에서 `BLOCKED/UNKNOWN`으로
빌드를 시작하지 못했다. 기존 Ready Preview의 health 200은 확인했지만 Preview에는
Supabase·Kakao 환경변수가 없고 배포 코드도 V1 이전 버전이라 유효 V1 smoke로
대체할 수 없다. Production은 승격하거나 변경하지 않았으며 이 단계는 미완료로 남긴다.

---

### Task 3: Add Android deadline domain policy

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/domain/route/ArrivalDeadlinePolicy.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/domain/route/RouteFlowModels.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/domain/route/ArrivalDeadlinePolicyTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/domain/Models.kt`

**Interfaces:**
- Consumes: KST clock choice, current epoch, route legs
- Produces: deadline validity, immutable V1 criteria fields, and selected-stop timing

- [x] **Step 1: Write failing policy tests**

Cover same-day selection, passed clock rolling to next day, exactly 15 minutes, 14m59s invalid, exactly 24 hours, more than 24 hours, and no implicit selected time.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.domain.route.ArrivalDeadlinePolicyTest"
```

- [x] **Step 3: Implement pure constants and functions**

Create `resolveArrivalDeadline`, `remainingWholeMinutes`, `isValidArrivalDeadline`, `floorToFiveMinutes`, and `selectedStopTiming`. Use `java.time` and no Android `Context`.

- [x] **Step 4: Evolve models additively**

Add nullable `arrivalDeadlineEpochMillis` to `SearchCriteria` while the legacy UI still compiles. Add nullable V1 fields to `SafeRecommendation`:

```kotlin
val minimumStayMinutes: Int? = null
val maximumStayMinutes: Int? = null
val latestDepartureEpochMillis: Long? = null
```

- [x] **Step 5: Run focused and existing domain tests**

- [x] **Step 6: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/domain android/app/src/test/java/com/tteumsae/app/domain
git commit -m "feat: 도착 마감 경로 도메인 추가"
```

---

### Task 4: Connect the Android V1 wire contract

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/data/route/RouteGateway.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/data/TteumsaeApiTest.kt`

**Interfaces:**
- Consumes: `SearchCriteria.arrivalDeadlineEpochMillis`
- Produces: `RouteGateway.recommendations(criteria)` and `calculateRoute(...)`

- [x] **Step 1: Write failing serialization tests**

Expose `internal fun recommendationRequestBody(criteria): JSONObject`. V1 must contain `arrivalDeadlineEpochMillis` and `timeModel`, and must omit `deadlineMinutes`, `extraTimeMinutes`, and `safetyBufferMinutes`. Legacy criteria must serialize exactly as before.

- [x] **Step 2: Write failing parser tests**

Parse a literal V1 fixture and require all V1 fields. A malformed V1 item is an `ApiException`; legacy items keep safe defaults.

- [x] **Step 3: Run `TteumsaeApiTest` and verify RED**

- [x] **Step 4: Add `RouteGateway` and implement it in `TteumsaeApi`**

Keep the existing 0~5 `route` method as a compatibility wrapper. Add a waypoint value type so ViewModel does not depend on `PlaceCandidate` serialization details.

```kotlin
interface RouteGateway {
    suspend fun recommendations(criteria: SearchCriteria): RecommendationResult
    suspend fun calculateRoute(
        start: Coordinates,
        destination: Coordinates,
        waypoints: List<RouteWaypoint>,
    ): RouteSummary
}
```

- [x] **Step 5: Return response meta in `RecommendationResult`**

Carry `calculatedAtEpochMillis`, absolute deadline, minimum stay, base route, and corridor radius into the domain result.

- [x] **Step 6: Run focused tests and compile**

Run `TteumsaeApiTest`, then `compileDebugKotlin`.

- [x] **Step 7: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/data android/app/src/test/java/com/tteumsae/app/data
git commit -m "feat: Android 도착 마감 API 연결"
```

---

### Task 5: Introduce RouteFlow state and remove the mandatory conditions step

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowUiState.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowViewModel.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/route/RouteFlowViewModelTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/navigation/AppDestination.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/ui/navigation/AppDestinationTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/AppContainer.kt`

**Interfaces:**
- Consumes: Task 4 `RouteGateway`
- Produces: single-source route state, one selected ID, search/refresh/select events

- [x] **Step 1: Write failing ViewModel transition tests**

Test valid search, loading success, empty results, failure, one selection replacing the previous selection, deselection, new search clearing selection, and refresh retaining the last safe result on failure.

- [x] **Step 2: Write failing SavedStateHandle tests**

Persist only location names/coordinates, deadline, filter names, and selected place ID. When recommendations payload is absent after process recreation, state returns to LOCATION and never exposes empty RESULTS/DETAIL.

- [x] **Step 3: Verify RED**

Run focused ViewModel and navigation tests.

- [x] **Step 4: Implement state and factory**

Use `viewModelFactory { initializer { RouteFlowViewModel(createSavedStateHandle(), gateway) } }`. Do not put `Context` in the ViewModel.

The public event surface is `updateStart`, `updateDestination`, `updateDeadline`, `updateFilters`,
`search`, `selectPlace`, `clearSelection`, `refresh`, and `startNewSearch`. `RouteFlowUiState`
owns input values, `RouteStage`, recommendations, base route, selected place ID, calculation time,
warning, and error; Compose does not duplicate those mutable values.

- [x] **Step 5: Remove `CONDITIONS` from active navigation**

`previousDestination(LOADING/RESULTS)` returns LOCATION. Restore fallbacks with locations but no payload also return LOCATION.

- [x] **Step 6: Verify GREEN and compile**

- [x] **Step 7: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/AppContainer.kt android/app/src/main/java/com/tteumsae/app/ui/navigation android/app/src/main/java/com/tteumsae/app/ui/route android/app/src/test/java/com/tteumsae/app/ui
git commit -m "feat: 한 곳 경로 상태와 복원 구현"
```

---

### Task 6: Extract LOCATION and collect deadline plus optional filters

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/LocationScreen.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/route/LocationContinuePolicyTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`

**Interfaces:**
- Consumes: Task 5 state/events
- Produces: valid start/destination/deadline and optional filter selection

- [x] **Step 1: Extract current location UI without behavior changes and compile**

Move `LocationScreen`, its search field, and permission/settings dialogs. Keep theme tokens passed explicitly or `internal`; do not move unrelated HOME/SAVED/SETTINGS code.

- [x] **Step 2: Write failing continue-policy tests**

Start, destination, and a 15~1,440-minute deadline are required. Manual location search remains valid without location permission.

- [x] **Step 3: Verify RED**

- [x] **Step 4: Add an unselected-by-default Material time picker row**

The picker may open at a convenient clock value, but state changes only after confirmation. Show next-day date explicitly after rollover. Do not add a time-only screen.

- [x] **Step 5: Place optional intent/filter chips on LOCATION**

Default is `아무거나`; no filter is required to search. The existing `ConditionsScreen` is no longer navigated.

- [x] **Step 6: Wire LOCATION directly to LOADING and verify**

Run focused tests, full unit tests, and `compileDebugKotlin`.

- [x] **Step 7: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/main/java/com/tteumsae/app/ui/route android/app/src/test/java/com/tteumsae/app/ui/route
git commit -m "feat: 위치 화면에 도착 마감 입력 추가"
```

---

### Task 7: Replace multi-select RESULTS with one-stop decisions

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/ResultsScreen.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteResultComponents.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteMap.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/route/RouteResultPolicyTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/ui/KakaoMapRouteTest.kt`

**Interfaces:**
- Consumes: Task 5 state and Task 4 result fields
- Produces: one-stop selection summary, direct fallback, refresh, and external navigation

- [x] **Step 1: Extract map/result components and compile before behavior changes**

- [x] **Step 2: Write failing result policy tests**

Pins use `+N분`; only one selected ID exists; selection exposes `maximumStayMinutes` and formatted latest departure; navigation receives at most one product waypoint while the low-level helper remains 0~5 compatible.

- [x] **Step 3: Verify RED**

- [x] **Step 4: Implement the selected and empty states**

Before selection, emphasize detour only. After selection show `이동 기준 최대 약 N분` and `H시 M분까지 출발하면 돼요`. Remove average-stay and five-waypoint copy.

If no candidate is eligible, keep the destination navigation CTA. If base route plus buffer already misses the deadline, show the stronger immediate-departure warning.

- [x] **Step 5: Add lifecycle-safe manual refresh**

On app resume show `현재 교통으로 다시 확인`; refresh through ViewModel. Failure preserves the last result and adds a non-blocking stale warning.

- [x] **Step 6: Keep DETAIL as secondary place information**

`상세보기` may open DETAIL, but `이곳 들르기` and `길 안내 시작` remain the primary one-stop completion path.

- [x] **Step 7: Verify GREEN**

Run focused tests, all Android unit tests, compile, and lint.

- [x] **Step 8: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/ui android/app/src/test/java/com/tteumsae/app/ui
git commit -m "feat: 한 곳 도착 마감 결과 UI 적용"
```

---

### Task 8: Persist an active trip and schedule opt-in reminders

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTrip.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTripStore.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/DepartureReminderPolicy.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/DepartureReminderScheduler.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/AlarmManagerDepartureReminderScheduler.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/DepartureReminderReceiver.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderRescheduleReceiver.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderNotifications.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/reminder/ActiveTripTest.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/reminder/DepartureReminderPolicyTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/tteumsae/app/TteumsaeApplication.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/AppContainer.kt`

**Interfaces:**
- Consumes: one selected stop and `latestDepartureEpochMillis`
- Produces: expiring local trip snapshot and best-effort reminder 5 minutes before departure

- [x] **Step 1: Write failing storage and policy tests**

Cover JSON round-trip, replacement, deadline+2h expiry, reminder trigger, immediate warning when trigger passed but departure has not, skip after departure, and rescheduling only valid trips.

- [x] **Step 2: Verify RED**

- [x] **Step 3: Implement storage without location history**

Store only confirmed start/destination/one stop coordinates, absolute deadline, latest departure, navigation URL, and expiry. Do not store background samples.

- [x] **Step 4: Implement inexact alarms**

Use `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`, immutable/update-current `PendingIntent`, and a short receiver. Add `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`; do not add exact-alarm or background-location permission.

- [x] **Step 5: Add contextual opt-in**

The RESULTS row says `출발 5분 전에 알려드릴까요?`, defaults off, and requests Android 13+ notification permission only when enabled. Denial leaves navigation enabled.

- [x] **Step 6: Verify GREEN**

Run reminder tests, full unit tests, `lintDebug`, and `assembleDebug`.

- [x] **Step 7: Commit**

```powershell
git add android/app/src/main android/app/src/test
git commit -m "feat: 출발 마감 로컬 알림 추가"
```

---

### Task 9: Remove transitional legacy UI state and document Gate 2 truth

**Files:**
- Modify: `android/app/src/main/java/com/tteumsae/app/domain/Models.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `docs/00_START_HERE.md`
- Modify: `docs/02_ARCHITECTURE.md`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/04_SCREEN_FLOWS.md`
- Modify: `docs/05_API_AND_DATA.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`

**Interfaces:**
- Consumes: all prior tasks
- Produces: no active legacy product flow and evidence-backed Gate 2 status

- [x] **Step 1: Remove transitional route state**

Delete `DEFAULT_EXTRA_TIME_MINUTES`, user-editable buffer, product five-waypoint selection, `CONDITIONS` UI navigation, and deadline-relative new-flow math. Keep legacy API compatibility code and tests.

- [x] **Step 2: Search for stale active copy**

Run:

```powershell
rg -n "평균 머무름|경유지는 최대 5곳|DEFAULT_EXTRA_TIME_MINUTES|AppDestination.CONDITIONS|실시간 추적|도착 보장" android/app/src/main docs
```

Classify historical decision/plan mentions separately; active UI must contain none.

- [x] **Step 3: Run complete local verification**

Backend:

```powershell
pnpm test
pnpm check
```

Android:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

- [x] **Step 4: Update documents with only verified implementation**

Record request/response examples, file ownership, notification fallback, process restoration, test counts, and any unverified device-only cases. Do not mark device QA complete without a device.

- [x] **Step 5: Commit and push**

```powershell
git add android backend docs
git commit -m "docs: Gate 2 구현 및 검증 상태 기록"
git push -u newrred codex/gate-2-arrival-deadline-flow
```

## Acceptance Checklist

독립 코드 리뷰에서 확인된 알림 수명주기, SharedPreferences 백업 제외, Android
구조화 오류 보존을 추가 구현했다. 구간별 분 올림 합계 기준 우회시간과 알림 탭 QA
문구도 함께 수정했다. 권한 응답이나 재조회 시점에 출발 권장시각이 지난 알림은
저장·활성화하지 않고 기존 상태도 정리한다. 전체 회귀는 Backend 151/151, Android 116/116,
lint 오류 0과 debug APK 빌드로 다시 확인했다.

- [x] LOCATION 한 화면에서 출발지·목적지·도착 마감과 선택 필터를 정한다.
- [x] 신규 Android는 절대 마감만 전송하고 서버는 수신시각으로 남은 분을 내림 계산한다.
- [x] 모든 후보가 내부 여유 10분 뒤 최소 15분을 확보한다.
- [x] 핀에는 추가 이동시간, 한 곳 선택 후에는 최대 체류시간과 출발 권장시각이 보인다.
- [x] 후보 없음과 직행도 빠듯한 상태 모두 외부 내비 CTA를 제공한다.
- [x] 위치·알림 권한 거부가 추천과 길 안내를 막지 않는다.
- [x] 새 검색·프로세스 복원·새로고침이 잘못된 선택 또는 빈 RESULTS를 노출하지 않는다.
- [x] 기존 `extraTimeMinutes` 추천과 `/api/route` 0~5곳 테스트가 계속 통과한다.
- [x] Backend·Android 자동 검증이 통과하고 실기기 미검증 항목은 문서에 남는다.

## Official Platform Constraints Rechecked — 2026-08-28

- Android 13+ notification permission: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- Inexact `setAndAllowWhileIdle` alarm behavior: <https://developer.android.com/develop/background-work/services/alarms>
- `SavedStateHandle` for system-initiated process death: <https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate>
