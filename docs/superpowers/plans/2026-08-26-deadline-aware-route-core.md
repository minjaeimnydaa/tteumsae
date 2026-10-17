# Deadline-Aware Route Core Implementation Plan

> **상태: 실행 중지.** 2026-08-28 감사에서 API 계약이 상대 `deadlineMinutes`에서 절대
> `arrivalDeadlineEpochMillis`로 바뀌고, 알파 범위가 1곳으로 축소됐다. Task 1의
> `ARRIVAL_DEADLINE_V1` 검증 골격만 현재 코드에 반영됐으며 Task 2 이후를 이 문서대로
> 실행하지 않는다. 개정 스펙 승인 후 Gate 0 계획과 1곳 핵심 계획으로 교체한다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 출발지·목적지·도착 마감만 정하면, 최소 15분 이상 머물 수 있는 경유지 한 곳을 우선 추천하고 선택 시 최대 체류 가능 시간과 출발 마감을 보여주며, 가능한 경우에만 두 번째 경유지를 선택적으로 제안하고 고정 출발 알림까지 예약한다.

**Architecture:** 기존 `HOME → LOCATION → CONDITIONS → LOADING → RESULTS → DETAIL` 흐름과 `/api/recommendations`, `/api/route`를 유지한다. 서버에는 명시적 `ARRIVAL_DEADLINE_V1` 계산 분기를 추가해 기존 클라이언트 동작을 보존하고, Android에는 순수 시간 계산 계층·화면 상태를 소유하는 ViewModel·얇은 Compose 화면·AlarmManager 기반 알림 계층을 추가한다. 두 번째 경유지는 첫 장소 선택 후 상위 6개 후보만 기존 `/api/route`로 정확 계산하며 동시에 최대 2개만 요청한다.

**Tech Stack:** Node.js 24.x ES modules/node:test, Kotlin 2.3.20, AGP 8.13.2, Java 17, Android API 26–36 (`targetSdk=35`), Jetpack Compose BOM 2024.12.01, Lifecycle 2.8.7, kotlinx-coroutines 1.9.0, Room 2.8.4, Supabase 3.5.0, Ktor 3.0.3, AlarmManager, SharedPreferences, Kakao Mobility/Kakao Map

**Spec:** `docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md`

## Rebase Checkpoint (2026-08-27)

- 이 계획은 `agent/new-route-flow-ui`의 `d113002`를 기준으로 현재 코드와 다시 대조했다.
- 계정 기반, OAuth, Room 저장 장소, 저장/설정 화면 분리는 이미 구현돼 있으므로 선행 계획을 다시 실행하지 않는다.
- `TteumsaeApp.kt`는 5,091줄에서 약 3,882줄로 줄었지만 위치·조건·결과 화면은 여전히 이 파일에 집중돼 있다. Task 8~9에서 기존 저장/설정 분리를 보존하며 점진적으로 추출한다.
- 현재 경로 선택 순수 함수는 `domain/route/RouteSelectionPolicy.kt`에 있다. 같은 이름의 UI 계층 정책을 새로 만들지 않고 이 파일을 확장한다.
- 앱 조립 경계는 `AppContainer.kt`다. 네트워크 게이트웨이와 알림 구현체는 여기에 연결하고 ViewModel에는 Android `Context`를 주입하지 않는다.
- 기준 상태의 사용자 소유 미추적 경로는 `output/`, `tmp/`뿐이며 이 계획의 작업 대상에서 제외한다.

## Global Constraints

- Prerequisite: `docs/superpowers/plans/2026-08-27-tour-data-enrichment.md`를 먼저 완료해 기본 동기화가 상세 데이터를 지우지 않고 운영시간 우선 백필이 가능한 상태로 만든다. 도착 마감 코드 테스트는 독립적으로 가능하지만 운영 활성화는 TourAPI 인트로 백필 확인 뒤 진행한다.
- Prerequisite: `docs/superpowers/plans/2026-08-26-account-foundation-local-saved.md`의 실제 구현 상태를 유지한다. 완료된 계정/OAuth/Room/저장·설정 화면을 되돌리거나 중복 구현하지 않는다.
- 현재 빌드 도구 버전을 유지한다. 이 기능을 위해 Kotlin, AGP, Lifecycle, Compose BOM을 별도로 업그레이드하지 않는다.
- 제품 상 경유지는 최대 2곳이지만 서버 `/api/route`의 0–5개 호환성은 유지한다.
- 사용자는 체류시간과 안전 여유를 입력하지 않는다. `SAFETY_BUFFER_MINUTES=10`, `MINIMUM_STAY_MINUTES=15`, `DEPARTURE_REMINDER_LEAD_MINUTES=5`를 단일 도메인 상수로 사용한다.
- 지도 핀에는 추가 이동시간만 표시한다. 최대 체류 가능 시간과 출발 마감은 장소 선택 뒤 표시한다.
- 내비게이션은 외부 Kakao Map이 담당한다. 앱이 지속 추적·정시 도착을 보장한다고 표현하지 않는다.
- 위치 권한과 Android 13+ 알림 권한 거부는 추천 조회나 외부 내비 실행을 막지 않는다.
- `timeModel`이 없는 서버 요청은 기존 `default_stay_minutes`/`extraTimeMinutes` 계산과 응답을 그대로 유지한다.
- 기존의 사용자 소유 미추적 파일 `output/`, `tmp/`는 수정·삭제·커밋하지 않는다.
- 각 작업은 테스트 실패 확인 → 최소 구현 → 통과 확인 → 해당 작업 파일만 커밋하는 순서로 진행한다.

