# Account Saved-Place Sync and Deletion Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Room 로컬 원본과 Supabase user_saved_places를 오프라인 우선으로 동기화하고, 최초 게스트 병합·다중 기기 충돌·로그아웃 격리·계정 삭제 로컬 정리를 완성한다.

**Architecture:** Room 행의 desired state와 DIRTY 상태를 로컬 권위로 두고, WorkManager가 전체 pull → non-dirty 적용 → dirty push → 최종 pull 순서로 수렴시킨다. Auth 세션 코디네이터가 guest/user scope를 전환하고 최초 로그인 합집합 병합을 수행하며, 계정 삭제 오케스트레이터가 서버 성공과 응답 유실을 모두 처리한다.

**Tech Stack:** Kotlin 2.2.21, Android API 26–35, Room 2.8.4, WorkManager 2.11.2, Supabase Kotlin 3.5.0, Ktor 3.0.3, coroutines, AlarmManager integration from deadline route core

**Spec:** docs/superpowers/specs/2026-08-26-account-sync-user-data-design.md

## Global Constraints

- Prerequisite: account-foundation-local-saved and supabase-auth-profile-backend plans are complete.
- Prerequisite for the deletion completion gate: 2026-08-26-deadline-aware-route-core.md is complete.
- Room is the UI source of truth; Supabase responses never directly drive Compose lists.
- Guest writes stay local and SYNCED. Signed-in writes become DIRTY immediately.
- Client wall-clock time never decides cross-device conflict order.
- Saving and unsaving never waits for network.
- Supabase Realtime is not added.
- Server rows keep is_saved=false tombstones; clients do not physically delete them.
- Explicit logout removes the account cache and returns to an empty guest scope.
- Server deletion failure does not clear local account data.
- Account deletion clears saved cache, session, ActiveTrip, alarms, and any registered arrival state.
- output/, tmp/ and unrelated changes remain untouched.

---

## File Map

| 파일 | 책임 |
|---|---|
| domain/saved/SavedPlaceMergePolicy.kt | 최초 합집합·pull/dirty 병합 |
| data/saved/SavedPlaceRemoteDataSource.kt | Supabase DTO pull·insert/update |
| data/saved/SavedPlaceSyncEngine.kt | 한 번의 수렴 알고리즘 |
| data/saved/SavedPlaceHydrator.kt | place ID → 공개 카드 사본 |
| data/saved/SavedPlacesSyncWorker.kt | 네트워크 제약·retry |
| data/saved/SavedPlacesSyncScheduler.kt | 사용자별 unique work |
| data/saved/SavedSessionCoordinator.kt | 로그인 병합·scope 전환 |
| data/account/AccountDeletionCoordinator.kt | 서버 삭제와 로컬 정리 |
| data/account/UserScopedDataCleaner.kt | 저장·경로·알림 정리 계약 |
| ui/saved/SavedPlacesViewModel.kt | scope별 목록·동기화 상태 |
| ui/settings/SettingsScreen.kt | 계정별 저장 문구와 재시도 |
| data/TteumsaeApi.kt | GET /api/places/{id} hydration |

### Task 1: Extend local saved storage for account scopes and sync transactions

**Files:**
- Modify: android/app/src/main/java/com/tteumsae/app/data/local/SavedPlaceDao.kt
- Modify: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlacesRepository.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/OwnerScope.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedSyncStatus.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/OwnerScopeTest.kt
- Test: android/app/src/androidTest/java/com/tteumsae/app/data/local/SavedPlaceSyncDaoTest.kt

**Interfaces:**
- Produces: OwnerScope.Guest and OwnerScope.User(userId)
- Produces: userScope(userId), all(scope), applyRemote, markSynced, markAuthFailed
- Produces: observeSyncStatus(scope)
- Consumes: final-shape entity from foundation plan

- [ ] **Step 1: Write owner-scope tests**

~~~kotlin
@Test fun user_scope_is_stable_and_not_guest() {
    assertEquals("USER:abc-123", OwnerScope.User("abc-123").databaseKey)
    assertEquals("GUEST", OwnerScope.Guest.databaseKey)
}
@Test(expected = IllegalArgumentException::class)
fun blank_user_id_is_rejected() {
    OwnerScope.User(" ")
}
~~~

- [ ] **Step 2: Add DAO integration tests**

Verify:
- dirty rows are ordered by local_revision;
- remote apply cannot overwrite DIRTY;
- markSynced changes only the matching scope/place/revision;
- markAuthFailed affects dirty account rows only;
- copying Guest to User does not expose another user;
- full account scope deletion leaves Guest and other users.

