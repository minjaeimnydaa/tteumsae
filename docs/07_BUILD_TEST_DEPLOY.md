# 빌드·테스트·배포 운영서

작성 기준: 2026-08-16 통합 소스

이 문서는 새 Windows 개발 환경에서 저장소를 검증하고 Android, 백엔드, APK
다운로드 페이지를 배포하는 순서를 정의한다. 명령은 저장소 루트를
`C:\dev\tteumsae`로 가정한다.

## 1. 현재 버전과 산출물 상태

| 구성 | 소스 버전/상태 | 운영 위치 |
|---|---|---|
| Android | `0.12.3`, `versionCode 24` | `android/` |
| Backend | npm package `0.2.0`, health `0.2.0` | `backend/` |
| APK 다운로드 HTML | `tteumsae-v0.12.3-debug.apk`를 가리킴 | `download/` |

### 반드시 알고 시작할 불일치

- APK·AAB는 Git에서 제외하므로 이 저장소의 `download/`에는 HTML만 있고 APK
  바이너리는 없다.
- 현재 운영 다운로드 주소의 `v0.12.3` APK는 같은 버전명의 최신 통합 소스보다
  먼저 만들어졌다. 최근 경로 후보 핀 아이콘·정렬 등 후속 수정이 소스에는 있지만
  그 APK에는 없을 수 있다.
- 즉, **소스 `0.12.3`과 배포 APK `0.12.3`을 동일 빌드로 간주하면 안 된다.**
- 다음 테스트 APK는 최소 `versionName 0.12.4`, `versionCode 25`처럼 버전을
  올리고 새 파일명으로 배포한다. 기존 `v0.12.3` 파일을 덮어쓰지 않는다.
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
| Node.js | 20 이상 |
| pnpm | Corepack 또는 별도 설치 |
| Vercel CLI | 백엔드 `devDependency` 또는 `pnpm dlx` |
| ADB | Android SDK Platform Tools |

현재 고정된 주요 Android 버전:

```text
Gradle Wrapper 8.9
Android Gradle Plugin 8.7.3
Kotlin 2.0.21
Compose BOM 2024.12.01
Kakao Maps Android SDK 2.14.0
minSdk 26 / compileSdk 35 / targetSdk 35
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
git clone https://github.com/minjaeimnydaa/tteumsae.git
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
- 원격 `https://github.com/minjaeimnydaa/tteumsae.git`
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

2026-08-16 통합 기준선에서는 최신 소스의 Kotlin 컴파일, 테스트 소스 컴파일과
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

#### 알려진 JUnit 실행 문제

현재 이력상 `compileDebugUnitTestKotlin`은 성공하지만 `testDebugUnitTest` 실행 시
여러 테스트 클래스가 `ClassNotFoundException`으로 실패한다. 이는 테스트
assertion 실패가 아니라 Gradle/JUnit 테스트 런타임이 컴파일된 클래스를 로드하지
못하는 문제다.

인수 후 우선 다음 순서로 재현·분리한다.

```powershell
# 1. 영문·무공백 경로에서 새 clone
Set-Location C:\dev\tteumsae\android

# 2. 기존 출력 제거 후 테스트 재실행
.\gradlew.bat clean testDebugUnitTest --rerun-tasks --info

# 3. 컴파일 산출물 존재 확인
Get-ChildItem app\build\tmp\kotlin-classes\debugUnitTest -Recurse -Filter *.class

# 4. 특정 테스트만 실행
.\gradlew.bat testDebugUnitTest --tests '*TteumsaeApiTest' --stacktrace
```

영문 경로의 깨끗한 clone에서도 재현되면 Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21
조합의 테스트 classpath와 생성된 test task 설정을 조사한다. 이 오류가 해결되기
전까지 `assembleDebug` 성공만으로 단위 테스트 통과라고 보고하면 안 된다.

### 4.5 릴리스 AAB

Play Store 제출 형식은 APK가 아니라 AAB다.

