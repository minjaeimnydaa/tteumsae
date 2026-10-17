# Gate 0 Release Truth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공개 출시 전에 현재 제공하지 않는 기능 약속을 제거하고, API 36·안전한 화면 복원·자동 검증 기준선을 만든다.

**Architecture:** 제품 문구와 정책은 테스트 가능한 상수·정적 페이지 계약으로 고정한다. 현재 대형 Compose 구조는 유지하되 프로세스 복원 시 payload 없는 RESULTS/DETAIL을 순수 navigation policy로 차단한다. Backend와 Android CI는 독립 job으로 실행해 한쪽 실패가 다른쪽 검증 로그를 가리지 않게 한다.

**Tech Stack:** Kotlin 2.3.20, Jetpack Compose, JUnit 4, Android API 26–36, Node.js 24.x, node:test, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md`

## Global Constraints

- 현재 브랜치의 `output/`, `tmp/`와 사용자 로컬 설정을 추적하거나 수정하지 않는다.
- 장소 저장은 현재 Room `GUEST` 범위이며 계정 간 동기화를 약속하지 않는다.
- `targetSdk=36`, `compileSdk=36`, `minSdk=26`, Java 17을 사용한다.
- `TteumsaeApp.kt`를 전면 재작성하지 않는다.
- 신규 화면 복원 정책은 현재 레거시 흐름과 이후 `RouteFlowViewModel` 전환 모두에서 재사용 가능한 순수 함수로 둔다.
- 실제 키는 CI나 저장소에 추가하지 않는다. 빈 Kakao·Supabase 설정으로 unit/lint가 통과해야 한다.
- 각 Task는 실패 확인 → 최소 구현 → 전체 관련 검증 → 문서 갱신 → 독립 커밋 순서로 끝낸다.

---

### Task 1: Make account and policy copy match local-only saved places

**Files:**
- Create: `android/app/src/main/java/com/tteumsae/app/ui/account/AccountCopy.kt`
- Create: `android/app/src/test/java/com/tteumsae/app/ui/account/AccountCopyTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/account/AccountComponents.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/account/LoginSheet.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/platform/ExternalSettings.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/platform/ExternalSettingsTest.kt`
- Modify: `backend/privacy.html`
- Modify: `backend/account-deletion.html`
- Modify: `backend/tests/policy-pages.test.js`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`

**Interfaces:**
- Consumes: existing optional Kakao/Google account flow, Room `GUEST` saved-place behavior, `BuildConfig.API_BASE_URL`.
- Produces: `GUEST_ACCOUNT_DESCRIPTION`, `LOGIN_SHEET_TITLE`, `LOGIN_SHEET_DESCRIPTION`, `ACCOUNT_DELETION_IMPACT`, and a non-empty `PRIVACY_POLICY_URL` derived from the deployed backend base URL.

- [x] **Step 1: Write failing Android copy tests**

```kotlin
package com.tteumsae.app.ui.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCopyTest {
    @Test
    fun `account copy only promises profile management and device local saves`() {
        val copy = listOf(
            GUEST_ACCOUNT_DESCRIPTION,
            LOGIN_SHEET_TITLE,
            LOGIN_SHEET_DESCRIPTION,
            ACCOUNT_DELETION_IMPACT,
        ).joinToString(" ")

        assertFalse(copy.contains("여러 기기"))
        assertFalse(copy.contains("동기화"))
        assertFalse(copy.contains("저장을 이어"))
        assertTrue(LOGIN_SHEET_DESCRIPTION.contains("이 기기"))
        assertTrue(ACCOUNT_DELETION_IMPACT.contains("기기에 저장된 장소는 유지"))
    }
}
```

- [x] **Step 2: Extend policy and URL tests so the current code fails**

Add to `ExternalSettingsTest.kt`:

```kotlin
import com.tteumsae.app.BuildConfig

@Test
fun `개인정보처리방침은 운영 백엔드 공개 주소를 사용한다`() {
    assertEquals("${BuildConfig.API_BASE_URL}/privacy", PRIVACY_POLICY_URL)
}
```

Add assertions to `backend/tests/policy-pages.test.js`:

```js
// 개인정보처리방침 테스트의 html 변수
assert.match(html, /저장한 장소는 이 기기에만 저장/);
assert.doesNotMatch(html, /저장한 장소 동기화/);

// 계정 삭제 페이지 테스트의 html 변수
assert.match(html, /기기에만 저장한 장소는 계정 삭제로 삭제되지/);
```

