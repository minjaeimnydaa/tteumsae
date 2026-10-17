# 빌드·테스트·배포 운영서

작성 기준: 2026-08-22 통합 소스

이 문서는 새 Windows 개발 환경에서 저장소를 검증하고 Android, 백엔드, APK
다운로드 페이지를 배포하는 순서를 정의한다. 명령은 저장소 루트를
`C:\dev\tteumsae`로 가정한다.

## 1. 현재 버전과 산출물 상태

| 구성 | 소스 버전/상태 | 운영 위치 |
|---|---|---|
| Android | `0.12.7`, `versionCode 28` | `android/` |
| Backend | npm package `0.2.0`, health `0.2.0` | `backend/` |
| APK 다운로드 HTML | `tteumsae-v0.12.6-spatial-data-live-20260908-debug.apk`를 가리킴 | `download/` |

### 반드시 알고 시작할 배포 원칙

- APK·AAB는 Git에서 제외하므로 이 저장소의 `download/`에는 HTML만 있고 APK
  바이너리는 없다.
- 현재 운영 다운로드 주소는 `v0.12.6` 공간 정보 실데이터 확인용 디버그 APK를 제공한다.
- 소스 변경 후 다시 배포할 때는 기존 APK를 덮어쓰지 말고 새 파일명과 더 높은
  `versionCode`를 사용한다.
- `backend/vercel.json`의 `v0.4.0` APK rewrite는 레거시이며 현재 APK 다운로드
  프로젝트의 최신 링크가 아니다.

## 2. 새 컴퓨터 준비

### 2.1 필수 도구

| 도구 | 현재 프로젝트 기준 |
|---|---|
| Git | 최신 안정 버전 |
| Android Studio | Ladybug 이상 권장 |
| JDK | 17, Android Studio 내장 JBR 사용 가능 |
| Android SDK Platform | 35 |
| Android SDK Build Tools | SDK 35와 호환되는 최신 버전 |
| Node.js | 24.x |
| pnpm | 11.19.0, Corepack 또는 별도 설치 |
| Vercel CLI | 백엔드 `devDependency` 또는 `pnpm dlx` |
| ADB | Android SDK Platform Tools |

현재 고정된 주요 Android 버전:

```text
Gradle Wrapper 8.9
Android Gradle Plugin 8.7.3
Kotlin 2.0.21
Compose BOM 2024.12.01
Kakao Maps Android SDK 2.14.0
minSdk 26 / compileSdk 36 / targetSdk 36

`targetSdk=36` 전환은 2026-08-28 자동 테스트·lint·debug APK 빌드로 검증했다.
API 36의 실제 기기 회귀와 서명 AAB는 별도 출시 게이트다.
Java/Kotlin target 17
```

첫 Gradle·pnpm 실행은 인터넷에서 배포본과 의존성을 받아야 한다. 회사 방화벽을
사용한다면 `services.gradle.org`, Google Maven, Maven Central, Kakao SDK
저장소, npm registry와 Vercel 접근 정책을 먼저 확인한다.

### 2.2 복제 경로

한글·공백이 없는 짧은 경로를 권장한다.

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Set-Location C:\dev
git clone https://github.com/Newrred/tteumsae.git
Set-Location tteumsae
```

기존 한글·공백 경로에서 Android JUnit 실행기가 테스트 클래스를 찾지 못한
이력이 있다. `android.overridePathCheck=true`가 있어 앱 컴파일은 가능하더라도
테스트 문제를 숨기는 해결책은 아니다.

## 3. 최초 체크아웃 검증

```powershell
git status --short
git branch --show-current
git remote -v
```

기대값:

- 작업 트리가 깨끗함
- 기준 브랜치 `main`
- 작업 원격 `https://github.com/Newrred/tteumsae.git`
- 원본 추적이 필요하면 upstream으로 `https://github.com/minjaeimnydaa/tteumsae.git` 추가
- `android/`, `backend/`, `download/`, `design/`, `docs/`가 모두 존재

실제 키, `.env.local`, `local.properties`, APK, 키스토어가 Git에서 내려오면
안 된다.

## 4. Android 설정과 빌드

### 4.1 로컬 설정 생성

```powershell
Set-Location C:\dev\tteumsae\android
Copy-Item local.properties.example local.properties
```

`local.properties`에 로컬 SDK 경로와 `KAKAO_MAP_NATIVE_APP_KEY`를 입력한다.
선택 로그인을 시험할 때만 `SUPABASE_URL`과 `SUPABASE_PUBLISHABLE_KEY`도
입력한다. 두 값이 비어 있으면 게스트 기능을 유지한 채 로그인만 비활성화된다.
키 이름과 Kakao 플랫폼 등록 방법은
[`06_ENVIRONMENT_AND_ACCESS.md`](06_ENVIRONMENT_AND_ACCESS.md)를 따른다.

PowerShell 세션에서 Android Studio 내장 JDK를 사용할 때:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

설치 위치가 다르면 실제 JBR 또는 JDK 17 경로로 바꾼다.

### 4.2 빠른 컴파일 검증

```powershell
.\gradlew.bat --version
.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin
```

이 단계는 앱 Kotlin 코드와 단위 테스트 소스가 컴파일되는지 먼저 확인한다.

