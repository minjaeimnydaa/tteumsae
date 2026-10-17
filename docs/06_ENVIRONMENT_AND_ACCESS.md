# 환경설정 및 서비스 접근권한 인수인계

작성 기준: 2026-08-16 통합 소스

이 문서는 새 담당자가 비밀값을 Git에 넣지 않고 Android, Vercel,
Supabase와 Kakao 연동을 복구하도록 안내한다. 이 문서에는 환경변수 **이름만**
기록한다. 실제 키·비밀번호·복구 코드는 조직의 비밀관리 도구 또는 일회성 보안
채널로 전달한다.

## 1. 환경별 구성 요약

| 실행 위치 | 설정 파일/관리 화면 | 필요한 설정 |
|---|---|---|
| Android 로컬 빌드 | `android/local.properties` | SDK 경로, Kakao 네이티브 앱 키, 선택 로그인용 Supabase 공개 설정 |
| Android 앱 코드 | `android/app/build.gradle.kts` | 백엔드 기준 URL, 앱 버전 |
| 백엔드 로컬 | `backend/.env.local` | Vercel 환경변수의 로컬 사본 |
| 백엔드 운영 | Vercel Project Settings | TourAPI, Kakao REST, Supabase, Cron 설정 |
| 데이터베이스 | Supabase SQL Editor/CLI | 마이그레이션 001~009 순차 적용 |
| Kakao Android | Kakao Developers 플랫폼 설정 | 패키지명과 빌드 서명 키 해시 |

## 2. 절대 커밋하지 않는 항목

루트 [`.gitignore`](../.gitignore)가 다음을 제외한다.

```text
**/local.properties
**/.env*
!**/.env.example
**/.vercel/
**/*.jks
**/*.keystore
**/*.apk
**/*.aab
**/recovery-codes.txt
```

커밋 전에는 반드시 다음을 실행한다.

```powershell
git status --short
git diff --cached
```

실제 키처럼 보이는 문자열이 없는지도 백엔드 검사로 확인한다.

```powershell
cd backend
pnpm run check
```

이 검사는 64자리 16진수 인증키 형태를 탐지하지만 모든 종류의 비밀값을 찾는
완전한 보안 스캐너는 아니다. `.env`, 키스토어, 복구 코드와 콘솔 스크린샷은
사람이 추가로 확인해야 한다.

과거 대화·스크린샷에 실제 TourAPI 키가 공유된 이력이 있으므로, 인수인계 전에
기존 키의 노출 범위를 점검하고 가능하면 재발급·교체한다. 기존 값을 이 문서나
이슈에 다시 적지 않는다.

## 3. Android 로컬 설정

### 3.1 `local.properties`

[`android/local.properties.example`](../android/local.properties.example)을 복사한다.

```powershell
cd android
Copy-Item local.properties.example local.properties
```

새 파일에 다음 이름을 설정한다.

```properties
sdk.dir=새 컴퓨터의 Android SDK 절대 경로
KAKAO_MAP_NATIVE_APP_KEY=Kakao Developers 네이티브 앱 키
SUPABASE_URL=Supabase Project URL
SUPABASE_PUBLISHABLE_KEY=Supabase publishable key
```

주의:

- `KAKAO_MAP_NATIVE_APP_KEY`는 Kakao REST API 키가 아니다.
- 네이티브 앱 키는 `BuildConfig.KAKAO_MAP_NATIVE_APP_KEY`로 APK에 포함되는
  클라이언트 식별자다. 서버 비밀키처럼 취급하지는 않지만 Git에는 넣지 않는다.
- 값이 비어 있으면 앱이 빌드되더라도 지도 영역에 앱 키 설정 필요 안내가 나온다.
- 서버용 `KAKAO_REST_API_KEY`를 이 파일에 넣으면 안 된다.
- `SUPABASE_URL`과 `SUPABASE_PUBLISHABLE_KEY` 중 하나라도 비어 있으면 로그인만
  비활성화되고 지도·추천·게스트 저장 기능은 계속 동작한다.
- Android에는 publishable key만 넣는다. RLS를 우회하는
  `SUPABASE_SERVICE_ROLE_KEY`는 절대 넣지 않는다.

