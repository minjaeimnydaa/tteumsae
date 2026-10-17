# Account Foundation and Local Saved Places Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TteumsaeApp.kt에서 안전한 순수 로직·설정·저장 화면을 단계적으로 분리하고, 기존 SharedPreferences 저장 장소를 Room 기반 게스트 저장소로 무손실 이전한다.

**Architecture:** 현재 화면 전환은 유지하면서 순수 정책, 외부 인텐트, 저장·설정 UI를 작은 파일로 옮긴다. 저장 목록의 단일 원본은 Room Flow로 바꾸며 최종 계정 동기화에 필요한 owner scope, tombstone, dirty 열을 처음부터 포함하되 이 계획에서는 GUEST scope만 활성화한다.

**Tech Stack:** Kotlin 2.0.21, Java 17, Android API 26–35, AGP 8.7.3, Gradle 8.9, Compose BOM 2024.12.01, Room 2.8.4, coroutines 1.9.0, JUnit 4

**Spec:** docs/superpowers/specs/2026-08-26-account-sync-user-data-design.md

## Global Constraints

- 실행 순서상 이 계획을 먼저 완료한 뒤 `2026-08-26-deadline-aware-route-core.md`를 실행하고, Supabase Auth 계획의 도구체인 업그레이드는 그 다음에 수행한다.
- Supabase와 로그인은 이 계획에서 추가하지 않는다.
- 기존 HOME → LOCATION → CONDITIONS → LOADING → RESULTS → DETAIL 전이와 Back 의미를 유지한다.
- 지도 SDK 생명주기와 마커 렌더링은 이동하지 않는다.
- SharedPreferences 원본은 Room 트랜잭션 성공 전 삭제하지 않는다.
- destructive migration을 사용하지 않는다.
- 동작 변경 없는 추출과 저장소 교체를 같은 커밋에 섞지 않는다.
- output/, tmp/와 관련 없는 변경을 건드리지 않는다.

---

## File Map