```powershell
.\gradlew.bat bundleRelease
```

하지만 현재 프로젝트에는 릴리스 `signingConfig`가 없다. 실제 제출 전에는 다음을
완료해야 한다.

1. 조직 소유 업로드 키 생성·보관
2. 비밀값을 환경변수 또는 로컬 `keystore.properties`로 읽는 signingConfig 추가
3. Google Play App Signing 활성화
4. release와 Play 앱 서명 키 해시를 Kakao Developers에 등록
5. release 빌드에서 난독화 후 지도·딥링크·JSON 파싱 회귀 테스트

키스토어와 비밀번호는 Git에 커밋하지 않는다.

## 5. 백엔드 설치와 테스트

### 5.1 의존성 설치

```powershell
Set-Location C:\dev\tteumsae\backend
corepack enable
pnpm install --frozen-lockfile
```

`pnpm-lock.yaml`은 lockfile v9다. 배포나 테스트 전에 lockfile을 임의로
갱신하지 않는다. 의존성을 의도적으로 바꿀 때만 `package.json`과 lockfile을
같은 커밋에 넣는다.

### 5.2 자동 테스트와 구조 검사

```powershell
pnpm test
pnpm run check
```

2026-08-16 통합본 검증 결과:

```text
Node 테스트: 16개 통과, 0개 실패
Project check: 31 files 통과
```

테스트 범위:

- 추천 요청 좌표·시간·카테고리 검증
- 안전 여유와 안전도
- 영업시간·휴무일 필터와 UNKNOWN 처리
- TourAPI 기본 행 변환과 상세 이미지·태그 정규화
- Kakao Local 장소·행정구역 변환
- Kakao Mobility 구간 변환, 인증 헤더, 후보별 부분 실패

이 테스트는 실제 TourAPI, Kakao, Supabase에 연결하지 않는 단위 테스트다.
Production 배포 후 API 스모크 테스트가 별도로 필요하다.

### 5.3 로컬 Vercel Functions

먼저 권한을 받은 뒤 로컬 프로젝트를 연결한다.

```powershell
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
pnpm dev
```

팀 `jaturi`, 프로젝트 `tteumsae-backend`를 선택한다. 운영 DB를 로컬에서
수정하지 않도록 `.env.local`의 대상 환경을 확인한다.

## 6. Supabase 최초 구성

기존 운영 DB를 그대로 인수하면 마이그레이션 적용 이력을 먼저 확인한다. 새 DB면
순서대로 적용한다.

```text
backend/migrations/001_initial.sql
backend/migrations/002_detail_sync_state.sql
```

적용 후 확인:

- `public.places`, `public.sync_state` 존재
- 두 테이블 RLS 활성화
- `sync_state`에 `tour_api`, `tour_details` 두 행 존재
- 익명 공개 정책 없음

마이그레이션을 Production에 적용하기 전에 백업 또는 Supabase 복구 지점을
확인한다.

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

### 7.2 Production 배포

```powershell
pnpm exec vercel --prod
```

운영 기준 주소:

```text
https://tteumsae-backend.vercel.app
```

환경변수를 추가·교체한 경우 코드 변경이 없어도 새 배포를 생성한다.

### 7.3 읽기 전용 스모크 테스트

```powershell
$base = 'https://tteumsae-backend.vercel.app'

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
  deadlineMinutes = 180
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
- 일부 실패가 있으면 `routeFailureCount`가 증가
- 도보는 `ESTIMATE`와 `meta.warning` 포함
- 없는 장소는 404
- 잘못된 카테고리·좌표·시간은 400
- 잘못된 메서드는 405와 `Allow` 헤더
- 오류 응답에 외부 API 본문이나 키가 없음

### 7.4 Cron 수동 검증

Cron은 DB를 변경하고 외부 API 쿼터를 사용한다. 운영 담당자의 승인 후 실행한다.
비밀값을 명령 기록에 직접 적지 말고 별도 환경변수로 주입한다.

```powershell
$base = 'https://tteumsae-backend.vercel.app'
$headers = @{ Authorization = "Bearer $env:TTEUMSAE_CRON_CALL_SECRET" }

