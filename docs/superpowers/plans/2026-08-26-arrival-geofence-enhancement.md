# Arrival Geofence Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 확정 경로에서 별도로 동의한 경우에만 경유지 도착을 위치 기반으로 추정하고, 현재 시각과 남은 정확 경로로 출발 마감을 갱신해 알려주되, 권한 거부·지오펜스 지연·오탐·네트워크 실패에는 1차 고정 알림으로 안전하게 돌아간다.

**Architecture:** 1차 계획의 `ActiveTrip`, `RouteGateway`, `TripTiming`, 고정 알림을 기반으로 한다. Google Play services 지오펜스는 도착 이벤트만 전달하고, 리시버는 고유 WorkManager 작업을 enqueue한 뒤 즉시 종료한다. Worker가 저장된 경로와 정확 `/api/route`를 사용해 현재 경유지의 출발 마감을 다시 계산하고 알림을 교체한다. 기능은 빌드 플래그 기본값 `false`로 시작하며 Play 백그라운드 위치 심사 자료와 개인정보 고지가 승인되기 전에는 운영 노출하지 않는다.

**Tech Stack:** Kotlin 2.0.21, Android API 26–35, Google Play services Location 21.4.0, WorkManager 2.11.2, Kotlin coroutines 1.9.0, SharedPreferences, AlarmManager, NotificationCompat, JUnit 4

**Spec:** `docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md` sections 7.4, 8, 9, 10.2, 11.3, and 12

## Global Constraints

- 이 계획은 `2026-08-26-deadline-aware-route-core.md`가 완료되고 검증된 뒤 실행한다.
- `ARRIVAL_GEOFENCE_ENABLED`는 기본 `false`다. 코드 완성은 운영 활성화를 뜻하지 않는다.
- 경로 확정 전이나 초기 온보딩에서 백그라운드 위치를 요청하지 않는다.
- 포그라운드 정밀 위치 → 기능 설명 → 백그라운드 위치 순으로 단계적으로 요청한다. Android 11+에서는 설정 화면 경로를 안내한다.
- 반경은 150m, 체류 전환은 2분, 제품 경유지는 최대 2곳으로 고정한다.
- 지오펜스는 실시간 추적이 아니다. 백그라운드 전달 지연을 허용하고 정시 도착을 보장하는 문구를 쓰지 않는다.
- 위치 좌표 기록이나 이동 이력을 서버·분석 도구에 저장하지 않는다. 활성 경로 좌표와 감지 상태는 로컬에서 `deadline + 2h`까지만 유지한다.
- 도착 재계산이 실패하거나 오탐 정정이 발생하면 기존 고정 출발 알림을 유지·복원한다.
- 권한을 거부하거나 설정에서 철회해도 추천과 Kakao Map 실행은 항상 가능해야 한다.
- 각 작업은 실패 테스트 → 최소 구현 → 통과 → 해당 파일만 커밋 순서로 진행한다.

## File Responsibility Map

| File | Responsibility |
|---|---|
| `android/app/build.gradle.kts` | 기본 비활성 플래그와 Location/WorkManager 의존성 |
| `android/app/src/main/AndroidManifest.xml` | 백그라운드 위치와 전용 receiver 등록 |
| `arrival/ArrivalDetectionModels.kt` | 권한·등록·감지·갱신 상태 타입 |
| `arrival/ArrivalPermissionPolicy.kt` | OS 버전별 다음 권한/설정 행동 결정 |
| `arrival/ArrivalGeofenceManager.kt` | 활성 경로의 150m/2분 지오펜스 등록·해제 |
| `arrival/ArrivalGeofenceReceiver.kt` | 지오펜스 이벤트 검증과 WorkManager enqueue |
| `arrival/ArrivalRefreshUseCase.kt` | 정확 남은 경로 조회, 출발 마감 재계산, 저장/알림 교체 |
| `arrival/ArrivalRefreshWorker.kt` | use case를 실행하는 지속 작업 어댑터 |
| `arrival/ArrivalNotifications.kt` | 도착 추정·정정·실패 알림과 액션 |
| `arrival/ArrivalCorrectionReceiver.kt` | `도착했어요`/`아직 도착 전이에요` 처리 |
| `reminder/ActiveTrip.kt` | 감지 전 기준값과 현재 갱신값 보관 |
| `reminder/ActiveTripStore.kt` | 스키마 마이그레이션과 원자적 상태 교체 |
| `ui/route/ResultsScreen.kt` | 경로 확정 후에만 보이는 명시적 opt-in UI |