현재 Android 식별값:

| 항목 | 값/위치 |
|---|---|
| 패키지·applicationId | `com.tteumsae.app` |
| minSdk | 26 |
| compileSdk / targetSdk | 36 / 36 |
| API 기준 URL | `android/app/build.gradle.kts`의 `BuildConfig.API_BASE_URL` |
| 현재 API 기준 URL | `https://tteumsae-backend-one.vercel.app` |

Preview 백엔드를 쓰려면 Git에 임시 URL을 커밋하지 말고 로컬 브랜치 또는 별도
build type의 `API_BASE_URL`로 분리한다.

### 3.2 Kakao Android 플랫폼 등록

Kakao Developers에서 틈새 앱의 Android 플랫폼에 다음을 등록해야 실제 지도가
표시된다.

```text
패키지명: com.tteumsae.app
키 해시: 해당 APK를 서명한 인증서의 Kakao 키 해시
```

새 컴퓨터의 기본 디버그 키스토어는 기존 컴퓨터와 다를 수 있다. 두 방법 중
하나를 선택한다.

1. 기존 `%USERPROFILE%\.android\debug.keystore`를 비밀관리 도구로 안전하게
   전달해 팀 테스트 APK의 서명을 동일하게 유지한다.
2. 새 디버그 키스토어를 사용하고 그 키 해시를 Kakao Developers에 추가한다.

기본 디버그 키스토어 정보는 Android 도구의 관례상 alias `androiddebugkey`,
store/key password `android`다. Git Bash와 OpenSSL이 있다면 다음으로 Kakao용
키 해시를 얻을 수 있다.

```bash
keytool -exportcert \
  -alias androiddebugkey \
  -keystore "$HOME/.android/debug.keystore" \
  -storepass android \
  -keypass android \
  | openssl sha1 -binary \
  | openssl base64
```

확인 절차:

1. Kakao Developers 앱의 Android 플랫폼에 패키지명과 키 해시를 저장한다.
2. `local.properties`에 같은 Kakao 앱의 네이티브 앱 키를 넣는다.
3. `clean assembleDebug`로 다시 빌드한다.
4. 실기기에 설치해 홈 지도가 실제 타일과 POI를 표시하는지 확인한다.

Play Store용 릴리스는 디버그 인증서와 서명이 다르다. 출시 전 다음 키 해시를
각각 등록한다.

- 로컬 업로드 키 또는 직접 서명 릴리스 키
- Google Play App Signing이 실제 배포 APK를 서명하는 앱 서명 키

키스토어 파일, alias, 비밀번호는 Git이 아닌 비밀관리 도구에 보관한다. 현재
저장소에는 릴리스 `signingConfig`와 Play App Signing 절차가 아직 없다.

## 4. 백엔드 환경변수

기준 템플릿은 [`backend/.env.example`](../backend/.env.example)이다.

### 4.1 필수 변수

| 이름 | 비밀 여부 | 사용처 | 누락 시 영향 |
|---|---:|---|---|
| `TOUR_API_SERVICE_KEY` | 예 | TourAPI 기본·상세 동기화 | 두 Cron 실패 |
| `TOUR_CONGESTION_API_SERVICE_KEY` | 예 | 관광지 집중률 별도 활용신청 키 | 수동 혼잡 stage만 실패, 기존 기능 정상 |
| `PUBLIC_PARKING_API_SERVICE_KEY` | 예 | 전국주차장정보표준데이터 별도 활용신청 키 | 수동 주차장 stage만 실패, 기존 기능 정상 |
| `KMA_SHORT_FORECAST_SERVICE_KEY` | 예 | 기상청 단기예보 별도 활용신청 키 | 활성 날씨만 생략, 기존 상세 정상 |
| `KAKAO_REST_API_KEY` | 예 | Kakao Local 검색·지역·도보 경로, Kakao Mobility | 위치 검색·지역·차량 추천 실패, 활성 도보는 예상값 전환 |
| `SUPABASE_URL` | 아니오에 가까움 | PostgREST 기준 주소 | 장소·추천·동기화 실패 |
| `SUPABASE_SERVICE_ROLE_KEY` | 매우 중요 | 서버 전용 DB 전체 접근 | 장소·추천·동기화 실패 |
| `CRON_SECRET` | 예 | 예약·수동 동기화 Bearer 검증 | 예약/수동 동기화 인증 불가 |

