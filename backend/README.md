# 틈새(Tteumsae) Backend

강원도 TourAPI 장소를 정기 동기화하고, 사용자의 마감 시간과 안전
여유시간 안에 방문할 수 있는 장소를 추천하는 Vercel Functions
백엔드입니다.

## 구성

- Vercel Functions: HTTP API 및 Cron
- Supabase Postgres: 장소와 동기화 커서 저장
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
| POST | `/api/recommendations` | 타임 세이프 추천 |
| GET | `/api/cron/tour-sync` | TourAPI 동기화, Bearer 인증 필요 |
| GET | `/api/cron/tour-detail-sync` | 이미지·편의 태그 상세 동기화, Bearer 인증 필요 |

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
  "deadlineMinutes": 90,
  "safetyBufferMinutes": 15,
  "transport": "CAR",
  "categories": ["CAFE", "ATTRACTION"]
}
```

차량 추천 응답의 `meta.routeProvider`는 `KAKAO_MOBILITY`이며 각 추천의
`route`에는 출발지→장소, 장소→목적지의 이동시간과 거리가 포함됩니다.

## 환경변수

Vercel Project Settings에서 다음 환경변수를 등록합니다.

```text
TOUR_API_SERVICE_KEY
KAKAO_REST_API_KEY
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
CRON_SECRET
TOUR_SYNC_MAX_PAGES=10
TOUR_DETAIL_SYNC_BATCH_SIZE=10
KAKAO_ROUTE_CANDIDATE_LIMIT=20
```

API 키와 서비스 역할 키, Cron 비밀값은 Sensitive로 저장합니다. 실제
값은 GitHub, `.env.example`, Android 앱에 넣지 않습니다.

## 로컬 검증

Node.js 20 이상:

```powershell
npm test
npm run check
```

## 보안

- 외부 API 키는 Vercel Functions에서만 사용합니다.
- 카카오 REST API 키는 Android 앱이나 API 응답에 포함하지 않습니다.
- Supabase 테이블은 RLS를 활성화하고 클라이언트 공개 정책을 만들지 않습니다.
- Cron은 `CRON_SECRET` Bearer 헤더를 검증합니다.
- 외부 API 또는 DB 오류 본문은 사용자 응답에 노출하지 않습니다.