## File Responsibility Map

| File | Responsibility |
|---|---|
| `backend/lib/validation.js` | `timeModel` 허용값과 요청 조합 검증 |
| `backend/lib/time-safe.js` | 레거시/도착 마감 모델 분기, 최소·최대 체류 및 영업 종료 계산 |
| `backend/api/recommendations.js` | 모델 전달, 후보 탐색, 응답 메타 조립 |
| `android/app/src/main/java/com/tteumsae/app/domain/route/RouteFlowModels.kt` | 제품 상수와 경유 흐름 도메인 타입 |
| `android/app/src/main/java/com/tteumsae/app/domain/route/TripTiming.kt` | 마감 선택·1/2경유 시간 계산 순수 함수 |
| `android/app/src/main/java/com/tteumsae/app/domain/route/RouteSelectionPolicy.kt` | 기존 경유 순서 정책과 새 도착 마감 선택 정책 |
| `android/app/src/main/java/com/tteumsae/app/data/route/RouteGateway.kt` | ViewModel이 사용하는 네트워크 경계 |
| `android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt` | V1 요청/응답 직렬화와 기존 API 호출 |
| `android/app/src/main/java/com/tteumsae/app/AppContainer.kt` | 게이트웨이·저장소·알림 구현체 조립 |
| `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowUiState.kt` | 화면 상태와 파생 UI 모델 |
| `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowViewModel.kt` | 검색, 선택, 정확 경로 탐색, 오류/취소 소유 |
| `android/app/src/main/java/com/tteumsae/app/ui/route/LocationScreen.kt` | 출발·목적지·도착 마감 입력 UI |
| `android/app/src/main/java/com/tteumsae/app/ui/route/ResultsScreen.kt` | 1곳 우선 선택과 선택형 두 번째 장소 UI |
| `android/app/src/main/java/com/tteumsae/app/ui/route/RouteResultComponents.kt` | 추천 카드·요약·보조 CTA |
| `android/app/src/main/java/com/tteumsae/app/ui/route/RouteMap.kt` | 지도·경로·이동시간 핀 렌더링 |
| `android/app/src/main/java/com/tteumsae/app/reminder/*` | 활성 경로 저장, 고정 알림 예약/수신/복원 |
| `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt` | 상위 화면 전환과 새 계층 연결만 담당 |

---

### Task 1: Version the backend recommendation request without breaking legacy clients

**Files:**

- Modify: `backend/tests/validation.test.js`
- Modify: `backend/lib/validation.js`

- [ ] Add failing validation cases for the supported model, an unknown model, and invalid `extraTimeMinutes + ARRIVAL_DEADLINE_V1` pairing.

```js
test("도착 마감 V1 모델은 deadlineMinutes와 함께만 허용한다", () => {
  const parsed = parseRecommendationRequest({
    ...valid,
    safetyBufferMinutes: 10,
    timeModel: "ARRIVAL_DEADLINE_V1"
  });
  assert.equal(parsed.timeModel, "ARRIVAL_DEADLINE_V1");

  assert.throws(
    () => parseRecommendationRequest({ ...valid, timeModel: "UNKNOWN" }),
    /timeModel/
  );

  const { deadlineMinutes, ...withoutDeadline } = valid;
  assert.throws(
    () => parseRecommendationRequest({
      ...withoutDeadline,
      extraTimeMinutes: deadlineMinutes,
      timeModel: "ARRIVAL_DEADLINE_V1"
    }),
    /deadlineMinutes/
  );
});
```

- [ ] Run `node --test tests/validation.test.js` from `backend/`; expect the new test to fail because `timeModel` is ignored.
- [ ] Add the model set and explicit combination check to `parseRecommendationRequest`.

```js
export const ARRIVAL_DEADLINE_TIME_MODEL = "ARRIVAL_DEADLINE_V1";
const timeModels = new Set([ARRIVAL_DEADLINE_TIME_MODEL]);

const timeModel = value.timeModel;
if (timeModel != null && !timeModels.has(timeModel)) {
  throw new Error("지원하지 않는 timeModel입니다.");
}
if (timeModel === ARRIVAL_DEADLINE_TIME_MODEL && !hasDeadline) {
  throw new Error("ARRIVAL_DEADLINE_V1은 deadlineMinutes와 함께 사용해야 합니다.");
}

return {
  mode: value.mode,
  start: value.start,
  destination: value.destination,
  ...(hasExtraTime
    ? { extraTimeMinutes: value.extraTimeMinutes }
    : { deadlineMinutes: value.deadlineMinutes }),
  safetyBufferMinutes: value.safetyBufferMinutes,
  transport: value.transport,
  categories,
  ...(timeModel ? { timeModel } : {})
};
```

- [ ] Re-run `node --test tests/validation.test.js`; expect all validation tests to pass.
- [ ] Commit only these files: `git add backend/lib/validation.js backend/tests/validation.test.js && git commit -m "feat: 도착 마감 추천 모델 검증 추가"`.

### Task 2: Add structured opening-window calculations

**Files:**

- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/lib/time-safe.js`

- [ ] Add failing tests for same-day closing, overnight hours, 24-hour operation, unknown format, and closed arrival.

```js
test("구조화된 영업시간에서 남은 영업 분을 계산한다", () => {
  const tuesday1730 = new Date("2026-08-11T08:30:00.000Z");
  const tuesday2330 = new Date("2026-08-11T14:30:00.000Z");
  assert.equal(minutesUntilClosing({
    ...place,
    opening_hours: "09:00~18:00",
    closed_days: "연중무휴"
  }, tuesday1730), 30);
  assert.equal(minutesUntilClosing({
    ...place,
    opening_hours: "20:00~02:00",
    closed_days: "연중무휴"
  }, tuesday2330), 150);
  assert.equal(minutesUntilClosing({ ...place, opening_hours: "24시간" }, tuesday1730), null);
  assert.equal(minutesUntilClosing({ ...place, opening_hours: "매장 문의" }, tuesday1730), null);
  assert.equal(minutesUntilClosing({ ...place, opening_hours: "09:00~17:00" }, tuesday1730), 0);
});
```

- [ ] Run `node --test tests/time-safe.test.js`; expect an import/export failure for `minutesUntilClosing`.
- [ ] Extract a shared `openingRanges` parser and implement `minutesUntilClosing(place, arrival)` with this contract: `0` means closed, positive integer means a known limit, and `null` means 24-hour or unstructured/unknown data.

```js
function openingRanges(hours) {
  return [...hours.matchAll(
    /(\d{1,2})(?::(\d{2}))?\s*(?:~|-|–|부터)\s*(\d{1,2})(?::(\d{2}))?/g
  )].map((match) => [
    Number(match[1]) * 60 + Number(match[2] ?? 0),
    Number(match[3]) * 60 + Number(match[4] ?? 0)
  ]);
}

export function minutesUntilClosing(place, arrival = new Date()) {
  const hours = String(place.opening_hours ?? "").trim();
  const status = operationStatus(place, arrival);
  if (status === "CLOSED") return 0;
  if (/24\s*시간/.test(hours)) return null;
  const ranges = openingRanges(hours);
  if (ranges.length === 0) return null;
  const minute = seoulDateParts(arrival).minuteOfDay;
  const remaining = ranges.flatMap(([start, end]) => {
    if (end > start && minute >= start && minute < end) return [end - minute];
    if (end <= start && minute >= start) return [1_440 - minute + end];
    if (end <= start && minute < end) return [end - minute];
    return [];
  });
  return remaining.length ? Math.max(...remaining) : 0;
}
```

- [ ] Refactor `operationStatus` to reuse `seoulDateParts` and `openingRanges`; verify existing weekly-closure behavior remains byte-for-byte equivalent at the public API.
- [ ] Re-run `node --test tests/time-safe.test.js`; expect all tests to pass.
- [ ] Commit: `git add backend/lib/time-safe.js backend/tests/time-safe.test.js && git commit -m "feat: 영업 종료 가능 시간 계산 추가"`.

### Task 3: Implement the V1 minimum/maximum-stay recommendation model

**Files:**

- Modify: `backend/tests/time-safe.test.js`
- Modify: `backend/lib/time-safe.js`

- [ ] Add failing tests that pin the exact boundary formulas and legacy compatibility.

```js
const v1 = {
  ...criteria,
  deadlineMinutes: 45,
  safetyBufferMinutes: 10,
  timeModel: "ARRIVAL_DEADLINE_V1"
};

test("V1은 최소 15분과 최대 체류 가능 시간을 반환한다", () => {
  const [item] = recommendPlaces(v1, [place], fixedRoute);
  assert.equal(item.minimumStayMinutes, 15);
  assert.equal(item.maximumStayMinutes, 15); // 45 - (10 + 10) - 10
  assert.equal(item.totalMinutes, 35);        // driving 20 + minimum stay 15
  assert.equal(item.marginMinutes, 10);
});

test("최대 체류가 14분이면 제외하고 레거시는 기본 체류를 유지한다", () => {
  assert.equal(recommendPlaces({ ...v1, deadlineMinutes: 44 }, [place], fixedRoute).length, 0);
  const [legacy] = recommendPlaces(criteria, [place], fixedRoute);
  assert.equal(legacy.stayMinutes, 30);
  assert.equal(legacy.maximumStayMinutes, undefined);
});
```

- [ ] Add a closing-time cap test: with 20 minutes until closing, V1 `maximumStayMinutes` must be 20; with 14 minutes it must be filtered out.
- [ ] Run `node --test tests/time-safe.test.js`; expect missing V1 fields and incorrect legacy calculation branch.
- [ ] Add constants and branch only when `criteria.timeModel` matches V1.

```js
export const MINIMUM_STAY_MINUTES = 15;
export const ARRIVAL_DEADLINE_TIME_MODEL = "ARRIVAL_DEADLINE_V1";