`SUPABASE_SERVICE_ROLE_KEY`는 RLS를 우회한다. Android, 프런트엔드, 공개 로그,
문서, 이슈에 절대 노출하지 않는다.

공공데이터포털에서 같은 프로젝트 서비스 키로 여러 API를 활용신청했다면 위 세 공급자
변수에 같은 값을 등록할 수 있다. 키 갱신 뒤 일반 TourAPI는 성공하고 특정 API만 403이면
환경변수를 다시 복사하기보다 해당 활용신청의 개발계정 승인·활용 상태와 반영 지연을 먼저
확인한다. 2026-09-08 무장애 API는 활용신청 직후 403이었다가 승인 반영 뒤 같은 키로 200이
됐으므로, 일반 TourAPI 성공 여부와 전용 API 권한을 분리해 진단한 사례로 남긴다.

### 4.2 선택 조정 변수

| 이름 | 기본값 | 코드상 상한 | 의미 |
|---|---:|---:|---|
| `TOUR_SYNC_MAX_PAGES` | 10 | 25 | 기본 동기화 한 실행의 최대 페이지 |
| `TOUR_CONGESTION_DAILY_BUDGET` | 100 | 1000 | 집중률 API KST 일일 hard stop |
| `TOUR_CONGESTION_PAGE_LIMIT` | 20 | 20 | 한 번의 수동 혼잡 동기화 최대 페이지 |
| `KMA_WEATHER_ENABLED` | false | `true`만 활성 | 확인된 야외 장소의 상세 날씨 사용 여부 |
| `KMA_WEATHER_DAILY_BUDGET` | 1000 | 10000 | 단기예보 KST 일일 hard stop |
| `KAKAO_ROUTE_CANDIDATE_LIMIT` | 8 | 8 | 차량·활성 도보 경로를 실제 계산할 후보 수 |
| `KAKAO_WALK_ROUTE_ENABLED` | false | `true`만 활성 | 카카오 실제 도보 경로 사용 여부 |
| `KAKAO_WALK_DAILY_WARNING` | 700 | budget 이하 | 도보 경로 KST 일일 운영 경고선 |
| `KAKAO_WALK_DAILY_BUDGET` | 800 | 1000 | 도보 경로 외부 호출 전 hard stop |
| `TOUR_INTRO_SYNC_BATCH_SIZE` | 20 | 40 | intro 동기화 한 실행의 장소 수 |
| `TOUR_SYNC_CONCURRENCY` | 4 | 4 | intro 병렬 요청 수 |
| `KAKAO_MOBILITY_DAILY_WARNING` | 7000 | budget 이하 | KST 일일 운영 경고선 |
| `KAKAO_MOBILITY_DAILY_BUDGET` | 8000 | 10000 | 외부 호출 전 hard stop |

빈 값, 숫자가 아니거나 0 이하이면 기본값을 사용한다. 한도를 늘리려면 환경변수만
바꾸면 안 되고 코드의 `Math.min` 상한, Vercel 실행시간, 외부 API 쿼터와 비용을
함께 검토해야 한다.

### 4.3 Vercel 환경 범위

운영 프로젝트의 Production에 최소 필수 다섯 변수를 모두 설정한다. Preview와
Development를 사용할 때는 운영 DB·Cron을 무심코 공유하지 않도록 별도 값을
사용하거나 필요한 변수만 제한적으로 설정한다.

```powershell
cd backend
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
```

연결 대상은 Vercel 팀 `newrreds-projects`의 `tteumsae-backend` 프로젝트다. `.vercel/`과
`.env.local`은 Git에 포함하지 않는다. 환경변수를 바꾼 뒤에는 새 Production
배포가 필요하다.

값을 출력하지 않고 이름만 확인하는 방법:

```powershell
pnpm dlx vercel env ls
```