- [ ] **Step 3: Implement DAO transaction methods**

Add list-all, count-dirty, insert-many, update remote only when sync_state is not DIRTY, compare-and-mark-synced, and mark-auth-failed methods. Keep DB schema version unchanged because columns already exist.

- [ ] **Step 4: Generalize repository writes**

~~~kotlin
interface SavedPlacesRepository {
    fun observeSaved(scope: OwnerScope): Flow<List<SavedPlace>>
    fun observeSyncStatus(scope: OwnerScope): Flow<SavedSyncStatus>
    suspend fun toggle(scope: OwnerScope, place: PlaceCandidate, nowMillis: Long)
    suspend fun restore(scope: OwnerScope, savedPlace: SavedPlace)
    suspend fun clear(scope: OwnerScope)
    suspend fun deleteScope(scope: OwnerScope)
}
~~~

Guest rows use SYNCED. User rows use DIRTY. Repeated toggles overwrite one row with the latest desired state and next revision.

- [ ] **Step 5: Verify and commit**

~~~powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
git add app/src/main/java/com/tteumsae/app/data app/src/test app/src/androidTest
git commit -m "feat: 계정별 저장 로컬 상태 확장"
~~~

### Task 2: Add remote saved-place access and public place hydration

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlaceRemoteDto.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlaceRemoteDataSource.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlaceHydrator.kt
- Modify: android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt
- Modify: android/app/src/test/java/com/tteumsae/app/data/TteumsaeApiTest.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedPlaceRemoteDataSourceTest.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedPlaceHydratorTest.kt

**Interfaces:**
- Produces: pullAll(userId): List<RemoteSavedPlace>
- Produces: writeState(userId, mutation): RemoteSavedPlace
- Produces: TteumsaeApi.place(id): PlaceCandidate
- Produces: hydrateMissing(scope, remoteRows)
- Consumes: authenticated Supabase client, GET /api/places/{id}

- [ ] **Step 1: Write remote mapping tests**

Test true and false rows, nullable saved_at, server updated_at parsing, current user ID enforcement, and DTO values matching SQL names.

~~~kotlin
@Serializable
data class SavedPlaceRemoteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("is_saved") val isSaved: Boolean,
    @SerialName("saved_at") val savedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
)
~~~

- [ ] **Step 2: Implement authenticated pull and least-privilege writes**

pullAll filters user_id by the current authenticated user. writeState first updates is_saved and saved_at using both user_id and place_id filters. If no row exists, it inserts user_id, place_id, is_saved, and saved_at. If two devices race and insert returns a unique conflict, retry the filtered non-key update. It never updates user_id/place_id or sends updated_at. Decode the representation so the worker stores server time.

- [ ] **Step 3: Add a failing single-place API test**

Assert GET /api/places/encoded-id maps through the same toPlaceCandidate parser and 404 produces a typed NotFound result rather than a generic crash.

- [ ] **Step 4: Implement place(id) and hydration**

Limit hydration concurrency to four. New remote IDs without a local card start with snapshot_state=MISSING. Persist successful public snapshots as READY. Keep saved state on 404 and set snapshot_state=UNAVAILABLE so UI can show current information unavailable and disable route addition.

- [ ] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
git add app/src/main/java/com/tteumsae/app/data app/src/test
git commit -m "feat: 저장 장소 원격 조회와 카드 복원 추가"
~~~

### Task 3: Implement pure merge and conflict policies

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/domain/saved/SavedPlaceMergePolicy.kt
- Test: android/app/src/test/java/com/tteumsae/app/domain/saved/SavedPlaceMergePolicyTest.kt

**Interfaces:**
- Produces: planFirstLogin(guest, remote): FirstLoginMergePlan
- Produces: mergePull(local, remote): PullMergeResult
- Consumes: local desired state/revision and remote server state

- [ ] **Step 1: Write table-driven first-login tests**

Cover:
- only remote saved;
- only guest saved;
- same saved in both;
- remote false plus current guest true → push true;
- remote true plus no guest → keep true;
- duplicate IDs → one result;
- no rows → empty plan.

~~~kotlin
data class FirstLoginMergePlan(
    val remoteToInsertLocally: List<RemoteSavedPlace>,
    val guestToPush: List<SavedMutation>,
    val finalSavedIds: Set<String>,
)
~~~

- [ ] **Step 2: Run and confirm failure**

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*SavedPlaceMergePolicyTest"
~~~