function deadlineAwareRecommendation(criteria, place, route, now) {
  const drivingMinutes = route.firstLegMinutes + route.secondLegMinutes;
  const arrival = new Date(now.getTime() + route.firstLegMinutes * 60_000);
  const closeLimit = minutesUntilClosing(place, arrival);
  const budgetLimit = criteria.deadlineMinutes
    - drivingMinutes
    - criteria.safetyBufferMinutes;
  const maximumStayMinutes = closeLimit == null
    ? budgetLimit
    : Math.min(budgetLimit, closeLimit);
  if (maximumStayMinutes < MINIMUM_STAY_MINUTES) return null;

  const totalMinutes = drivingMinutes + MINIMUM_STAY_MINUTES;
  const marginMinutes = criteria.deadlineMinutes - totalMinutes;
  return {
    place,
    route,
    stayMinutes: MINIMUM_STAY_MINUTES,
    minimumStayMinutes: MINIMUM_STAY_MINUTES,
    maximumStayMinutes,
    totalMinutes,
    marginMinutes,
    operationStatus: operationStatus(place, arrival),
    safetyLevel: safetyLevel(marginMinutes)
  };
}
```

- [ ] In `selectRouteCandidates`, use 15 minutes for V1 sorting and `default_stay_minutes` otherwise, so expensive Kakao route calls are prioritized by the active model.
- [ ] Re-run `node --test tests/time-safe.test.js`; expect all old and new tests to pass.
- [ ] Commit: `git add backend/lib/time-safe.js backend/tests/time-safe.test.js && git commit -m "feat: 최소 최대 체류 추천 계산 추가"`.

### Task 4: Expose the V1 contract through `/api/recommendations`

**Files:**

- Modify: `backend/tests/recommendations.test.js`
- Modify: `backend/api/recommendations.js`

- [ ] Add an integration test with `deadlineMinutes=45`, `safetyBufferMinutes=10`, and `timeModel=ARRIVAL_DEADLINE_V1`; assert `minimumStayMinutes`, `maximumStayMinutes`, `meta.timeModel`, and that `extraTimeMinutes` is absent.
- [ ] Extend the existing legacy integration test to assert that no V1-only field or `meta.timeModel` appears for its `extraTimeMinutes` request.
- [ ] Run `node --test tests/recommendations.test.js`; expect the V1 response assertions to fail.
- [ ] Ensure `effectiveCriteria` keeps `timeModel`, and add model metadata without changing existing metadata names.

```js
function recommendationModelMeta(criteria) {
  return {
  ...(criteria.timeModel ? { timeModel: criteria.timeModel } : {}),
  ...(criteria.timeModel === "ARRIVAL_DEADLINE_V1"
    ? { minimumStayMinutes: 15 }
    : {})
  };
}
```

Spread `recommendationModelMeta(criteria)` into the existing `meta` object after `safetyBufferMinutes`; do not rename or remove any current metadata field.

- [ ] Run `node --test tests/validation.test.js tests/time-safe.test.js tests/recommendations.test.js`; expect all tests to pass.
- [ ] Run `npm run check` from `backend/`; expect the project structural check to pass.
- [ ] Commit: `git add backend/api/recommendations.js backend/tests/recommendations.test.js && git commit -m "feat: 도착 마감 추천 API 응답 제공"`.

### Task 5: Introduce Android route-flow domain types and pure deadline calculations

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/domain/route/RouteFlowModels.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/domain/route/TripTiming.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/domain/route/TripTimingTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/domain/Models.kt`

- [ ] Write failing unit tests for nearest-future time selection, past-clock rollover, 15-minute/24-hour bounds, one-stop timing, two-stop timing, and insufficient two-stop time.

```kotlin
@Test fun `one stop exposes maximum stay latest departure and reminder`() {
    val result = calculateTripTiming(
        nowEpochMillis = instant("2026-08-26T06:00:00Z"),
        deadlineEpochMillis = instant("2026-08-26T08:00:00Z"),
        legMinutes = listOf(20, 30),
    )!!
    assertEquals(60, result.combinedStayMinutes) // 120 - 50 - 10
    assertEquals(instant("2026-08-26T07:20:00Z"), result.stops.single().latestDepartureEpochMillis)
    assertEquals(instant("2026-08-26T07:15:00Z"), result.stops.single().reminderEpochMillis)
}

@Test fun `two stops reserve minimum time for the second stop`() {
    val result = calculateTripTiming(
        nowEpochMillis = instant("2026-08-26T06:00:00Z"),
        deadlineEpochMillis = instant("2026-08-26T08:00:00Z"),
        legMinutes = listOf(20, 15, 30),
    )!!
    assertEquals(45, result.combinedStayMinutes)
    assertEquals(30, result.stops[0].maximumStayMinutes)
    assertEquals(15, result.stops[1].minimumStayMinutes)
}
```

- [ ] Run `./gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.domain.route.TripTimingTest"` from `android/`; expect compilation failure because the files do not exist.
- [ ] Create the constants and immutable types.

```kotlin
const val SAFETY_BUFFER_MINUTES = 10
const val MINIMUM_STAY_MINUTES = 15
const val DEPARTURE_REMINDER_LEAD_MINUTES = 5
const val MAX_PRODUCT_WAYPOINTS = 2
const val SECOND_STOP_PROBE_LIMIT = 6
const val SECOND_STOP_PROBE_CONCURRENCY = 2

data class StopTiming(
    val minimumStayMinutes: Int = MINIMUM_STAY_MINUTES,
    val maximumStayMinutes: Int,
    val latestDepartureEpochMillis: Long,
    val reminderEpochMillis: Long,
)

data class TripTiming(
    val combinedStayMinutes: Int,
    val stops: List<StopTiming>,
)

data class RouteWaypoint(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
)
```

- [ ] Implement `resolveArrivalDeadline(hour, minute, nowEpochMillis, zoneId)`, `minutesUntilDeadline(...)`, and `calculateTripTiming(...)` as pure functions using `java.time`.

