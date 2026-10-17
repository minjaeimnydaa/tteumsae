# 도착 마감 기반 1곳 경유 플로우 설계

최초 작성: `2026-08-26`

감사 반영 개정: `2026-08-28`

상태: 사용자 방향 승인, Gate 2 실행 기준

## 1. 목적

사용자는 출발지, 목적지와 반드시 도착할 시각을 정한다. 틈새는 현재 교통 기준으로
늦을 위험을 줄이면서 의미 있게 들를 한 곳을 찾고 다음 정보를 제공한다.

- 직행보다 늘어나는 이동시간
- 선택한 장소에서 머물 수 있는 이동 기준 최대시간
- 목적지 마감을 지키기 위한 경유지 출발 권장시각
- 안전한 후보가 없을 때 바로 출발해야 한다는 판단

체류시간과 안전여유는 사용자가 입력하지 않는다. 서비스는 여행 일정을 대신 작성하는
것이 아니라 `지금 이곳을 들러도 되는가`를 빠르게 결정하게 한다.

## 2. 확정 원칙

1. 활성 시나리오는 지금 자동차로 출발하는 사용자다.
2. 목적지와 도착 마감은 기존 LOCATION 화면에서 함께 입력한다.
3. 시간 전용 화면과 필수 CONDITIONS 단계를 추가하지 않는다.
4. 관심 조건은 기본 `아무거나`인 선택 필터다.
5. 내부 안전여유는 10분, 최소 의미 체류시간은 15분이다.
6. 핀에는 직행 대비 추가 이동시간을 표시한다.
7. 최대 체류시간과 출발 권장시각은 장소 선택 후 강조한다.
8. 알파의 완료 경로는 한 곳이며 두 번째 장소는 사용자 검증 후 별도 실험한다.
9. 외부 카카오맵이 실제 주행을 안내한다.
10. 틈새는 정시 도착 보장, 실시간 지속 추적과 앱 내부 내비를 약속하지 않는다.
11. 고정 로컬 출발 알림은 opt-in이며 위치 권한 없이 동작한다.
12. 알림 권한을 거부해도 추천과 외부 내비는 정상 동작한다.

## 3. 범위

### 3.1 알파에 포함

- LOCATION의 도착 마감 선택과 검증
- 절대 도착 마감 기반 Backend V1 계약
- 최소 15분 후보 필터와 최대 체류시간 계산
- 후보 핀의 추가 이동시간
- 한 곳 선택·해제와 확정 요약
- 현재 교통 스냅샷 기준 문구
- 고정 출발 알림
- 앱 복귀 시 사용자가 재확인할 수 있는 새로고침
- `RouteFlowViewModel + SavedStateHandle` 기반 상태 복원

### 3.2 검증 후 별도 설계

- 두 번째 경유지
- 지오펜스 도착 감지와 백그라운드 위치
- 장소별 체류시간 사용자 입력
- 서버 계정 기반 활성 여행 동기화
- 앱 내부 턴바이턴 내비

## 4. 사용자 흐름

```text
HOME → LOCATION → LOADING → RESULTS → DETAIL
```

관심 조건은 LOCATION 하단 또는 RESULTS 필터 칩으로 제공하며 별도 필수 화면을 만들지
않는다. 필터의 정확한 배치는 기존 Compose 구조를 유지하면서 구현 계획에서 정하되,
필터를 선택하지 않아도 검색을 시작할 수 있어야 한다.

### 4.1 HOME

일일 차단 팝업에 제품 설명을 의존하지 않는다. 홈에 다음 가치가 항상 보여야 한다.

```text
늦지 않게, 가는 길의 틈새를 발견하세요
목적지와 도착 시간만 알려주면 지금 들러도 되는 한 곳을 찾아드려요.
```

주요 행동은 `어디까지 몇 시에 도착하나요?`다.

### 4.2 LOCATION

- 출발지는 GPS 또는 검색 장소이며 수정할 수 있다.
- 목적지는 강원도 장소다.
- `몇 시까지 도착해야 하나요?` 행에서 Material 시간 선택기를 연다.
- 선택 시각은 `Asia/Seoul`의 현재보다 이후인 가장 가까운 시각으로 해석한다.
- 자정을 넘긴 시각은 다음 날이다.
- 허용 범위는 현재부터 15분 이상 24시간 이하다.
- 홈에서 새 탐색을 시작하면 이전 도착 마감을 지운다.
- 임의 시간 기본값을 넣지 않는다.
- 출발지, 목적지와 도착 마감이 모두 유효할 때 검색할 수 있다.
- 관심 필터 기본값은 `아무거나`다.