---

### Task 1: Add a default-off release gate and verified dependencies

**Files:**

- Modify: `android/app/build.gradle.kts`
- Modify: `android/local.properties.example`

- [ ] Add a local opt-in property while keeping the committed default disabled.

```kotlin
val arrivalGeofenceEnabled =
    localProperties.getProperty("ARRIVAL_GEOFENCE_ENABLED", "false").toBooleanStrictOrNull()
        ?: false

defaultConfig {
    buildConfigField("boolean", "ARRIVAL_GEOFENCE_ENABLED", arrivalGeofenceEnabled.toString())
}
```

- [ ] Add `ARRIVAL_GEOFENCE_ENABLED=false` to the local-properties example and confirm the existing `**/local.properties` rule in `.gitignore` still excludes the real file; no ignore-file edit is required.
- [ ] Add the exact verified dependencies.

```kotlin
implementation("com.google.android.gms:play-services-location:21.4.0")
implementation("androidx.work:work-runtime-ktx:2.11.2")
testImplementation("androidx.work:work-testing:2.11.2")
```

- [ ] Run `./gradlew.bat dependencies --configuration debugRuntimeClasspath` from `android/`; confirm one resolved version of Play services Location and WorkManager and no downgrade conflict.
- [ ] Run `./gradlew.bat compileDebugKotlin`; expect pass with the feature still disabled.
- [ ] Commit: `git add android/app/build.gradle.kts android/local.properties.example && git commit -m "build: 도착 감지 기능 플래그와 의존성 추가"`.

### Task 2: Extend ActiveTrip with reversible arrival state

**Files:**

- Modify: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTrip.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/reminder/ActiveTripStore.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/reminder/ActiveTripTest.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalDetectionModels.kt`

- [ ] Add failing round-trip/migration tests for an old record without arrival fields and a new record with pending/confirmed/corrected state.
- [ ] Add a concurrent-update test around `ActiveTripStore.update(expectedTripId)`: an expired or replaced trip must not be overwritten by a stale Worker result.
- [ ] Run the focused test; expect missing arrival fields and update API.
- [ ] Add schema version 2 and retain both the fixed baseline and current reminder values.

```kotlin
enum class ArrivalStatus { NOT_DETECTED, PENDING_CONFIRMATION, CONFIRMED, CORRECTED }

data class ActiveTripStop(
    val placeId: String,
    val placeName: String,
    val coordinates: Coordinates,
    val baselineLatestDepartureEpochMillis: Long,
    val baselineReminderEpochMillis: Long,
    val currentLatestDepartureEpochMillis: Long = baselineLatestDepartureEpochMillis,
    val currentReminderEpochMillis: Long = baselineReminderEpochMillis,
    val arrivalStatus: ArrivalStatus = ArrivalStatus.NOT_DETECTED,
    val detectedAtEpochMillis: Long? = null,
)
```

- [ ] Implement old-record defaults so a user upgrading with a live fixed reminder does not lose it. Treat unknown future schema versions as unreadable and clear only that active record.
- [ ] Implement `update(expectedTripId, transform)` under one synchronized read-modify-write section and refuse updates after expiry.
- [ ] Re-run the focused test; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/reminder android/app/src/main/java/com/tteumsae/app/arrival/ArrivalDetectionModels.kt android/app/src/test/java/com/tteumsae/app/reminder/ActiveTripTest.kt && git commit -m "feat: 되돌릴 수 있는 도착 감지 상태 저장"`.

