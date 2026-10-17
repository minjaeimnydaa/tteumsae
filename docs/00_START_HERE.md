# 새 담당자 시작 안내

## 최신 작업 상태 — 2026-10-16 code35 기본 수동 모드 빌드 검증 통과

현재 소스는 Android **0.14.0 / code35**다. 공통 여백·padding·반경·글자 규칙과
조건 편집 취소 복원, 상세 예상 도착 기준 캐시, 지도 카메라 복원, 실제 요청 로딩,
시간 프리셋, 알림 차단 안내·전송 복구, 탐색 빈 상태·큰 글자·사진 재시도를 반영했다.
10월 15일 구현 반영 후 10월 16일 기본 수동 모드는 **270/270 / 58 suites**,
lint 오류 **0**·경고 **52**, `assembleDebug`를 통과했다. 자동 위치 보존 모드도
**270/270 / 58 suites**와 `assembleDebug`를 통과했으며 새 실기기 회귀는 완료하지 않았다.
APK 경로·크기·SHA-256은 최신 QA를 따른다.
공식 JDK 17·Android SDK 36 준비는 완료했다. 현재 로컬에는 지도 앱 키·로그인 설정과
출시 서명이 없어 Android Debug 서명 테스트 APK를 생성했다. 지도·OAuth 정상 동작과
기존 출시본 위 업데이트 설치는 검증하지 않았다. 사용자 요청으로 GitHub 업로드를
보류했으며 원격 저장소 변경은 없다.

먼저 [공통 스타일](30_GUI_DESIGN_SYSTEM.md), [추가 감사 조치](31_GUI_FOLLOWUP_AUDIT.md),
[최신 QA](08_QA_AND_KNOWN_ISSUES.md)를 읽는다. 아래 code34·이전 버전의 APK·기기·배포
수치는 해당 배포본의 이력이며 code35 검증 증거가 아니다. 운영 백엔드·DB·Cron·스토어
심사·공개 다운로드의 변경을 이번 Android 코드 개선에 포함하지 않는다.

## 배포 이력 — 2026-09-13 code34 사용성 보완·최종 기기 검사 완료

사용자 승인으로 도로명·지번 검색, 지속 지도 위 단일 결과 시트, 선택 버튼을 보완했다.
Android **0.13.1 / code34**의 최종 30E0 빌드는 수동·자동 각각 **227/227 / 51 suites**,
수동 lint 오류0/경고48·서명 빌드·데이터 유지 설치를 통과했다. 마지막 주소/시트 흐름은
**1/1(73초, 8장)** 재실행 통과했고, 묶음 핀의 하단 시트 가림·상단 버튼 겹침 해소도 실기기로 확인했다.
앞선 검색 공급자 응답 지연 실패와 같은 검색 재시도 복구는 별도 이력으로 보존한다.
선택·상세·알림 토글 검사는 이전 E55 후보, 큰 글자 검사는 더 앞선220개 후보의 증거와 구분한다.
주소 검색 backend는 **265/265·검사121개** 후 운영 반영 및 실제 주소/장소명 응답을 확인했다.
주소 backend는 `8e3ae42`, Android/UI·검증 문서·다운로드 페이지는 **`d877f32`로 origin/main 반영 완료**다.
**06:39 공개 APK를 code34로 교체하고 익명 재다운로드 해시 일치를 확인했다.** 원스토어 등록본은
code32, 기존 오전 11시 공유 묶음은 code33이다. 심사 신청 완료가 아니다.
상세 뒤로에서 시트·목록은 복원하나 카메라는 재생성된다. 먼저 [code34 변경 안내](28_CODE34_REVIEW_ADDENDUM.md)를 읽고
[사용성 보완 현황](27_RELEASE_UX_REFINEMENT.md)에서 최종 파일·검증 경계를 확인한다.
아래 code33 및 이전 버전 기록은 각 당시의 검증 이력으로 보존한다.

## 최신 승인 경계 — 2026-09-13 오전 11시 팀 검토

최종 심사 신청은 보류한다. 오전 11시 팀 검토 후 사용자에게 새 명시 지시를 받아야 하며,
이전 포괄 승인이나 시간이 됐다는 사실만으로 신청·공개하지 않는다.
[팀 검토 안내](23_TEAM_REVIEW_2026-09-13.md), [원스토어 인계](24_ONESTORE_FINAL_HANDOFF.md),
[공모전 인계](25_CONTEST_FINAL_HANDOFF.md), [남은 위험](26_RELEASE_REVIEW_RISKS.md)를 먼저 읽는다.
Git 제외 공유 묶음은 `output/team-review-20260913-1100` 및 같은 이름의 ZIP으로 준비한다.