| 파일 | 책임 |
|---|---|
| ui/navigation/AppDestination.kt | 화면 enum과 뒤로가기 |
| domain/route/RouteSelectionPolicy.kt | 경유 순서·예산 계산 |
| domain/recommendation/RecommendationFilters.kt | 의도·카테고리 변환 |
| platform/ExternalNavigation.kt | 카카오맵 URL·실행 |
| platform/ExternalSettings.kt | 설정·정책·문의·캐시 |
| ui/common/Formatters.kt | 시간·거리 표시 |
| domain/saved/SavedPlace.kt | 저장 장소 domain 모델 |
| data/local/* | Room, DAO, codec, 이전 |
| data/saved/SavedPlacesRepository.kt | 게스트 저장 진입점 |
| ui/saved/*, ui/settings/* | 추출된 화면 |
| ui/TteumsaeApp.kt | 최상위 조립 |

### Task 1: Extract navigation policy

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/ui/navigation/AppDestination.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Test: android/app/src/test/java/com/tteumsae/app/ui/navigation/AppDestinationTest.kt

**Interfaces:**
- Produces: AppDestination, MainTab, previousDestination(AppDestination)
- Consumes: none

- [ ] **Step 1: Write the failing test**

~~~kotlin
@Test fun previous_destinations_match_current_behavior() {
    assertEquals(AppDestination.HOME, previousDestination(AppDestination.SETTINGS))
    assertEquals(AppDestination.LOCATION, previousDestination(AppDestination.CONDITIONS))
    assertEquals(AppDestination.CONDITIONS, previousDestination(AppDestination.RESULTS))
    assertEquals(AppDestination.RESULTS, previousDestination(AppDestination.DETAIL))
}
~~~

- [ ] **Step 2: Run and confirm failure**

~~~powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest --tests "*AppDestinationTest"
~~~

Expected: unresolved AppDestination.

- [ ] **Step 3: Implement the policy**

~~~kotlin
enum class AppDestination {
    HOME, SAVED, SETTINGS, LOCATION, CONDITIONS, LOADING, RESULTS, DETAIL,
}
enum class MainTab { EXPLORE, SAVED, SETTINGS }

fun previousDestination(current: AppDestination): AppDestination = when (current) {
    AppDestination.SAVED, AppDestination.SETTINGS, AppDestination.LOCATION -> AppDestination.HOME
    AppDestination.CONDITIONS -> AppDestination.LOCATION
    AppDestination.LOADING, AppDestination.RESULTS -> AppDestination.CONDITIONS
    AppDestination.DETAIL -> AppDestination.RESULTS
    AppDestination.HOME -> AppDestination.HOME
}
~~~

- [ ] **Step 4: Replace only enum names and the BackHandler switch**

~~~kotlin
BackHandler(enabled = screen != AppDestination.HOME) {
    screen = previousDestination(screen)
}
~~~

- [ ] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
git add android/app/src/main/java/com/tteumsae/app/ui/navigation android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/test
git commit -m "refactor: 화면 전환 정책 분리"
~~~

### Task 2: Extract pure route, filter, formatter, and platform helpers

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/domain/route/RouteSelectionPolicy.kt
- Create: android/app/src/main/java/com/tteumsae/app/domain/recommendation/RecommendationFilters.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/common/Formatters.kt
- Create: android/app/src/main/java/com/tteumsae/app/platform/ExternalNavigation.kt
- Create: android/app/src/main/java/com/tteumsae/app/platform/ExternalSettings.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Move/modify: android/app/src/test/java/com/tteumsae/app/ui/KakaoMapRouteTest.kt
- Test: android/app/src/test/java/com/tteumsae/app/domain/recommendation/RecommendationFiltersTest.kt

**Interfaces:**
- Produces: current route helper signatures unchanged
- Produces: RecommendationIntent and filter functions
- Produces: formatMinutes, formatDistance
- Produces: current Kakao URL and Context action signatures

- [ ] **Step 1: Write failing recommendation tests**

~~~kotlin
@Test fun any_and_specific_intent_do_not_coexist() {
    assertEquals(
        setOf(RecommendationIntent.CAFE),
        toggleRecommendationIntent(setOf(RecommendationIntent.ANY), RecommendationIntent.CAFE),
    )
}
@Test fun no_food_excludes_restaurants() {
    val result = recommendationIntentFilters(setOf(RecommendationIntent.NO_FOOD))
    assertTrue(result.second)
    assertFalse(PlaceCategory.RESTAURANT in result.first)
}
~~~

- [ ] **Step 2: Run the focused test and confirm failure**

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*RecommendationFiltersTest"
~~~

- [ ] **Step 3: Move current implementations verbatim**

Move route calculations, RecommendationIntent/filter helpers, time/distance formatters, Kakao URL builders, and external Context actions. Change only package, visibility, and imports. Do not rewrite algorithms or UI copy.

- [ ] **Step 4: Prove old definitions are gone**

~~~powershell
rg -n "fun (orderWaypointIdsAlongRoute|recommendationIntentFilters|openKakaoMap|openPolicy|formatMinutes)" app/src/main/java
~~~

Expected: one production definition per function outside TteumsaeApp.kt.

- [ ] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug
git add android/app/src/main/java/com/tteumsae/app/domain android/app/src/main/java/com/tteumsae/app/platform android/app/src/main/java/com/tteumsae/app/ui/common android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/test
git commit -m "refactor: 순수 정책과 외부 실행 책임 분리"
~~~

### Task 3: Extract saved and settings screens

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/domain/saved/SavedPlace.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlacesScreen.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlaceDetailScreen.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlaceComponents.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsComponents.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Test: android/app/src/test/java/com/tteumsae/app/domain/saved/SavedPlaceTest.kt

**Interfaces:**
- Produces: SavedPlace(place, savedAtMillis), latestFirst()
- Produces: event-only SavedPlacesScreen and SettingsScreen
- Consumes: existing copy and callbacks

- [ ] **Step 1: Write and run the failing sorting test**

~~~kotlin
@Test fun saved_places_are_latest_first() {
    val old = SavedPlace(place("old"), 10)
    val recent = SavedPlace(place("recent"), 20)
    assertEquals(listOf("recent", "old"), listOf(old, recent).latestFirst().map { it.place.id })
}
~~~

~~~powershell
.\gradlew.bat testDebugUnitTest --tests "*SavedPlaceTest"
~~~

- [ ] **Step 2: Implement the model**

~~~kotlin
data class SavedPlace(val place: PlaceCandidate, val savedAtMillis: Long)
fun List<SavedPlace>.latestFirst(): List<SavedPlace> = sortedByDescending { it.savedAtMillis }
~~~

- [ ] **Step 3: Move leaf components, then screens**

Move saved image/filter/card and settings title/group/row/divider first. Move screen functions only after leaf components compile. Extracted screens receive state and callbacks only; they do not access Context storage or network.

- [ ] **Step 4: Replace SavedPlaceEntry with SavedPlace and compile**

~~~powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest lintDebug
~~~

- [ ] **Step 5: Commit**

~~~powershell
git add android/app/src/main/java/com/tteumsae/app/domain/saved android/app/src/main/java/com/tteumsae/app/ui/saved android/app/src/main/java/com/tteumsae/app/ui/settings android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/test
git commit -m "refactor: 저장과 설정 화면 분리"
~~~

### Task 4: Add Room schema, DAO, and snapshot codec

**Files:**
- Modify: android/build.gradle.kts
- Modify: android/app/build.gradle.kts
- Create: android/app/src/main/java/com/tteumsae/app/data/local/SavedPlaceEntity.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/local/SavedPlaceDao.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/local/TteumsaeDatabase.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/local/SavedPlaceSnapshotCodec.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/local/SavedPlaceSnapshotCodecTest.kt
- Test: android/app/src/androidTest/java/com/tteumsae/app/data/local/SavedPlaceDaoTest.kt

**Interfaces:**
- Produces: GUEST_SCOPE, SavedPlaceSyncState, SavedPlaceSnapshotState, SavedPlaceEntity, SavedPlaceDao
- Produces: SavedPlaceSnapshotCodec.encode/decode

- [ ] **Step 1: Add stable Room dependencies**

~~~kotlin
// root plugins
id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false

// app
id("org.jetbrains.kotlin.kapt")
implementation("androidx.room:room-runtime:2.8.4")
implementation("androidx.room:room-ktx:2.8.4")
kapt("androidx.room:room-compiler:2.8.4")
androidTestImplementation("androidx.room:room-testing:2.8.4")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
~~~

Export schemas to android/app/schemas.

- [ ] **Step 2: Write codec round-trip tests**

Assert durable public place fields round-trip. Assert firstLegMinutes, secondLegMinutes, and detourMinutes decode as zero because recommendation-route metrics are not permanent place data.

- [ ] **Step 3: Implement the final-shape entity**

~~~kotlin
enum class SavedPlaceSyncState { SYNCED, DIRTY, FAILED_AUTH }
enum class SavedPlaceSnapshotState { READY, MISSING, UNAVAILABLE }
const val GUEST_SCOPE = "GUEST"

@Entity(
    tableName = "saved_places",
    primaryKeys = ["owner_scope", "place_id"],
    indices = [Index("owner_scope"), Index(value = ["owner_scope", "sync_state"])],
)
data class SavedPlaceEntity(
    @ColumnInfo(name = "owner_scope") val ownerScope: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "place_snapshot_json") val placeSnapshotJson: String,
    @ColumnInfo(name = "snapshot_state") val snapshotState: String,
    @ColumnInfo(name = "desired_saved") val desiredSaved: Boolean,
    @ColumnInfo(name = "saved_at_millis") val savedAtMillis: Long,
    @ColumnInfo(name = "local_revision") val localRevision: Long,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: String?,
    @ColumnInfo(name = "sync_state") val syncState: String,
)
~~~

- [ ] **Step 4: Implement required DAO methods**

~~~kotlin
@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places WHERE owner_scope = :scope AND desired_saved = 1 ORDER BY saved_at_millis DESC")
    fun observeSaved(scope: String): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places WHERE owner_scope = :scope AND place_id = :placeId LIMIT 1")
    suspend fun find(scope: String, placeId: String): SavedPlaceEntity?

    @Query("SELECT COALESCE(MAX(local_revision), 0) + 1 FROM saved_places WHERE owner_scope = :scope")
    suspend fun nextRevision(scope: String): Long

    @Upsert suspend fun upsert(entity: SavedPlaceEntity)

    @Query("UPDATE saved_places SET desired_saved = 0, local_revision = :revision WHERE owner_scope = :scope")
    suspend fun markAllUnsaved(scope: String, revision: Long)

    @Query("DELETE FROM saved_places WHERE owner_scope = :scope")
    suspend fun deleteScope(scope: String)

    @Query("SELECT * FROM saved_places WHERE owner_scope = :scope AND sync_state = 'DIRTY' ORDER BY local_revision")
    suspend fun dirty(scope: String): List<SavedPlaceEntity>
}
~~~

- [ ] **Step 5: Test DAO behavior**

Use an in-memory DB and verify owner isolation, latest ordering, tombstone hiding, revision increments, and scope deletion.

- [ ] **Step 6: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
git add android/build.gradle.kts android/app/build.gradle.kts android/app/schemas android/app/src/main/java/com/tteumsae/app/data/local android/app/src/test android/app/src/androidTest
git commit -m "feat: 저장 장소 Room 스키마 추가"
~~~

### Task 5: Migrate legacy preferences and add guest repository

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/local/SavedPlacePreferencesMigration.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/saved/SavedPlacesRepository.kt
- Create: android/app/src/main/java/com/tteumsae/app/AppContainer.kt
- Modify: android/app/src/main/java/com/tteumsae/app/TteumsaeApplication.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/local/SavedPlacePreferencesMigrationTest.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/saved/SavedPlacesRepositoryTest.kt

**Interfaces:**
- Produces: MigrationResult and migrateIfNeeded()
- Produces: observeSaved, toggleGuest, restoreGuest, clearGuest
- Consumes: Task 4 DB, DAO, codec

- [ ] **Step 1: Write migration tests**

Cover empty source, valid rows, mixed valid/corrupt rows, DAO failure, and repeated execution. Assert entries is removed only after DB success.

~~~kotlin
sealed interface MigrationResult {
    data object AlreadyComplete : MigrationResult
    data class Migrated(val count: Int, val skipped: Int) : MigrationResult
    data class Failed(val cause: Throwable) : MigrationResult
}
~~~

- [ ] **Step 2: Implement transaction-first migration**

Read exact names saved_places and entries. Insert valid rows under GUEST. Set saved_places_room_migration/complete and remove the old JSON only after transaction success.

Every successfully decoded legacy row uses snapshot_state=READY. No migration path may emit MISSING for a place whose legacy card JSON was decoded.

- [ ] **Step 3: Write repository tests**

~~~kotlin
@Test fun guest_toggle_creates_then_tombstones() = runTest {
    repository.toggleGuest(place, 100)
    assertTrue(dao.find(GUEST_SCOPE, place.id)!!.desiredSaved)
    repository.toggleGuest(place, 200)
    assertFalse(dao.find(GUEST_SCOPE, place.id)!!.desiredSaved)
}
~~~

Also test restore timestamp, clear, and SYNCED guest state.

- [ ] **Step 4: Implement the repository contract**

~~~kotlin
interface SavedPlacesRepository {
    fun observeSaved(scope: String = GUEST_SCOPE): Flow<List<SavedPlace>>
    suspend fun toggleGuest(place: PlaceCandidate, nowMillis: Long)
    suspend fun restoreGuest(savedPlace: SavedPlace)
    suspend fun clearGuest()
}
~~~

Use withTransaction for read-revision-write operations.

- [ ] **Step 5: Build one AppContainer**

TteumsaeApplication owns one DB and repository. Run migration on Dispatchers.IO. Do not construct data services in Composables.

- [ ] **Step 6: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
git add android/app/src/main/java/com/tteumsae/app android/app/src/test
git commit -m "feat: 기존 저장 장소를 Room으로 이전"
~~~

### Task 6: Integrate Room Flow and remove legacy storage

**Files:**
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/saved/SavedPlacesScreen.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt
- Modify: android/app/build.gradle.kts
- Modify: docs/02_ARCHITECTURE.md
- Modify: docs/03_FEATURE_MATRIX.md
- Modify: docs/04_SCREEN_FLOWS.md
- Modify: docs/08_QA_AND_KNOWN_ISSUES.md

**Interfaces:**
- Consumes: SavedPlacesRepository
- Produces: current guest UI backed only by Room Flow

- [ ] **Step 1: Add lifecycle collection and observe Room**

~~~kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
~~~

~~~kotlin
val savedPlaces by repository.observeSaved()
    .collectAsStateWithLifecycle(initialValue = emptyList())
~~~

Route toggle, restore, and clear callbacks to repository suspend methods.

- [ ] **Step 2: Remove legacy constants and functions**

Delete SAVED_PLACES_PREFERENCES, SAVED_PLACES_KEY, SavedPlaceEntry, loadSavedPlaces, and storeSavedPlaces only after compilation succeeds.

- [ ] **Step 3: Add settings copy policy**

~~~kotlin
fun guestSavedStorageDescription(count: Int): String =
    "이 기기에 " + count + "개 저장됨"
~~~

Test zero and positive counts, then use it in SettingsScreen.

- [ ] **Step 4: Update docs**

Record Room as local-only for this phase and SharedPreferences as simple device settings only.

- [ ] **Step 5: Run verification**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
rg -n "loadSavedPlaces|storeSavedPlaces|SAVED_PLACES_PREFERENCES" app/src/main/java
git diff --check
~~~

Expected: builds pass and rg has no matches.

- [ ] **Step 6: Perform upgrade-install QA**

Install over a current build with two saved places. Verify migration, order, save, unsave, Snackbar restore, clear, force-stop/relaunch, recommendation detail, and Kakao Map launch. Record results in docs/08_QA_AND_KNOWN_ISSUES.md.

- [ ] **Step 7: Commit**

~~~powershell
git add android/app docs/02_ARCHITECTURE.md docs/03_FEATURE_MATRIX.md docs/04_SCREEN_FLOWS.md docs/08_QA_AND_KNOWN_ISSUES.md
git commit -m "refactor: 저장 장소를 로컬 저장소 계층으로 전환"
~~~

## Plan Completion Gate

- [ ] TteumsaeApp.kt no longer defines saved/settings screens, legacy saved JSON, external actions, or moved pure policies.
- [ ] Guest behavior works without Supabase configuration.
- [ ] Existing JSON migrates once and is not cleared before Room success.
- [ ] Room contains owner scope and sync metadata for the later sync plan.
- [ ] Unit, lint, assemble, and connected Room tests pass.
- [ ] Upgrade-install evidence is recorded.