### Task 3: Model the staged permission flow before touching Compose

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalPermissionPolicy.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalPermissionPolicyTest.kt`

- [ ] Write a table-driven failing test for API 26–28, API 29, and API 30–35 with combinations of notification, fine location, and background location grants.

```kotlin
@Test fun `Android 11 이상은 포그라운드 허용 뒤 설정 안내로 이동한다`() {
    assertEquals(
        ArrivalPermissionAction.OpenBackgroundLocationSettings,
        nextArrivalPermissionAction(
            sdkInt = 35,
            notificationsGranted = true,
            fineLocationGranted = true,
            backgroundLocationGranted = false,
        ),
    )
}

@Test fun `권한이 모두 있어도 기능 플래그가 꺼지면 활성화하지 않는다`() {
    assertEquals(
        ArrivalPermissionAction.FeatureUnavailable,
        nextArrivalPermissionAction(35, true, true, true, featureEnabled = false),
    )
}
```

- [ ] Run the focused test; expect missing policy.
- [ ] Implement a sealed action contract rather than branching on OS versions inside Composables.

```kotlin
sealed interface ArrivalPermissionAction {
    data object FeatureUnavailable : ArrivalPermissionAction
    data object RequestNotifications : ArrivalPermissionAction
    data object RequestFineLocation : ArrivalPermissionAction
    data object RequestBackgroundLocation : ArrivalPermissionAction // API 29
    data object OpenBackgroundLocationSettings : ArrivalPermissionAction // API 30+
    data object EnableArrivalDetection : ArrivalPermissionAction
}
```

- [ ] Make API 26–28 require fine location but not the nonexistent background runtime permission. For API 29, request `ACCESS_BACKGROUND_LOCATION` separately after fine location. For API 30+, return the settings action and expose the system-provided background-permission label in UI integration later.
- [ ] Re-run the focused test; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/arrival/ArrivalPermissionPolicy.kt android/app/src/test/java/com/tteumsae/app/arrival/ArrivalPermissionPolicyTest.kt && git commit -m "feat: 도착 감지 단계별 권한 정책 추가"`.

### Task 4: Add contextual opt-in without blocking navigation

**Files:**

- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/route/ResultsScreen.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowUiState.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/route/RouteFlowViewModel.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalPermissionCoordinator.kt`

- [ ] Add `ACCESS_BACKGROUND_LOCATION` to the manifest, but do not add a foreground service or continuous-location permission/service.
- [ ] Show the opt-in only when a 1–2 stop route is exact, confirmed, and `BuildConfig.ARRIVAL_GEOFENCE_ENABLED` is true. The default remains off.
- [ ] Use this disclosure before the first permission action: `경유지 도착을 감지해 남은 시간과 출발 알림을 다시 계산합니다. 활성 경로가 끝나면 위치 감지를 중지하며 이동 기록은 저장하지 않습니다.`
- [ ] Drive permission actions through separate Activity Result launchers. Never request foreground and background location in one launcher call.
- [ ] For API 30+, show the label returned by `packageManager.backgroundPermissionOptionLabel`, then open the app details settings page. On `ON_RESUME`, re-read grants and continue only if the user explicitly returns with permission.
- [ ] If any permission is denied, set `arrivalDetectionEnabled=false`, keep the fixed reminder state unchanged, show `고정 출발 알림은 그대로 사용할 수 있어요`, and leave `길 안내 시작` enabled.
- [ ] Run `./gradlew.bat compileDebugKotlin lintDebug`; expect pass and no combined foreground/background permission warning.
- [ ] Commit: `git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/tteumsae/app/ui/route android/app/src/main/java/com/tteumsae/app/arrival/ArrivalPermissionCoordinator.kt && git commit -m "feat: 경로 확정 후 도착 감지 동의 흐름 추가"`.

### Task 5: Register bounded dwell geofences for the active trip

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalGeofenceManager.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalGeofenceManagerTest.kt`

- [ ] Define an injectable boundary so tests do not depend on a device Google Play services process.