- [x] **Step 3: Run focused tests and verify failure**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.ui.account.AccountCopyTest" --tests "com.tteumsae.app.platform.ExternalSettingsTest"
cd ..\backend
node --test tests/policy-pages.test.js
```

Expected: Android fails because the copy constants do not exist; Backend fails because the current policy promises saved-place synchronization.

- [x] **Step 4: Add copy constants and use them from Compose**

Create `AccountCopy.kt`:

```kotlin
package com.tteumsae.app.ui.account

internal const val GUEST_ACCOUNT_DESCRIPTION =
    "로그인은 선택사항이에요. 로그인하면 프로필을 설정하고 계정을 관리할 수 있어요."
internal const val LOGIN_SHEET_TITLE = "로그인하고 프로필을 설정하세요"
internal const val LOGIN_SHEET_DESCRIPTION =
    "카카오 또는 Google 계정으로 프로필을 관리할 수 있어요. 로그인하지 않아도 장소 추천과 이 기기 저장은 그대로 사용할 수 있습니다."
internal const val ACCOUNT_DELETION_IMPACT =
    "프로필과 틈새 계정 정보가 삭제되며 복구할 수 없습니다. 기기에 저장된 장소는 유지됩니다."
```

Replace the four inline strings in `AccountComponents.kt` and `LoginSheet.kt` with these constants.

- [x] **Step 5: Connect the existing privacy page and correct current policy claims**

Change `ExternalSettings.kt` to:

```kotlin
internal val PRIVACY_POLICY_URL = "${BuildConfig.API_BASE_URL}/privacy"
```

Keep `LOCATION_TERMS_URL` blank until a separate public document exists. Rewrite the privacy and deletion page saved-place paragraphs to say that current saved places remain on the device and are not synchronized to the account. Keep profile, auth provider, Vercel log and Supabase processor disclosures.

- [x] **Step 6: Run focused and full tests**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
cd ..\backend
node scripts/run-tests.js
node scripts/check-project.js
```

Expected: Android 73 tests pass after adding `AccountCopyTest` and the privacy URL assertion; Backend policy tests and full suite pass.

- [x] **Step 7: Update feature and Gate 0 status docs**

Record that account copy no longer promises remote saved-place sync and that the privacy URL is connected. Do not mark remote sync as implemented.

- [x] **Step 8: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/ui/account android/app/src/test/java/com/tteumsae/app/ui/account android/app/src/main/java/com/tteumsae/app/platform/ExternalSettings.kt android/app/src/test/java/com/tteumsae/app/platform/ExternalSettingsTest.kt backend/privacy.html backend/account-deletion.html backend/tests/policy-pages.test.js docs/03_FEATURE_MATRIX.md docs/09_NEXT_VERSION_PLAN.md
git commit -m "fix: 계정과 저장 기능 안내를 실제 동작에 맞춤"
```

### Task 2: Raise the Android target SDK to API 36

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `README.md`
- Modify: `android/README.md`
- Modify: `docs/00_START_HERE.md`
- Modify: `docs/06_ENVIRONMENT_AND_ACCESS.md`
- Modify: `docs/07_BUILD_TEST_DEPLOY.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`

**Interfaces:**
- Consumes: current compileSdk 36 and minSdk 26.
- Produces: targetSdk 36 builds without adding permissions or changing runtime behavior intentionally.

- [x] **Step 1: Verify the precondition fails the API 36 gate**

Run:

```powershell
$text = Get-Content -Raw android\app\build.gradle.kts
if ($text -notmatch 'targetSdk\s*=\s*36') { throw 'targetSdk 36 gate failed as expected' }
```

Expected: command throws because current targetSdk is 35.

- [x] **Step 2: Change only the target SDK**

```kotlin
defaultConfig {
    applicationId = "com.tteumsae.app"
    minSdk = 26
    targetSdk = 36
}
```

Do not update unrelated dependencies in this task.

- [x] **Step 3: Run Android verification**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: all unit tests pass, lint reports zero errors, and `app-debug.apk` is created.

- [x] **Step 4: Update current-state documentation**

Replace current `targetSdk=35` facts and Play-blocker warnings with target 36 completion. Retain release signing, AAB and real-device regression as unfinished.

- [x] **Step 5: Commit**

```powershell
git add android/app/build.gradle.kts README.md android/README.md docs/00_START_HERE.md docs/06_ENVIRONMENT_AND_ACCESS.md docs/07_BUILD_TEST_DEPLOY.md docs/08_QA_AND_KNOWN_ISSUES.md docs/09_NEXT_VERSION_PLAN.md
git commit -m "build: Android target SDK를 36으로 상향"
```

### Task 3: Prevent payload-less route screens after restoration

**Files:**
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/navigation/AppDestination.kt`
- Modify: `android/app/src/test/java/com/tteumsae/app/ui/navigation/AppDestinationTest.kt`
- Modify: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`
- Modify: `docs/03_FEATURE_MATRIX.md`
- Modify: `docs/04_SCREEN_FLOWS.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`

**Interfaces:**
- Consumes: `AppDestination`, current in-memory start/end locations, `activeCriteria`, `baseRoute`, and selected recommendation.
- Produces: `safeRestoredDestination(current, hasLocations, hasResults, hasDetail)` and a root effect that redirects invalid restored states.

- [x] **Step 1: Write failing policy tests**

Add to `AppDestinationTest.kt`:

```kotlin
@Test
fun payload_less_route_destinations_fall_back_safely() {
    assertEquals(AppDestination.LOCATION, safeRestoredDestination(AppDestination.CONDITIONS, false, false, false))
    assertEquals(AppDestination.LOCATION, safeRestoredDestination(AppDestination.LOADING, false, false, false))
    assertEquals(AppDestination.LOCATION, safeRestoredDestination(AppDestination.RESULTS, false, false, false))
    assertEquals(AppDestination.CONDITIONS, safeRestoredDestination(AppDestination.RESULTS, true, false, false))
    assertEquals(AppDestination.RESULTS, safeRestoredDestination(AppDestination.DETAIL, true, true, false))
}