### 4.3 디버그 APK

```powershell
.\gradlew.bat clean assembleDebug
```

산출물:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

2026-08-20 통합 기준선에서는 최신 소스의 Kotlin 컴파일, 테스트 소스 컴파일과
디버그 APK 생성이 성공했다. 이 로컬 산출물은 운영 다운로드 페이지에 배포하지
않았고 실기기 전체 회귀도 아직 수행하지 않았다.

해시와 크기를 기록한다.

```powershell
$apk = 'app\build\outputs\apk\debug\app-debug.apk'
Get-Item -LiteralPath $apk | Select-Object Name,Length,LastWriteTime
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
```

USB/ADB 설치가 가능한 환경이면:

```powershell
adb devices
adb install -r $apk
```

사용자가 USB 디버깅이나 방화벽/PIN 환경을 원하지 않는 경우 APK를 승인된 파일
전송 수단으로 휴대폰에 보내 직접 설치한다. 알 수 없는 앱 설치 권한은 테스트가
끝난 뒤 다시 끄는 것이 좋다.

### 4.4 Android 단위 테스트

```powershell
.\gradlew.bat testDebugUnitTest
```

현재 테스트 파일:

```text
app/src/test/java/com/tteumsae/app/data/TteumsaeApiTest.kt
app/src/test/java/com/tteumsae/app/domain/TimeSafeEngineTest.kt
app/src/test/java/com/tteumsae/app/ui/HomeIntroTest.kt
app/src/test/java/com/tteumsae/app/ui/KakaoMapRouteTest.kt
app/src/test/java/com/tteumsae/app/ui/LocationStartTest.kt
```

#### 한글·공백 경로 JUnit 실행 우회

원래 작업 경로에서는 `compileDebugUnitTestKotlin`은 성공하지만
`testDebugUnitTest`가 여러 테스트 클래스의 `ClassNotFoundException`으로
실패했다. 같은 소스를 `subst Q:` 영문 드라이브로 매핑한 뒤 실행하면 위 5개
테스트 클래스가 모두 통과한다. assertion 결함이 아니라 Windows 경로에 따른
Gradle/JUnit 클래스 로딩 문제로 확인된 상태다.

현재 재현 가능한 검증 순서:

```powershell
# 1. 저장소 루트를 영문 드라이브로 매핑
$repoRoot = (Resolve-Path ..).Path
subst Q: $repoRoot
Set-Location Q:\android

# 2. 테스트 재실행
.\gradlew.bat clean testDebugUnitTest --rerun-tasks --info

# 3. 컴파일 산출물 존재 확인
Get-ChildItem app\build\tmp\kotlin-classes\debugUnitTest -Recurse -Filter *.class

# 4. 필요 시 특정 테스트만 실행
.\gradlew.bat testDebugUnitTest --tests '*TteumsaeApiTest' --stacktrace

# 5. 원래 위치로 돌아간 뒤 매핑 해제
Set-Location $repoRoot
subst Q: /d
```

CI는 영문·무공백 checkout을 사용한다. 원래 한글·공백 경로의 실패와 `Q:`에서의
통과를 구분해 기록하며, `assembleDebug` 성공만으로 단위 테스트 통과라고
보고하지 않는다.

### 4.5 릴리스 AAB

Play Store 제출 형식은 APK가 아니라 AAB다.

```powershell
Copy-Item keystore.properties.example keystore.properties
# keystore.properties에 조직 소유 업로드 키 경로·alias·비밀번호 입력
.\gradlew.bat bundleRelease
```

`app/build.gradle.kts`는 Git에서 제외한 `keystore.properties` 또는 아래 CI 환경변수에서
릴리스 서명값을 읽는다.

```text
TTEUMSAE_RELEASE_STORE_FILE
TTEUMSAE_RELEASE_STORE_PASSWORD
TTEUMSAE_RELEASE_KEY_ALIAS
TTEUMSAE_RELEASE_KEY_PASSWORD
```

서명값 없이 `bundleRelease` 또는 `assembleRelease`를 실행하면 미서명 산출물의 오배포를
막기 위해 즉시 실패한다. 난독화·컴파일만 확인할 때만 명시적으로
`.\gradlew.bat bundleRelease -PallowUnsignedRelease=true`를 사용한다.

2026-08-28에는 위 compile-only 모드에서 R8·lintVital·AAB 패키징이 성공했고, 생성된
AAB가 미서명임을 `jarsigner -verify`로 확인했다. 실제 제출 전에는 다음을 완료해야 한다.

1. 조직 소유 업로드 키 생성·보관
2. 로컬 또는 CI에 위 서명값 설정
3. Google Play App Signing 활성화
4. 업로드 키와 Play 앱 서명 키 해시를 Kakao Developers에 등록
5. 서명 release 빌드에서 지도·OAuth·딥링크·JSON 파싱 회귀 테스트

키스토어와 비밀번호는 Git에 커밋하지 않는다.

## 5. 백엔드 설치와 테스트

### 5.1 의존성 설치

```powershell
Set-Location C:\dev\tteumsae\backend
corepack enable
pnpm install --frozen-lockfile
```