```kotlin
interface ArrivalGeofenceManager {
    suspend fun replaceFor(trip: ActiveTrip): Result<Unit>
    suspend fun removeFor(tripId: String): Result<Unit>
}

internal data class ArrivalGeofenceSpec(
    val requestId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val loiteringDelayMillis: Int = 120_000,
    val expiresAtEpochMillis: Long,
)
```

- [ ] Add failing tests that a two-stop trip creates exactly two stable request IDs, uses 150m/120s, never expires after the trip, rejects missing permission, and replacement removes old IDs first.
- [ ] Run the focused test; expect missing manager/spec.
- [ ] Implement `PlayServicesArrivalGeofenceManager` using `GEOFENCE_TRANSITION_DWELL`, `setLoiteringDelay(120_000)`, `INITIAL_TRIGGER_DWELL`, and a component-explicit update-current broadcast PendingIntent. It must use `FLAG_MUTABLE` on Android 12+ because Location Services fills transition extras, and the pre-12 compatible flags otherwise.
- [ ] Derive request IDs as `tteumsae:<tripId>:<stopIndex>:<placeId>`; parse through one tested helper rather than string splitting inside the receiver.
- [ ] Check the build flag and required grants immediately before registration. Return a typed failure and keep fixed reminders untouched when Google Play services rejects registration.
- [ ] Re-run the focused test and `./gradlew.bat compileDebugKotlin`; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/arrival/ArrivalGeofenceManager.kt android/app/src/test/java/com/tteumsae/app/arrival/ArrivalGeofenceManagerTest.kt && git commit -m "feat: 활성 경로 도착 지오펜스 등록"`.

### Task 6: Convert geofence events into unique persistent refresh work

**Files:**

- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalGeofenceReceiver.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalWorkEnqueuer.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalWorkEnqueuerTest.kt`

- [ ] Register `ArrivalGeofenceReceiver` as `android:exported="false"`; it receives only the app-created explicit PendingIntent.
- [ ] Add failing tests for unique work name, `ExistingWorkPolicy.KEEP`, duplicate events, non-DWELL transitions, malformed IDs, expired trips, and stale trip IDs.
- [ ] Run the focused test; expect missing enqueuer behavior.
- [ ] In the receiver, parse `GeofencingEvent`, discard errors/non-DWELL transitions, validate the request against the current active trip, and enqueue work. Do not make a network call or post the final arrival notification in `onReceive`.

```kotlin
internal fun arrivalWorkName(tripId: String, stopIndex: Int) =
    "arrival-refresh:$tripId:$stopIndex"

workManager.enqueueUniqueWork(
    arrivalWorkName(tripId, stopIndex),
    ExistingWorkPolicy.KEEP,
    OneTimeWorkRequestBuilder<ArrivalRefreshWorker>()
        .setInputData(workDataOf(TRIP_ID to tripId, STOP_INDEX to stopIndex))
        .build(),
)
```

- [ ] Keep receiver work below Android's broadcast timeout; all durable work goes through WorkManager.
- [ ] Re-run the focused test and `./gradlew.bat lintDebug`; expect pass.
- [ ] Commit: `git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/tteumsae/app/arrival/ArrivalGeofenceReceiver.kt android/app/src/main/java/com/tteumsae/app/arrival/ArrivalWorkEnqueuer.kt android/app/src/test/java/com/tteumsae/app/arrival/ArrivalWorkEnqueuerTest.kt && git commit -m "feat: 도착 이벤트를 지속 작업으로 연결"`.

### Task 7: Recalculate the current stop from the exact remaining route

**Files:**

- Modify: `android/app/src/main/java/com/tteumsae/app/domain/TripTiming.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/domain/TripTimingTest.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalRefreshUseCase.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalRefreshWorker.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalRefreshUseCaseTest.kt`

- [ ] Add failing timing tests for arrival at the only stop and arrival at the first of two stops.

