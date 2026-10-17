# GUI 추가 감사와 조치 현황

감사일: 2026-10-14 / 구현 반영일: 2026-10-15 / 검증 갱신일: 2026-10-16
대상: `codex/gui-design-system`, 원본 `Newrred/tteumsae@55b5755`
빌드 대상: Android `0.14.0 / code35`

공통 padding·반경·글자 규칙 적용 후 찾은 열 가지 항목을 아래와 같이 구현했다.
최초 감사 때의 미구현·빌드 생략 상태는 후속 개선·빌드 요청으로 변경됐다.
10월 16일 기본 수동 모드 단위 테스트·lint·debug 빌드와 자동 위치 보존 모드의
단위 테스트·debug 빌드를 통과했다. 실제 화면·지도·알림·외부 앱 회귀까지 완료했다는 뜻은
아니다. 확정 결과는 [QA 기록](08_QA_AND_KNOWN_ISSUES.md)에서 확인한다.

## 1. 탐색 검색 중·실패·최종 무결과

첫 페이지에 검색 조건과 맞는 장소가 없어도 뒤 페이지가 남아 있으면 최종 무결과로
단정하지 않는다. `CatalogPresentationPolicy`가 초기 로딩·초기 실패·추가 검색 중·
추가 조회 실패·최종 무결과·결과 목록을 구분한다. 빈 결과에서도 `다시 시도`로
검색어·지역·카테고리를 유지하며 다음 페이지를 재요청한다.

찜은 로컬 Room 자료로 판단한다. 카탈로그의 로딩·실패가 찜을 가리지 않고,
찜만 보는 동안 전체 카탈로그의 추가 페이지 조회와 페이지 안내를 만들지 않는다.

근거: `ui/saved/CatalogPresentationPolicy.kt`, `SavedPlacesScreen.kt`,
`CatalogPresentationPolicyTest.kt`, 기존 `CatalogContinuationTest.kt`.

## 2. 조건 편집과 새 검색

결과에서 입력으로 돌아갈 때 `RouteFlowViewModel.beginEditing()`이 기존 입력·결과·
선택을 메모리 스냅샷으로 보존한다. 편집을 취소하면 원래 입력과 결과·선택으로
돌아가고 기존 출발 알림도 유지한다. 새 조건으로 검색을 실제 실행할 때 이전 결과·
선택·알림을 정리한다. 명시적인 새 검색은 기존 초기화 경로를 사용한다.

편집 스냅샷은 프로세스를 종료한 뒤 결과를 영구 복원하는 저장소가 아니다.
추천 payload가 없는 앱 복원은 기존처럼 안전한 입력 화면으로 되돌린다.

근거: `ui/route/RouteFlowViewModel.kt`, `RouteFlowUiState.kt`, `RouteFlowViewModelTest.kt`,
`ui/TteumsaeApp.kt`의 `editRouteConditions`·`leaveRouteInput`.

## 3. 상세 예보의 예상 도착 시각

상세 캐시 키를 장소 ID 단독에서 `장소 ID + 예상 도착 epoch`로 바꿨다.
예상 도착은 `경로 계산 시각 + 첫 구간 이동시간`이다. 경로가 바뀌어 이 키가 달라지면
기존 상세를 그대로 쓰지 않고 새 도착 시각으로 API를 조회한다. 같은 키의 정상 상세는
재사용하고 실패는 기존 재시도 동작으로 복구한다. 취소된 요청의 예외는 삼키지 않는다.

근거: `ui/route/RouteDetailRequestKey.kt`, `RouteDetailRequestKeyTest.kt`,
`ui/TteumsaeApp.kt`의 DETAIL 분기.

## 4. 출발 알림의 시스템 상태와 만료

런타임 권한, 앱 전체 알림, 출발 알림 채널 차단을 구분한다. 차단되면 예정 시각을
수신 가능한 예약처럼 표시하지 않고 알림 설정으로 가는 행동을 제공한다. 앱 시작·복귀와
30초 주기 확인에서 시스템 상태 및 현재 여행의 출발 권장시각·만료를 다시 확인한다.

알림 전송 시 실제 차단된 여행의 토큰만 기기에 보존한다. 전송이 허용된 상태에서
앱 시작·복귀·주기 확인이 이 토큰을 한 번 회수해 유효한 여행만 재예약한다. 예약 시각이
지났어도 출발 권장시각 전이면 즉시 알림을 시도하고, 이미 지난 출발 권장시각이나
만료 여행을 되살리지 않는다. 정상 전달한 알림은 다시 예약하지 않으며, 여행 교체·
선택 해제·만료 시 이전 차단 토큰을 폐기한다.
새로운 알림 켜기는 현재 경로·선택·권한을 확인한다. 절전으로 인한 전달 지연 안내와
`setAndAllowWhileIdle` 정책은 유지한다.

근거: `reminder/ReminderDeliveryPolicy.kt`, `ReminderNotifications.kt`,
`DepartureReminderCoordinator.kt`, `ui/route/ReminderPresentation.kt`, 각 회귀 테스트.
차단·해제·알림 실제 수신은 실기기 회귀가 필요하다.