## 2026-09-13 출시 후보 0.13.0 / code 33

현재 작업 트리는 기본 빌드를 수동 출발지 선택으로 전환하고 기존 자동 GPS 구현은 별도
소스로 보존한다. 사진 이용조건·오프라인 라이선스 고지와 접힌 검색 지도 로고 위치를 보완해
수동·자동 각각 **195/195**, 수동 lint 오류0/경고48, 서명 수동 APK 업데이트 설치와
카카오 로그인 유지까지 확인했다. 접힌 검색 지도·다시 펼치기·홈 복귀를 확인했고,
실제 출발 알림은 약3분 지연 수신 후 올바른 카카오맵 경로로 연결됐다. 정시보장은 아니다.
운영 backend **253/253**, 공개 APK는 `https://tteumsae-apk-six.vercel.app/`.
공개 다운로드는 code33으로 교체하고 공개 서버에서 재다운로드한 파일 해시 일치를 확인했다.
ONE store는 아직 code32이며 최종 code33 APK와 권리 PDF 교체가 남았다.
ONE store 공통/판매정보와3+ 연령등급 저장은 완료했으나 심사 신청·승인 완료가 아니다.
대한민국 배포국가 일반 저장 확인창은 사용자 직접 클릭 대기 중이다.
[위치 빌드 모드](18_LOCATION_BUILD_MODES.md)를 먼저 확인한다.
아래 0.12.9 GPS·실기기 이력은 이전 배포본 기록이며 신규 수동 APK 검증·원스토어 심사 완료가 아니다.
최신 진행 상태는 [출시 스프린트](16_RELEASE_SPRINT_2026-09-13.md)를 따른다.

기준일: `2026-09-11`
Android 버전: `0.12.9` (`versionCode 30`)

사용자 피드백 후 장소 탐색 스크롤 안내·로딩/끝 표시를 보강하고 결과 시트 전환을
화면 공통 `지도 보기 ↔ 목록 보기`로 대체했다. 0.12.9 자동 검증·기기 설치와 SM-F776N의
반복 전환·선택 유지·100→200곳 추가 로딩·320dp/글자 2.0 주요 버튼 접근을 확인했다.
핀/묶음 핀 직접 조작 등 남은 경계는 QA 문서를 따른다. 아래 0.12.8 QA는 이전 구현의 이력이다.

2026-09-11 UX 감사 후 검색 피드백·결과 상단과 시트·계산 기준·주차장 요약을 개선했다.
SM-F776N에서 실제 검색·선택·상세 왕복과 작은 폭/큰 글자의 주요 버튼 접근을 확인했다.
다기종·가로 화면·알림 실제 수신까지 완료한 것은 아니다. 후속 항목은
`09_NEXT_VERSION_PLAN.md` 마지막 UX 실행 상태와 `08_QA_AND_KNOWN_ISSUES.md`를 따른다.
Android 패키지: `com.tteumsae.app`

이 저장소가 Android 앱, 백엔드, 다운로드 페이지와 인수인계 문서의 유일한 기준 원본입니다. 다른 폴더에 남은 APK나 과거 소스 사본을 기준으로 작업하지 마세요.

## 1. 현재 수준을 먼저 이해하기

틈새는 다음이 실제로 연결된 기능형 MVP입니다.

- Kotlin·Jetpack Compose Android 앱
- Kakao Map Android SDK 지도
- GPS 권한과 현재 위치는 별도 `automatic` 빌드에 보존하며, 현재 수동 제출본에서는 사용하지 않음
- Vercel Functions 백엔드
- Supabase 장소 DB
- 한국관광공사 TourAPI 동기화
- Kakao Local 검색·역지오코딩
- Kakao Mobility 차량 경로
- 이동 경로 주변 추천과 카카오맵 딥링크

현재 소스의 활성 경로 흐름은
`HOME → LOCATION → LOADING → RESULTS → DETAIL`입니다. `LOCATION` 한 화면에서
출발지, 목적지, 절대 도착 마감과 선택 관심 필터를 입력합니다. 결과에서는 경유지
한 곳만 선택할 수 있습니다. 지도 핀은 장소 종류를 그림으로 구분하고, 묶음 핀은
현재 배율에서 가까운 후보를 대표 카테고리와 개수로 보여줍니다. 추가 이동시간,
최대 체류시간과 늦어도 출발할 시각은 비교하기 쉬운 결과 목록에서 제공합니다.
선택하지 않고 목적지로 바로 안내할 수도 있습니다.