Invoke-RestMethod -Headers $headers "$base/api/cron/tour-sync"
Invoke-RestMethod -Headers $headers "$base/api/cron/tour-detail-sync"
```

`TTEUMSAE_CRON_CALL_SECRET`는 실행 세션용 이름일 뿐 Vercel의 실제 변수 이름은
`CRON_SECRET`이다. 실행 후 세션 변수를 제거한다.

```powershell
Remove-Item Env:TTEUMSAE_CRON_CALL_SECRET
```

응답의 `status`, 처리 수, `nextPage`와 Supabase `sync_state`를 대조한다. 상세
동기화는 기본 장소가 먼저 있어야 한다.

## 8. 테스트 APK 다운로드 페이지 배포

APK 다운로드는 백엔드가 아니라 별도 [`download/`](../download/) 프로젝트다.

### 8.1 버전 증가

[`android/app/build.gradle.kts`](../android/app/build.gradle.kts)의 두 값을 먼저
올린다.

```kotlin
versionCode = 이전 값보다 큰 정수
versionName = "새 버전"
```

현재 소스와 과거 APK가 같은 `0.12.3`을 사용하므로 다음 빌드는 최소
`versionCode 25`, `versionName 0.12.4`를 사용한다.

### 8.2 APK 생성과 복사

```powershell
Set-Location C:\dev\tteumsae\android
.\gradlew.bat clean assembleDebug

$version = '0.12.4'
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
https://tteumsae-apk.vercel.app
```

운영 파일 확인:

```powershell
$version = '0.12.4'
$url = "https://tteumsae-apk.vercel.app/tteumsae-v$version-debug.apk"
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
- [ ] 남는 시간 15분~6시간 슬라이더
- [ ] 직접 입력은 최대 1440분
- [ ] 안전 여유 10/15/20/30분
- [ ] 추천 의도 `아무거나/식사/카페/산책·관광/실내 활동/지금은 음식 제외`
- [ ] 붉은 후보 영역과 카테고리 아이콘 핀
- [ ] 경유지 최대 5개, 선택 순서 번호와 추가/제거
- [ ] 선택 수·예상 시간·여유 표시
- [ ] 카카오맵에 출발지·선택 경유지·최종 목적지가 모두 전달
- [ ] 카카오맵 미설치 시 설치 안내

앱 내부 복수 경유지 시간은 합산 예상이고 카카오맵이 최종 경로를 다시
계산한다는 점을 확인한다.

### 9.4 근처에서 갈 장소

- [ ] 강원도 안에서 추천 가능
- [ ] 강원도 밖에서는 안내 후 돌아가기
- [ ] 남는 시간에 따라 반경 UI 변경
- [ ] 도보 예상 경고 표시
- [ ] 결과 없음에서 시간 +30분, 조건 해제, 재검색 행동 제공

### 9.5 장소 카드·저장소·설정

- [ ] 대표 이미지 정상/실패/없는 장소의 fallback
- [ ] `평균 머무름` 문구
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

- [ ] Git 작업 트리와 배포 커밋 식별 가능
- [ ] 비밀값 검사 완료
- [ ] 백엔드 16개 테스트 및 project check 통과
- [ ] Android 컴파일·APK 빌드 통과
- [ ] JUnit ClassNotFoundException 해결 또는 미해결 사실을 릴리스 노트에 명시
- [ ] Preview 스모크 테스트 통과
- [ ] Production API 스모크 테스트 통과
- [ ] 새 버전 APK 직접 링크 200 및 실기기 설치 통과
- [ ] Kakao 지도·GPS·추천·복수 경유지·최종 목적지 확인
- [ ] 알려진 문제와 롤백 대상 기록