### 4.3 LOADING

문구는 `늦지 않고 15분 이상 머물 수 있는 곳을 찾고 있어요`를 사용한다.
`실시간 경유 경로 비교`처럼 지속 추적을 암시하는 표현을 사용하지 않는다.

마감이 지났거나 서버 수신시각 기준 남은 시간이 15분 미만이면 외부 경로 API를 호출하지
않고 시간 재선택을 안내한다.

### 4.4 RESULTS — 선택 전

- 지도 핀: `+5분`, `+8분`, `+12분`
- 후보 수: 품질이 높은 3~6개를 우선 노출
- 카드: 추가 이동시간, 장소 유형, `영업시간 확인 필요` 상태
- 주요 행동: `이곳 들르기`
- 보조 행동: `상세보기`

선택 전 카드의 최대 체류시간은 보조 정보로만 표시하거나 숨길 수 있다. 핵심 강조는 사용자가
장소를 선택한 뒤 제공한다. `평균 머무름`은 표시하지 않는다.

후보가 없지만 직행은 가능한 경우:

```text
지금은 경유지 없이 바로 가는 게 안전해요.
[목적지로 바로 출발하기]
```

직행도 마감을 넘기는 경우:

```text
지금 출발해도 도착 시간이 빠듯해요.
현재 교통 상황을 확인하고 바로 출발해 주세요.
[목적지로 바로 출발하기]
```

### 4.5 한 곳 선택

```text
이 장소에 들러도 17:50 도착 예상
이동 기준 최대 약 35분 머물 수 있어요
17:12까지 출발하면 돼요
```

- 주요 행동: `길 안내 시작`
- 선택 행동: `출발 5분 전에 알려드릴까요?`
- 보조 행동: `다른 장소 보기`

안전여유는 선택값으로 노출하지 않는다. 상세 도움말에는 현재 교통과 내부 여유를 반영한
예상이며 카카오맵에서 경로가 다시 계산된다고 설명한다.

### 4.6 앱 복귀

외부 내비 사용 중 틈새가 이동을 계속 추적했다고 가정하지 않는다. 사용자가 앱으로 돌아오면
`현재 교통으로 다시 확인` 행동을 제공한다. 재조회 성공 시 계산시각과 출발 권장시각을
갱신하고 실패하면 마지막 계산값과 `현재 교통을 다시 확인하지 못했어요`를 표시한다.

## 5. 시간 모델

### 5.1 상수

```text
SAFETY_BUFFER_MINUTES = 10
MINIMUM_STAY_MINUTES = 15
DEPARTURE_REMINDER_LEAD_MINUTES = 5
MAX_ALPHA_WAYPOINTS = 1
```

상수는 Backend와 Android 각 도메인 모듈 한 곳에서 관리하고 UI에 숫자를 중복하지 않는다.

### 5.2 절대 마감 계약

신규 Android는 선택한 절대시각을 `arrivalDeadlineEpochMillis`로 전송한다. 서버가 실제
요청 수신시각 `serverNowEpochMillis`를 기준으로 계산한다.

```text
remainingWholeMinutes =
  floor((arrivalDeadlineEpochMillis - serverNowEpochMillis) / 60,000)
```

신규 계약에서 Android가 계산한 상대 `deadlineMinutes`를 보내지 않는다. 요청 지연이
마감시각을 뒤로 미루지 않으며 서버 테스트는 주입 가능한 clock을 사용한다.

### 5.3 한 곳 후보

Kakao의 각 leg duration 초는 분으로 올린다.

```text
firstLegMinutes = ceil(firstLegSeconds / 60)
secondLegMinutes = ceil(secondLegSeconds / 60)
candidateDrivingMinutes = firstLegMinutes + secondLegMinutes
detourMinutes = max(0, candidateDrivingMinutes - baseRouteMinutes)

rawMaximumStayMinutes =
  remainingWholeMinutes - candidateDrivingMinutes - SAFETY_BUFFER_MINUTES

maximumStayMinutes = floor(rawMaximumStayMinutes / 5) * 5
eligible = maximumStayMinutes >= MINIMUM_STAY_MINUTES

latestDepartureEpochMillis =
  arrivalDeadlineEpochMillis
  - (secondLegMinutes + SAFETY_BUFFER_MINUTES) * 60,000
```

영업시간을 확실히 구조화할 수 있으면 예상 도착부터 최소 15분 동안 영업하는 장소만 남기고,
닫는 시각이 더 빠르면 최대 체류시간을 5분 단위로 추가 내림한다. 평일·주말, 계절,
입장 마감을 확실히 구분하지 못하면 운영 상태는 `UNKNOWN`이며 경로 계산과 별도로
`영업시간 확인 필요`를 표시한다.