`package.json`은 CI와 로컬이 같은 `pnpm@11.19.0`을 사용하도록 고정한다.
`pnpm-workspace.yaml`은 Vercel 의존성이 실제로 요구하는 `esbuild` 설치 스크립트만
허용한다.

`pnpm-lock.yaml`은 lockfile v9다. 배포나 테스트 전에 lockfile을 임의로
갱신하지 않는다. 의존성을 의도적으로 바꿀 때만 `package.json`과 lockfile을
같은 커밋에 넣는다.

### 5.2 자동 테스트와 구조 검사

```powershell
pnpm test
pnpm run check
```

2026-08-28 Gate 1-A 로컬 검증 결과:

```text
Node 테스트: 94개 통과, 0개 실패
Project check: 84개 파일 통과
```

테스트 범위:

- 추천 요청 좌표·`extraTimeMinutes`·시간·카테고리 검증
- `effectiveDeadlineMinutes=baseRouteMinutes+extraTimeMinutes` 계약
- 안전 여유와 안전도
- 영업시간·휴무일 필터와 UNKNOWN 처리
- TourAPI 기본 행 변환과 상세 이미지·태그 정규화
- Kakao Local 장소·행정구역 변환
- Kakao Mobility 구간 변환, 인증 헤더, 후보별 부분 실패
- Supabase·Kakao·TourAPI 요청별 timeout과 25초 전체 요청 deadline
- 동기화 lease의 소유권·완료·실패 수명주기와 Cron 중복 실행 차단
- Cron 45초 이후 신규 작업 유예와 정확 Kakao 후보 경로 8개 상한

이 테스트는 실제 TourAPI, Kakao, Supabase에 연결하지 않는 단위 테스트다.
Production 배포 후 API 스모크 테스트가 별도로 필요하다.

### 5.3 GitHub CI

`.github/workflows/ci.yml`은 push와 pull request마다 다음 두 작업을 실행한다.

- Backend: Node.js 24, `pnpm install --frozen-lockfile`, `pnpm test`, `pnpm run check`
- Android: JDK 17, Android API 36 설치, `testDebugUnitTest`, `lintDebug`

CI checkout은 영문·무공백 Linux 경로를 사용하며 `android/gradlew`의 실행 권한을
저장소에서 유지한다. 이 워크플로는 회귀 검증용이다. 서명된 AAB 생성, 실기기 OAuth·지도·
외부 내비게이션 검증, Preview 스모크 테스트, Production 승격을 대신하지 않는다. 최초
GitHub 원격 실행 결과는 이 커밋을 push한 뒤 Actions 화면에서 별도로 확인한다.

### 5.4 로컬 Vercel Functions

먼저 권한을 받은 뒤 로컬 프로젝트를 연결한다.

```powershell
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
pnpm dev
```

팀 `newrreds-projects`, 프로젝트 `tteumsae-backend`를 선택한다. 운영 DB를 로컬에서
수정하지 않도록 `.env.local`의 대상 환경을 확인한다.

## 6. Supabase 최초 구성

기존 운영 DB를 그대로 인수하면 마이그레이션 적용 이력을 먼저 확인한다. 새 DB면
순서대로 적용한다.

```text
backend/migrations/001_initial.sql
backend/migrations/002_detail_sync_state.sql
backend/migrations/003_user_accounts.sql
backend/migrations/004_tour_enrichment.sql
backend/migrations/005_sync_runtime_safety.sql
```

적용 후 확인:

- `public.places`, `public.sync_state`, `public.profiles`, `public.user_saved_places` 존재
- 네 테이블 RLS 활성화
- `sync_state`에 `tour_api`, `tour_details`, `tour_catalog_delta`, `tour_intro` 행 존재
- `sync_state`에 lease·마지막 실행 상태 열이 존재
- `claim_sync_job`, `finish_sync_job` RPC는 `service_role`만 실행 가능
- 익명 공개 정책 없음
- `node scripts/verify-user-rls.js`가 임시 사용자 2명의 교차 접근 차단 `PASS`

마이그레이션을 Production에 적용하기 전에 백업 또는 Supabase 복구 지점을
확인한다.

### 6.1 Gate 1-A migration 005 적용 순서

운영 DB에 바로 적용하지 않고 다음 순서를 지킨다.

1. 테스트 또는 빈 Supabase에 migration 001~005를 순서대로 적용한다.
2. 서로 다른 두 DB 세션에서 같은 작업 ID로 `claim_sync_job`을 동시에 호출해
   한 호출만 `true`이고 다른 호출은 `false`인지 확인한다.
3. 백업·복구 지점을 확인한 Production Supabase에 migration 005를 적용한다.
4. Preview를 수동 스모크 테스트한다. Vercel은 Preview 배포에서 Cron을 자동
   실행하지 않으므로 필요하면 승인 후 Bearer 인증으로 직접 호출한다.
5. 검증된 배포를 명시적으로 Production에 승격하고 Vercel UI에서 카탈로그
   `20 18 * * *`, intro `20 22 * * *` UTC 스케줄을 확인한다.

로컬 migration 계약 테스트 통과는 실제 Supabase 적용 증거가 아니다. Production
migration, 동시 RPC, Preview, Production Cron 결과는 각각 실행 로그를 남긴 뒤에만
완료로 표시한다.

