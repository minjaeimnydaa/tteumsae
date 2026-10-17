# 틈새 Android MVP

강원도 여행 중 목적지에 늦지 않고 들를 수 있는 장소를 추천하는 Android
네이티브 앱입니다.

## 현재 소스 — 2026-10-16 검증 (10월 15일 GUI 구현 반영)

Android **0.14.0 / code35**의 기본 수동 모드 단위 테스트·lint·debug 빌드를 통과했습니다. 활성 흐름은
`HOME → LOCATION → LOADING → RESULTS → DETAIL`이며 출발지·목적지·절대 도착 마감과
선택 관심 필터를 한 입력 화면에서 받습니다. 자동차 경로의 한 곳 선택, 최대 체류시간,
출발 권장시각과 선택형 출발 알림을 제공합니다. 기본 빌드는 수동 출발지 선택이며
자동 GPS 공급자는 별도 소스로 보존합니다.

이번 후보에는 공통 padding·반경·글자 규칙, 조건 편집 취소 복원, 상세 예상 도착 시각
캐시, 상세 왕복 지도 위치 복원, 실제 요청 로딩·시간 프리셋, 알림 차단·설정·실제 전송
차단된 유효 여행의 1회 복구, 탐색 상태·큰 글자·사진 재시도를 반영했습니다. 아래 초기 MVP 이력은 현재
동작이나 다음 개발 목표가 아닙니다. 새 후보 검증과 기기 확인은
[최신 QA](../docs/08_QA_AND_KNOWN_ISSUES.md), 구현 내용은
[GUI 공통 규칙](../docs/30_GUI_DESIGN_SYSTEM.md)과
[추가 감사 조치](../docs/31_GUI_FOLLOWUP_AUDIT.md)를 따릅니다.

10월 16일 기본 수동 모드는 **270/270 / 58 suites**, lint 오류 **0**·경고 **52**,
`assembleDebug`를 통과했습니다. Android Debug 서명과 code35·0.14.0·target36,
FINE/COARSE/BACKGROUND 위치 권한 부재를 확인했습니다. 자동 위치 보존 모드도
**270/270 / 58 suites**와 debug 빌드를 통과했습니다. 현재 APK에는 지도 앱 키·로그인 설정이 없으며
실기기 회귀·출시 서명·기존 출시본 위 업데이트 설치는 완료하지 않았습니다.
사용자 요청으로 GitHub 업로드를 보류했고 원격 저장소는 변경하지 않았습니다.

## 초기 MVP 구현 이력

- Kotlin + Jetpack Compose
- 활성 경로 탐색 모드 (`HOME → LOCATION → CONDITIONS → LOADING → RESULTS → DETAIL`)
- 근처 탐색 모드는 코드에만 남은 비활성 레거시
- 출발지와 목적지 카카오 키워드 검색
- Vercel 운영 백엔드 연결
- TourAPI 기반 강원도 장소 추천
- 활성 경로 탐색은 카카오 자동차 이동시간 적용
- 비활성 근처 탐색 레거시의 도보 분기는 거리 기반 추정시간 사용
- 카카오맵 Android SDK 기반 실제 지도 표시
- 시간 입력 없이 내부 호환값 `extraTimeMinutes=1,440`, `safetyBufferMinutes=15` 사용
- 장소 유형별 기본 머무름을 적용한 레거시 시간 필터와 결과 카드
- 카카오맵 앱 딥링크
- 네트워크 실패 메시지 및 재시도

당시 초기 MVP는 신규 도착 마감 제품 플로우가 아니었습니다. 당시 다음 목표는 LOCATION에서 목적지와
절대 도착 마감을 받고, 한 곳 선택 후 `이동 기준 최대 체류시간`과 `출발 권장시각`을
표시하는 것이었으며 현재는 구현됐습니다. 확정 기준은
`docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md`를 따릅니다.

서버용 TourAPI 키와 카카오 REST API 키는 Android 앱에 포함되지 않습니다.
앱은 공개된 틈새 백엔드 API만 호출합니다.
카카오맵 네이티브 앱 키는 Git에 포함되지 않는 `local.properties`의
`KAKAO_MAP_NATIVE_APP_KEY` 값으로 주입합니다.

## 운영 API

```text
https://tteumsae-backend-one.vercel.app
```

배포 환경별 주소를 분리하려면
`app/build.gradle.kts`의 `BuildConfig.API_BASE_URL`을 빌드 타입별로
설정합니다.

## 실행 환경

- 프로젝트의 Android Gradle Plugin을 지원하는 Android Studio
- JDK 17
- Android SDK compile 36 / target 36

저장소의 실제 빌드 설정 기준입니다. 도구 버전은 `build.gradle.kts`와
`gradle/wrapper/gradle-wrapper.properties`를 함께 확인합니다.

| 도구 | 버전 |
|---|---|
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin·Compose compiler plugin | 2.3.20 |

이 도구 버전 표는 현재 파일 설정이며 빌드 성공 기록을 뜻하지 않습니다.

이 폴더를 Android Studio에서 열고 Gradle Sync 후 `app` 구성을
실행합니다.

## 주요 파일

```text
app/src/main/java/com/tteumsae/app/
├── MainActivity.kt
├── data/
│   ├── TteumsaeApi.kt
│   ├── DataContracts.kt
│   └── SamplePlaces.kt
├── domain/
│   ├── Models.kt
│   └── TimeSafeEngine.kt
└── ui/
    ├── TteumsaeApp.kt
    └── theme/Theme.kt
```

## 초기 MVP 당시 출시 작업 목록 (이력)

현재 진행 순서는 [다음 버전 계획](../docs/09_NEXT_VERSION_PLAN.md)과 최신 QA가 우선합니다.

- 결과 카드·지도 마커·카메라 동기화 실기기 회귀
- 설정 탭 최종 정보 구조와 디자인
- 온보딩과 위치 권한 영구 거부 안내
- 영업시간·휴무·입장 마감 정규화
- 무장애 여행정보 운영 활성화와 상세 화면 실데이터 검증
- 주변 공영주차장 운영 적재와 상세 화면 실데이터 검증
- 개인정보처리방침 및 위치기반서비스 고지
- 앱 아이콘, 스플래시, 접근성, 다크 모드
- API 36 동작의 실제 기기 회귀 테스트
- Play App Signing용 릴리스 서명과 AAB 생성