@Test
fun complete_route_payload_keeps_the_requested_destination() {
    assertEquals(AppDestination.RESULTS, safeRestoredDestination(AppDestination.RESULTS, true, true, false))
    assertEquals(AppDestination.DETAIL, safeRestoredDestination(AppDestination.DETAIL, true, true, true))
}
```

- [x] **Step 2: Run the focused test and verify failure**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.ui.navigation.AppDestinationTest"
```

Expected: compilation fails because `safeRestoredDestination` does not exist.

- [x] **Step 3: Implement the pure restoration policy**

Add to `AppDestination.kt`:

```kotlin
fun safeRestoredDestination(
    current: AppDestination,
    hasLocations: Boolean,
    hasResults: Boolean,
    hasDetail: Boolean,
): AppDestination = when (current) {
    AppDestination.CONDITIONS,
    AppDestination.LOADING,
    -> if (hasLocations) current else AppDestination.LOCATION

    AppDestination.RESULTS -> when {
        hasResults -> AppDestination.RESULTS
        hasLocations -> AppDestination.CONDITIONS
        else -> AppDestination.LOCATION
    }

    AppDestination.DETAIL -> when {
        hasResults && hasDetail -> AppDestination.DETAIL
        hasResults -> AppDestination.RESULTS
        hasLocations -> AppDestination.CONDITIONS
        else -> AppDestination.LOCATION
    }

    else -> current
}
```

- [x] **Step 4: Apply the policy at the root Compose state boundary**

After the route state declarations in `TteumsaeApp.kt`, derive:

```kotlin
val hasRouteLocations = startLocation != null &&
    (mode == SearchMode.NEARBY || endLocation != null)
val safeScreen = safeRestoredDestination(
    current = screen,
    hasLocations = hasRouteLocations,
    hasResults = activeCriteria != null && baseRoute != null,
    hasDetail = selected != null,
)
LaunchedEffect(safeScreen) {
    if (screen != safeScreen) screen = safeScreen
}
```

Import the policy function. Keep the later `RouteFlowViewModel + SavedStateHandle` migration in Gate 2; this task only prevents broken restored screens.

- [x] **Step 5: Run focused and full Android verification**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.tteumsae.app.ui.navigation.AppDestinationTest"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: focused policy tests and the full Android suite pass with zero lint errors.

- [x] **Step 6: Update current-state and QA docs**

Document the safe fallback while retaining the known limitation that route payload itself is not yet restored across process death.

- [x] **Step 7: Commit**

```powershell
git add android/app/src/main/java/com/tteumsae/app/ui/navigation/AppDestination.kt android/app/src/test/java/com/tteumsae/app/ui/navigation/AppDestinationTest.kt android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt docs/03_FEATURE_MATRIX.md docs/04_SCREEN_FLOWS.md docs/08_QA_AND_KNOWN_ISSUES.md docs/09_NEXT_VERSION_PLAN.md
git commit -m "fix: 복원 데이터 없는 경로 화면을 안전하게 되돌림"
```