- [ ] **Step 3: Implement first-login union**

Guest desired_saved=true is an explicit present intent and wins over an old remote false tombstone during first login only. Do not use savedAtMillis to compare devices.

- [ ] **Step 4: Add normal pull tests**

Verify remote updates apply to SYNCED rows, never overwrite DIRTY rows, preserve dirty mutation order, and create local tombstones for remote false rows.

- [ ] **Step 5: Implement pull merge and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*SavedPlaceMergePolicyTest"
git add app/src/main/java/com/tteumsae/app/domain/saved app/src/test
git commit -m "feat: 저장 장소 병합 충돌 정책 추가"
~~~

### Task 4: Build the sync engine with deterministic reconciliation

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlaceSyncEngine.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SyncResult.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedPlaceSyncEngineTest.kt

**Interfaces:**
- Produces: sync(userId): SyncResult
- Consumes: DAO transaction facade, remote data source, merge policy, hydrator

- [ ] **Step 1: Write ordered-call tests with fakes**

Assert exact order:
1. snapshot dirty;
2. pull;
3. apply remote to non-dirty;
4. push dirty by revision;
5. compare-and-mark each success;
6. final pull;
7. hydrate missing saved snapshots.

Also cover network failure before push, partial push failure, 401, an entity changed locally while a push was in flight, and final pull failure after successful pushes.

- [ ] **Step 2: Define results**

~~~kotlin
sealed interface SyncResult {
    data class Success(val pushed: Int, val pulled: Int) : SyncResult
    data class Retry(val cause: Throwable) : SyncResult
    data object AuthenticationRequired : SyncResult
}
~~~

- [ ] **Step 3: Implement the algorithm**

Use compare-and-mark-synced with the original localRevision so a new local toggle during network I/O remains DIRTY. Map network/5xx to Retry, 401/403 to AuthenticationRequired, and programmer/serialization errors to a bounded failure surfaced in settings rather than an infinite retry.

- [ ] **Step 4: Verify cancellation behavior**

Rethrow CancellationException. Do not catch it as Retry.

- [ ] **Step 5: Run and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*SavedPlaceSyncEngineTest"
git add app/src/main/java/com/tteumsae/app/data/saved app/src/test
git commit -m "feat: 저장 장소 동기화 엔진 추가"
~~~

### Task 5: Schedule persistent network sync with WorkManager

**Files:**
- Modify: android/app/build.gradle.kts
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlacesSyncWorker.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlacesSyncScheduler.kt
- Modify: android/app/src/main/java/com/tteumsae/app/AppContainer.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedPlacesSyncSchedulerTest.kt
- Test: android/app/src/androidTest/java/com/tteumsae/app/data/saved/SavedPlacesSyncWorkerTest.kt

**Interfaces:**
- Produces: enqueue(userId, reason), cancel(userId)
- Consumes: SyncEngine through AppContainer

- [ ] **Step 1: Add stable WorkManager dependencies**

~~~kotlin
implementation("androidx.work:work-runtime:2.11.2")
androidTestImplementation("androidx.work:work-testing:2.11.2")
~~~

- [ ] **Step 2: Write scheduler tests**

Verify CONNECTED constraint, exponential backoff, one unique work per hashed user identifier, KEEP for repeated background triggers, REPLACE for manual retry, and cancel only the target account.

- [ ] **Step 3: Implement a CoroutineWorker**

Map Success to Result.success, Retry to Result.retry, and AuthenticationRequired to Result.failure with an auth-required output flag. Retrieve dependencies from AppContainer; do not instantiate a second Supabase client or DB.

- [ ] **Step 4: Add worker integration tests**

Use WorkManager test initialization and fake SyncEngine. Verify constraints, retry, and auth failure output.

- [ ] **Step 5: Trigger only at approved events**

Schedule after session restore, login completion, foreground resume with pending dirties, account save/unsave, and manual retry. Do not add periodic work or Realtime.

- [ ] **Step 6: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
git add app/build.gradle.kts app/src/main/java/com/tteumsae/app/data/saved app/src/main/java/com/tteumsae/app/AppContainer.kt app/src/test app/src/androidTest
git commit -m "feat: 저장 장소 백그라운드 동기화 추가"
~~~

### Task 6: Merge guest data on first login and isolate logout

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedSessionCoordinator.kt
- Modify: android/app/src/main/java/com/tteumsae/app/data/auth/AuthRepository.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/account/AccountViewModel.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedSessionCoordinatorTest.kt