```kotlin
@Test fun `arrival at first stop reserves second stop minimum stay`() {
    val refreshed = calculateCurrentStopTiming(
        nowEpochMillis = instant("2026-08-26T06:30:00Z"),
        deadlineEpochMillis = instant("2026-08-26T08:00:00Z"),
        remainingLegMinutes = listOf(20, 30),
        remainingStopCount = 1,
    )!!
    assertEquals(15, refreshed.reservedLaterStayMinutes)
    assertEquals(15, refreshed.maximumCurrentStayMinutes) // 90 - 50 - 10 - 15
    assertEquals(instant("2026-08-26T06:45:00Z"), refreshed.latestDepartureEpochMillis)
}
```

- [ ] Implement `calculateCurrentStopTiming`: current maximum stay is remaining minutes minus all remaining driving, 10-minute safety, and 15 minutes per later stop. Return an `ImmediateDeparture` result when the maximum is zero or negative rather than fabricating a positive stay.
- [ ] Write use-case tests with fake gateway/store/scheduler/notifier for success, zero remaining waypoint, one remaining waypoint, route failure, stale trip, stale stop, immediate departure, and store race.
- [ ] Run the focused tests; expect missing behavior.
- [ ] Implement the use case in this order:

```kotlin
suspend fun refresh(tripId: String, stopIndex: Int, now: Long): ArrivalRefreshResult {
    val trip = store.load(now)?.takeIf { it.id == tripId } ?: return Stale
    val stop = trip.stops.getOrNull(stopIndex) ?: return Stale
    val laterStops = trip.stops.drop(stopIndex + 1)
    val route = gateway.calculateRoute(
        start = stop.coordinates,
        destination = trip.destinationCoordinates,
        waypoints = laterStops.map { RouteWaypoint(it.placeId, it.placeName, it.coordinates) },
    )
    val timing = calculateCurrentStopTiming(
        now,
        trip.arrivalDeadlineEpochMillis,
        route.legs.map { it.drivingMinutes },
        laterStops.size,
    )
    val updated = store.update(expectedTripId = tripId) { current ->
        current.copy(stops = current.stops.mapIndexed { index, item ->
            if (index != stopIndex) item else item.copy(
                currentLatestDepartureEpochMillis = timing.latestDepartureEpochMillis,
                currentReminderEpochMillis = timing.reminderEpochMillis,
                arrivalStatus = ArrivalStatus.PENDING_CONFIRMATION,
                detectedAtEpochMillis = now,
            )
        })
    } ?: return ArrivalRefreshResult.Stale
    scheduler.replaceFor(updated, now)
    notifier.showArrivalEstimate(updated, stopIndex, timing)
    return ArrivalRefreshResult.Updated(timing)
}
```