장소 단건 상세는 TourAPI 공통·소개·사진·반려동물 정보에 더해 `detailInfo2` 반복 안내와
사진별 공공누리 유형을 처리한다. 반복 안내는 `추가 안내`에 값이 있을 때만 표시하고,
사진 권리 문구는 실제 대표 사진 URL과 메타데이터가 일치할 때만 표시한다. migration 007은
운영 DB에 적용됐으며 새 presentation 배치가 각 장소를 보강한 뒤 값이 있는 상세부터 표시된다.

한국관광공사 관광지 집중률 예측은 2026-09-08 운영 동기화에서 2,880건을 저장했고,
강릉 장소와 exact unique name으로 일치한 450건만 상세 보조 정보로 연결했다. 기상청
단기예보도 운영 기능 플래그를 켰으며 순포해변 도착 무렵 예보의 기온·강수확률·하늘·바람과
격자 캐시 저장을 확인했다. 둘 다 추천 순위나 방문 가능 계산은 바꾸지 않는다. 혼잡도는
오매칭 표본 검수가 더 필요하므로 아직 예약 Cron에 넣지 않는다.

`KorWithService2` 무장애 여행정보도 별도 stage와 migration 010으로 운영 코드·DB가 준비돼 있다.
강원도 무장애 목록에 포함된 장소만 `detailWithTour2`를 조회하고, 기존 TourAPI content ID와
일치하는 장소의 정규화 안내만 상세의 `접근성 안내`에 조건부 표시한다. 운영 migration과
활용신청 승인 뒤 첫 20건을 운영 동기화해 기존 카탈로그 13곳과 연결했고 실패는 없었다.
강릉 `정은숙초당순두부` 상세에서 접근로·대중교통·출입구 안내가 공개 API와 올바른 제목으로
반환되는 것까지 확인했다. 전체 939건 순회와 실기기 화면 확인 전에는 Cron에 예약하지 않는다.
기본 검색 화면이나 추천 점수에는 영향을 주지 않는다.

전국주차장정보표준데이터는 별도 `stage=parking`과 migration 011로 운영한다.
강릉시 제공기관의 공영주차장 170건 중 좌표가 유효한 167건을 서버 전용으로 적재했고,
장소 반경 1km 안의 가까운 3곳만
단건 상세의 `주변 공영주차장`에 조건부 표시한다. 거리는 도보 경로가 아닌 직선거리임을
명시하며 장소 자체의 TourAPI 주차 문구와 합치지 않는다. 강릉항회센터 상세에서 3곳 반환까지
운영 검증했으며 실기기 화면 확인은 남아 있다. 추천·지도 핀·입력 단계에는 영향을 주지 않는다.

활성 경로 흐름의 이동수단은 자동차로 고정한다. 서버와 Android에 남은 `WALK` 계약은 과거·저수준
호환용이며 사용자 선택지나 현행 보강 계획으로 확장하지 않는다.

이 흐름의 API 시간 모델은 `ARRIVAL_DEADLINE_V1`입니다. 서버가 요청 수신시각을
기준으로 고정 안전여유 10분과 최소 체류 15분을 적용합니다. 기존
`extraTimeMinutes` 계약과 `/api/route`의 경유지 0~5개 지원은 이전 클라이언트
호환을 위해 남아 있지만 활성 제품 UI에서는 사용하지 않습니다. 제품 기준은
[도착 마감 플로우 설계](superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md),
실행 순서는 [다음 버전 계획](09_NEXT_VERSION_PLAN.md)을 따릅니다.

Play Store 제출 준비는 끝나지 않았습니다. 로컬 조직 소유 업로드 키와 서명 AAB,
릴리스 서명 실기기의 핵심 경로·카카오맵 전환까지는 검증했습니다. 업로드 키의 독립된
보안 백업, Play App Signing 인증서의 카카오 키 해시, 실제 예약 만료 시각의 화면 알림 관찰,
정책 URL 최종 점검과 스토어 리소스가 남아 있습니다.

이전 배포본 확인용 APK는 `tteumsae-apk-six.vercel.app`에 배포돼 있습니다. code35 공개
배포본이 아닙니다. APK는 Git에서 제외되므로 소스가 바뀌면 새 파일명으로 다시 빌드·배포하세요.

## 2. 필요한 프로그램

- Git
- Android Studio Ladybug 이상 권장
- JDK 17 또는 Android Studio 내장 JBR
- Android SDK Platform 36과 SDK Build Tools
- Node.js 24.x
- Corepack 또는 pnpm

