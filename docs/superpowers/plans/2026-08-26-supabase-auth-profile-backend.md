# Supabase Auth, Profile, and Account Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오·Google 선택형 로그인, 본인만 접근 가능한 프로필, Vercel 계정 삭제 API, 공개 개인정보·삭제 요청 페이지를 추가하되 게스트 기능을 항상 유지한다.

**Architecture:** Android는 Supabase Auth와 PostgREST에 직접 연결하고 Vercel은 기존 공개 API와 관리자 계정 삭제만 담당한다. Auth SDK 호환을 위해 도구체인을 독립 커밋으로 올린 뒤, DB/RLS와 서버를 먼저 검증하고 Android 어댑터·ViewModel·설정 UI를 연결한다.

**Tech Stack:** AGP 8.13.2, Gradle 8.13, Kotlin 2.3.20, Java 17, Android API 26–36 (target 35), Supabase Kotlin BOM 3.5.0, Ktor Android 3.0.3, kotlinx serialization, Node.js 24.x, Vercel Functions, Supabase Auth/Postgres

**Spec:** docs/superpowers/specs/2026-08-26-account-sync-user-data-design.md

## Global Constraints

- Prerequisite: `docs/superpowers/plans/2026-08-26-account-foundation-local-saved.md`와 `docs/superpowers/plans/2026-08-26-deadline-aware-route-core.md`가 모두 완료되어야 한다. 이 계획의 Task 1부터 Kotlin 2.3.20/AGP 8.13.2로 올린다. Supabase Kotlin 3.5.0의 Kotlin 2.3 메타데이터와 AndroidX Browser 1.9.0 요구사항 때문에 compileSdk 36을 사용하되 targetSdk 35와 minSdk 26은 유지한다.
- 로그인은 선택 기능이며 Auth 설정 누락·장애가 추천·경로·게스트 저장을 막지 않는다.
- Android에는 Supabase URL과 publishable key만 포함한다.
- service role은 Vercel 환경변수에만 둔다.
- 이메일을 필수 사용자 식별자나 profiles 열로 사용하지 않는다.
- 연령대·성별은 nullable이고 추천 요청·점수에 사용하지 않는다.
- 다른 사용자 행을 읽거나 쓰는 RLS 테스트 없이 배포하지 않는다.
- 계정 삭제 API는 body의 user ID를 신뢰하지 않는다.
- 프로필·토큰·이메일을 로그에 남기지 않는다.
- 공개 정책 페이지는 JavaScript 없이 본문을 읽을 수 있어야 한다.

---

## File Map