```kotlin
fun calculateTripTiming(
    nowEpochMillis: Long,
    deadlineEpochMillis: Long,
    legMinutes: List<Int>,
): TripTiming? {
    require(legMinutes.size in 2..3)
    val deadlineMinutes = minutesUntilDeadline(deadlineEpochMillis, nowEpochMillis)
    val combinedStay = deadlineMinutes - legMinutes.sum() - SAFETY_BUFFER_MINUTES
    val stopCount = legMinutes.size - 1
    if (combinedStay < MINIMUM_STAY_MINUTES * stopCount) return null
    val minuteMillis = 60_000L
    val stops = if (legMinutes.size == 2) {
        val latest = deadlineEpochMillis -
            (SAFETY_BUFFER_MINUTES + legMinutes[1]) * minuteMillis
        listOf(
            StopTiming(
                maximumStayMinutes = combinedStay,
                latestDepartureEpochMillis = latest,
                reminderEpochMillis = latest - DEPARTURE_REMINDER_LEAD_MINUTES * minuteMillis,
            ),
        )
    } else {
        val firstLatest = deadlineEpochMillis -
            (SAFETY_BUFFER_MINUTES + legMinutes[1] + legMinutes[2] +
                MINIMUM_STAY_MINUTES) * minuteMillis
        val secondLatest = deadlineEpochMillis -
            (SAFETY_BUFFER_MINUTES + legMinutes[2]) * minuteMillis
        val perStopMaximum = combinedStay - MINIMUM_STAY_MINUTES
        listOf(
            StopTiming(MINIMUM_STAY_MINUTES, perStopMaximum, firstLatest,
                firstLatest - DEPARTURE_REMINDER_LEAD_MINUTES * minuteMillis),
            StopTiming(MINIMUM_STAY_MINUTES, perStopMaximum, secondLatest,
                secondLatest - DEPARTURE_REMINDER_LEAD_MINUTES * minuteMillis),
        )
    }
    return TripTiming(combinedStayMinutes = combinedStay, stops = stops)
}
```

- [ ] Add nullable `arrivalDeadlineEpochMillis` to `SearchCriteria` while temporarily retaining `deadlineMinutesFromNow` and `safetyBufferMinutes` so the existing UI compiles between commits. Add `minimumStayMinutes` and nullable `maximumStayMinutes` to `SafeRecommendation`. Keep `PlaceCandidate.stayMinutes` for legacy place-list data, but stop using it as user-facing dwell time in the new route flow. Task 12 removes the temporary legacy criteria fields after all call sites migrate.
- [ ] Re-run the focused unit test; expect all timing tests to pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/domain/Models.kt android/app/src/main/java/com/tteumsae/app/domain/route android/app/src/test/java/com/tteumsae/app/domain/route/TripTimingTest.kt && git commit -m "feat: 도착 마감 경로 시간 도메인 추가"`.

### Task 6: Add a testable Android gateway and V1 wire contract

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/data/route/RouteGateway.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/data/TteumsaeApiTest.kt`

- [ ] Define the gateway used by the ViewModel.

```kotlin
interface RouteGateway {
    suspend fun recommendations(
        criteria: SearchCriteria,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): RecommendationResult

    suspend fun calculateRoute(
        start: Coordinates,
        destination: Coordinates,
        waypoints: List<RouteWaypoint>,
    ): RouteSummary
}
```

- [ ] Add failing tests against an `internal fun recommendationRequestBody(criteria, nowEpochMillis): JSONObject`: when `arrivalDeadlineEpochMillis` is present it must send `deadlineMinutes`, fixed `safetyBufferMinutes=10`, `timeModel=ARRIVAL_DEADLINE_V1`, and never `extraTimeMinutes`. Add a legacy case proving criteria without the absolute deadline still serialize exactly as before until Task 12.
- [ ] Add parser tests using a literal V1 JSON fixture to assert 15-minute minimum and maximum stay; add a legacy fixture proving missing fields fall back safely.
- [ ] Run `./gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.data.TteumsaeApiTest"`; expect new tests to fail.
- [ ] Make `TteumsaeApi : RouteGateway`, move request construction into the internal pure helper, and calculate the remaining minutes immediately before the request.

```kotlin
internal fun recommendationRequestBody(
    criteria: SearchCriteria,
    nowEpochMillis: Long,
): JSONObject = JSONObject()
    .put("mode", criteria.mode.name)
    .put("start", requireNotNull(criteria.startCoordinates).toJson())
    .put("destination", requireNotNull(criteria.endCoordinates).toJson())
    .put("deadlineMinutes", minutesUntilDeadline(
        requireNotNull(criteria.arrivalDeadlineEpochMillis),
        nowEpochMillis,
    ))
    .put("safetyBufferMinutes", SAFETY_BUFFER_MINUTES)
    .put("timeModel", "ARRIVAL_DEADLINE_V1")
    .put("transport", criteria.transportMode.name)
    .put("categories", JSONArray().apply {
        criteria.categories.forEach { put(it.name) }
    })
```

- [ ] Make the helper branch before the shown V1 builder: use it when `arrivalDeadlineEpochMillis != null`, otherwise preserve the current `extraTimeMinutes` body. Parse `minimumStayMinutes` with a 15-minute fallback and require `maximumStayMinutes` for V1; surface malformed V1 data as `ApiException` rather than silently inventing availability.
- [ ] Re-run the focused API tests; expect all to pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/data/route/RouteGateway.kt android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt android/app/src/test/java/com/tteumsae/app/data/TteumsaeApiTest.kt && git commit -m "feat: Android 도착 마감 API 계약 연결"`.

### Task 7: Build the selection policy and exact second-stop probe

**Files:**

- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/tteumsae/app/domain/route/RouteSelectionPolicy.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/AppContainer.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowUiState.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowViewModel.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/domain/route/RouteSelectionPolicyTest.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/route/RouteFlowViewModelTest.kt`