## 7. 백엔드 배포

### 7.1 Preview 배포

```powershell
Set-Location C:\dev\tteumsae\backend
pnpm test
pnpm run check
pnpm exec vercel
```

출력된 Preview URL에서 `/api/health`와 읽기 전용 API를 확인한다. Preview가
운영 환경변수를 공유하지 않으면 연동 플래그가 false이거나 DB 요청이 실패하는
것이 정상일 수 있다.

Preview 배포에는 Vercel Cron이 자동 실행되지 않는다. Cron 검증은 migration 005가
적용된 테스트 DB를 대상으로 수동 실행하고, 운영 Cron 실행 결과로 간주하지 않는다.

### 7.2 Production 배포

Vercel 프로젝트는 `Newrred/tteumsae`와 연결되어 있다. `main` push 또는 CLI 배포는
먼저 Preview 검증 대상으로 취급하고, 자동 Production 배포에 의존하지 않는다.
Vercel Production Branch는 `main`을 기준으로 한다. 저장소 루트 `.vercelignore`는 Android 빌드 산출물,
`node_modules`, `output/`, `tmp/`가 CLI 업로드에 포함되지 않게 하므로 삭제하지
않는다. Vercel Build and Deployment의 Node.js 버전과 `backend/package.json`의
`engines.node`는 모두 `24.x`를 사용한다.

```powershell
git push newrred main
```

연결된 Preview 배포의 스모크 테스트와 migration 호환성을 확인한 뒤 Vercel UI에서
해당 검증 배포를 명시적으로 Production으로 승격한다. Preview URL 확인 없이 브랜치
push만으로 운영 완료를 선언하지 않는다.

운영 기준 주소:

```text
https://tteumsae-backend-one.vercel.app
```

환경변수를 추가·교체한 경우 코드 변경이 없어도 새 배포를 생성한다.
Production 승격 후 Vercel Settings의 Cron 목록에서 두 UTC 스케줄을 직접 확인하고,
첫 예약 실행의 `sync_state.last_status`, `last_finished_at`, `last_run_summary`를 기록한다.

### 7.3 읽기 전용 스모크 테스트

```powershell
$base = 'https://tteumsae-backend-one.vercel.app'

Invoke-RestMethod "$base/api/health"
Invoke-RestMethod "$base/api/places?page=1&pageSize=2"
Invoke-RestMethod "$base/api/geocode?q=%EA%B0%95%EB%A6%89%EC%97%AD"
Invoke-RestMethod "$base/api/region?latitude=37.7519&longitude=128.8761"
```

추천 테스트:

```powershell
$body = @{
  mode = 'ON_THE_WAY'
  start = @{ latitude = 37.7519; longitude = 128.8761 }
  destination = @{ latitude = 37.7644; longitude = 128.8996 }
  extraTimeMinutes = 90
  safetyBufferMinutes = 15
  transport = 'CAR'
  categories = @('ATTRACTION')
} | ConvertTo-Json -Depth 5

$request = @{
  Method = 'Post'
  Uri = "$base/api/recommendations"
  ContentType = 'application/json'
  Body = $body
}
Invoke-RestMethod @request
```

추가 확인:

- `meta.routeProvider`가 자동차는 `KAKAO_MOBILITY`
- `meta.effectiveDeadlineMinutes = meta.baseRouteMinutes + meta.extraTimeMinutes`
- 일부 실패가 있으면 `routeFailureCount`가 증가
- 도보 기능 비활성 시 `ESTIMATE`, 활성 성공 시 `KAKAO_MAP_WALK`
- 도보 기능 활성 후 외부 실패 시 `ESTIMATE_FALLBACK`과 `meta.warning` 포함
- 없는 장소는 404
- 잘못된 카테고리·좌표·시간은 400
- 잘못된 메서드는 405와 `Allow` 헤더
- 오류 응답에 외부 API 본문이나 키가 없음

### 7.4 Cron 수동 검증

Cron은 DB를 변경하고 외부 API 쿼터를 사용한다. 운영 담당자의 승인 후 실행한다.
비밀값을 명령 기록에 직접 적지 말고 별도 환경변수로 주입한다.

혼잡 보강은 별도 활용신청과 migration 008 뒤 Preview에서만 먼저
`/api/cron/tour-intro-sync?stage=congestion`을 수동 실행한다. 응답의 `matchedRows`,
`ambiguousRows`, `unmatchedRows`를 기록하고 이름 불일치가 많은 상태에서는 Cron을 예약하지 않는다.

날씨 보강은 별도 단기예보 활용신청과 migration 009 뒤 Preview에서
`KMA_WEATHER_ENABLED=true`로 제한 검증한다. 자연 관광지 한 곳의 도착시각을 바꿔
예보시각·캐시 hit·공급자 실패 폴백을 확인하고, 같은 설정을 Production에 바로 복사하지 않는다.