### 5.4 숫자 표현

- 추가 이동시간은 정수 분으로 표시한다.
- 최대 체류시간은 이미 5분 단위로 보수적으로 내린 값을 `약 N분`으로 표시한다.
- 출발 권장시각은 분 단위로 표시한다.
- `도착 보장`, `정확히 N분`, `실시간 추적`을 사용하지 않는다.

## 6. API 계약

### 6.1 요청

```json
{
  "mode": "ON_THE_WAY",
  "start": { "latitude": 37.1, "longitude": 128.1 },
  "destination": { "latitude": 37.5, "longitude": 127.1 },
  "arrivalDeadlineEpochMillis": 1787907600000,
  "timeModel": "ARRIVAL_DEADLINE_V1",
  "transport": "CAR",
  "categories": ["CAFE"]
}
```

- `ARRIVAL_DEADLINE_V1`은 `arrivalDeadlineEpochMillis`를 필수로 요구한다.
- V1 요청에서 `deadlineMinutes`, `extraTimeMinutes`, `safetyBufferMinutes`를 거부한다.
- 서버가 안전여유 10분을 적용한다.
- `timeModel`이 없는 기존 요청은 `extraTimeMinutes` 레거시 동작을 유지한다.
- 저수준 `POST /api/route`의 0~5곳 계약은 구버전 호환을 위해 유지한다.

### 6.2 응답

```json
{
  "recommendations": [
    {
      "place": { "id": "tour:123", "name": "예시 카페" },
      "minimumStayMinutes": 15,
      "maximumStayMinutes": 35,
      "latestDepartureEpochMillis": 1787904720000,
      "openingStatus": "UNKNOWN",
      "route": {
        "firstLegMinutes": 20,
        "secondLegMinutes": 38,
        "detourMinutes": 8
      }
    }
  ],
  "meta": {
    "timeModel": "ARRIVAL_DEADLINE_V1",
    "calculatedAtEpochMillis": 1787899800000,
    "arrivalDeadlineEpochMillis": 1787907600000,
    "safetyBufferMinutes": 10,
    "minimumStayMinutes": 15
  }
}
```

V1 Android는 기존 `stayMinutes`를 시간 적합 판정이나 사용자 문구에 사용하지 않는다.
응답의 `calculatedAtEpochMillis`로 결과가 언제 계산됐는지 판단한다.

## 7. Android 구조

전면 Navigation·DI 전환을 하지 않는다. 이번 기능과 직접 관련된 책임만 분리한다.

| 파일 | 책임 |
|---|---|
| `domain/route/ArrivalDeadlinePolicy.kt` | 시간 상수, 시간 선택 검증, 표시용 내림 |
| `domain/route/RouteFlowModels.kt` | 입력, 후보, 한 곳 선택과 시간 결과 모델 |
| `ui/route/RouteFlowUiState.kt` | 입력, 로딩, 후보, 선택, 오류와 복원 상태 |
| `ui/route/RouteFlowViewModel.kt` | 추천 호출, 선택, 새로고침, SavedStateHandle |
| `ui/route/LocationScreen.kt` | 출발지·목적지·도착 마감·선택 필터 |
| `ui/route/ResultsScreen.kt` | 후보 없음·선택 전·한 곳 선택 상태 |
| `ui/route/RouteResultComponents.kt` | 핀 라벨, 카드, 확정 요약, 빈 결과 |
| `reminder/ActiveTrip.kt` | 한 곳 경로와 출발 권장시각 |
| `reminder/ActiveTripStore.kt` | 만료 가능한 로컬 확정 경로 저장 |
| `reminder/DepartureReminderScheduler.kt` | 고정 알림 예약·취소 계약 |

`TteumsaeApp.kt`는 상위 탭과 화면 조립만 남기며 새 계산 공식을 직접 소유하지 않는다.

### 상태 복원

- 화면 enum만 저장하지 않는다.
- 출발지·목적지 좌표, 도착 마감, 필터, 추천 결과와 선택 장소를 ViewModel 상태로 관리한다.
- 프로세스 복원에 필요한 작은 입력값과 선택 ID는 SavedStateHandle에 저장한다.
- 추천 payload가 없는데 RESULTS가 복원되면 LOCATION으로 안전하게 돌아간다.
- 새 검색은 이전 선택·알림·계산시각을 지운다.

## 8. 고정 출발 알림