- [ ] If exact route lookup throws or returns inconsistent legs, do not mutate the trip or cancel the baseline alarm; return `FallbackKept` and post only a best-effort failure notice if notification permission exists.
- [ ] Implement `ArrivalRefreshWorker` as a thin `CoroutineWorker`. It returns success for stale/duplicate events, retry only for transient network failure with bounded backoff, and failure for malformed input.
- [ ] Re-run all focused tests; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app/domain/TripTiming.kt android/app/src/test/java/com/tteumsae/app/domain/TripTimingTest.kt android/app/src/main/java/com/tteumsae/app/arrival/ArrivalRefreshUseCase.kt android/app/src/main/java/com/tteumsae/app/arrival/ArrivalRefreshWorker.kt android/app/src/test/java/com/tteumsae/app/arrival/ArrivalRefreshUseCaseTest.kt && git commit -m "feat: 도착 후 남은 경로와 출발 마감 갱신"`.

### Task 8: Notify the estimate and let the user correct false arrivals

**Files:**

- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalNotifications.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalCorrectionReceiver.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalCorrectionTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

- [ ] Add failing tests for confirm, false-arrival correction, repeated action, stale trip, and correction after a new trip replaced the old one.
- [ ] Run the focused test; expect missing receiver/policy.
- [ ] Post `OO에 도착하셨나요?` with body `도착으로 감지했어요. HH:mm까지 출발하면 돼요.` and actions `도착했어요` and `아직 도착 전이에요`. Use explicit immutable PendingIntents scoped by trip/stop/action.
- [ ] On confirm, atomically change `PENDING_CONFIRMATION → CONFIRMED`, keep the refreshed reminder, and remove only the confirmed stop's geofence.
- [ ] On correction, atomically change to `CORRECTED`, restore baseline latest-departure/reminder values, cancel the refreshed alarm, and re-register the baseline alarm if it is still in the future. Remove the stop geofence to prevent a notification loop; the remaining stop geofence stays active.
- [ ] If the refreshed latest departure is already within five minutes, post an immediate `출발 시간이 가까워졌어요` notification instead of scheduling an alarm in the past.
- [ ] Keep `onReceive` local and short; any Play services removal that cannot finish promptly is delegated to WorkManager.
- [ ] Re-run the focused test and `./gradlew.bat lintDebug`; expect pass.
- [ ] Commit: `git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/tteumsae/app/arrival/ArrivalNotifications.kt android/app/src/main/java/com/tteumsae/app/arrival/ArrivalCorrectionReceiver.kt android/app/src/test/java/com/tteumsae/app/arrival/ArrivalCorrectionTest.kt && git commit -m "feat: 도착 추정 알림과 오탐 정정 추가"`.

### Task 9: Reconcile lifecycle, permission revocation, and cleanup

**Files:**

- Modify: `android/app/src/main/java/com/tteumsae/app/TteumsaeApplication.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/reminder/ReminderRescheduleReceiver.kt`
- Create: `android/app/src/main/java/com/tteumsae/app/arrival/ArrivalDetectionReconciler.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/arrival/ArrivalDetectionReconcilerTest.kt`

- [ ] Write failing tests for app resume with permission revoked, expired trip, new trip replacement, device reboot, time/timezone change, feature flag turned off, and all stops confirmed.
- [ ] Run the focused test; expect missing reconciler.
- [ ] Implement one idempotent `reconcile(now)` policy: remove all owned geofences if the flag is off, permission is missing, or the trip expired; otherwise register only unresolved stops and restore only future alarms.
- [ ] Call reconciliation on app resume and from the existing system reschedule receiver through a short WorkManager job; never perform Google Play services work directly in the boot receiver.
- [ ] When a new trip replaces an old one, remove old geofences before registering the new set. When the trip expires or navigation is cancelled, clear geofences, arrival work, active notifications, and local arrival state.
- [ ] Re-read actual permission state rather than trusting a remembered opt-in boolean. Show the setting as off after revocation without repeatedly prompting.
- [ ] Re-run the focused test, then all Android tests and lint; expect pass.
- [ ] Commit: `git add android/app/src/main/java/com/tteumsae/app android/app/src/test/java/com/tteumsae/app/arrival/ArrivalDetectionReconcilerTest.kt && git commit -m "feat: 도착 감지 상태 복구와 만료 정리"`.

### Task 10: Complete policy material, device QA, and guarded rollout

**Files:**

- Modify: `docs/01_PRODUCT_AND_SCOPE.md`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/04_SCREEN_FLOWS.md`
- Modify: `docs/06_ENVIRONMENT_AND_ACCESS.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`
- Modify: `docs/10_DECISION_LOG.md`
- Create: `docs/privacy/arrival-location-disclosure.md`
- Create: `docs/release/arrival-geofence-rollout-checklist.md`

- [ ] Write the prominent in-app disclosure, privacy-policy data statement, retention/deletion statement, and Google Play declaration answers using only implemented behavior. State that location supports active-trip arrival detection, is not used for ads/analytics/profiling, and is removed after expiry.
- [ ] Record the Play review assets required before enablement: feature walkthrough video link field, disclosure screenshots, permission-flow screenshots, reviewer instructions, test account requirements (`없음` if unchanged), and privacy-policy URL field. Empty evidence fields block production enablement; they are not left as `TODO` in the checked-in checklist but use explicit `미제출 — 운영 활성화 금지` status.
- [ ] Run full automated verification.

```powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest compileDebugKotlin lintDebug assembleDebug

cd C:\app\tteumsae\backend
node --test tests/validation.test.js tests/time-safe.test.js tests/recommendations.test.js tests/route-api.test.js tests/routing.test.js tests/kakao-local.test.js tests/kakao-mobility.test.js tests/places-api.test.js tests/tour-api.test.js
pnpm run check
```

- [ ] Test on at least API 28, 29, 30, 33, and 35 devices/emulators: permission grant/deny/revoke, settings return, one/two stops, 150m dwell, drive-by without dwell, delayed event, airplane/offline refresh failure, process death, reboot, time change, false-arrival correction, and trip expiry.
- [ ] Confirm the feature remains absent in a clean build without the local flag, then build a QA-only APK with `ARRIVAL_GEOFENCE_ENABLED=true` and repeat the end-to-end flow.
- [ ] Search for policy and implementation drift.

```powershell
cd C:\app\tteumsae
rg -n "실시간 추적|항상 추적|정시 보장|TODO|TBD|ARRIVAL_GEOFENCE_ENABLED" android docs
git status --short
```

- [ ] Confirm `output/` and `tmp/` remain outside the commit. Commit documentation and rollout gate: `git add docs android/local.properties.example && git commit -m "docs: 도착 감지 정책과 단계적 출시 절차 추가"`.
- [ ] Production activation is a separate authorized change only after Play approval and all checklist statuses are `승인/검증 완료`; do not flip the committed default in this task.

## Acceptance Checklist

- [ ] 기능 플래그가 꺼진 기본 빌드는 1차 고정 알림과 완전히 동일하게 동작한다.
- [ ] 사용자는 경로 확정 뒤에만 도착 감지를 켤 수 있고 위치 사용 이유·보존 범위를 먼저 본다.
- [ ] OS 버전별 권한 흐름이 단계적으로 동작하며 거부·철회가 길 안내를 막지 않는다.
- [ ] 각 경유지는 반경 150m에서 2분 체류할 때만 도착 후보 이벤트를 만든다.
- [ ] 리시버는 네트워크 작업 없이 고유 WorkManager 작업만 예약한다.
- [ ] 정확 남은 경로 계산 성공 시 현재 장소의 출발 마감/알림만 안전하게 갱신된다.
- [ ] 실패·오탐·경쟁 상태에는 기준 고정 알림이 유지 또는 복원된다.
- [ ] 사용자는 도착 추정을 확인하거나 `아직 도착 전이에요`로 정정할 수 있다.
- [ ] 만료·새 경로·권한 철회·기능 비활성 시 지오펜스와 위치 관련 로컬 상태가 제거된다.
- [ ] Google Play 심사와 개인정보 자료가 완료되기 전에는 운영 플래그가 켜지지 않는다.

## Official Constraints Checked Before Planning

- 지오펜스에는 정밀 위치가 필요하고 Android 10+ 백그라운드 사용에는 `ACCESS_BACKGROUND_LOCATION`이 필요하며, 백그라운드 이벤트는 수분 지연될 수 있다: <https://developer.android.com/develop/sensors-and-location/location/geofencing>
- 백그라운드 위치는 앱의 핵심 사용자 기능에 필요하고 사용자에게 명확해야 하며 단계적 권한 요청이 필요하다: <https://developer.android.com/develop/sensors-and-location/location/background>
- Google Play는 백그라운드 위치에 선언, 주요 고지, 개인정보처리방침, 시연 영상 등 별도 검토를 요구한다: <https://support.google.com/googleplay/android-developer/answer/9799150>
- BroadcastReceiver는 짧게 끝내고 오래 지속되어야 하는 작업은 예약 작업으로 넘겨야 한다: <https://developer.android.com/develop/background-work/background-tasks/broadcasts>
- WorkManager 2.11.2는 앱 종료/재부팅을 견뎌야 하는 지속 작업에 적합하다: <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started>
- 현재 공식 Google Play services 설정 문서의 Location artifact는 21.4.0이다: <https://developers.google.com/android/guides/setup>