- [ ] Reuse the existing `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`; add only `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` for ViewModel tests.
- [ ] Write pure policy tests: one selected stop is always valid if its V1 recommendation is eligible; a two-stop route needs 3 legs and at least 30 combined stay minutes; first-stop max stay reserves 15 minutes for the second stop.
- [ ] Write ViewModel tests with a fake `RouteGateway`: selecting a first result probes only the top 6 unselected candidates; at most 2 route calls are active; infeasible/error candidates are omitted; a new first selection cancels/invalidates stale probe results.
- [ ] Run the two focused test classes; expect missing types and behavior failures.
- [ ] Implement these UI states.

```kotlin
sealed interface SecondStopState {
    data object Hidden : SecondStopState
    data object Checking : SecondStopState
    data class Available(val candidates: List<SecondStopOption>) : SecondStopState
}

data class RouteFlowUiState(
    val criteria: SearchCriteria? = null,
    val recommendations: List<SafeRecommendation> = emptyList(),
    val selectedStops: List<SafeRecommendation> = emptyList(),
    val selectedRoute: RouteSummary? = null,
    val timing: TripTiming? = null,
    val directRouteMissesDeadline: Boolean = false,
    val secondStopState: SecondStopState = SecondStopState.Hidden,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
```

- [ ] Implement `evaluateRoute(deadline, route.legs)` through `calculateTripTiming`; do not reuse the old base-route-plus-extra-time helper. Mark `directRouteMissesDeadline` when `baseRoute.totalDrivingMinutes + SAFETY_BUFFER_MINUTES` exceeds the current remaining minutes.
- [ ] Implement `probeSecondStops(first)` by sorting unselected recommendations by detour, taking 6, and processing `chunked(2).flatMap { chunk -> coroutineScope { chunk.map { async { ... } }.awaitAll() } }`; only exact `/api/route` results that pass the two-stop timing policy become options. Cache results by `(firstPlaceId, secondPlaceId, arrivalDeadlineEpochMillis)` and clear both the visible options and irrelevant cache entries when the first selection changes. Use a monotonically increasing probe generation to discard stale responses.
- [ ] Add tests for cache reuse, cache reset, and resume-time recalculation. When there is no active navigation trip, `onResume(now)` must recompute the selected timing and refresh recommendations if their timestamp is older than 60 seconds; with an active trip, leave baseline reminders unchanged because the app cannot infer the travel stage without phase-2 arrival detection.
- [ ] Re-run focused tests and then `./gradlew.bat compileDebugKotlin`; expect pass.
- [ ] Wire the concrete `RouteGateway` and `RouteFlowViewModel` factory at `AppContainer.kt` without moving existing auth/profile/saved-place ownership.
- [ ] Commit: `git add android/app/build.gradle.kts android/app/src/main/java/com/tteumsae/app/AppContainer.kt android/app/src/main/java/com/tteumsae/app/domain/route/RouteSelectionPolicy.kt android/app/src/main/java/com/tteumsae/app/ui/route android/app/src/test/java/com/tteumsae/app/domain/route/RouteSelectionPolicyTest.kt android/app/src/test/java/com/tteumsae/app/ui/route/RouteFlowViewModelTest.kt && git commit -m "feat: 한 곳 우선 경유 선택 상태 구현"`.

### Task 8: Move and enhance the LOCATION screen without adding a new step

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/LocationScreen.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/route/LocationContinuePolicyTest.kt`

- [ ] Extract the current `LocationScreen`, `LocationSearchField`, and location-permission settings dialog from `TteumsaeApp.kt` without visual behavior changes; compile to verify the extraction before adding deadline UI.
- [ ] Add a failing pure test for `canContinueLocation(start, destination, deadline, now)`: both locations are required and remaining time must be 15–1,440 minutes.
- [ ] Run the focused test; expect the new policy to be missing.
- [ ] Add a Material 3 time picker row below destination with label `도착해야 하는 시간`, a default rounded to the next practical clock slot, and helper text `이 시간까지 목적지에 도착하도록 계산해요`.
- [ ] On selection call `resolveArrivalDeadline`; if the chosen clock time already passed today, show the resolved next-day date in the row rather than silently hiding rollover.
- [ ] Keep the single existing next button. Do not add a separate confirmation screen or expose safety/minimum-stay controls.
- [ ] Make location permission failure leave manual search and the next button usable.
- [ ] Re-run the focused test and `./gradlew.bat compileDebugKotlin`; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/ui/route/LocationScreen.kt android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/test/java/com/tteumsae/app/ui/route/LocationContinuePolicyTest.kt && git commit -m "feat: 위치 화면에 도착 마감 입력 추가"`.