- 한 곳을 선택한 뒤 사용자가 알림을 켤 때만 Android 13+ 알림 권한을 요청한다.
- 알림 시각은 `latestDepartureEpochMillis - 5분`이다.
- 정확 알람 특별 권한에 의존하지 않고 best-effort 전달임을 문서화한다.
- 이미 알림시각이 지났지만 출발 권장시각 전이면 즉시 경고한다.
- 새 경로 확정, 알림 해제 또는 여행 만료 시 기존 알림을 취소한다.
- 활성 경로는 도착 마감 2시간 뒤 만료한다.
- 백업 대상에서 활성 여행 좌표와 인증 토큰을 제외한다.

## 9. 오류와 대체 동작

| 상황 | 동작 |
|---|---|
| 마감이 지났거나 15분 미만 | LOCATION 유지, 시간 재선택 안내 |
| 직행부터 마감 초과 | 즉시 출발 경고와 목적지 내비 CTA |
| 추천 후보 없음 | 경유지 없이 바로 출발 CTA |
| 일부 후보 경로 실패 | 성공한 후보만 표시 |
| 모든 후보 경로 실패 | 재시도와 바로 출발 CTA |
| 영업시간 불명 | 후보 유지, `영업시간 확인 필요` |
| 알림 권한 거부 | 추천·외부 내비 유지, 알림만 끔 |
| 앱 복귀 재조회 실패 | 마지막 결과 유지, 재확인 실패 표시 |
| 복원 payload 없음 | RESULTS를 표시하지 않고 LOCATION 복귀 |
| 외부 카카오맵 없음 | 설치 안내 또는 웹 지도 fallback |

Backend 오류는 Android까지 HTTP 상태, `requestId`, `Retry-After`와 작업 종류를 보존한다.

## 10. 테스트 전략

### Backend

- V1에 절대 마감 누락·과거·24시간 초과 거부
- V1과 상대 시간·사용자 안전여유 혼용 거부
- 14분 59초 남음의 내림 경계
- 15분 최대 체류 포함, 14분 제외
- Kakao 초→분 올림과 최대 체류 5분 내림
- 느린 요청에서도 절대 마감 불변
- 영업 종료 cap과 불명확 운영시간 UNKNOWN
- 기존 `extraTimeMinutes` 요청·응답 회귀

### Android 단위 테스트

- 같은 날·자정 넘김 시간 선택
- 15분·24시간 경계와 임의 기본값 없음
- V1 wire contract에 절대 마감만 포함
- 핀 추가 이동시간과 카드 값 일치
- 선택·해제·새 검색 상태 전이
- SavedStateHandle 복원과 payload 없는 RESULTS 안전 복귀
- 알림 허용·거부·지난 시각·교체·만료

### 실기기

- 작은 화면·키보드·시간 선택기
- 후보 없음·직행 빠듯·네트워크 실패
- 한 곳 선택과 카카오맵 전달
- 앱 복귀 후 재조회
- 알림 허용·거부와 앱 종료 후 전달
- Google·Kakao OAuth 회귀

## 11. 배포 순서

1. V1 절대 마감 검증·계산을 구버전 호환으로 Backend에 배포한다.
2. 운영 API에서 V1 응답과 레거시 요청을 함께 스모크 테스트한다.
3. Android RouteFlow 상태와 화면을 기능 브랜치에서 완성한다.
4. 실제 기기에서 한 곳 선택, 외부 내비와 알림을 검증한다.
5. 알파 테스터에게만 배포하고 30개 실경로 감사를 먼저 수행한다.
6. 성공 기준 통과 후 두 번째 장소 또는 지오펜스를 별도 설계한다.

## 12. 완료 기준

- 목적지와 도착 마감을 한 화면에서 입력하고 필터 없이도 검색할 수 있다.
- 서버는 절대 마감과 수신시각으로 보수적으로 계산한다.
- 모든 후보가 이동 기준 최소 15분과 내부 여유 10분을 만족한다.
- 핀에는 추가 이동시간, 선택 후에는 최대 체류시간과 출발 권장시각이 보인다.
- 한 곳 선택만으로 외부 내비를 시작할 수 있다.
- 후보 없음과 직행 초과가 안전한 다음 행동을 제공한다.
- 위치·알림 권한 거부가 추천과 외부 내비를 막지 않는다.
- 회전·프로세스 복원 후 빈 RESULTS나 잘못된 선택 상태가 나타나지 않는다.
- 신규·레거시 Backend 테스트와 Android 단위 테스트가 통과한다.
- UI 어디에도 평균 머무름, 5곳 선택, 실시간 추적 또는 도착 보장 표현이 남지 않는다.