주변 공영주차장은 별도 활용신청과 migration 011 뒤 Preview에서
`/api/cron/tour-intro-sync?stage=parking`을 페이지별로 실행한다. 강원 공영·좌표 보유 행 수,
1km 이내 0건·1건·3건 이상 장소, 직선거리 정렬, 기준일과 운영·요금 문구를 확인한다.
전체 순회에서 사라진 원천 행 처리 정책을 확정하기 전에는 Cron을 예약하지 않는다.

```powershell
$base = 'https://tteumsae-backend-one.vercel.app'
$headers = @{ Authorization = "Bearer $env:TTEUMSAE_CRON_CALL_SECRET" }

Invoke-RestMethod -Headers $headers "$base/api/cron/tour-catalog-sync"
Invoke-RestMethod -Headers $headers "$base/api/cron/tour-intro-sync"
```

`TTEUMSAE_CRON_CALL_SECRET`는 실행 세션용 이름일 뿐 Vercel의 실제 변수 이름은
`CRON_SECRET`이다. 실행 후 세션 변수를 제거한다.

```powershell
Remove-Item Env:TTEUMSAE_CRON_CALL_SECRET
```

응답의 `status`, 처리 수, `nextPage`와 Supabase `sync_state`를 대조한다. 동일
작업을 겹쳐 호출했을 때 한 요청은 `already_running`으로 건너뛰어야 한다. intro
동기화는 카탈로그 장소가 먼저 있어야 한다.

## 8. 테스트 APK 다운로드 페이지 배포

APK 다운로드는 백엔드가 아니라 별도 [`download/`](../download/) 프로젝트다.

### 8.1 버전 증가

[`android/app/build.gradle.kts`](../android/app/build.gradle.kts)의 두 값을 먼저
올린다.

```kotlin
versionCode = 이전 값보다 큰 정수
versionName = "새 버전"
```

현재 확인용 APK가 `versionCode 27`, `versionName 0.12.6`이므로 다음 배포
빌드는 최소 `versionCode 28`, `versionName 0.12.7`을 사용한다.

### 8.2 APK 생성과 복사

```powershell
Set-Location C:\dev\tteumsae\android
.\gradlew.bat clean assembleDebug

$version = '0.12.5'
$sourceApk = 'app\build\outputs\apk\debug\app-debug.apk'
$targetApk = "..\download\tteumsae-v$version-debug.apk"
Copy-Item -LiteralPath $sourceApk -Destination $targetApk
Get-FileHash -Algorithm SHA256 -LiteralPath $targetApk
```

[`download/index.html`](../download/index.html)의 파일명과 링크 문구를 같은 버전으로
바꾼다. APK는 `.gitignore` 때문에 Git에는 들어가지 않지만 Vercel 배포 작업
디렉터리에는 배포 시점에 존재해야 한다.

### 8.3 Preview와 Production

```powershell
Set-Location C:\dev\tteumsae\download
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel
```

Preview 링크에서 HTML과 APK를 직접 다운로드해 설치 검증한 뒤:

```powershell
pnpm dlx vercel --prod
```

운영 페이지:

```text
https://tteumsae-apk-six.vercel.app
```

운영 파일 확인:

```powershell
$url = "https://tteumsae-apk-six.vercel.app/tteumsae-v0.12.5-spatial-data-20260907-debug.apk"
Invoke-WebRequest -Method Head -Uri $url
```

응답 200, 적절한 `Content-Length`, 실제 설치 성공을 모두 확인한다. 메신저에서
링크 복사가 어려운 테스터에게는 다운로드 페이지와 APK 직접 링크를 함께 보낸다.

## 9. Android 실기기 스모크 테스트

### 9.1 설치·첫 실행

- [ ] 기존 앱 위에 업데이트 설치 가능
- [ ] 앱 이름 `틈새`, 패키지 `com.tteumsae.app`
- [ ] 첫 안내 팝업과 오늘 하루 보지 않기
- [ ] 지도 타일과 POI가 정상 표시
- [ ] CTA와 하단 내비게이션이 화면 밖으로 잘리지 않음

### 9.2 위치

- [ ] GPS 비활성→활성→비활성 토글
- [ ] 최초 권한 허용
- [ ] 권한 거부
- [ ] `다시 묻지 않음` 후 설정으로 이동
- [ ] 현재 좌표로 지도 이동
- [ ] `현재 위치`가 실제 행정구역명으로 바뀌고 긴 이름은 말줄임
- [ ] 다른 화면을 다녀와도 의도한 GPS 상태 유지

### 9.3 경로 따라 갈 장소

- [ ] 출발지는 현재 위치 자동 선택, 검색으로 교체 가능
- [ ] 목적지 검색과 선택
- [ ] 도착 마감 1시간 뒤 기본값, 인라인 휠, 15분·24시간 경계와 자정 넘김
- [ ] 선택 관심 필터 없이도 곧바로 추천 가능
- [ ] `ARRIVAL_DEADLINE_V1` 절대 epoch 요청과 서버 고정 안전여유 10분
- [ ] 핀에 `+N분` 추가 이동시간 표시
- [ ] 경유지 없음 또는 한 곳 선택·교체·해제
- [ ] 선택 후 최대 체류시간과 늦어도 출발할 시각 표시
- [ ] 미선택 직행 또는 선택 한 곳과 최종 목적지가 카카오맵에 전달
- [ ] 선택형 출발 5분 전 알림과 권한 거부 후 길 안내 유지
- [ ] 카카오맵 미설치 시 설치 안내

