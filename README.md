# 틈새(Tteumsae)

강원도 여행 중 이동과 약속 사이에 남는 시간 안에서 들를 수 있는 장소를 추천하는 Android 앱입니다.

## 현재 작업과 기준선

- 기준일: `2026-10-16` (GUI 구현 반영: 10월 15일)
- Android 소스: `0.14.0` (`versionCode 35`), **기본 수동 모드 단위 테스트·lint·debug 빌드 통과**
- Android 패키지: `com.tteumsae.app`
- 현재 제품 단계: 목적지·도착 마감 기반 한 곳 우선 경유 판단 MVP
- 다음 작업: 지도·로그인 설정과 출시 서명을 준비한 뒤 기기 회귀
- 출시 단계: 테스트 APK 생성 완료, 실기기 회귀·운영 공개·스토어 신청 미진행; GitHub 업로드는 사용자 요청으로 보류
- 운영 백엔드: <https://tteumsae-backend-one.vercel.app>
- APK 페이지: <https://tteumsae-apk-six.vercel.app>

이번 소스는 공통 padding·모서리·글자 역할, 조건 편집 취소 복원, 상세 예상 도착 기준
캐시와 지도 카메라 복원, 실제 요청 로딩과 시간 프리셋, 알림 시스템 차단·전송 복구,
탐색 빈 상태·큰 글자·사진 재시도를 개선했다. [GUI 공통 규칙](docs/30_GUI_DESIGN_SYSTEM.md)과
[추가 감사 조치](docs/31_GUI_FOLLOWUP_AUDIT.md)를 따른다. 공개 APK 페이지와 code34의
과거 검증·배포 이력은 새 code35의 결과가 아니다. 10월 16일 기본 수동 모드는
**270/270 / 58 suites**, lint 오류 **0**·경고 **52**, debug 빌드를 통과했다.
자동 위치 보존 모드도 **270/270 / 58 suites**와 debug 빌드를 통과했다.
APK 경로·식별값과 미완료 항목은 [최신 QA](docs/08_QA_AND_KNOWN_ISSUES.md)를 따른다.

활성 흐름은 `HOME → LOCATION → LOADING → RESULTS → DETAIL`입니다. 출발지·목적지와
절대 도착 마감을 입력하면 고정 여유 10분 뒤 최소 15분 이상 머물 수 있는 후보를
보여주고, 한 곳 선택 시 최대 체류시간과 출발 권장시각을 제공합니다. 기존
`extraTimeMinutes` 추천과 `/api/route` 경유지 0~5곳 계약은 이전 클라이언트 호환용으로
유지합니다.

현재 Android는 compileSdk·targetSdk 36을 사용합니다. 이번 APK는 Android Debug 서명이며
지도 앱 키·로그인 설정이 없는 테스트용입니다. 스토어 제출에는 릴리스 서명·AAB·
실기기 회귀를 별도로 확인해야 합니다.

## 다른 데스크톱에서 시작

Node.js 24.x, Git, Android Studio/JDK 17, Android SDK 36을 먼저 설치한 뒤 PowerShell에서:

```powershell
git clone https://github.com/Newrred/tteumsae.git C:\dev\tteumsae
cd C:\dev\tteumsae
node scripts/workspace-doctor.mjs --init
```

진단기는 누락된 `android/local.properties`와 `backend/.env.local`을 템플릿에서 만들지만
기존 파일은 덮어쓰지 않고 비밀값을 출력하지 않습니다. 표시된 항목은 승인된 보안
채널 또는 Vercel/Supabase/Kakao 콘솔에서 채웁니다. 이어서:

```powershell
cd backend
corepack enable
pnpm install --frozen-lockfile
pnpm test
pnpm run check

cd ..\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Codex는 저장소 루트에서 열면 `AGENTS.md`와 아래 인수인계 문서로 현재 Gate와 다음
작업을 복원할 수 있습니다.

## 저장소 구성

```text
android/   Kotlin·Jetpack Compose Android 앱
backend/   Vercel Functions·TourAPI·Kakao·Supabase 백엔드
download/  테스터용 APK 다운로드 페이지
design/    디자인 시스템과 화면 흐름 참고자료
docs/      제품·구현·운영 인수인계 문서
```

## 먼저 읽을 문서

1. [개발 시작 안내](docs/00_START_HERE.md)
2. [제품 범위](docs/01_PRODUCT_AND_SCOPE.md)
3. [아키텍처](docs/02_ARCHITECTURE.md)
4. [기능 구현표](docs/03_FEATURE_MATRIX.md)
5. [화면 흐름](docs/04_SCREEN_FLOWS.md)
6. [API와 데이터](docs/05_API_AND_DATA.md)
7. [환경과 접근 권한](docs/06_ENVIRONMENT_AND_ACCESS.md)
8. [빌드·테스트·배포](docs/07_BUILD_TEST_DEPLOY.md)
9. [QA와 알려진 문제](docs/08_QA_AND_KNOWN_ISSUES.md)
10. [다음 버전 계획](docs/09_NEXT_VERSION_PLAN.md)
11. [결정 기록](docs/10_DECISION_LOG.md)
12. [공모전 90초 데모와 실기기 QA](docs/11_CONTEST_DEMO_AND_DEVICE_QA.md)
13. [공모전 사용자·알고리즘 검증 양식](docs/12_CONTEST_VALIDATION_TEMPLATES.md)

## 빠른 검증

Android:

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

백엔드:

```powershell
cd backend
corepack enable
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

실기기 UI 스모크 테스트는 USB 디버깅을 허용한 Android 기기를 연결한 뒤 저장소
루트에서 Maestro CLI로 실행합니다. 테스트는 앱 데이터를 초기화하지 않습니다.

```powershell
maestro test .maestro --include-tags smoke
```

현재 플로우는 홈 안내창을 조건부로 닫고 목적지 검색·틈새 발견·설정 UI가 표시되는지
확인한 뒤 지도 화면 캡처를 `android/app/build/maestro-results`에 저장합니다.

2026-08-28 기준 자동 검증은 Android 116/116, Backend 155/155이며 Android lint 오류는
0건입니다. 이 수치는 실기기 지도·OAuth·외부 내비 검증을 대신하지 않습니다.

실제 비밀값, `local.properties`, `.env.local`, Vercel 연결 정보, 키스토어와 APK는 Git에 포함하지 않습니다.
