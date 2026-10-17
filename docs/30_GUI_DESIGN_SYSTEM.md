# GUI 공통 스타일 규칙

작성일: 2026-10-13 / 구현 반영: 2026-10-15 / 검증 갱신: 2026-10-16
대상: `codex/gui-design-system`, Android `0.14.0 / code35`
상태: 기본 수동 모드 단위 테스트·lint·debug 빌드 통과. 새 실기기 회귀·배포 미완료, GitHub 업로드 보류.

## 적용 범위

홈, 입력, 시간 선택, 로딩, 결과, 상세, 장소 탐색, 저장 상세, 설정, 로그인,
프로필, 확인창과 지도 상태 안내에 공통 규칙을 적용했다. 추천 계산과 서버 API
계약은 유지한다. 조건 편집·상세 갱신·알림·탐색 상태 처리의 후속 개선은
[추가 감사 조치 현황](31_GUI_FOLLOWUP_AUDIT.md)을 따른다.

공통 정의는 `ui/theme/LayoutTokens.kt`, 글자 규칙은 `ui/theme/Theme.kt`에 있다.
새 화면은 화면별 숫자를 반복해서 지정하지 않고 아래 역할을 사용한다.

## 여백

기본 간격은 **4 / 8 / 12 / 16 / 24 / 32dp**다.

| 용도 | 규칙 | 코드 |
|---|---|---|
| 화면 좌우·목록 시작/끝 | 24dp | `TteumSpacing.screenHorizontal` |
| 카드 내부 | 사방 16dp | `TteumInsets.card` |
| 입력창 내부 | 좌우 16dp, 위아래 12dp | `TteumInsets.field` |
| 목록 행 내부 | 좌우 16dp, 위아래 12dp | `TteumInsets.row` |
| 버튼 내용 | 좌우 16dp, 위아래 12dp | `TteumInsets.button` |
| 작은 정보 배지 | 좌우 8dp, 위아래 4dp | `TteumInsets.badge` |
| 팝업 내부 | 사방 24dp | `TteumInsets.dialog` |
| 섹션 사이 | 24dp | `TteumSpacing.sectionGap` |
| 카드·항목 사이 | 12dp | `TteumSpacing.itemGap` |
| 아이콘과 글자 사이 | 8dp | `TteumSpacing.inlineGap` |

박스 바깥 여백은 배치용 `TteumSpacing`, 박스 내부는 내용용 `TteumInsets`로
구분한다. `Scaffold`의 전달 padding과 시스템 inset은 그대로 적용한다.

## 모서리 반경

| 용도 | 반경 | 코드 |
|---|---|---|
| 작은 사각 배지 | 8dp | `TteumShapes.badge` |
| 작은 보조 박스 | 12dp | `TteumShapes.compact` |
| 입력창·버튼 | 16dp | `TteumShapes.control` |
| 카드 | 16dp | `TteumShapes.card` |
| 팝업 | 24dp | `TteumShapes.dialog` |
| 바텀시트·하단 탐색 표면 | 위쪽 24dp, 아래쪽 0dp | `TteumShapes.sheet` |

Material 기본 컴포넌트도 `TteumShapes.material`을 테마에서 공유한다.
원형 아이콘, 캡슐 배지·필터, 시트 손잡이는 원형/50% 형태를 사용한다. 카드
선택 표시의 얇은 3dp 스트립은 카드 모서리 역할이 아니므로 기하값을 유지한다.

## 글자

폰트는 기존 Pretendard를 사용한다. 크기/줄 간격은 sp 단위다.

| 역할 | 크기 / 줄 간격 | 굵기 | Material 스타일 |
|---|---|---|---|
| 일반 페이지 제목 | 26 / 34 | Bold | `headlineMedium` |
| 시트·팝업 제목 | 24 / 32 | Bold | `headlineSmall` |
| 섹션 제목 | 20 / 28 | SemiBold | `titleLarge` |
| 카드 제목·입력값 | 18 / 26 | SemiBold | `titleMedium` |
| 작은 제목 | 16 / 24 | SemiBold | `titleSmall` |
| 본문·설정 행 | 16 / 24 | Regular | `bodyLarge` |
| 설명 | 14 / 22 | Regular | `bodyMedium` |
| 보조 설명·출처 | 13 / 20 | Regular | `bodySmall` |
| 주 버튼 | 16 / 24 | SemiBold | `labelLarge` |
| 보조 버튼·필터 | 14 / 20 | Medium | `labelMedium` |
| 작은 라벨 | 13 / 18 | Medium | `labelSmall` |

`fontSize`와 `lineHeight`를 개별 화면에 직접 지정하지 않는다. 진행 상태·선택
강조처럼 의미가 있을 때만 같은 역할 안에서 굵기를 바꾼다. 큰 숫자 제목을
위한 `display*`와 `headlineLarge`도 테마에 정의돼 있다.

## 크기와 화면 대응

- 주 버튼은 최소 56dp, 보조 동작은 최소 48dp, 입력 행은 최소 64dp다.
- 탐색 카드 최소 폭은 `144dp × max(1, fontScale)`다. 일반 글자에서는 좌우
  24dp·간격 12dp를 둔 360dp 화면에 두 열이 들어간다. 큰 글자에서는 최소 폭을
  늘려 `Adaptive`가 열 수를 줄인다. 로딩 카드에도 같은 폭 정책을 적용한다.
- 검색어 입력 중에는 지역·카테고리를 접어 결과 공간을 확보한다. `필터 보기`로
  기존 지역·찜·카테고리를 다시 펼칠 수 있고 정렬도 유지한다. 로그인 시트와
  검색 상태 안내는 가용 높이 안에서 스크롤할 수 있다.
- 결과 선택/상세 버튼의 폭 정책은 공통 16dp 안쪽 여백과 14sp 라벨을 반영한다.
  큰 글자에서 충분한 폭이 없으면 두 행동을 세로로 배치한다.
- 지도 카메라 padding, 시트 앵커, 상태 바, SDK 로고 위치, 시간 휠 치수와
  Bitmap 핀의 기하값은 일반 컴포넌트 padding 규칙으로 치환하지 않는다.
- 사진의 이용조건과 원형/캡슐 형태는 기존 정책을 따른다.

## 확인한 범위와 남은 검증

공통 역할 참조, 남은 직접 여백/글자/반경 설정과 차분 형식을 검토했다. 후속 사용자
요청에 따라 공식 JDK 17·Android SDK 36을 준비하고 10월 16일 기본 수동 모드
`testDebugUnitTest` **270/270 / 58 suites**, `lintDebug` 오류 **0**·경고 **52**,
`assembleDebug`를 통과했다. 자동 위치 보존 모드도 **270/270 / 58 suites**와
`assembleDebug`를 통과했다. APK 식별값은
[최신 QA 기록](08_QA_AND_KNOWN_ISSUES.md)을 따른다. 기존 0.13.1의 검증 수치를
이번 변경의 검증 결과로 사용하지 않는다. 실기기 화면 검증은 아직 완료하지 않았다.
지도 앱 키·로그인 설정과 출시 서명이 없어 debug 테스트 APK를 대상으로 하며,
지도·OAuth 정상 동작과 기존 출시본 위 업데이트 설치 검증은 포함하지 않는다.

남은 화면 검증은 320/360/384dp, 글자
1.0/1.3/2.0, 키보드 열림/닫힘, 입력·선택·상세 왕복과 지도 상태 안내를 확인한다.