최종 안내 경로와 시간은 카카오맵이 다시 계산한다. 앱의 수치는 요청 시점 현재 교통
기준 예상이며 도착 보장이 아니다. `/api/route` 0~5개는 legacy 호환 테스트로만 남는다.

### 9.4 근처에서 갈 장소 — 현재 비활성 레거시

- 현재 제출 흐름에서는 진입할 수 없다. 다시 활성화할 때 아래를 회귀한다.
- [ ] 강원도 안에서 추천 가능
- [ ] 강원도 밖에서는 안내 후 돌아가기
- [ ] 남는 시간에 따라 반경 UI 변경
- [ ] 도보 경로는 현행 제품 범위에서 제외; `WALK` 호환 계약 회귀만 유지
- [ ] 결과 없음에서 시간 +30분, 조건 해제, 재검색 행동 제공

### 9.5 장소 카드·저장소·설정

- [ ] 대표 이미지 정상/실패/없는 장소의 fallback
- [ ] 저장 목록·상세에 평균 머무름 문구가 없음
- [ ] 긴 제목 말줄임과 태그 `+N`
- [ ] 운영시간 `OPEN`과 `운영시간 확인 필요`
- [ ] 저장·저장 해제·되돌리기
- [ ] 추가 페이지 스크롤 로딩과 안내 툴팁
- [ ] 캐시 지우기와 저장 장소 전체 삭제
- [ ] 문의 메일 열기
- [ ] 약관 URL 미설정 상태가 `준비 중`으로 표시

### 9.6 실패 상태

- [ ] 비행기 모드/백엔드 장애 시 어느 작업이 실패했는지 표시
- [ ] 재시도 버튼 동작
- [ ] 장소 검색 실패, 지역 확인 실패, 추천 실패가 구분됨
- [ ] 추천 결과 없음에서 복구 행동 표시

## 10. 버전 및 배포 기록 규칙

각 배포 커밋 또는 `CHANGELOG.md`에 다음을 남긴다.

```text
Android versionName / versionCode
Git commit SHA
Backend 배포 URL과 배포시각
APK 파일명, 크기, SHA-256
검증한 기기 모델과 Android 버전
자동 테스트 결과
실기기 스모크 테스트 결과
알려진 문제
```

규칙:

- `versionCode`는 Play Console에 올린 값보다 반드시 큼
- 한 번 배포한 버전 파일은 덮어쓰지 않음
- 소스 변경 뒤 APK를 재생성하면 버전도 증가
- 백엔드 계약 변경은 Android 호환성을 먼저 확인
- 운영 배포와 테스트 APK 배포를 별도 단계로 기록
- Production URL만 보고 배포 완료로 판단하지 않고 스모크 테스트까지 수행

## 11. 롤백

### 백엔드

1. Vercel Deployments에서 마지막 정상 배포를 확인한다.
2. 해당 배포를 Production으로 승격하거나 정상 Git 커밋을 재배포한다.
3. DB 마이그레이션이 포함되었다면 되돌리기 SQL보다 호환 가능한 전진 수정을
   우선하고, 백업 상태를 확인한다.
4. `/api/health`, 장소, 추천 API를 다시 검사한다.

### 테스트 APK

1. 이전 버전 APK URL은 그대로 유지한다.
2. 다운로드 HTML만 마지막 정상 버전으로 되돌릴 수 있다.
3. 이미 설치한 더 높은 `versionCode` 앱 위에는 낮은 버전을 일반 업데이트로
   설치할 수 없으므로 필요하면 제거 후 설치해야 한다.

### Play Store

Play Console에서 `versionCode`를 되돌릴 수 없다. 문제를 수정한 더 높은
`versionCode`의 새 AAB를 제출한다.

## 12. 배포 완료 조건

- [x] Git 작업 트리와 배포 커밋 식별 가능
- [x] 비밀값 검사 완료
- [x] 백엔드 155개 테스트 및 84개 파일 project check 통과
- [x] Android 116개 단위 테스트·lint·APK 빌드 통과
- [ ] GitHub CI의 Backend·Android 검증 작업 통과
- [x] 도메인 미연결 Production-target 후보 스모크 테스트 통과
- [x] Production API 스모크 테스트 통과
- [ ] 새 버전 APK 직접 링크 200 및 실기기 설치 통과
- [ ] Kakao 지도·GPS·도착 마감 추천·한 곳 선택·최종 목적지·알림 확인
- [ ] 알려진 문제와 롤백 대상 기록

## 13. Gate 1-B 운영 반영 런북

반영 순서는 다음과 같다.

1. 현재 `places`와 `sync_state`를 export한다.
2. migration 001~005 적용 상태를 확인하고 누락분을 먼저 적용한다.
3. migration 006을 적용한 뒤 새 객체와 anon/authenticated 차단을 스모크 테스트한다.
4. Vercel Production에 `KAKAO_MOBILITY_DAILY_WARNING=7000`,
   `KAKAO_MOBILITY_DAILY_BUDGET=8000`을 설정한다.
