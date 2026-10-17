# 틈새(Tteumsae) Backend

강원도 TourAPI 장소를 정기 동기화하고, 사용자가 경유에 쓸 순수 여유시간과
안전여유 안에 방문할 수 있는 장소를 추천하는 Vercel Functions
백엔드입니다.

## 구성

- Vercel Functions: HTTP API 및 Cron
- Supabase Postgres: 장소와 동기화 커서 저장
- Supabase Auth/Postgres: 선택형 로그인 사용자와 본인 프로필·저장 tombstone 보관
- TourAPI `KorService2`: 강원도 `areaCode=32` 장소 수집
- 카카오모빌리티 자동차 길찾기 API: 차량 이동시간 계산

차량 모드에서는 카카오모빌리티가 반환한 실시간 교통 기반 구간별
이동시간을 사용합니다. 일반 길찾기 API에서 도보를 지원하지 않으므로,
도보 모드는 현재 직선거리 기반 보수적 추정값을 사용합니다.

## API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/health` | 서버 및 연동 설정 상태 |
| GET | `/api/places` | 장소 목록 |
| GET | `/api/places/{contentId}` | 장소 상세 |
| GET | `/api/geocode?q={검색어}` | 카카오 키워드 장소 검색 |
| GET | `/api/region?latitude={위도}&longitude={경도}` | 행정구역·강원도 여부 확인 |
| POST | `/api/recommendations` | 타임 세이프 추천 |
| POST | `/api/route` | 출발지·경유지 0~5개·목적지 통합 차량 경로 |
| DELETE | `/api/account` | 검증된 Bearer 사용자의 Supabase 계정 영구 삭제 |
| GET | `/api/cron/tour-sync` | TourAPI 동기화, Bearer 인증 필요 |
| GET | `/api/cron/tour-catalog-sync` | TourAPI 증분 카탈로그 동기화, Bearer 인증 필요 |
| GET | `/api/cron/tour-intro-sync` | 운영시간·휴무일 intro 보강, Bearer 인증 필요 |
| GET | `/api/ops/status` | Gate 1 운영 집계, `CRON_SECRET` Bearer 인증 필요 |

공개 운영 문서는 JavaScript나 로그인 없이 다음 clean URL로 제공합니다.

```text
/privacy
/account-deletion
```

추천 요청 예시:

```json
{
  "mode": "ON_THE_WAY",
  "start": {
    "latitude": 37.7519,
    "longitude": 128.8761
  },
  "destination": {
    "latitude": 37.7644,
    "longitude": 128.8996
  },
  "extraTimeMinutes": 90,
  "safetyBufferMinutes": 15,
  "transport": "CAR",
  "categories": ["CAFE", "ATTRACTION"]
}
```

차량 추천 응답의 `meta.routeProvider`는 `KAKAO_MOBILITY`이며 각 추천의
`route`에는 출발지→장소, 장소→목적지의 이동시간과 거리가 포함됩니다.
서버는 직행 `baseRoute`를 먼저 계산하고
`effectiveDeadlineMinutes = baseRouteMinutes + extraTimeMinutes`로 전체 예산을
만듭니다. 추천 조건은 다음과 같습니다.

```text
우회 주행시간 + 기본 머무름 + 안전여유 <= extraTimeMinutes
```

`deadlineMinutes`는 이전 클라이언트의 전체 시간 예산 호환용입니다.
`deadlineMinutes`와 `extraTimeMinutes`는 정확히 하나만 보내야 하며 신규
Android는 `extraTimeMinutes`를 사용합니다.

## 환경변수

Vercel Project Settings에서 다음 환경변수를 등록합니다.

```text
TOUR_API_SERVICE_KEY
KAKAO_REST_API_KEY
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
SUPABASE_SERVICE_ROLE_KEY
CRON_SECRET
TOUR_SYNC_MAX_PAGES=10
TOUR_INTRO_SYNC_BATCH_SIZE=20
TOUR_SYNC_CONCURRENCY=4
KAKAO_ROUTE_CANDIDATE_LIMIT=8
KAKAO_MOBILITY_DAILY_WARNING=7000
KAKAO_MOBILITY_DAILY_BUDGET=8000
```