운영 `/api/health`는 세 묶음의 설정 존재 여부를 확인한다. 실제 권한·쿼터·DB
연결까지 확인하는 요청은 아니므로 스모크 테스트를 별도로 수행한다.

## 5. Supabase 설정과 권한

새 Supabase 프로젝트로 복구할 때 SQL Editor 또는 관리되는 마이그레이션 도구로
아래 순서를 지킨다.

1. [`backend/migrations/001_initial.sql`](../backend/migrations/001_initial.sql)
2. [`backend/migrations/002_detail_sync_state.sql`](../backend/migrations/002_detail_sync_state.sql)
3. [`backend/migrations/003_user_accounts.sql`](../backend/migrations/003_user_accounts.sql)
4. [`backend/migrations/004_tour_enrichment.sql`](../backend/migrations/004_tour_enrichment.sql)
5. [`backend/migrations/005_sync_runtime_safety.sql`](../backend/migrations/005_sync_runtime_safety.sql)
6. [`backend/migrations/006_gate_1b_data_trust.sql`](../backend/migrations/006_gate_1b_data_trust.sql)

그다음 새 프로젝트의 URL과 service role 키를 Vercel 환경변수에 설정하고
카탈로그·소개/운영정보 Cron을 각각 스모크 테스트한다. Hobby 환경에서는 같은 시간대의
분 단위 실행 순서에 의존하지 않는다.

확인할 테이블:

```text
public.places
public.sync_state
public.profiles
public.user_saved_places
public.provider_usage_daily
public.place_curations
public.effective_places
```

모든 테이블은 RLS가 활성화되어 있어야 한다. 장소 데이터는 Android나 익명
사용자를 위한 공개 정책을 만들지 않고 Vercel API를 통해서만 읽는다. 로그인
이용자의 `profiles`, `user_saved_places`는 Supabase Auth의 본인 행만 직접
조회·변경할 수 있다. 새 프로젝트에서는 다음으로 실제 RLS 격리를 검증한다.

```powershell
cd backend
node scripts/verify-user-rls.js
```

검증에는 서로 다른 테스트 이용자 두 명의 액세스 토큰이 필요하다. 서비스 역할
키나 토큰의 실제 값은 콘솔·문서에 남기지 않는다.

## 5.1 공개 정책 페이지

백엔드를 Vercel에 배포하면 다음 주소가 공개된다.

```text
https://운영-도메인/privacy
https://운영-도메인/account-deletion
```

두 페이지는 JavaScript나 외부 폰트 없이 동작하며, Google Play Console의
개인정보처리방침 URL과 계정 삭제 URL에 각각 등록한다. 운영자 또는 문의 메일이
바뀌면 두 HTML과 앱·스토어 연락처를 함께 수정한다.

## 5.2 카카오·Google 로그인 콜백 설정

Android 앱은 PKCE 로그인 완료 주소로 다음 exact 딥링크만 받는다.

```text
tteumsae://auth-callback
```

새 Supabase 프로젝트를 만든 뒤 다음 순서로 설정한다.

1. Supabase Dashboard의 Authentication → URL Configuration → Redirect URLs에
   `tteumsae://auth-callback`을 추가한다.
2. Supabase Authentication → Sign In / Providers에서 Kakao와 Google을 켠다.
3. 각 Provider 화면에 표시되는 Supabase 콜백 주소
   `https://<project-ref>.supabase.co/auth/v1/callback`을 복사한다.
4. Kakao Developers의 Kakao Login Redirect URI와 Google Auth Platform의 Web
   OAuth Client Authorized redirect URI에 위 Supabase 콜백 주소를 각각 등록한다.
5. Kakao REST API key와 Kakao Login Client Secret, Google Web Client ID와 Client
   Secret은 Supabase Provider 설정 화면에만 저장한다.
6. Kakao 이메일 동의를 필수로 받지 않을 계획이므로 Supabase Kakao Provider의
   **Allow users without an email**을 켠다. 프로필 닉네임·이미지 동의 항목은
   서비스 화면에 실제 사용하는 범위로 설정한다.