5. 운영 비밀값을 Preview에 복제하지 않고 `--prod --skip-domain` 후보 배포를 만든다.
6. 후보 URL에서 health, places, recommendation, route, ops 무인증 차단을 확인한다.
7. 검수 CSV 100/100을 재검증하고 활성 강릉 ID만 원자 upsert한다.
8. ops 집계의 curation 100/100과 사용량 집계를 확인한 뒤 동일 배포를 promote한다.

롤백은 마지막 정상 Vercel 배포를 다시 promote하는 방식으로 한다. migration 005~006의
테이블·컬럼·집계 행은 운영 증거이므로 삭제하지 않는다. 이전 코드는 새 객체를 읽지 않아
코드 롤백과 공존할 수 있다.

### 13.1 2026-08-28 적용 기록

| 항목 | 결과 |
|---|---|
| 적용 브랜치 | `codex/gate-1b-data-trust` |
| 데이터 커밋 | `9ed9d53` |
| Vercel 함수 한도 수정 | `abef2f8`, 레거시 `tour-detail-sync` 제거 후 12개 |
| 운영 백업 | Supabase SQL 결과 `Supabase Snippet Untitled query.csv`, 장소 1,719행과 sync metadata |
| DB 선행 점검 | migration 005 누락 발견, 005 적용 후 006 재검증 |
| DB 권한 | anon table/RPC 접근 차단 true, service-role ops RPC 정상 |
| 후보 배포 | `dpl_FRzg3BSngdtoZJVj8UiGxpecrPVa`, 도메인 미연결 Production-target |
| 후보 스모크 | health 200, places 200, route 200, recommendations 200/8건, ops 무인증 401 |
| 검수 import | 100행, 운영시간 VERIFIED 75, 주차 VERIFIED 71 |
| ops 집계 | curation 100/100, Mobility 예약/성공 10/10, 잔여 7,990 |
| Production 승격 | `tteumsae-backend-one.vercel.app`, health 200, curation 상세 200 |
| Cron 비밀값 정합성 | 기존 배포와 현재 Production 설정 불일치 발견, `CRON_SECRET` 회전 후 재배포 |
| 최종 Production | `dpl_2uF9qkGXpRwymLvU9xttEJAqTZSs`, `Ready`, 운영 별칭 연결 |
| 인증 ops HTTP | 200, curation 100/100, Mobility 예약/성공 10/10 |
| 수동 운영 Cron | catalog 200/completed, intro 200/completed·20건 갱신·실패 0 |
| 실행 요약 영속화 | catalog 4,240ms, intro 10,663ms, 두 작업 모두 `lastStatus=completed` |
| 후속 catalog 예약 주기 | `2026-08-31T19:01:25.674Z` 시작, `19:01:32.498Z` 완료, 6,824ms, `completed`, 1페이지·0건 |
| 후속 intro 예약 주기 | `2026-08-30T22:47:02.803Z` 시작, `22:47:15.026Z` 완료, 12,223ms, `completed`, 처리/갱신 20/20·실패 0 |

운영 `CRON_SECRET`은 원문을 파일이나 로그에 남기지 않고 프로세스 메모리에서 생성해
Vercel Sensitive 환경 변수로 회전했다. 동일 프로세스에서 재배포와 인증 요청을 수행해
`/api/ops/status`, catalog Cron, intro Cron의 실제 Production HTTP 경계를 검증했다.
수동 실행 뒤 ops에는 catalog와 intro의 완료 시각·소요시간·요약이 저장됐다. 이후
Vercel에서 두 Production Cron이 활성화되고 `20 18 * * *`, `20 22 * * *` UTC 스케줄을
유지하는지 확인했다. Supabase `sync_state`에는 수동 시각 `2026-08-28T04:08Z`와 별개인
후속 실행 시각, `completed` 상태와 `last_run_summary`가 저장돼 Gate 1 예약 실행
운영 점검을 완료했다.

### 13.2 2026-09-07 공간 정보 보강 운영 반영

사용자 승인 뒤 운영 Supabase에 migration 007~009를 적용하고 세 객체를 실제 조회했다.
브랜치 `codex/spatial-data-enrichment`의 `ecde5aa`를 Production 배포
`dpl_5ciPSYBC9UjTuBrv37eWRPm4P5Lo`로 올렸으며 `tteumsae-backend-one.vercel.app` 별칭과
health·장소 목록·장소 상세·V1 추천 8건을 확인했다.

혼잡도와 날씨의 별도 키는 등록되지 않았고 날씨 기능 플래그도 꺼져 있으므로, 이번 배포는
기존 기능을 유지하면서 코드와 DB 수용 구조만 먼저 반영한다. 새 debug APK는 운영 고정 주소를
사용하며 64,671,011바이트, SHA-256
`7C96D65EF1955C99FE8327D502362368A712655B9850707A7D616D85DEB01891`이다. 연결 기기가 없어
설치 QA는 수행하지 않았다.

### 13.3 2026-09-07 접근성·공영주차장 DB와 확인용 APK 반영

`main`을 `6af0cd8`까지 fast-forward하고 운영 Supabase에 migration 010
`tour_accessibility`와 011 `public_parking_lots`를 순서대로 적용했다. 새 테이블은 RLS를
활성화하고 public·anon·authenticated 권한을 회수했으며 service role만 접근 가능함을
운영 메타데이터로 확인했다.