### Task 4: Add repository CI for Backend and Android

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify file mode: `android/gradlew` to executable
- Modify: `docs/07_BUILD_TEST_DEPLOY.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: `docs/09_NEXT_VERSION_PLAN.md`

**Interfaces:**
- Consumes: `backend/pnpm-lock.yaml`, Node 24 package engine, Gradle wrapper 8.13, JDK 17.
- Produces: `backend` and `android` CI jobs on pushes and pull requests without production secrets.

- [x] **Step 1: Verify CI is absent**

Run:

```powershell
if (-not (Test-Path .github\workflows\ci.yml)) { throw 'CI file missing as expected' }
```

Expected: command throws.

- [x] **Step 2: Create the workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
  pull_request:

permissions:
  contents: read

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 24
      - run: corepack enable
      - run: pnpm install --frozen-lockfile
      - run: pnpm test
      - run: pnpm run check

  android:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle
      - uses: android-actions/setup-android@v3
      - run: sdkmanager "platforms;android-36"
      - run: chmod +x gradlew
      - run: ./gradlew testDebugUnitTest lintDebug
```

- [x] **Step 3: Preserve executable mode for Unix checkouts**

Run:

```powershell
git update-index --chmod=+x android/gradlew
```

- [x] **Step 4: Run the workflow commands locally**

Run:

```powershell
cd backend
node scripts/run-tests.js
node scripts/check-project.js
cd ..\android
.\gradlew.bat testDebugUnitTest lintDebug
```

Expected: Backend tests/check and Android unit/lint pass without production secrets.

- [x] **Step 5: Validate workflow invariants**

Run:

```powershell
$workflow = Get-Content -Raw .github\workflows\ci.yml
@('actions/checkout@v4', 'node-version: 24', 'pnpm install --frozen-lockfile', './gradlew testDebugUnitTest lintDebug') | ForEach-Object {
    if (-not $workflow.Contains($_)) { throw "Missing CI invariant: $_" }
}
```

- [x] **Step 6: Update build, QA and Gate 0 docs**

Document the two CI jobs and keep real-device, signed release and production promotion as separate manual gates.

- [x] **Step 7: Commit**

```powershell
git add .github/workflows/ci.yml android/gradlew docs/07_BUILD_TEST_DEPLOY.md docs/08_QA_AND_KNOWN_ISSUES.md docs/09_NEXT_VERSION_PLAN.md
git commit -m "ci: Android와 Backend 검증 워크플로 추가"
```

### Task 5: Close the locally verifiable Gate 0 checkpoint

**Files:**
- Modify: `docs/09_NEXT_VERSION_PLAN.md`
- Modify: `docs/08_QA_AND_KNOWN_ISSUES.md`
- Modify: this plan's task checkboxes as execution evidence

**Interfaces:**
- Consumes: Tasks 1–4 commits and verification output.
- Produces: an honest Gate 0 status that separates completed local work from external release and DB recovery gates.

- [x] **Step 1: Run final verification from a clean index**

Run:

```powershell
cd backend
node scripts/run-tests.js
node scripts/check-project.js
cd ..\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
cd ..
git diff --check
git status --short
```

Expected: all automatic checks pass; only deliberate documentation status edits and pre-existing untracked `output/`, `tmp/` remain.

- [x] **Step 2: Record completed and external Gate 0 items accurately**

Mark copy, privacy URL, targetSdk 36, safe restoration and CI complete. Leave these unchecked:

- signed release AAB and real-device OAuth regression
- protected main/Preview smoke/explicit production promotion in GitHub and Vercel UI
- empty Supabase 001–004 recovery rehearsal

- [x] **Step 3: Commit checkpoint evidence**

```powershell
git add docs/09_NEXT_VERSION_PLAN.md docs/08_QA_AND_KNOWN_ISSUES.md docs/superpowers/plans/2026-08-28-gate-0-release-truth.md
git commit -m "docs: Gate 0 로컬 검증 결과 기록"
```

- [x] **Step 4: Stop for review before Gate 1 or Gate 2**

Report exact test counts, lint result, APK path, commits and remaining external gates. Do not start the absolute-deadline Backend contract until this checkpoint is reviewed.

## Execution record — 2026-08-28

- Account/privacy truth: `263aa8b`
- Android API 36 target: `0971a3c`
- Safe restored destination: `104b6d8`
- Backend/Android CI: `4f4d198`
- Deterministic pnpm 11.19.0 and `esbuild` allowlist: `a5ce2c4`
- Backend: frozen install 성공, tests 69/69, project check 56 files
- Android: tests 75/75, lint errors 0, `assembleDebug` 성공
- Local checkpoint only: GitHub Actions 최초 원격 실행, 서명 AAB·실기기 OAuth,
  Preview/Production 승격, 빈 Supabase migration 001–004 복구 리허설은 미완료