복제 경로는 한글과 공백이 없는 `C:\dev\tteumsae`를 권장합니다. 기존 한글 경로에서는 Gradle JUnit 워커가 테스트 클래스를 읽지 못하는 `ClassNotFoundException`이 발생했습니다.

주요 버전:

| 항목 | 버전 |
|---|---|
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.20 |
| Java/JVM | 17 |
| Compose BOM | 2024.12.01 |
| Kakao Maps Android SDK | 2.14.0 |
| Android SDK | min 26 / compile 36 / target 36 |

## 3. 저장소 복제 후 보안 원칙

새 Windows 데스크톱에서는 저장소 루트에서 다음 진단기를 먼저 실행할 수 있습니다.

```powershell
node scripts/workspace-doctor.mjs --init
```

템플릿이 없는 로컬 설정만 생성하고 기존 파일은 보존합니다. 진단 출력에는 값 대신
누락된 변수 이름만 표시됩니다. Codex로 이어서 작업할 때는 저장소 루트를 열어
`AGENTS.md`가 이 문서와 `09_NEXT_VERSION_PLAN.md`를 안내하도록 합니다.

승인된 비밀관리 채널에서 받은 env 파일에 Android 로그인용 공개 설정이 함께 있으면
서버 비밀을 복사하지 않고 아래 두 값만 `local.properties`로 가져올 수 있습니다.

```powershell
node scripts/workspace-doctor.mjs --import-android-public-env C:\secure\tteumsae.env
```

선별 대상은 `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`뿐이다. Sensitive로 저장된
Vercel 변수는 CLI에서 원문을 다시 받을 수 없으므로 Supabase 프로젝트의 API Keys
화면, 승인된 Supabase 연결 도구 또는 조직 비밀관리 도구에서 확보해야 한다.

Git에 실제 키를 추가하지 마세요. 다음 파일은 각 개발자가 직접 만들거나 서비스에서 내려받습니다.

- `android/local.properties`
- `backend/.env.local`
- Android 디버그·릴리스 키스토어
- Vercel의 `.vercel/` 연결 폴더

키 값은 메신저나 문서가 아닌 승인된 비밀 관리 채널로 전달합니다. 자세한 소유권과 환경변수는 [환경과 접근 권한](06_ENVIRONMENT_AND_ACCESS.md)을 따릅니다.

## 4. Android 준비와 첫 빌드

1. Android Studio에서 `android/` 폴더를 엽니다.
2. `android/local.properties.example`을 `android/local.properties`로 복사합니다.
3. 로컬 Android SDK 경로와 카카오 네이티브 앱 키를 입력합니다.

```properties
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
KAKAO_MAP_NATIVE_APP_KEY=별도_전달받은_네이티브_앱_키
```

4. Gradle Sync 후 다음 명령을 실행합니다.

```powershell
cd C:\dev\tteumsae\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat assembleDebug
```

APK 위치:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

2026-09-07 공간 정보 보강 운영 배포 뒤 `testDebugUnitTest` 145/145,
`lintDebug` 오류 0과 debug APK 빌드를 확인했습니다. 자동 테스트는 실제 지도,
OAuth, 카카오맵 복귀와 알림 실기기 QA를 대신하지 않습니다.

2026-09-08 실데이터 활성화 기준 `0.12.6`은 Android 145/145, lint 오류 0·경고 44와
debug APK 빌드, Backend 215/215 및 프로젝트 검사 118개를 통과했습니다. 혼잡도·기상청
날씨·주변 공영주차장은 운영 상세 API에서 실제 값을 확인했습니다. 무장애 정보도 첫 페이지
13곳을 적재했고 강릉 표본 상세에서 접근로·대중교통·출입구 안내를 확인했습니다.

결과 화면의 최신 동작과 데이터 원칙은 [지도 후보·결과 목록·장소 상세 V2](13_RESULTS_MAP_DETAIL_V2.md),
실기기 화면 회귀 절차는 [빌드·테스트·배포](07_BUILD_TEST_DEPLOY.md)를 기준으로 합니다.

같은 날 새 업로드 키로 `bundleRelease`와 `assembleRelease`를 통과하고, SM-F776N에
release APK를 설치해 GPS 거부 직접 입력부터 한 곳 선택, 실제 카카오맵의
출발지·경유지·도착지 전달까지 Maestro로 재검증했습니다. 키스토어와
`android/keystore.properties`는 로컬 Git 제외 파일이며 별도 보안 백업이 필요합니다.