## 5. 실제 요청에 맞는 로딩 피드백

시간만 지나면 단계별 체크를 늘리던 진행 표현을 제거했다. 요청 중에는 진행 표시와
설명을 보여주고 10초가 지나면 지연·취소 안내를 제공한다. 성공 후 후보가 있으면
`틈새를 찾았어요`, 0건이면 `경로를 확인했어요`로 완료 문구를 구분한다.

근거: `ui/route/LoadingScreen.kt`, `RouteLoadingPresentation.kt`,
`RouteLoadingPresentationTest.kt`.

## 6. 상세 왕복 지도 위치 복원

결과 지도 카메라 이동이 끝나면 중심 위도·경도·확대 배율을 저장한다. 결과 UI의
저장 가능 상태를 상세 왕복과 조건 편집 취소 동안 보존하고, MapView 재생성 시
해당 카메라에서 시작한다. 복원 직후 자동 경로 맞추기가 이 위치를 덮지 않게 한다.
새 검색은 이전 결과 UI 상태를 초기화한다. SDK 로고·MapView 생명주기는 유지한다.

근거: `ui/route/RouteMapCameraSnapshot.kt`, `RouteMapCameraSnapshotTest.kt`,
`ui/TteumsaeApp.kt`의 결과 상태 보관·지도 카메라 처리. 실제 카메라 왕복은 기기 확인이 필요하다.

## 7. 큰 글자 카드 폭

탐색·로딩 카드 최소 폭을 `144dp × max(1, fontScale)`로 계산한다. 일반 360dp 화면의
두 열을 유지하고 큰 글자에서는 카드가 넓어져 열 수가 줄어들게 한다. 글자를
임의로 축소하지 않는다. 공통 카드 padding·모서리는 그대로 사용한다.

근거: `CatalogPresentationPolicy.kt`, `CatalogPresentationPolicyTest.kt`, `SavedPlacesScreen.kt`.

## 8. 키보드와 시트 가용 높이

탐색 검색 중에는 지역·카테고리를 접고 현재 조건 요약과 `필터 보기`를 표시한다.
펼치면 기존 지역·찜·카테고리 기능을 그대로 쓸 수 있고 정렬도 유지한다. 검색 중·
오류·무결과 안내와 로그인 시트는 가용 높이 안에서 스크롤한다. 키보드 여백은
탐색 화면의 공통 콘텐츠에 적용한다.

근거: `ui/saved/SavedPlacesScreen.kt`, `ui/account/LoginSheet.kt`.
좁은 화면·가로 화면·큰 글자의 실제 버튼 접근은 화면 QA가 남아 있다.

## 9. 사진 다운로드와 재시도

URL별 사진 없음·로딩·표시 가능·실패를 구분한다. 사진 없음은 기존 기본 이미지,
로딩은 중립 배경의 진행 표시, 실패는 다시 불러오기 행동으로 표시한다. 재시도 때
해당 URL의 비트맵·메모리 캐시를 비우고 새로 요청한다. URL 변경은 이전 요청을
취소하고 상태를 교체한다. 실제 사진 출처·이용조건과 크롭 허용 조건은 유지한다.

근거: `ui/saved/SavedPlaceComponents.kt`의 `SavedPlaceImage`, 기존 사진 권리 회귀 테스트.
느린 응답·손상 이미지·실패 후 성공은 수동 회귀가 필요하다.

## 10. 오늘·내일과 빠른 도착 마감

도착 마감을 `오늘/내일 HH:mm까지`로 입력 영역에 분명히 표시한다. `30분 후`와
`1시간 후`는 버튼을 누른 현재 시각부터 계산한다. 기존 시간 휠과 15분~24시간
유효 범위는 유지한다. 별도 날짜 선택기는 추가하지 않았다.

근거: `ui/route/ArrivalDeadlinePresentation.kt`, `ArrivalDeadlinePresentationTest.kt`,
`LocationScreen.kt`, 기존 `ArrivalDeadlinePolicyTest.kt`.

## 검증 상태

- 기본 수동 모드: 10월 16일 **270/270 / 58 suites**, lint 오류 **0**·경고 **52**, `assembleDebug` 통과. APK 버전·서명·크기·해시는 QA 기록을 따른다.
- 자동 위치 보존 모드: 10월 16일 **270/270 / 58 suites**, `assembleDebug` 통과. 자동 모드 lint는 이번 검증 범위에 포함하지 않았다.
- 실기기: 새 코드의 지도·알림·OAuth·카카오맵 전환과 320/360/384dp·글자 1.0/1.3/2.0 회귀 미완료.
- 운영: 백엔드·DB·Cron·스토어 심사·공개 다운로드는 이번 Android 코드 개선의 자동 배포 대상이 아니다.
- GitHub: 사용자 요청으로 업로드를 보류했고 원격 저장소 변경은 없다.