### Task 9: Replace multi-select results with one-stop-first progressive disclosure

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/ResultsScreen.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteResultComponents.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteMap.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/ui/KakaoMapRouteTest.kt`

- [ ] Extract current result, card, summary, and map functions into the three route files; keep theme primitives in their current visibility or change them to `internal` only where compilation requires it.
- [ ] Add a failing test that product route URL construction receives at most two selected waypoints, while the low-level URL helper continues to support the backend-compatible limit where existing tests require it.
- [ ] Run `./gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.ui.KakaoMapRouteTest"`; expect the product-limit assertion to fail.
- [ ] Render each unselected map pin as `+N분` using `place.detourMinutes`; remove `평균 머무름` copy from the route-selection path.
- [ ] After the first selection, render `이 장소에서 최대 N분 머물 수 있어요` using the smaller of the exact route budget and the server-provided `maximumStayMinutes` (which may be capped by closing time), and render the exact `TripTiming` latest departure as `HH:mm까지 출발하면 돼요`.
- [ ] While probing, show a quiet inline progress state. Show `경유지 한 곳 더 보기` only for `SecondStopState.Available`; never show a disabled or empty CTA when no pair is feasible.
- [ ] After a second selection, show both stop rows, combined driving time, each latest-departure time, and a weak `두 번째 경유지 빼기` action. Do not expose a third-stop affordance.
- [ ] Keep `길 안내 시작` as the dominant bottom CTA and continue using the existing external Kakao Map intent/fallback URL.
- [ ] When no waypoint is eligible, keep a direct-navigation CTA. If even the direct route plus 10-minute buffer misses the deadline, show `경유 없이 바로 출발해도 늦을 수 있어요` instead of an empty recommendation screen.
- [ ] Make external navigation use `selectedStops.take(MAX_PRODUCT_WAYPOINTS)` and verify permission denial does not gate it.
- [ ] Run the focused test, full Android unit tests, and `./gradlew.bat compileDebugKotlin`; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/ui android/app/src/test/java/com/tteumsae/app/ui/KakaoMapRouteTest.kt && git commit -m "feat: 한 곳 우선 경유 결과 UI 적용"`.

### Task 10: Persist an active trip and derive reminder records

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTrip.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTripStore.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/reminder/ActiveTripTest.kt`

- [ ] Write failing tests for JSON round-trip, replacement, `deadline + 2h` expiry, and selecting only future reminders.
- [ ] Run the focused test; expect missing reminder types.
- [ ] Implement stable records with IDs derived from trip ID and stop index.

```kotlin
data class ActiveTripStop(
    val placeId: String,
    val placeName: String,
    val coordinates: Coordinates,
    val latestDepartureEpochMillis: Long,
    val reminderEpochMillis: Long,
)

data class ActiveTrip(
    val id: String,
    val arrivalDeadlineEpochMillis: Long,
    val destinationName: String,
    val destinationCoordinates: Coordinates,
    val navigationUrl: String,
    val stops: List<ActiveTripStop>,
    val expiresAtEpochMillis: Long = arrivalDeadlineEpochMillis + 2 * 60 * 60_000L,
)
```

- [ ] Keep serialization in an `internal ActiveTripCodec` so JVM tests do not need Android context. Make `ActiveTripStore` a thin SharedPreferences adapter with `save`, `load(now)`, and `clear`.
- [ ] Never persist location history; core storage contains only the confirmed route, deadline, stop identifiers/names, reminder times, and navigation URL.
- [ ] Re-run the focused test; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/reminder android/app/src/test/java/com/tteumsae/app/reminder && git commit -m "feat: 활성 경로 알림 데이터 저장"`.

### Task 11: Schedule best-effort departure notifications and restore them after clock changes

**Files:**

- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/DepartureReminderScheduler.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/AlarmManagerDepartureReminderScheduler.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/DepartureReminderReceiver.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderNotifications.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderRescheduleReceiver.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/reminder/DepartureReminderPolicyTest.kt`

- [ ] Add failing pure tests that reminders whose latest-departure time already passed are skipped, a trigger that passed while latest departure is still future becomes an immediate alert, replacing an active trip cancels old request codes, and rescheduling chooses only reminders before trip expiry.
- [ ] Run the focused test; expect missing policy classes.
- [ ] Add `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`; register an app-only reminder receiver and a system reschedule receiver for `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED`. Set explicit `android:exported` values and do not add exact-alarm permissions.
- [ ] Create notification channel `departure_reminders`; the text must say `정시 도착을 위해 5분 뒤에는 출발하는 편이 좋아요` or the exact remaining value, not claim guaranteed timing.
- [ ] Schedule each future stop through `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)` with immutable/update-current PendingIntents. If `reminderEpochMillis <= now < latestDepartureEpochMillis`, show the departure warning immediately instead of scheduling in the past. Document in code that delivery is best-effort and may be deferred by the OS.
- [ ] Keep `BroadcastReceiver.onReceive` short: read the stored trip, validate trip/stop/expiry, post one notification, and return. The reschedule receiver only reloads the trip and re-registers future alarms.
- [ ] Re-run the focused test and `./gradlew.bat lintDebug`; expect no new manifest or API-level errors.
- [ ] Commit: `git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/tteumsae/app/reminder android/app/src/test/java/com/tteumsae/app/reminder/DepartureReminderPolicyTest.kt && git commit -m "feat: 출발 마감 로컬 알림 예약"`.

### Task 12: Add contextual notification opt-in and complete app integration

**Files:**

- Modify: `android/app/src/main/java/com/tteumsae/app/ui/route/ResultsScreen.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowViewModel.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/TteumsaeApplication.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderPreferenceStore.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/reminder/ReminderOptInPolicyTest.kt`