**Interfaces:**
- Produces: onSignedIn(userId), onSignedOut(userId)
- Consumes: repository, merge policy, remote data source, scheduler

- [ ] **Step 1: Write first-login sequence tests**

Assert guest rows remain when pull/push fails, guest is removed only after remote and USER scope converge, repeated onSignedIn is idempotent, and a second account never receives the first account cache.

- [ ] **Step 2: Persist per-device merge completion**

Store a local completion marker keyed by hashed user ID. The marker is set after successful union. If absent, run first-login union; if present, run normal sync.

- [ ] **Step 3: Implement login flow**

Pull remote, plan union, push guest-only current saves, populate USER scope, hydrate snapshots, then clear GUEST and mark complete. A failure leaves GUEST intact and exposes sync pending.

- [ ] **Step 4: Implement explicit logout isolation**

Cancel work, sign out, delete USER scope, clear profile memory, and expose an empty GUEST scope. Do not copy account saves into Guest.

- [ ] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*SavedSessionCoordinatorTest" --tests "*AccountViewModelTest"
git add app/src/main/java/com/tteumsae/app/data app/src/main/java/com/tteumsae/app/ui/account app/src/test
git commit -m "feat: 최초 로그인 저장 병합과 로그아웃 격리"
~~~

### Task 7: Integrate account-aware saved UI and clear-all semantics

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlacesViewModel.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlacesScreen.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Test: android/app/src/test/java/com/tteumsae/app/ui/saved/SavedPlacesViewModelTest.kt

**Interfaces:**
- Produces: SavedPlacesUiState(items, scope, syncStatus, storageDescription)
- Consumes: AccountSession, repository, scheduler

- [ ] **Step 1: Write UI-state tests**

Cover guest description, account synced, pending, auth-required, retry action, guest clear, account clear marking every current saved row DIRTY false, and immediate empty UI.

- [ ] **Step 2: Implement ViewModel scope switching**

Guest observes GUEST. SignedIn observes USER:userId. Restoring keeps the last safe local list but disables account-only status actions. NeedsReauthentication shows local account cache plus re-login copy until explicit logout.

- [ ] **Step 3: Route save actions through the ViewModel**

Remove repository launches from TteumsaeApp. The ViewModel calls repository.toggle and scheduler.enqueue for account scope. Guest does not schedule work.

- [ ] **Step 4: Implement copy and unavailable cards**

Use:
- 이 기기에 N개 저장됨
- 계정에 N개 동기화됨
- N개 저장됨 · 동기화 대기 중
- 다시 로그인하면 동기화를 계속할 수 있어요

A missing public snapshot remains visible as 현재 정보를 불러올 수 없음 and cannot start route guidance.

- [ ] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
git add app/src/main/java/com/tteumsae/app/ui app/src/test
git commit -m "feat: 계정 저장 동기화 상태 UI 연결"
~~~

### Task 8: Orchestrate complete account deletion and response-loss recovery

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/account/UserScopedDataCleaner.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/account/AccountDeletionCoordinator.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/account/AccountDeletionMarkerStore.kt
- Modify: android/app/src/main/java/com/tteumsae/app/reminder/ActiveTripStore.kt
- Modify: android/app/src/main/java/com/tteumsae/app/reminder/AlarmManagerDepartureReminderScheduler.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/account/AccountViewModel.kt
- Modify: docs/superpowers/plans/2026-08-26-arrival-geofence-enhancement.md
- Test: android/app/src/test/java/com/tteumsae/app/data/account/AccountDeletionCoordinatorTest.kt

**Interfaces:**
- Produces: UserScopedDataCleaner.clearAll(userId)
- Produces: deleteCurrentAccount(): AccountDeletionResult
- Consumes: AccountDeletionApi, AuthRepository, saved scheduler/repository, ActiveTripStore, reminder scheduler

- [ ] **Step 1: Write deletion state-machine tests**

Cover:
- marker set before request;
- 500 keeps session/data/marker and returns retry;
- 204 clears every registered cleaner, signs out, removes marker;
- response lost plus next-launch invalid session clears local data;
- response lost plus valid session retries server deletion;
- repeated clear calls are idempotent;
- one cleaner failure does not skip remaining cleaners and is retried before marker removal.

- [ ] **Step 2: Define cleaner contracts**

~~~kotlin
fun interface UserDataCleaner {
    suspend fun clear(userId: String)
}