| 파일 | 책임 |
|---|---|
| backend/migrations/003_user_accounts.sql | profiles, saved tombstone, trigger, grants, RLS |
| backend/lib/supabase-auth.js | bearer 검증·관리자 삭제 |
| backend/api/account.js | DELETE account 계약 |
| backend/privacy.html | 공개 개인정보처리방침 |
| backend/account-deletion.html | 외부 이메일 삭제 요청 |
| data/auth/SupabaseClientProvider.kt | Auth/PostgREST 클라이언트 |
| data/auth/AuthRepository.kt | 세션·OAuth·로그아웃 |
| data/profile/ProfileRepository.kt | 본인 프로필 조회·insert/update |
| data/account/AccountDeletionApi.kt | Vercel DELETE 호출 |
| domain/account/* | 세션·프로필·선택값 |
| ui/account/* | 로그인·프로필·삭제 UI와 ViewModel |
| ui/settings/SettingsScreen.kt | 계정 섹션 호스트 |

### Task 1: Upgrade the Android toolchain and add Supabase dependencies

**Files:**
- Modify: android/gradle/wrapper/gradle-wrapper.properties
- Modify: android/build.gradle.kts
- Modify: android/app/build.gradle.kts
- Modify: android/app/proguard-rules.pro
- Modify: android/local.properties.example
- Test: existing Android test suite

**Interfaces:**
- Produces: BuildConfig.SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, AUTH_ENABLED
- Produces: Auth and Postgrest modules available
- Consumes: current Java 17 and API 26 minimum

- [x] **Step 1: Establish the pre-upgrade baseline**

~~~powershell
cd C:\app\tteumsae\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
~~~

Expected: BUILD SUCCESSFUL. Save the task output in the work log.

- [x] **Step 2: Upgrade versions in one isolated change**

Use Gradle 8.13, AGP 8.13.2, Kotlin Android/Compose/serialization/kapt 2.3.20. Use compileSdk 36, targetSdk 35, minSdk 26, Java 17.

~~~kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.20" apply false
}
~~~

- [x] **Step 3: Run the same baseline before adding Supabase**

~~~powershell
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
~~~

Expected: Gradle 8.13 and BUILD SUCCESSFUL. Fix only toolchain compatibility errors in this step.

- [x] **Step 4: Add Supabase modules and safe BuildConfig inputs**

~~~kotlin
id("org.jetbrains.kotlin.plugin.serialization")

implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.ktor:ktor-client-android:3.0.3")
~~~

Read SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY from local.properties with blank defaults. Define AUTH_ENABLED as true only when both are nonblank. Add names with empty values to local.properties.example; never add real values.

- [x] **Step 5: Verify release shrinking and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
git diff --check
git add android/gradle/wrapper android/build.gradle.kts android/app/build.gradle.kts android/app/proguard-rules.pro android/local.properties.example
git commit -m "build: Supabase 인증 도구체인 준비"
~~~

### Task 2: Add user tables, least-privilege grants, and RLS

**Files:**
- Create: backend/migrations/003_user_accounts.sql
- Create: backend/tests/user-migration.test.js
- Create: backend/scripts/verify-user-rls.js
- Modify: backend/scripts/check-project.js
- Modify: backend/README.md
- Modify: docs/05_API_AND_DATA.md

**Interfaces:**
- Produces: public.profiles and public.user_saved_places
- Produces: authenticated-only select/insert/update policies
- Produces: environment-gated two-user RLS verifier
- Consumes: auth.users and public.places(content_id)

- [x] **Step 1: Write a failing migration contract test**

~~~javascript
test("사용자 테이블은 RLS와 최소 권한을 선언한다", async () => {
  const sql = await readFile(new URL("../migrations/003_user_accounts.sql", import.meta.url), "utf8");
  assert.match(sql, /create table public\.profiles/i);
  assert.match(sql, /create table public\.user_saved_places/i);
  assert.match(sql, /enable row level security/gi);
  assert.match(sql, /auth\.uid\(\) is not null/i);
  assert.doesNotMatch(sql, /grant .* to anon/i);
  assert.match(sql, /on delete cascade/i);
});
~~~

- [x] **Step 2: Run and confirm failure**

~~~powershell
cd C:\app\tteumsae\backend
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
~~~

- [x] **Step 3: Implement the schema**

The migration must create:
- profiles with user_id PK/FK, display_name max 40, avatar_url max 2048, checked age_group and gender, server timestamps;
- user_saved_places with composite PK, place FK, is_saved, nullable saved_at, server updated_at;
- one private set_updated_at trigger function with empty search_path and fully qualified names;
- explicit revoke from anon/authenticated followed by column-level authenticated grants;
- separate select, insert, update policies using auth.uid() is not null and auth.uid() = user_id;
- no authenticated physical delete grant.

Use this concrete structure; keep each policy separate rather than replacing it with one `for all` policy:

~~~sql
create schema if not exists private;

create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text check (
    display_name is null or char_length(btrim(display_name)) between 1 and 40
  ),
  avatar_url text check (avatar_url is null or char_length(avatar_url) <= 2048),
  age_group text check (age_group is null or age_group in (
    'UNDER_20','TWENTIES','THIRTIES','FORTIES',
    'FIFTIES','SIXTY_PLUS','PREFER_NOT_TO_SAY'
  )),
  gender text check (gender is null or gender in (
    'FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY'
  )),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.user_saved_places (
  user_id uuid not null references auth.users(id) on delete cascade,
  place_id text not null references public.places(content_id) on delete cascade,
  is_saved boolean not null,
  saved_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (user_id, place_id)
);

create trigger profiles_set_updated_at before update on public.profiles
for each row execute function private.set_updated_at();
create trigger saved_places_set_updated_at before update on public.user_saved_places
for each row execute function private.set_updated_at();

alter table public.profiles enable row level security;
alter table public.user_saved_places enable row level security;
revoke all on public.profiles, public.user_saved_places from anon, authenticated;
grant select on public.profiles, public.user_saved_places to authenticated;
grant insert (user_id, display_name, avatar_url, age_group, gender)
  on public.profiles to authenticated;
grant update (display_name, avatar_url, age_group, gender)
  on public.profiles to authenticated;
grant insert (user_id, place_id, is_saved, saved_at)
  on public.user_saved_places to authenticated;
grant update (is_saved, saved_at)
  on public.user_saved_places to authenticated;
grant all on public.profiles, public.user_saved_places to service_role;

create policy profiles_select_own on public.profiles for select to authenticated
using (auth.uid() is not null and auth.uid() = user_id);
create policy profiles_insert_own on public.profiles for insert to authenticated
with check (auth.uid() is not null and auth.uid() = user_id);
create policy profiles_update_own on public.profiles for update to authenticated
using (auth.uid() is not null and auth.uid() = user_id)
with check (auth.uid() is not null and auth.uid() = user_id);

create policy saved_select_own on public.user_saved_places for select to authenticated
using (auth.uid() is not null and auth.uid() = user_id);
create policy saved_insert_own on public.user_saved_places for insert to authenticated
with check (auth.uid() is not null and auth.uid() = user_id);
create policy saved_update_own on public.user_saved_places for update to authenticated
using (auth.uid() is not null and auth.uid() = user_id)
with check (auth.uid() is not null and auth.uid() = user_id);
~~~

Use these allowed values exactly:

~~~sql
age_group in (
  'UNDER_20','TWENTIES','THIRTIES','FORTIES',
  'FIFTIES','SIXTY_PLUS','PREFER_NOT_TO_SAY'
)
gender in ('FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY')
~~~

- [x] **Step 4: Add the live RLS verifier**

verify-user-rls.js runs only when SUPABASE_TEST_URL, SUPABASE_TEST_PUBLISHABLE_KEY, and SUPABASE_TEST_SERVICE_ROLE_KEY exist. It creates two temporary Auth users with random emails, signs each in, verifies own CRUD, verifies cross-user select/update returns no row or 403, deletes both admin users in finally, and exits nonzero on any leak.

- [x] **Step 5: Run static tests and, on the new test project, live RLS tests** *(2026-08-27: 프로젝트 `ysainvblgtewlpsygfyr`에 001–003 적용, 44 tests + project check + two-user RLS PASS)*

~~~powershell
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
node scripts/check-project.js
node scripts/verify-user-rls.js
~~~

Expected: all pass. The live script must print SKIPPED with exact missing variable names when not configured; production rollout requires an actual PASS.

- [x] **Step 6: Commit**

~~~powershell
git add backend/migrations/003_user_accounts.sql backend/tests/user-migration.test.js backend/scripts backend/README.md docs/05_API_AND_DATA.md
git commit -m "feat: 사용자 프로필과 저장 RLS 추가"
~~~

### Task 3: Implement the authenticated Vercel account deletion endpoint

**Files:**
- Create: backend/lib/supabase-auth.js
- Create: backend/api/account.js
- Create: backend/tests/account.test.js
- Modify: backend/lib/http.js
- Modify: backend/.env.example
- Modify: backend/scripts/check-project.js
- Modify: docs/05_API_AND_DATA.md

**Interfaces:**
- Produces: readBearerToken(request): string
- Produces: verifySupabaseUser(token, fetchImpl): Promise<{id:string}>
- Produces: deleteSupabaseUser(userId, fetchImpl): Promise<void>
- Produces: DELETE /api/account → 204
- Consumes: SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, SUPABASE_SERVICE_ROLE_KEY

- [x] **Step 1: Write failing endpoint tests**

Cover method 405, no bearer 401, invalid token 401, valid token plus admin delete 204, admin failure 500, and a malicious JSON user_id that is never used.

~~~javascript
test("검증된 토큰 사용자만 삭제한다", async () => {
  const calls = [];
  global.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith("/auth/v1/user")) return Response.json({ id: "verified-user" });
    return new Response(null, { status: 204 });
  };
  const response = await handler.fetch(new Request("https://test/api/account", {
    method: "DELETE",
    headers: { authorization: "Bearer valid-token" }
  }));
  assert.equal(response.status, 204);
  assert.match(calls[1].url, /verified-user$/);
});
~~~

- [x] **Step 2: Run and confirm failure**

~~~powershell
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
~~~

- [x] **Step 3: Implement token verification and hard deletion**

GET SUPABASE_URL/auth/v1/user with project publishable apikey and user bearer token. Use only the returned id. DELETE SUPABASE_URL/auth/v1/admin/users/{verified id} with service-role apikey and bearer. Do not log headers or response bodies containing user data.

- [x] **Step 4: Add 204 support and rate limit**

Add an emptyResponse(status) helper to lib/http.js instead of sending JSON for 204. Apply a small per-IP best-effort limit such as 3/minute; authentication remains the real authorization boundary.

- [x] **Step 5: Verify and commit**

~~~powershell
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
node scripts/check-project.js
git add backend/api/account.js backend/lib backend/tests/account.test.js backend/.env.example backend/scripts/check-project.js docs/05_API_AND_DATA.md
git commit -m "feat: 인증된 계정 삭제 API 추가"
~~~

### Task 4: Add public privacy and deletion-request pages

**Files:**
- Create: backend/privacy.html
- Create: backend/account-deletion.html
- Modify: backend/vercel.json
- Modify: backend/scripts/check-project.js
- Create: backend/tests/policy-pages.test.js
- Modify: docs/06_ENVIRONMENT_AND_ACCESS.md

**Interfaces:**
- Produces: GET /privacy and GET /account-deletion
- Consumes: final operator name and support email before release

- [x] **Step 1: Write failing static-page tests**

Read both files and assert UTF-8 HTML, tteumsae/틈새 name, privacy contact, collected data categories, deletion/retention section, in-app deletion path, and a mailto deletion request. Assert vercel rewrites expose clean paths.

- [x] **Step 2: Implement semantic no-JS HTML**

Use headings, lists, visible effective date, responsive viewport, and normal anchor links. Do not use PDF, login wall, geofencing, remote fonts, trackers, or JavaScript-only content.

- [x] **Step 3: Add clean rewrites**

~~~json
{ "source": "/privacy", "destination": "/privacy.html" }
{ "source": "/account-deletion", "destination": "/account-deletion.html" }
~~~

Preserve current APK rewrite and cron entries.

- [x] **Step 4: Verify locally and after preview deployment** *(2026-08-27: 로컬 44 tests + project check, Production `/privacy`·`/account-deletion` HTTP 200 확인)*

~~~powershell
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
node scripts/check-project.js
~~~

Open both preview URLs in a private browser window with JavaScript disabled. Confirm no authentication and HTTP 200.

- [ ] **Step 5: Commit**

~~~powershell
git add backend/privacy.html backend/account-deletion.html backend/vercel.json backend/tests/policy-pages.test.js backend/scripts/check-project.js docs/06_ENVIRONMENT_AND_ACCESS.md
git commit -m "feat: 개인정보와 계정 삭제 안내 페이지 추가"
~~~

### Task 5: Add the Supabase client and testable auth repository

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/domain/account/AccountSession.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/auth/SupabaseClientProvider.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/auth/AuthGateway.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/auth/AuthRepository.kt
- Modify: android/app/src/main/java/com/tteumsae/app/AppContainer.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/auth/AuthRepositoryTest.kt

**Interfaces:**
- Produces: AccountSession.Guest/Restoring/SignedIn/NeedsReauthentication/AuthUnavailable
- Produces: AuthRepository.sessions, signIn(provider), signOut(), accessToken()
- Consumes: Supabase Auth SDK only through AuthGateway

- [x] **Step 1: Write repository state tests with a fake gateway**

Test initial restoring, no stored session → guest, authenticated event → signed in, refresh network failure → unavailable without clearing guest app state, explicit sign-out → guest, and login cancellation → guest.

- [x] **Step 2: Implement domain states**

~~~kotlin
sealed interface AccountSession {
    data object Guest : AccountSession
    data object Restoring : AccountSession
    data class SignedIn(val userId: String, val provider: LoginProvider) : AccountSession
    data object NeedsReauthentication : AccountSession
    data class AuthUnavailable(val message: String) : AccountSession
}
enum class LoginProvider { KAKAO, GOOGLE }
~~~

- [x] **Step 3: Build the Supabase client only when configured**

Install Auth with PKCE, scheme tteumsae, host auth-callback, and Postgrest. When AUTH_ENABLED is false, AppContainer supplies DisabledAuthGateway and the rest of the app remains operational.

- [x] **Step 4: Implement the repository and adapter**

Map SDK sessionStatus/events into domain states. Keep SDK types out of UI and domain packages. Never log tokens.

- [x] **Step 5: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
git add android/app/src/main/java/com/tteumsae/app/domain/account android/app/src/main/java/com/tteumsae/app/data/auth android/app/src/main/java/com/tteumsae/app/AppContainer.kt android/app/src/test
git commit -m "feat: 선택형 로그인 세션 계층 추가"
~~~

### Task 6: Wire PKCE deep links for Kakao and Google

**Files:**
- Modify: android/app/src/main/AndroidManifest.xml
- Modify: android/app/src/main/java/com/tteumsae/app/MainActivity.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/auth/AuthDeepLinkHandler.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/auth/AuthDeepLinkHandlerTest.kt
- Modify: docs/06_ENVIRONMENT_AND_ACCESS.md

**Interfaces:**
- Produces: handle(intent): DeepLinkResult
- Consumes: Supabase client from Task 5

- [x] **Step 1: Write URI acceptance tests**

Accept only scheme tteumsae and host auth-callback. Reject missing code, wrong host, and wrong scheme before forwarding to the SDK adapter.

- [x] **Step 2: Add the exported callback intent filter**

Keep MainActivity as the launcher. Add VIEW, DEFAULT, BROWSABLE and exact scheme/host. Do not add broad path wildcards.

- [x] **Step 3: Handle cold and warm intents**

Call the handler in onCreate with the initial intent and override onNewIntent to forward the new intent. Send SDK verification errors to AuthRepository as a user-safe login failure.

- [x] **Step 4: Configure provider consoles** *(2026-08-27: Kakao 앱·Supabase provider·`tteumsae://auth-callback` 연결 완료. Google Cloud `tteumsae-auth` 프로젝트와 Web OAuth Client 생성, Supabase callback·provider 연결, 승인 도메인·브랜딩 저장, 외부 사용자 대상 프로덕션 게시 완료. Supabase settings API에서 Google·Kakao 활성화 및 Google authorize의 `accounts.google.com` 리다이렉트 확인)*

Register the Supabase callback required by Kakao/Google and tteumsae://auth-callback in Supabase redirect allowlist. Store provider secrets only in provider/Supabase dashboards.

- [ ] **Step 5: Verify and commit** *(54 unit tests + lintDebug + assembleDebug 완료, 연결된 Android 기기가 없어 adb 콜백 스모크 테스트 대기)*

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb shell am start -a android.intent.action.VIEW -d "tteumsae://auth-callback?code=invalid" com.tteumsae.app
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/tteumsae/app/MainActivity.kt android/app/src/main/java/com/tteumsae/app/data/auth android/app/src/test docs/06_ENVIRONMENT_AND_ACCESS.md
git commit -m "feat: 모바일 OAuth 콜백 연결"
~~~

Expected: invalid code shows a recoverable account-area error and does not crash or leave the app.

### Task 7: Add private profile data access

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/domain/account/UserProfile.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/profile/ProfileDto.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/profile/ProfileRemoteDataSource.kt
- Create: android/app/src/main/java/com/tteumsae/app/data/profile/ProfileRepository.kt
- Test: android/app/src/test/java/com/tteumsae/app/data/profile/ProfileRepositoryTest.kt

**Interfaces:**
- Produces: AgeGroup and Gender enums matching SQL values
- Produces: loadOrCreate(user): UserProfile, update(profile): UserProfile
- Consumes: authenticated Postgrest client and OAuth metadata adapter

- [x] **Step 1: Write validation and creation tests**

Cover missing row creation, provider nickname/avatar mapping, no email duplication, blank nickname → null, nickname 41 chars rejected, optional age/gender, and explicit prefer-not-to-say.

- [x] **Step 2: Implement domain model**

~~~kotlin
enum class AgeGroup {
    UNDER_20, TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTY_PLUS, PREFER_NOT_TO_SAY
}
enum class Gender { FEMALE, MALE, OTHER, PREFER_NOT_TO_SAY }

data class UserProfile(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val ageGroup: AgeGroup?,
    val gender: Gender?,
)
~~~

- [x] **Step 3: Implement RLS-scoped PostgREST access**

Select profiles filtered by current user_id. If absent, insert current user ID and provider metadata. If a concurrent first-login insert returns unique conflict, select again instead of broadening key-column update grants. Existing-row updates send only display_name, avatar_url, age_group, gender. Do not send user_id, created_at, or updated_at in an update.

- [x] **Step 4: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest compileDebugKotlin
git add android/app/src/main/java/com/tteumsae/app/domain/account android/app/src/main/java/com/tteumsae/app/data/profile android/app/src/test
git commit -m "feat: 선택형 사용자 프로필 추가"
~~~

### Task 8: Add account deletion client, ViewModel, and settings UI

**Files:**
- Create: android/app/src/main/java/com/tteumsae/app/data/account/AccountDeletionApi.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/account/AccountViewModel.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/account/LoginSheet.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/account/ProfileEditScreen.kt
- Create: android/app/src/main/java/com/tteumsae/app/ui/account/AccountComponents.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/navigation/AppDestination.kt
- Modify: android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt
- Test: android/app/src/test/java/com/tteumsae/app/ui/account/AccountViewModelTest.kt

**Interfaces:**
- Produces: deleteCurrentAccount(accessToken): DeleteAccountResult
- Produces: AccountUiState and AccountEvent
- Consumes: AuthRepository, ProfileRepository, existing AppContainer

- [x] **Step 1: Write ViewModel tests**

Test guest login CTA, login cancellation, signed-in profile load, nullable demographic save, sign-out, delete requiring confirmation and re-login, server failure preserving local session, and server success returning guest.

- [x] **Step 2: Implement the deletion HTTP client**

Use DELETE BuildConfig.API_BASE_URL/api/account with Authorization bearer. Return Success for 204, NeedsLogin for 401, Retryable for 429/5xx, and Failed for other responses. Do not include user ID or JSON body.

- [x] **Step 3: Implement AccountViewModel**

Use one immutable AccountUiState. The ViewModel owns auth/profile/loading/error states, but not saved places or route state. Trigger provider login through AuthRepository and profile load only after SignedIn.

- [x] **Step 4: Build account UI**

Guest settings show login benefit, Kakao, Google, and non-required copy. Signed-in settings show avatar/name/provider, profile management, and logout. Profile screen edits only nickname, age group, and gender; show that optional demographics are not used in current recommendations.

- [x] **Step 5: Add deletion confirmation and re-login**

Use two explicit confirmation states. After fresh provider login, call AccountDeletionApi. At this phase clear profile/session and return guest; the saved-sync plan extends cleanup to user scopes, ActiveTrip, alarms, and geofences.

- [x] **Step 6: Verify and commit**

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
git add android/app/src/main/java/com/tteumsae/app/data/account android/app/src/main/java/com/tteumsae/app/ui/account android/app/src/main/java/com/tteumsae/app/ui/settings android/app/src/main/java/com/tteumsae/app/ui/navigation android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt android/app/src/test
git commit -m "feat: 계정과 프로필 관리 화면 추가"
~~~

### Task 9: Complete auth/backend QA and documentation

**Files:**
- Modify: docs/02_ARCHITECTURE.md
- Modify: docs/03_FEATURE_MATRIX.md
- Modify: docs/04_SCREEN_FLOWS.md
- Modify: docs/05_API_AND_DATA.md
- Modify: docs/06_ENVIRONMENT_AND_ACCESS.md
- Modify: docs/07_BUILD_TEST_DEPLOY.md
- Modify: docs/08_QA_AND_KNOWN_ISSUES.md
- Modify: docs/10_DECISION_LOG.md
- Modify: backend/README.md

**Interfaces:**
- Consumes: Tasks 1–8
- Produces: deployable auth/profile/delete slice and runbook

- [x] **Step 1: Update source-of-truth docs**

Document guest-first behavior, provider setup, exact env names, deep link, profiles schema, age/gender non-use, deletion API, policy URLs, and failure fallback.

- [x] **Step 2: Run automated verification**

~~~powershell
cd C:\app\tteumsae\backend
$tests=(Get-ChildItem tests -Filter "*.test.js").FullName
node --test $tests
node scripts/check-project.js
cd ..\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
git diff --check
~~~

- [ ] **Step 3: Run live service checks** *(2026-08-27: Supabase migration/RLS PASS, Vercel health·places·Kakao Mobility route·정책 페이지·무토큰 삭제 401 확인. TourAPI Production 키 반영 후 기본 783건·상세 10건 동기화 성공. `Newrred/tteumsae` Git 자동 배포 연결 및 `agent/new-route-flow-ui` Production Branch 지정 완료. Google·Kakao provider 활성화와 Google authorize 리다이렉트 확인. 서명된 Android 기기의 로그인·삭제 E2E만 대기)*

- Apply migrations 001–003 to the new Supabase project.
- Run verify-user-rls.js and require PASS.
- Deploy Vercel preview and check health, existing route/recommendation, DELETE with invalid token, privacy, and deletion pages.
- Test Kakao and Google login on a release-signed Android build.
- Test a Kakao account without exposed email.
- Test logout and account deletion/re-registration.

- [x] **Step 4: Record QA evidence and commit**

~~~powershell
git add docs backend/README.md
git commit -m "docs: 계정 인증과 삭제 운영 절차 정리"
~~~

## Plan Completion Gate

- [x] Toolchain upgrade passes debug/release tests.
- [x] Live RLS verifier proves two-user isolation.
- [ ] Kakao and Google PKCE login work on a signed device.
- [x] Profile remains optional and demographics never enter recommendation requests.
- [ ] Account deletion uses verified token identity and hard-deletes Auth/profile rows.
- [x] Guest recommendation, route, and local saved behavior remain available when Auth is disabled.
- [x] Privacy and deletion pages return public HTTPS 200 without JavaScript.
- [x] Policy, Data Safety, provider, Supabase, and Vercel operational inputs are documented.