- [ ] Add failing tests for first-use default off, remembered choice, denied-notification fallback, and active-trip creation from one/two selected stops.
- [ ] Run the focused test; expect missing opt-in policy.
- [ ] Show a compact opt-in row next to the confirmed-route summary: `출발 5분 전에 알려드릴까요?`. Keep it off on first use and remember the user's choice.
- [ ] On Android 13+, request `POST_NOTIFICATIONS` only when the user turns this on, using `rememberLauncherForActivityResult(RequestPermission())`; on denial, leave `길 안내 시작` active and show a non-blocking explanation.
- [ ] On navigation start, snapshot the current exact timing into `ActiveTrip`, replace the previous trip, cancel its alarms, schedule the new future reminders if opted in, and then launch Kakao Map regardless of scheduling outcome.
- [ ] Initialize the notification channel in `TteumsaeApplication` and keep concrete store/scheduler creation at the app composition boundary; do not put `Context` in `RouteFlowViewModel`.
- [ ] Remove obsolete `DEFAULT_EXTRA_TIME_MINUTES`, the temporary `SearchCriteria.deadlineMinutesFromNow`/`safetyBufferMinutes` fields, user-editable buffer state, five-waypoint product selection, and `isRouteWithinExtraTimeBudget` usage. Keep the current `TteumsaeApi.route(List<PlaceCandidate>)` as a compatibility wrapper over `calculateRoute(List<RouteWaypoint>)`, and keep low-level `/api/route` and URL compatibility helpers where tests still cover them.
- [ ] Re-run the focused test, all Android unit tests, compile, lint, and assemble.

```powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest compileDebugKotlin lintDebug assembleDebug
```

- [ ] Commit: `git add android/app/src/main android/app/src/test android/app/build.gradle.kts && git commit -m "feat: 도착 마감 경유 흐름과 알림 통합"`.

### Task 13: Update project documentation and run release-level verification

**Files:**

- Modify: `docs/00_START_HERE.md`
- Modify: `docs/01_PRODUCT_AND_SCOPE.md`
- Modify: `docs/02_ARCHITECTURE.md`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/04_SCREEN_FLOWS.md`
- Modify: `docs/05_API_AND_DATA.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`
- Modify: `docs/10_DECISION_LOG.md`

- [ ] Update the documented `TteumsaeApp.kt` line count/ownership from the current approximately 3,882-line baseline after extraction and mark only actually implemented items complete.
- [ ] Document the V1 request/response examples, exact constants, one-stop/two-stop formulas, notification permission fallback, and continued 0–5 backend compatibility.
- [ ] Add manual QA cases: same-day deadline, next-day rollover, exactly 15 minutes available, 14 minutes unavailable, no feasible second stop, failed second-route probe, notification denied, device reboot/time change, and Kakao Map missing.
- [ ] Run the backend test runner and structural check maintained by the project.

```powershell
cd C:\app\tteumsae\backend
npm test
npm run check
```

- [ ] Run Android verification.

```powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest compileDebugKotlin lintDebug assembleDebug
```

- [ ] On an API 35 device/emulator, manually verify the full flow and capture: location denial, notification denial, one-stop, optional two-stop, external navigation, and a fired reminder.
- [ ] Search for stale semantics and placeholders; expect no active product copy or new-route calculation using them.

```powershell
cd C:\app\tteumsae
rg -n "평균 머무름|DEFAULT_EXTRA_TIME_MINUTES|isRouteWithinExtraTimeBudget|경유지 3|TODO|TBD" android backend docs
```

- [ ] Review the diff to confirm `output/` and `tmp/` are absent, then commit docs only: `git add docs && git commit -m "docs: 도착 마감 경유 흐름 구현 상태 반영"`.

## Acceptance Checklist

- [ ] LOCATION 화면의 기존 한 단계 안에서 목적지와 도착 마감을 모두 정할 수 있다.
- [ ] 추천 핀은 이동시간만 보이고, 선택 후에만 최대 체류시간과 출발 마감이 보인다.
- [ ] 최대 체류 가능 시간이 15분 미만인 장소는 추천되지 않는다.
- [ ] 두 번째 경유지 CTA는 정확 경로가 가능한 경우에만 약하게 나타나며 최대 2곳을 넘지 않는다.
- [ ] 추천 직전 남은 시간이 다시 계산되어 오래 열린 화면의 마감 예산이 낡지 않는다.
- [ ] 알림은 사용자가 켠 경우에만 예약되고, 권한 거부·OS 지연이 길 안내를 막지 않는다.
- [ ] 레거시 서버 요청과 `/api/route` 0–5개 계약이 회귀하지 않는다.
- [ ] 모든 자동 검증과 기기 수동 QA가 통과하고 문서가 실제 코드 구조와 일치한다.

## Official Constraints Checked Before Planning

- Android 13+ 알림은 런타임 `POST_NOTIFICATIONS` 권한이 필요하며 기능 맥락에서 요청해야 한다: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- 사용자 지정 시간 알림을 exact-alarm 특별 권한 없이 예약할 때 `setAndAllowWhileIdle()`을 사용할 수 있으나 전달은 정확하지 않을 수 있다: <https://developer.android.com/develop/background-work/services/alarms>
- `BOOT_COMPLETED`는 매니페스트 암시적 브로드캐스트 제한의 예외이며, 리시버는 짧게 실행해야 한다: <https://developer.android.com/develop/background-work/background-tasks/broadcasts>