Backend Production 배포 `dpl_AEtZmmbBTH3NXwqwBgcW1fhzSmQk`가 2026-09-07 17:47 KST에
Ready가 됐고 `tteumsae-backend-one.vercel.app` 별칭을 사용한다. health·장소 목록·장소
상세가 200이고, V1 추천은 `KAKAO_MOBILITY`, 최소 체류 15분, 고정 안전여유 10분으로
8건을 반환했다. Node.js 24.19.0에서 Backend 214/214와 프로젝트 검사 116개 파일을
다시 통과했다.

Android는 `0.12.5`(`versionCode 26`)로 올려 30 suites 145/145, lint 오류 0·경고 44,
`assembleDebug`를 통과했다. APK는 64,500,234바이트, SHA-256
`53D1C01BA43064FD66104A4EB43B39B544489138CAD6BFC3C6C31EB33F01CE05`다. APK Production
배포 `dpl_CfgLFMNMsdfRQnimAWm4H1i7cHEx`의 운영 별칭은
`https://tteumsae-apk-six.vercel.app`이며 새 파일의 HEAD 200, Content-Length와 원격 파일
전체 SHA-256 일치를 확인했다. 과거 `tteumsae-apk.vercel.app`은 현재 팀 소유가 아니어서 새 배포로 재지정하지
못했으므로 사용하지 않는다.

접근성 수동 stage는 운영 `CRON_SECRET`이 민감 변수로 로컬에 내려오지 않아 인증 전에
401로 종료됐고 DB를 변경하지 않았다. 공영주차장은 `PUBLIC_PARKING_API_SERVICE_KEY`가
없어 실행하지 않았다. 따라서 두 섹션은 실데이터가 들어오기 전까지 설계대로 숨겨지며,
Cron은 예약하지 않는다.

### 13.4 2026-09-08 공간 정보 실데이터 활성화와 APK

공공데이터포털 키 등록 뒤 migration 012로 사용량 원장의 허용 공급자에 `KMA`와
`PUBLIC_DATA`를 추가했다. 전국주차장 표준 API의 실제 최상위 `header/body` 응답과
`instt_nm=강원특별자치도 강릉시` 필터에 맞춰 호출을 교정했다. 운영 전체 순회는
170건 중 유효 좌표 167건을 저장했고 강릉항회센터 상세에서 8m·320m·508m의 가까운
공영주차장 3곳을 확인했다.

Production 배포 `dpl_Gm5QeJD4hycCoY7qjZAh8TeKAfLE`가 고정 주소
`https://tteumsae-backend-one.vercel.app`에 반영됐다. 관광지 집중률 2,880건 중
450건이 앱 장소와 보수적으로 연결됐고, 소돌해수욕장 상세에서 당일 `한산 예상`과
상대값을 확인했다. 날씨 기능 플래그를 켜고 순포해변의 도착 무렵 기상청 예보와
격자 캐시 저장을 확인했다. 무장애 API는 이후 `TOUR_API_SERVICE_KEY`를 갱신하고 Production을
재배포했지만 전용 `KorWithService2`만 계속 403이었다. 같은 키의 일반 intro stage는
20건 처리·18건 갱신·2건 빈 응답·실패 0으로 성공했으므로 무장애 활용 권한 활성화를
확인하기 전에는 stage를 반복하거나 Cron에 넣지 않는다.

Node.js 24.19.0 기준 Backend 215/215와 프로젝트 검사 118개를 통과했다. Android는
`0.12.6`(`versionCode 27`) 30 suites 145/145, lint 오류 0·경고 44,
`assembleDebug`를 통과했다. APK Production `dpl_BCPv4Pvg5YVGmG1JoQ5HrHqnyMK8`를
`https://tteumsae-apk-six.vercel.app`에 배포했다. APK는 64,500,238바이트이며 로컬·원격
SHA-256은 모두 `CF04A60F2CAF6D688CCD1843954DEB6D8D68AD3CAF4A69B31B415794C69DFF6A`다.

같은 날 TourAPI 키 갱신 뒤 후속 Production `dpl_5EY1aBPNLwZNvpSoNyWhKmTNhNEj`를
고정 주소에 반영했다. 일반 intro stage는 20건 중 18건 갱신·2건 빈 응답·실패 0이었고,
무장애 stage의 `TOUR_API` HTTP 403은 비밀값 없는 구조화 로그로 분리 확인했다. 최종 health와
장소 목록은 모두 200이었다.

활용신청 승인 반영 뒤 후속 Production `dpl_BvbbKjxJy1bQu3eZFFZ9vmQ3RGGR`에서 무장애
첫 페이지 20건을 다시 처리해 13곳 연결·7곳 미매칭·실패 0을 확인했다. 실응답 검수에서
`route`와 `publictransport` 제목이 뒤바뀐 문제를 발견해 각각 `접근로`와 `대중교통`으로
교정하고 같은 페이지를 재동기화했다. 강릉 `정은숙초당순두부` 공개 상세는 접근로·대중교통·
출입구 3개 항목을 올바르게 반환한다.