Provider secret, Supabase service role key, OAuth code와 사용자 access token은
`local.properties`, Android BuildConfig, Git 또는 로그에 넣지 않는다. Android에는
Project URL과 publishable key만 설정한다.

## 5.3 Gate 1-B 적용 순서

운영 반영은 다음 순서를 바꾸지 않는다.

1. `places`와 `sync_state`를 한 번의 JSON/CSV 결과로 백업한다.
2. 005가 적용됐는지 `sync_state.last_started_at`과 `claim_sync_job` 존재 여부를 확인한다.
3. 누락됐다면 005를 먼저 적용하고 006을 적용한다.
4. anon/authenticated가 새 table/view/RPC에 접근하지 못하는지 확인한다.
5. Vercel Production에 7,000/8,000 예산값을 등록하고 새 배포를 만든다.
6. `node scripts/validate-place-curations.mjs`가 100/100을 통과한 파일만 import한다.
7. ops 집계의 `dataQuality.curation.reviewed=100`을 확인한 후 승격한다.

Preview에 운영 service role key를 복제하지 않는다. 운영 비밀값을 써야 하는 후보 검증은
`vercel deploy --prod --skip-domain`으로 도메인에 연결되지 않은 Production-target
배포를 만든 뒤 점검하고, 통과한 동일 배포만 `vercel promote`한다.

## 6. 서비스 접근권한 전달표

비밀번호 공유 대신 각 서비스의 팀/협업자 초대 기능을 사용한다. 새 담당자가
아래 검증을 완료한 뒤 이전 담당자의 권한을 줄이거나 제거한다.

| 서비스 | 현재 대상 | 새 담당자 최소 권한 | 별도 전달 항목 | 인수 확인 |
|---|---|---|---|---|
| GitHub | `Newrred/tteumsae` (원본 `minjaeimnydaa/tteumsae` 포크) | 저장소 Write 이상 | 보호 브랜치·PR 규칙 | clone, branch push 가능 |
| Vercel 백엔드 | 팀 `newrreds-projects` / `tteumsae-backend` | 배포·환경변수 확인 가능 역할 | 팀 초대 | 프로젝트 연결, 배포·로그 조회 |
| Vercel APK 페이지 | 팀 `jaturi`의 APK 다운로드 프로젝트 | 배포 가능 역할 | 정확한 프로젝트 선택 | Preview 후 Production 배포 |
| Supabase | `tteumsae` (`ysainvblgtewlpsygfyr`) | Developer 이상, 비밀은 필요 시에만 | 프로젝트 초대 | 테이블·로그·SQL 접근 확인 |
| Kakao Developers | 틈새 Kakao 앱 | 앱 편집 가능한 팀원 | 앱 팀 초대 | 네이티브/REST 키 종류, 플랫폼 확인 |
| Kakao Mobility | 같은 Kakao 앱의 길찾기 제품 | 제품 설정·쿼터 확인 | 제품 활성화 상태 | 서버 REST 키로 테스트 경로 성공 |
| 공공데이터포털 | 한국관광공사 국문 관광정보 서비스 | 활용신청 관리 가능 계정 | 계정 소유권 또는 새 키 | 승인·만료일·쿼터 확인 |
| Google Auth Platform | `tteumsae-auth` (`proven-splicer-506804-q1`) | OAuth 설정 편집 권한 | 프로젝트 초대 | Web Client·Supabase callback 확인 |
| Google Play Console | `com.tteumsae.app` 앱 | 릴리스에 필요한 최소 역할 | 조직 초대 | 테스트 트랙·앱 서명 접근 확인 |
| 문의 메일 | 현재 개인 Gmail | 메일 수신·응답 담당 | 향후 회사 주소 | 실제 메일 앱 연결 확인 |

현재 문의 주소는
[`ExternalSettings.kt`](../android/app/src/main/java/com/tteumsae/app/platform/ExternalSettings.kt)의
`CONTACT_EMAIL` 상수에 직접 들어 있다. 회사 메일을 준비하면 코드와 스토어
연락처를 함께 바꾼다.

## 7. 서비스별 인수 체크리스트

### 7.1 GitHub

