# Gate 1-B Data Trust and Operations Design

상태: 승인된 설계
기준일: `2026-08-28`

## 1. 목적

Gate 1-A가 중복 Cron, 무제한 대기, 추천당 과도한 후보 호출을 제한했다면 Gate 1-B는
남은 데이터·운영 신뢰 문제를 닫는다. 무료 Vercel·Supabase 구성을 유지하면서 다음을
보장하는 것이 목표다.

- Kakao 호출량이 일일 쿼터를 소리 없이 소진하지 않는다.
- Kakao의 쿼터·서버·timeout 실패를 날짜별로 영구 집계한다.
- 운영시간 문구를 완전히 해석하지 못하면 장소를 `OPEN`으로 낙관하지 않는다.
- 지난 축제, 아직 시작하지 않은 축제, 날짜가 불완전한 축제를 추천하지 않는다.
- 첫 알파 기본 지역인 강릉의 핵심 장소 100개를 출처와 검수 시각이 있는 데이터로 관리한다.
- Cron 마지막 결과, 운영시간 보강률, 수동 검수 진행률을 인증된 운영 API에서 확인한다.

## 2. 검증된 현재 기준선

- Kakao 자동차 길찾기의 공식 일일 쿼터는 10,000건이다. 현재 자동차 추천 한 건은
  기본 경로 1회와 후보 경로 최대 8회, 즉 최악의 경우 9회를 사용한다.
- Kakao는 쿼터 초과를 HTTP 429뿐 아니라 HTTP 400과 오류 코드 `-10`으로도 반환할 수
  있다. 현재 `kakao-mobility.js`와 `kakao-local.js`는 비정상 응답 body를 읽지 않아
  이 경우를 구분하지 못한다.
- 운영 DB의 2026-08-28 기록은 활성 장소 1,719곳 중 운영시간 보유 16곳(0.9%)이다.
- `time-safe.js`는 단일 시간 범위와 단순 정기 휴무만 처리한다. 평일·주말 분기, 계절,
  입장 마감이 섞이면 정확한 판정을 보장하지 못한다.
- `places`에는 `event_start_date`, `event_end_date`와 활성 축제 종료일 인덱스가 있지만
  추천 경로에서 축제 날짜를 거르지 않는다.
- `sync_state`에는 Gate 1-A에서 추가한 `last_finished_at`, `last_status`,
  `last_run_summary`가 있으나 이를 한눈에 보는 인증 운영 endpoint가 없다.
- 현재 알파 카탈로그 범위는 강원도 18개 시군이고 앱의 기본 지역은 강릉
  (`sigungu_code=1`)이다.

공식 근거:

- [Kakao API quota](https://developers.kakao.com/docs/ko/getting-started/quota)
- [Kakao Mobility error response](https://developers.kakaomobility.com/affiliate-en/solution)
- [TourAPI service detail](https://www.data.go.kr/tcs/dss/selectApiDataDetailView.do?publicDataPk=15101578)

## 3. 범위

### 포함

1. API·operation별 일일 사용량 원자적 예약과 결과 집계
2. Kakao Mobility 7,000건 경고·8,000건 hard stop
3. Kakao 429, body code `-10`, 5xx, timeout 분류
4. Cron 상태와 데이터 품질 지표를 제공하는 인증 운영 API
5. 보수적인 운영시간 파서와 방문 구간 판정
6. 추천 전 축제 날짜 완전성·유효기간 필터
7. TourAPI 원문과 분리된 강릉 핵심 장소 100개 검수 오버레이
8. 검수 CSV export/import 도구와 출처·검수 시각 관리
9. 자동 테스트, 운영·API·QA 문서 갱신

### 제외

- 외부 관측성 SaaS, 메시지 큐, 별도 워커
- 모든 개별 API 요청 body·좌표·사용자 정보를 저장하는 상세 로그
- 전체 강원도 300개 수동 검수
- 사용자용 운영 대시보드 또는 Android 화면 개편
- Gate 2 도착 마감 신규 플로우
- 운영시간의 자연어 전체를 추론하는 AI 파서

## 4. 채택한 접근

### 4.1 일별 집계로 호출 예산 제어

호출별 event row를 무한히 쌓지 않고 `provider_usage_daily` 한 행에 일별 합계를 저장한다.

| 열 | 타입 | 의미 |
|---|---|---|
| `usage_date` | `date` | 서버가 계산해 넘긴 KST 날짜 |
| `provider` | `text` | `KAKAO_MOBILITY`, `KAKAO_LOCAL`, `TOUR_API` |
| `operation` | `text` | `DIRECTIONS`, `KEYWORD_SEARCH`, `REGION`, 상세 API명 등 |
| `budget_limit` | `integer` nullable | 해당 날짜 예약 시 적용된 내부 상한 |
| `reserved_count` | `integer` | 실제 외부 호출 직전에 승인된 수 |
| `success_count` | `integer` | 정상 응답 수 |
| `quota_error_count` | `integer` | 429, Kakao `-10`, TourAPI 제한 오류 수 |
| `server_error_count` | `integer` | upstream 5xx 수 |
| `timeout_count` | `integer` | timeout 수 |
| `other_error_count` | `integer` | 위 분류 외 실패 수 |
| `updated_at` | `timestamptz` | 마지막 집계 시각 |

기본키는 `(usage_date, provider, operation)`이다. 좌표, 검색어, 사용자 식별자, 응답
전문은 저장하지 않는다.

두 service-role 전용 RPC를 둔다.

```text
reserve_provider_usage(provider, operation, usage_date, budget_limit, units)
  -> allowed, reserved_count, remaining_count

record_provider_usage_result(provider, operation, usage_date, result_kind, units)
  -> void
```

`reserve_provider_usage`는 `INSERT ... ON CONFLICT DO UPDATE ... WHERE`로
`reserved_count + units <= budget_limit`일 때만 원자적으로 증가시킨다. 예약에 성공한
건은 후속 호출이 실패해도 차감하지 않는다. 그래야 동시 요청과 timeout 상황에서도 실제
시도 횟수를 과소 집계하지 않는다.

Gate 1-B의 강제 예산은 `KAKAO_MOBILITY/DIRECTIONS`에 적용한다.

- 경고 기준: 7,000건
- hard stop: 8,000건
- 공식 10,000건 중 20%는 운영·수동 확인·집계 오차를 위한 여유로 남긴다.
- 두 기준은 환경변수로 낮출 수 있지만 서버는 hard stop을 10,000보다 높게 허용하지 않는다.
- Kakao Local과 TourAPI도 같은 테이블에 분리 집계하되 이번 Gate에서는 관측을 우선하고
  기존 endpoint 동작을 막지 않는다.

길찾기 호출은 `reserve -> fetch -> classify result` 순서다. 예약이 거부되면 외부 API를
호출하지 않고 HTTP 503, `error.code="UPSTREAM_BUDGET_EXHAUSTED"`, 다음 KST 자정까지의
`Retry-After`를 반환한다. 부정확한 직선거리 경로로 자동 대체하지 않는다.

### 4.2 공급자 오류를 구조화

`lib/provider-usage.js`는 예산 예약, KST 날짜, 다음 자정까지의 초, 결과 분류를 담당한다.
`kakao-mobility.js`와 `kakao-local.js`는 비정상 응답 body를 제한된 크기로 읽고 HTTP
status와 공급자 code만 가진 안전한 오류를 던진다.

분류 규칙:

- `quota`: HTTP 429 또는 Kakao body `code=-10`; TourAPI 제한 result code
- `server_error`: HTTP 500, 502, 503 등 upstream 5xx
- `timeout`: `UPSTREAM_TIMEOUT`
- `other_error`: 나머지 HTTP·payload·파싱 오류
- `success`: 공급자 응답과 필수 payload 검증이 모두 끝난 경우

외부 응답 전문, URL query, API key는 로그·DB·공개 오류 응답에 포함하지 않는다.
`fetchKakaoRoute` 한 곳에 계측 경계를 두어 `/api/recommendations`와 `/api/route`가 같은
정책을 사용하게 한다.

### 4.3 보수적 운영시간 파서

`lib/operating-hours.js`에 문자열 정규화와 방문 구간 판정을 순수 함수로 분리한다.

```text
evaluateOperatingWindow(place, { arrival, departure, timeZone: "Asia/Seoul" })
  -> { status: OPEN | CLOSED | UNKNOWN, reason }
```

지원하는 명확한 패턴:

- `24시간`, `연중무휴`
- 매일 동일 시간 범위
- 평일과 주말이 각각 완전하게 주어진 범위
- 요일 또는 요일 범위별 완전한 시간 범위
- `매주 월요일` 같은 정기 휴무
- 명시적인 `입장 마감 HH:MM`
- 영업 종료 시각이 명확할 때의 `종료 N분 전 입장 마감`

판정 규칙:

1. KST에서 도착·예상 출발 날짜와 요일을 계산한다.
2. 해당 날짜가 명확한 휴무면 `CLOSED`다.
3. 도착이 입장 마감 이후면 `CLOSED`다.
4. 도착이 영업 시작 전이거나 예상 출발이 영업 종료 후면 `CLOSED`다.
5. 필요한 모든 분기와 예외를 해석했고 방문 구간이 안에 있으면 `OPEN`이다.
6. 계절별 시간, 공휴일 예외, 임시 휴무, 상충하는 복수 범위 등 필요한 조건 하나라도
   완전히 해석하지 못하면 `UNKNOWN`이다.

입장 마감은 도착에만 적용하고, 예상 출발에는 영업 종료를 적용한다. 기존처럼 도착과
출발 각각에 같은 파서를 호출해 입장 마감을 잘못 두 번 적용하지 않는다.

TourAPI 원문은 그대로 보존한다. 파서가 이해한 일부만으로 `OPEN`을 만들지 않는다.
기존 `operationStatus` 공개 함수는 호환 wrapper로 유지하되 추천은 새 방문 구간 함수를
사용한다.

### 4.4 축제는 예정 방문일 기준으로 필터

`content_type_id=15` 또는 `category=FESTIVAL`인 장소는 다음 조건을 모두 만족해야 한다.

```text
event_start_date와 event_end_date가 모두 유효함
event_start_date <= estimated_arrival_kst_date <= event_end_date
```

날짜가 하나라도 없거나 역전됐거나 유효하지 않으면 제외한다. 지난 축제뿐 아니라 아직
시작하지 않은 축제도 현재 추천에서 제외한다.

1차 필터는 직선 추정 첫 구간으로 계산한 예정 도착일을 사용해 Kakao 후보 경로 호출 전에
수행한다. 정확 경로를 받은 뒤 실제 예정 도착일로 다시 검사한다. 자정을 넘는 경로에서도
KST 날짜가 일치해야 한다.

### 4.5 수동 검수는 원본과 분리된 오버레이

TourAPI 동기화가 검수값을 덮어쓰지 않도록 `place_curations`를 별도 테이블로 둔다.

| 열 | 의미 |
|---|---|
| `content_id` | `places`를 참조하는 기본키 |
| `operating_info_status` | `VERIFIED` 또는 `UNKNOWN` |
| `opening_hours`, `closed_days` | 검수된 운영시간·휴무 원문 |
| `last_admission` | 명시된 입장 마감; 없거나 불명확하면 상태로 표현 |
| `admission_info_status` | `VERIFIED`, `NOT_APPLICABLE`, `UNKNOWN` |
| `parking_info`, `parking_info_status` | 검수된 주차 정보와 상태 |
| `source_urls` | 공식 근거 URL 배열 |
| `source_checked_at` | 근거 페이지를 마지막으로 확인한 시각 |
| `reviewed_at` | 이 레코드를 검수 완료한 시각 |
| `review_note` | 예외와 재검수 메모 |

검수 row가 존재하면 `UNKNOWN`도 명시적인 결정이다. 예를 들어 운영정보를 검수했지만
확인하지 못한 경우 오래된 TourAPI 값으로 다시 fallback하지 않는다.

`effective_places` view는 검수 row가 있으면 검수 상태와 값을 우선하고, 없으면 기존
`places` 원문을 노출한다. 기존 동기화 쓰기는 계속 `places`에만 수행한다. 서버의 장소
조회와 추천 읽기는 view로 전환한다.

첫 검수 배치는 다음 원칙으로 100개를 선정한다.

1. 활성 강릉 `sigungu_code=1`
2. 관광·문화·카페·음식·쇼핑·레저·축제의 카테고리 다양성 확보
3. 이미지·설명·상세 보강 여부와 실제 방문 후보로서의 유용성 우선
4. 공식 장소 홈페이지, 지자체, 공공 관광 출처 순으로 확인
5. 출처와 확인 날짜가 없는 값은 `VERIFIED`로 저장하지 않음

Git에는 `backend/data/gangneung-core-place-curations.csv`를 두고, export·validate·import
script를 제공한다. 실제 운영 DB 반영 전에는 content ID 존재, 100행, 중복 없음, 상태별
필수값, URL 형식을 검증한다.

### 4.6 인증 운영 상태 API

`GET /api/ops/status`는 기존 `CRON_SECRET` Bearer 인증을 재사용한다. 공개 `/api/health`는
환경변수 존재 여부만 반환하는 현재 계약을 유지한다.

운영 응답에는 다음을 포함한다.

- 오늘 KST 기준 provider·operation별 예약량, 결과 수, 예산, 남은 수
- 7,000건 경고 여부와 8,000건 차단 여부
- 각 `sync_state`의 마지막 시작·종료·상태·소요시간·작은 요약
- 활성 장소 수와 유효 운영시간 보유 수·비율
- 날짜 완전/불완전/지난 축제 수
- 강릉 핵심 장소 목표 100개 대비 검수 수와 상태별 누락 수

키, 좌표, 검색어, 사용자 정보, 외부 오류 전문은 반환하지 않는다. 응답은
`cache-control: no-store`다.

## 5. 파일 경계

### 새 파일

- `backend/migrations/006_gate_1b_data_trust.sql`
- `backend/lib/provider-usage.js`
- `backend/lib/operating-hours.js`
- `backend/api/ops/status.js`
- `backend/data/gangneung-core-place-curations.csv`
- `backend/scripts/export-place-curations.mjs`
- `backend/scripts/validate-place-curations.mjs`
- `backend/scripts/import-place-curations.mjs`
- 대응하는 provider usage, operating hours, festival, ops, migration, curation tests

### 주요 수정 파일

- `backend/lib/database.js`: usage RPC, effective view, ops 집계 adapter
- `backend/lib/kakao-mobility.js`: 예약·결과 집계와 구조화된 오류
- `backend/lib/kakao-local.js`, `backend/lib/tour-api.js`: 결과 분류·관측 연결
- `backend/lib/time-safe.js`: 방문 구간 판정과 축제 이중 필터
- `backend/api/recommendations.js`: 호출 전 축제 필터와 예산 오류 응답
- `backend/api/route.js`: 공통 Mobility 예산 오류 응답
- `backend/lib/http.js`: `UPSTREAM_BUDGET_EXHAUSTED` 503 계약
- `backend/.env.example`, `backend/README.md`, API·QA·배포·Gate 문서

Android 계약은 기존 일반 서버 오류 처리를 유지하므로 Gate 1-B에서 화면 변경을 요구하지
않는다. Gate 2에서 전용 사용자 문구가 필요하면 별도 UI 변경으로 다룬다.

## 6. 테스트와 완료 조건

### 자동 검증

- SQL migration의 기본키·원자 예약·권한·검수 상태 constraint
- 6,999→7,000 경고와 7,999→8,000 허용, 다음 1건 거부
- 동시 예약에서 hard stop 초과 없음
- Kakao HTTP 429, HTTP 400 `code=-10`, 5xx, timeout, 기타 오류 분류
- KST 자정 초기화와 `Retry-After`
- 매일·평일/주말·요일·정기휴무·24시간·입장 마감
- 계절·공휴일·상충·부분 문구의 `UNKNOWN`
- 지난·미래·진행 중·날짜 누락·날짜 역전·자정 경계 축제
- 검수값 우선, 검수 `UNKNOWN`이 원문 fallback을 막는지 확인
- `/api/ops/status` 인증과 집계 응답
- Backend 전체 테스트·lint, 프로젝트 검사, Android 회귀 테스트·빌드

### 데이터 검증

- 강릉 CSV 정확히 100개, content ID 중복 0
- 모든 행에 검수 상태·출처 확인 시각·검수 시각 존재
- `VERIFIED` 상태의 필수 정보와 공식 source URL 존재
- 운영 DB import 후 목표 100개와 API 집계가 일치
- 표본 재검수에서 출처와 저장값 불일치 0

### 완료를 주장하지 않는 조건

다음은 코드가 통과해도 외부 증거 없이는 완료로 표시하지 않는다.

- migration 006의 운영 Supabase 적용
- 운영 Vercel 환경변수 반영과 재배포
- 실제 `/api/ops/status`에서 호출·Cron 지표 확인
- 강릉 100개 출처 검수와 운영 DB import

## 7. 채택하지 않은 대안

### 호출별 상세 event log

진단은 세밀하지만 무료 DB에 행이 빠르게 쌓이고 보존·삭제 정책이 새 운영 부담이 된다.
Gate 1의 목적에는 일별 aggregate로 충분하다.

### Vercel 로그만 사용

여러 Function 인스턴스의 동시 호출을 원자적으로 차단할 수 없고 일일 상태를 안정적으로
보존하지 못하므로 예산 제어 요구를 충족하지 않는다.

### 자동 자연어 추론으로 `OPEN` 확대

현재 운영시간 보유율과 원문 품질에서 잘못된 `OPEN`은 추천 신뢰를 직접 훼손한다.
지원 패턴을 좁히고 수동 검수 오버레이를 우선하는 편이 알파에 적합하다.