class UserScopedDataCleaner(
    private val cleaners: List<UserDataCleaner>,
) {
    suspend fun clearAll(userId: String) {
        val failures = cleaners.mapNotNull { cleaner ->
            runCatching { cleaner.clear(userId) }.exceptionOrNull()
        }
        if (failures.isNotEmpty()) throw LocalCleanupException(failures)
    }
}
~~~

Register saved scope/work cleanup, profile/session cache cleanup, ActiveTrip clear, and alarm cancellation. Do not clear local data before server success or confirmed invalid-session recovery.

- [ ] **Step 3: Implement marker and coordinator**

Use a private SharedPreferences boolean plus hashed user ID. The marker contains no token. On app start, reconcile it with current session before normal account UI initialization.

- [ ] **Step 4: Integrate route and reminder cleanup**

Add idempotent clear/cancel APIs if the deadline route plan does not already expose them. Verify all alarm request IDs for the active trip are cancelled before ActiveTripStore clears.

- [ ] **Step 5: Extend the arrival enhancement plan**

Add a concrete requirement that its geofence manager and unique WorkManager jobs implement UserDataCleaner and register with the same AppContainer list. This keeps future arrival state within the deletion contract without enabling the feature now.

- [ ] **Step 6: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
git add app/src/main/java/com/tteumsae/app/data/account app/src/main/java/com/tteumsae/app/reminder app/src/main/java/com/tteumsae/app/ui/account app/src/test ..\docs\superpowers\plans\2026-08-26-arrival-geofence-enhancement.md
git commit -m "feat: 계정 삭제 로컬 데이터 정리 통합"
~~~

### Task 9: Run multi-device, offline, deletion, and policy release QA

**Files:**
- Modify: docs/02_ARCHITECTURE.md
- Modify: docs/03_FEATURE_MATRIX.md
- Modify: docs/04_SCREEN_FLOWS.md
- Modify: docs/05_API_AND_DATA.md
- Modify: docs/07_BUILD_TEST_DEPLOY.md
- Modify: docs/08_QA_AND_KNOWN_ISSUES.md
- Modify: docs/10_DECISION_LOG.md

**Interfaces:**
- Consumes: Tasks 1–8
- Produces: release-ready account sync slice and evidence

- [ ] **Step 1: Update docs with exact sync rules**

Document owner scopes, tombstones, last server arrival wins, first-login guest union, full pull algorithm, no Realtime, logout isolation, deletion marker, and local-only route/location state.

- [ ] **Step 2: Run all automated checks**

~~~powershell
cd C:\app\tteumsae\backend
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
node scripts/check-project.js
node scripts/verify-user-rls.js
cd ..\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
git diff --check
~~~

Expected: all pass; live RLS prints PASS.

- [ ] **Step 3: Run the two-device conflict matrix**

On two signed devices:
- A saves online, B pulls;
- A unsaves offline, B saves online, A reconnects and its later-arriving false wins;
- B pulls false and does not resurrect;
- repeated offline toggles send only final intent;
- force-stop during dirty push, relaunch, and converge;
- clear all offline, reconnect, and confirm both devices empty.

- [ ] **Step 4: Run account lifecycle QA**

- Guest saves then first login union.
- Existing server saves plus guest saves form a union.
- Pull failure leaves Guest intact.
- Logout leaves empty Guest and server rows intact.
- Another user login never sees the first cache.
- Deletion 500 preserves local data.
- Successful deletion clears Auth/profile/saved/ActiveTrip/alarms.
- Simulated response loss converges on next launch.
- Re-registering the same provider starts with no old data.

- [ ] **Step 5: Run policy and disclosure QA**

Verify public privacy/deletion URLs, in-app links, optional demographics copy, Data Safety entries, and absence of user location/history tables.

- [ ] **Step 6: Record evidence and commit**

~~~powershell
git add docs
git commit -m "docs: 계정 저장 동기화 출시 검증 기록"
~~~

## Plan Completion Gate

- [ ] Offline save/unsave is immediate and later converges.
- [ ] First login unions guest and server saves without clearing Guest on failure.
- [ ] Two-device tombstones prevent resurrection.
- [ ] No Realtime, trip, location, or search history enters user DB.
- [ ] Logout removes only local account cache and returns an empty Guest.
- [ ] Deletion failure preserves local state; success or confirmed response loss clears every registered user state.
- [ ] ActiveTrip and departure alarms are cleared on account deletion.
- [ ] All automated checks and two-device QA pass.
- [ ] Source-of-truth docs match the shipped data handling.