- [ ] 기본 브랜치와 원격 주소 확인
- [ ] 최신 통합 소스가 push되어 있는지 확인
- [ ] Actions/Dependabot/브랜치 보호 규칙 확인
- [ ] 이슈·PR·릴리스 담당자 지정
- [ ] Git 기록에 비밀값이 없는지 확인

### 7.2 Vercel

- [ ] 백엔드와 APK 다운로드 프로젝트를 혼동하지 않음
- [ ] Production 도메인 확인
- [ ] 환경변수 이름과 대상 환경 확인
- [ ] Cron 두 개와 최근 실행 로그 확인
- [ ] Functions의 최대 실행시간 60초 확인
- [ ] 환경변수 변경 뒤 재배포

### 7.3 Kakao

- [ ] 네이티브 앱 키와 REST API 키를 구분
- [ ] Android 패키지명 확인
- [ ] 디버그·릴리스·Play 앱 서명 키 해시 등록
- [ ] Kakao Map Android SDK 지도 표시 확인
- [ ] Kakao Local 키워드·지역 API 확인
- [ ] Kakao Mobility Directions 제품·쿼터 확인

### 7.4 TourAPI

- [ ] `KorService2` 활용신청 승인 상태 확인
- [ ] 키 만료일·일일 트래픽 확인
- [ ] 기본 `areaBasedList2` 호출 확인
- [ ] `detailIntro2`, `detailImage2`, `detailPetTour2` 호출 확인
- [ ] 강원도 `areaCode=32` 데이터 수 확인

### 7.5 Google Play

- [ ] 앱 생성 및 패키지명 소유 상태 확인
- [ ] Play App Signing과 업로드 키 보관 책임자 지정
- [ ] 내부/비공개 테스트 트랙 접근 확인
- [ ] 개인정보처리방침, 위치 고지, 데이터 보안 양식 담당자 지정

### 7.6 Google Auth Platform

- [ ] 운영 계정 `godburgundy@gmail.com`의 복구·2단계 인증 확인
- [x] `tteumsae-auth` Web OAuth Client와 Supabase callback 확인
- [x] Supabase Google provider의 Client ID·Secret 저장 상태 확인
- [x] 외부 사용자 대상 프로덕션 게시 상태 확인
- [ ] 서명된 Android 기기에서 Google PKCE 로그인·앱 복귀 확인

## 8. 키 교체 순서

키 노출이 의심되면 이전 키를 먼저 문서에서 찾으려 하지 말고 서비스에서 새 키를
만든다.

1. 새 키 발급 또는 재생성
2. Vercel/로컬 보안 저장소에 새 값 등록
3. Production 재배포
4. API 스모크 테스트
5. 이전 키 폐기
6. Vercel·외부 API 로그에서 비정상 사용 점검

Kakao 네이티브 앱 키 자체를 교체하면 Android를 다시 빌드·배포해야 한다.
TourAPI, Kakao REST, Supabase service role, Cron 키는 서버 환경변수 교체와
백엔드 재배포로 처리한다.

## 9. 현재 미완료 설정

- 개인정보처리방침과 계정 삭제 페이지는 Vercel 운영 도메인에 배포됐다.
  Google Play Console 등록과 앱 설정 화면의 정책 URL 연결은 후속 구현 대상이다.
- Google Auth Platform과 Supabase provider는 프로덕션 연결됐지만, 서명된 실제
  Android 기기에서 Google 로그인·딥링크 앱 복귀를 아직 확인하지 않았다.
- 릴리스 키스토어와 Gradle `signingConfig`가 없다.
- Play App Signing용 키 해시 등록 완료 여부를 저장소만으로 확인할 수 없다.
- APK 다운로드 프로젝트의 `.vercel` 연결 정보는 보안·로컬 파일이라 Git에 없다.
- 백엔드 공개 API 사용자 인증은 없고, 추천·경로의 IP별 제한은 Vercel 인스턴스
  메모리 기반 best-effort라 공유 저장소 수준의 남용 방어가 남아 있다.
- 운영 서비스의 실제 소유자·복구 담당·2단계 인증 상태는 Git으로 확인할 수
  없으므로 인수 회의에서 별도 확인해야 한다.