API 키와 서비스 역할 키, Cron 비밀값은 Sensitive로 저장합니다. 실제
값은 GitHub, `.env.example`, Android 앱에 넣지 않습니다.

### 런타임 안전 경계

Gate 1-A의 운영값은 다음과 같습니다.

```text
Supabase 요청 timeout: 5초
Kakao Local 요청 timeout: 5초
Kakao Mobility 요청 timeout: 8초
TourAPI 요청 timeout: 8초
추천·통합 경로 전체 deadline: 25초
Cron 전체 deadline: 50초
동기화 DB lease: 90초
정확 Kakao 후보 경로 상한: 8개
카탈로그 Cron: 20 18 * * * UTC
intro Cron: 20 22 * * * UTC
```

Cron은 종료 5초 전부터 새 페이지나 장소를 시작하지 않습니다. 두 Cron은 Vercel
Hobby의 실행 시각 오차에 분 단위 순서로 의존하지 않도록 UTC 기준 4시간 떨어져
있으며, 동일 작업의 중복 호출은 Supabase `claim_sync_job` RPC가 차단합니다.

## 로컬 검증

Node.js 24.x와 pnpm 11.19.0:

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm run check
node scripts/verify-user-rls.js
```

`verify-user-rls.js`는 아래 세 테스트 전용 환경변수가 있을 때만 실행됩니다.
두 임시 사용자의 본인 CRUD와 상대 사용자 차단을 확인하고 생성 데이터를 정리합니다.
설정이 없으면 누락 변수명을 포함한 `SKIPPED`를 출력하며, 운영 반영 전에는 반드시
별도 Supabase 테스트 프로젝트에서 `PASS`를 확인해야 합니다.

```text
SUPABASE_TEST_URL
SUPABASE_TEST_PUBLISHABLE_KEY
SUPABASE_TEST_SERVICE_ROLE_KEY
```

## 보안

- 외부 API 키는 Vercel Functions에서만 사용합니다.
- `/api/account`는 Bearer 토큰을 Supabase에서 먼저 검증하고, 검증 응답의 사용자
  ID만 service role 관리자 삭제에 사용합니다. 요청 본문의 사용자 ID는 읽지 않습니다.
- 카카오 REST API 키는 Android 앱이나 API 응답에 포함하지 않습니다.
- Supabase 테이블은 RLS를 활성화하고 클라이언트 공개 정책을 만들지 않습니다.
- `profiles`, `user_saved_places`는 authenticated 역할에 필요한 열만 허용하고
  `auth.uid() = user_id`인 본인 행만 조회·추가·수정할 수 있습니다.
- Cron은 `CRON_SECRET` Bearer 헤더를 검증합니다.
- 추천은 IP별 분당 12회, 경로는 분당 40회의 인스턴스 메모리 제한을 적용합니다.
  이는 서버리스 best-effort이므로 공개 규모가 커지면 공유 저장소나 Vercel 경계
  제한으로 교체해야 합니다.
- 외부 API 또는 DB 오류 본문은 사용자 응답에 노출하지 않습니다.

## Gate 1-B 운영 데이터

Kakao Mobility 호출은 KST 날짜별로 Supabase에 원자 예약한 뒤 실행합니다. 기본값은
7,000건부터 운영 경고, 8,000건에서 외부 호출 전 차단이며 공식 10,000건 상한보다
높게 설정할 수 없습니다. 예산 또는 공급자 쿼터가 소진되면 API는 503과
`Retry-After`를 반환하며 직선거리 경로로 위장하지 않습니다.

강릉 알파 검수 데이터는 TourAPI 원문과 분리된 `place_curations` overlay에 저장되고
`effective_places`가 검수값을 우선합니다. 다음 명령은 실제 비밀값을 출력하지 않습니다.

```powershell
node scripts/validate-place-curations.mjs
node scripts/import-place-curations.mjs
```

운영 집계는 `Authorization: Bearer {CRON_SECRET}`을 붙인
`GET /api/ops/status`에서 확인합니다. 응답에는 KST 사용일, 공급자별 예약·성공·오류,
동기화 작업 상태, 운영시간 보강률과 강릉 검수 100개 달성 여부가 포함됩니다.