같은 release APK에서 카카오·Google 로그인 동의와 앱 복귀, 앱 프로세스 재시작 후
각 세션 복원도 확인했습니다. Kakao Developers의 닉네임·프로필 사진·이메일 동의항목은
현재 모두 선택 동의로 설정돼 있습니다. 선택 경유지의 `오전 4시 35분까지 출발` UI가
알림 권한이 허용된 시스템 AlarmManager의 `오전 4시 30분` RTC_WAKEUP 예약으로
전달되는 것도 확인했습니다.

## 5. 카카오 Android 플랫폼 확인

카카오 네이티브 지도는 다음 두 값이 일치해야 합니다.

- 패키지명: `com.tteumsae.app`
- 설치 APK를 서명한 키의 키 해시

새 컴퓨터가 새 디버그 키스토어를 만들면 키 해시도 달라집니다. 다음 중 하나를 선택합니다.

1. 기존 디버그 키스토어를 보안 채널로 전달받습니다.
2. 새 디버그 키 해시를 Kakao Developers 애플리케이션에 추가합니다.

키스토어는 Git에 올리지 않습니다.

현재 카카오 앱의 `com.tteumsae.app` Android 플랫폼에는 2026-08-28 생성한 로컬
릴리스 업로드 키 해시가 등록돼 있습니다. Google Play에 첫 AAB를 올리고 Play App
Signing이 활성화되면 Play가 실제 배포 APK에 사용하는 별도 앱 서명 인증서의 키 해시도
같은 플랫폼에 추가해야 합니다.

## 6. 백엔드 준비와 검증

```powershell
cd C:\dev\tteumsae\backend
corepack enable
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

로컬에서 실제 외부 서비스를 호출해야 할 때만 `.env.example`을 참고해 `.env.local`을 준비합니다. 키 값은 Vercel 운영 환경에서 직접 확인하거나 소유자에게 접근 권한을 요청합니다.

Vercel 연결이 필요한 경우:

```powershell
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
```

팀 `newrreds-projects`, 프로젝트 `tteumsae-backend`를 선택합니다.

## 7. 첫 실행 스모크 테스트

실제 Android 기기에 디버그 APK를 설치하고 다음 순서로 확인합니다.

1. 앱 이름 `틈새`와 홈 지도 표시
2. GPS 권한 허용 후 현재 위치 표시
3. GPS 버튼을 다시 눌러 비활성화
4. 출발지·강원도 목적지와 절대 도착 마감 입력
5. 선택 관심 필터 또는 필터 없이 추천 시작
6. 추천 결과와 `+N분` 지도 후보 핀 표시
7. 경유지 없음·한 곳 선택·교체·해제와 최대 체류·출발 마감 확인
8. 선택형 출발 5분 전 알림의 허용·거부 확인
9. 카카오맵에 직행 또는 한 곳 경유·최종 목적지 전달
10. 장소 상세, 저장과 결과 화면 복귀
11. 장소 둘러보기 이미지·추가 로딩·저장 동작
12. 설정의 위치 권한, 카카오맵 실행, 캐시 삭제, 문의 메일

상세한 케이스는 [QA와 알려진 문제](08_QA_AND_KNOWN_ISSUES.md)를 사용합니다.

## 8. 문서 역할과 읽을 순서

- `00_START_HERE`, `03_FEATURE_MATRIX`, `04_SCREEN_FLOWS`: 현재 구현 사실
- `01_PRODUCT_AND_SCOPE`, `10_DECISION_LOG`, 최신 `superpowers/specs`: 확정 제품 목표
- `09_NEXT_VERSION_PLAN`, 최신 `superpowers/plans`: 실행 순서
- 과거 계획과 데모 문서는 최신 결정과 충돌하면 기준으로 사용하지 않음

1. [제품 범위](01_PRODUCT_AND_SCOPE.md)
2. [아키텍처](02_ARCHITECTURE.md)
3. [기능 구현표](03_FEATURE_MATRIX.md)
4. [화면 흐름](04_SCREEN_FLOWS.md)
5. [API와 데이터](05_API_AND_DATA.md)
6. [QA와 알려진 문제](08_QA_AND_KNOWN_ISSUES.md)
7. [다음 버전 계획](09_NEXT_VERSION_PLAN.md)
8. [결정 기록](10_DECISION_LOG.md)
9. [UI 사용성 개선 기준](11_UI_USABILITY_REFRESH.md)

기능을 수정하면 구현표·QA·변경 기록도 같은 커밋에서 갱신합니다.
