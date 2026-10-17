# API 및 데이터 설계

작성 기준: 2026-08-27 통합 소스
백엔드 패키지 버전: `0.2.0`
운영 API 기준 주소: `https://tteumsae-backend-one.vercel.app`

이 문서는 현재 구현된 HTTP 계약, 외부 API 연동, Supabase 스키마와
TourAPI 동기화 과정을 설명한다. 기획상 예정된 동작이 아니라 실제
[`backend/`](../backend/)와 Android 파서
[`TteumsaeApi.kt`](../android/app/src/main/java/com/tteumsae/app/data/TteumsaeApi.kt)를
기준으로 작성했다.

## 1. 전체 데이터 흐름

```text
Android 앱
  ├─ 선택 로그인·프로필 ─────> Supabase Auth / profiles(RLS)
  ├─ 위치 검색 ──────────────> GET /api/geocode ──> Kakao Local 장소명·주소 검색
  ├─ 행정구역 확인/역지오코딩 > GET /api/region ───> Kakao Local 좌표→행정구역
  ├─ 틈새 발견 목록 ─────────> GET /api/places ────> Supabase places
  ├─ 추천 요청 ──────────────> POST /api/recommendations
  │                                 ├─ Kakao Mobility 출발→목적 baseRoute
  │                                 ├─ baseRoute corridor의 Supabase 후보 조회
  │                                 └─ 후보별 출발→후보→목적 경로
  └─ 길 안내 ────────────────> 카카오맵 직행 또는 경유지 1곳

Legacy client
  └─ 복수 경유지 재계산 ─────> POST /api/route
                                    └─ Kakao Mobility 출발→경유지 0~5→목적

Vercel Cron
  ├─ /api/cron/tour-catalog-sync > TourAPI 변경 목록 → Supabase
  └─ /api/cron/tour-intro-sync ──> TourAPI 운영·휴무 정보 → Supabase
```

중요한 경계는 다음과 같다.

- Android 앱에는 TourAPI 키, Kakao REST API 키, Supabase 서비스 역할 키가 없다.
- 장소·추천·경로 기능은 Android가 Vercel 공개 API를 통해서만 호출한다.
- 예외적으로 선택 로그인 세션과 본인 `profiles` 행은 Supabase 공개 클라이언트와
  RLS를 통해 Android가 직접 처리한다. 장소·추천 데이터는 계속 Vercel만 통한다.
- Kakao Maps **네이티브 앱 키**만 Android 빌드 시 주입된다.
- Android에는 Supabase publishable key만 있고 service role key는 없다.
- 추천 API는 직행 `baseRoute`를 먼저 계산한 뒤 그 경로 주변에서 후보를 찾고,
  후보 한 곳을 경유하는 경로를 장소별로 계산한다.
- 활성 결과 UI는 경유지 없음 또는 한 곳만 카카오맵에 전달한다. `POST /api/route`의
  0~5개 계약은 이전 클라이언트 호환용으로 유지하며 신규 화면에서는 호출하지 않는다.
- 활성 UI는 LOCATION에서 절대 도착 마감을 입력한다. Android는
  `timeModel=ARRIVAL_DEADLINE_V1`과 `arrivalDeadlineEpochMillis`를 보내며,
  서버가 고정 안전여유 10분과 최소 체류 15분을 적용한다.

## 2. 공통 HTTP 규칙

### 2.1 응답 헤더

[`backend/lib/http.js`](../backend/lib/http.js)의 모든 정상·오류 JSON 응답은
다음을 사용한다.

```http
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
```

### 2.2 공통 오류 형태

```json
{
  "error": {
    "code": "BAD_REQUEST",
    "message": "사용자에게 표시할 수 있는 메시지",
    "details": null
  }
}
```

| HTTP | `error.code` | 현재 동작 |
|---:|---|---|
| 400 | `BAD_REQUEST` | 파라미터 또는 JSON 본문 검증 실패 |
| 401 | `UNAUTHORIZED` | Cron Bearer 인증 실패 |
| 404 | `NOT_FOUND` | 요청한 장소가 없을 때 |
| 405 | `METHOD_NOT_ALLOWED` | 허용하지 않은 메서드. `Allow` 헤더 포함 |
| 500 | `INTERNAL_ERROR` | 외부 API·DB·환경설정 등 서버 오류 |

500 응답은 외부 서비스 본문이나 비밀값을 노출하지 않고 무작위 `requestId`만
돌려준다.

```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "서버 처리 중 오류가 발생했습니다.",
    "requestId": "UUID"
  }
}
```

실제 오류 메시지는 Vercel Function 로그에서 같은 `requestId`로 확인한다. 공급자 오류는
비밀값·응답 원문·stack 없이 `code`, `provider`, HTTP `status`, `providerCode`만 구조화해
기록하므로 운영 401/403·quota·timeout을 공개 응답보다 구체적으로 구분할 수 있다.
Android는 비정상 HTTP 응답의 status, `error.code`, `error.message`, `requestId`,
`Retry-After`와 요청 path를 `ApiException`에 보존한다. UI에는 message를 우선
표시하고 JSON 해석에 실패하면 `서버 요청에 실패했습니다.`를 표시한다.
연결·타임아웃 등 `IOException`은 `네트워크 연결을 확인해주세요.`로 바뀐다.

### 2.3 공개 범위

공개 API에는 사용자 인증이 없다. 고비용 `/api/recommendations`는 IP별 분당 12회,
`/api/route`는 IP별 분당 40회의 인스턴스 메모리 제한을 적용하고 초과 시
`429 RATE_LIMITED`와 `Retry-After`를 반환한다. 이 제한은 Vercel 인스턴스별
best-effort라 분산 요청이나 IP 헤더가 없는 요청을 완전히 막지 못한다. Cron 두 개만
`Authorization: Bearer {CRON_SECRET}`을 요구한다. 공개 규모가 커지면 공유 저장소나
Vercel 경계 정책으로 교체해야 한다.

## 3. 엔드포인트 요약

| Method | Path | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/health` | 없음 | 서버 버전과 연동 설정 존재 여부 |
| GET | `/api/places` | 없음 | 활성 TourAPI 장소 페이지 조회 |
| GET | `/api/places/{contentId}` | 없음 | 활성 장소 한 건 조회 |
| GET | `/api/geocode` | 없음 | 카카오 장소명·도로명·지번 주소 검색 |
| GET | `/api/region` | 없음 | 좌표의 행정구역 및 강원도 여부 |
| POST | `/api/recommendations` | 없음 | 시간·경로·카테고리 기반 추천 |
| POST | `/api/route` | 없음 | 출발·최종 목적지와 경유지 0~5개의 통합 차량 경로 재계산 |
| DELETE | `/api/account` | Supabase Bearer | 현재 로그인 사용자의 Auth 계정과 연관 사용자 데이터 영구 삭제 |
| GET | `/api/cron/tour-sync` | Bearer | TourAPI 기본 장소 페이지 동기화 |
| GET | `/api/cron/tour-catalog-sync` | Bearer | TourAPI 증분 카탈로그 동기화 |
| GET | `/api/cron/tour-intro-sync` | Bearer | 운영시간·휴무일 intro 보강 |
| GET | `/api/cron/tour-intro-sync?stage=presentation` | Bearer | 소개·사진·반려동물·반복 상세 보강 |
| GET | `/api/cron/tour-intro-sync?stage=congestion` | Bearer | 강릉 관광지 향후 30일 집중률 선동기화(현재 수동 전용) |
| GET | `/api/cron/tour-intro-sync?stage=accessibility` | Bearer | 강원 무장애 장소 목록·상세 동기화(활용신청 후 수동 전용) |
| GET | `/api/ops/status` | Bearer | Gate 1 호출량·동기화·데이터 품질 집계 |

### 3.1 장소명·주소 검색 — 2026-09-13 보완

`GET /api/geocode?query=...`는 도로명+건물번호 또는 동·리·가+지번으로 끝나는
검색어를 Kakao Local 주소 검색(`analyze_type=exact`)으로 먼저 조회한다. 실제 주소 유형
`ROAD_ADDR`/`REGION_ADDR`와 유효 좌표만 허용한다. 도로·행정구역의 중심점을 정확한
출발지로 제시하지 않는다. 주소 검색이 정상적으로 빈 결과를 반환한 경우만 기존 키워드
검색으로 이어지며, 공급자 오류를 빈 결과로 숨기지 않는다. `강릉역 2번출구`나
`용지로 176 주차장`처럼 장소명을 포함한 검색은 기존 키워드 검색을 유지한다.

기존 `id`, `name`, `address`, `category`, `latitude`, `longitude`, `kakaoMapUrl` 계약을
유지한다. 주소 응답은 `type: "ADDRESS"`, `secondaryAddress`를 추가한다. 표시 주소는
도로명 우선이며 지번 주소가 다르면 보조 정보로 제공한다. `address:`로 시작하는 안정 ID를
사용하고 중복을 제거해 최대 10건을 반환한다. 기존 클라이언트도 기존 필드로 선택할 수 있다.
출발지 전국 검색과 목적지의 강원도 지원 범위 검증은 그대로다.

변경·검증 기록:

- 새 주소 테스트는 구현 전 실패를 확인한 뒤 통과했다. Backend 전체 **265/265**,
  프로젝트 검사 **121개** 통과. DB 구조·Cron·환경변수·비밀키는 변경하지 않았다.
- Production 환경을 사용하는 도메인 미연결 후보 `dpl_5YFbunSK8kYumCs1Lxki7oV4bN6H`를
  검증한 뒤 기존 사용자 승인 범위에서 운영 도메인으로 승격했다.
- 운영 `/api/geocode`에서 `강릉시 강릉대로 33`과 `전북 익산시 부송동 100`은 각각
  실제 주소 1건, `강릉역`은 기존 장소 10건을 확인했다. 주소 응답의 좌표도 확인했다.
- 일반 Preview는 해당 환경에 필요한 비공개 설정이 없어 실패했으며 승격하지 않았다.
  운영 환경 후보의 성공과 일반 Preview 실패를 혼동하지 않는다.
- Android의 주소 구분 표시와 결과 시트 변경은 별도 code34 후보이며, 이 서버 검증은
  새 APK의 실기기 화면·제스처 검증을 대신하지 않는다.

## 4. 장소 데이터 계약

### 4.1 앱에 공개되는 장소 객체

`GET /api/places`, `GET /api/places/{id}`와 추천 응답의 `place`는 다음
필드를 사용한다.

| 필드 | 타입 | nullable | 의미 |
|---|---|---:|---|
| `content_id` | string | 아니오 | TourAPI 콘텐츠 ID, DB 기본키 |
| `source` | string | 아니오 | 현재 항상 `TOUR_API` |
| `name` | string | 아니오 | 장소명 |
| `category` | string | 아니오 | 틈새 내부 카테고리 |
| `content_type_id` | integer | 아니오 | TourAPI 콘텐츠 유형 |
| `area_code` | integer | 아니오 | 현재 강원도 `32` |
| `sigungu_code` | integer | 예 | TourAPI 시군구 코드 |
| `latitude` | number | 아니오 | 위도 |
| `longitude` | number | 아니오 | 경도 |
| `address` | string | 예 | `addr1`과 `addr2` 결합 |
| `image_url` | string | 예 | 기본 대표 이미지 또는 상세 이미지 첫 항목 |
| `tel` | string | 예 | TourAPI 전화번호 |
| `default_stay_minutes` | integer | 아니오 | 카테고리 기본 머무름 시간 |
| `image_urls` | string[] | 아니오 | 상세 이미지 URL 목록, 없으면 빈 배열 |
| `tags` | string[] | 아니오 | 정제한 편의 태그, 없으면 빈 배열 |
| `opening_hours` | string | 예 | TourAPI 원문을 HTML 제거·공백 정리한 값 |
| `closed_days` | string | 예 | TourAPI 원문을 HTML 제거·공백 정리한 값 |
| `detail_items` | object[] | 아니오 | 단건 조회 전용. `title`, `description` 반복 상세 목록 |
| `accessibility_items` | object[] | 아니오 | 단건 조회 전용. 정규화된 무장애·영유아 편의 안내 목록 |
| `image_attributions` | object[] | 아니오 | 단건 조회 전용. 이미지 URL·명칭·공공누리 유형 |
| `congestion_forecast` | object | 예 | 단건 조회 전용. 요청 예상일의 상대 집중률 예측 |

`detail_items`, `accessibility_items`와 `image_attributions`는 장소 목록·추천 payload를 키우지 않기 위해
`GET /api/places/{id}`에서만 내려준다. DB의 `raw`, `enrichment_raw`, `is_active`,
`source_modified_at`, `synced_at` 필드는 공개 응답에서 제외된다.

현재 내부 카테고리는 다음 일곱 개다.

```text
ATTRACTION, RESTAURANT, CAFE, CULTURE, FESTIVAL, SHOPPING, LEISURE
```

다만 현재 TourAPI 기본 동기화는 별도 카페 콘텐츠 유형을 매핑하지 않는다.
음식점 콘텐츠 유형 `39`는 모두 `RESTAURANT`로 저장하므로 DB에 `CAFE`가 거의
없거나 없을 수 있다. 카페 추천 품질 개선 시 분류 규칙 또는 별도 데이터 소스가
필요하다.

### 4.2 콘텐츠 유형과 기본 머무름

[`backend/lib/tour-api.js`](../backend/lib/tour-api.js)의 현재 고정값이다.

| TourAPI `contenttypeid` | 내부 카테고리 | 기본 머무름 |
|---:|---|---:|
| 12 | `ATTRACTION` | 60분 |
| 14 | `CULTURE` | 90분 |
| 15 | `FESTIVAL` | 60분 |
| 28 | `LEISURE` | 60분 |
| 38 | `SHOPPING` | 40분 |
| 39 | `RESTAURANT` | 40분 |

숙박 등 이 표에 없는 콘텐츠 유형, ID·제목·좌표가 없는 항목은 DB 행으로
변환하지 않는다.

## 5. 공개 API 상세

### 5.1 `GET /api/health`

환경변수의 **존재 여부만** 확인한다. 외부 API나 DB에 실제 요청을 보내지
않으므로 `true`가 연동 정상까지 보장하지는 않는다.

```json
{
  "status": "ok",
  "service": "tteumsae-backend",
  "version": "0.2.0",
  "timestamp": "2026-08-16T00:00:00.000Z",
  "integrations": {
    "tourApiConfigured": true,
    "tourCongestionConfigured": false,
    "weatherConfigured": false,
    "weatherEnabled": false,
    "databaseConfigured": true,
    "kakaoRoutingConfigured": true
  }
}
```

### 5.2 `GET /api/places`

쿼리:

| 이름 | 기본값 | 제한 | 설명 |
|---|---:|---:|---|
| `page` | 1 | 1 이상 정수 | 페이지 번호 |
| `pageSize` | 30 | 1~100 | 한 페이지 항목 수 |
| `category` | 없음 | 내부 카테고리 7종 | 대소문자 무관 |
| `sigunguCode` | 없음 | 1~18 정수 | TourAPI 강원 시군구 코드. 생략하면 강원도 전체 |

정렬은 장소명 오름차순이고 `is_active=true`인 행만 반환한다.
`sigunguCode`를 전달하면 DB의 `sigungu_code`가 정확히 같은 행만 서버에서
필터링한다. 지원 범위는 강릉 1, 고성 2, 동해 3, 삼척 4, 속초 5, 양구 6,
양양 7, 영월 8, 원주 9, 인제 10, 정선 11, 철원 12, 춘천 13, 태백 14,
평창 15, 홍천 16, 화천 17, 횡성 18이다.

```http
GET /api/places?page=1&pageSize=100&category=ATTRACTION&sigunguCode=1
```

```json
{
  "data": [
    {
      "content_id": "123456",
      "source": "TOUR_API",
      "name": "예시 관광지",
      "category": "ATTRACTION",
      "content_type_id": 12,
      "area_code": 32,
      "sigungu_code": 1,
      "latitude": 37.75,
      "longitude": 128.88,
      "address": "강원특별자치도 강릉시 예시로 1",
      "image_url": "https://example.invalid/image.jpg",
      "tel": null,
      "default_stay_minutes": 60,
      "image_urls": [],
      "tags": ["주차 가능"],
      "opening_hours": null,
      "closed_days": null
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 100,
    "returned": 1,
    "hasMore": false
  }
}
```

`hasMore`는 `returned === pageSize`인지로만 계산하며 전체 개수는 제공하지
않는다. 마지막 페이지가 정확히 `pageSize`개이면 다음 빈 페이지를 요청할 수
있다. `sigunguCode`가 정수가 아니거나 1~18 밖이면 400
`지원하지 않는 강원도 지역입니다.`를 반환한다. 숫자가 아닌 `page`/`pageSize`의
명시적 400 검증은 현재 없어서 DB 오류로 500이 될 수 있다.

Android 장소 탐색 화면은 최초 `강릉`을 선택해 `sigunguCode=1`을 요청한다.
지역을 바꾸면 페이지를 1로 초기화하고 이후 추가 페이지에도 같은 코드를
유지하며, `강원도 전체`에서는 코드를 생략한다. 서버 카탈로그는 코드로
필터링하지만 Android가 로컬에 저장한 장소 사본에는 `sigungu_code`가 없으므로,
이 사본은 주소에 선택 지역명이 포함되는지로 보완 필터한다. 주소가 비어 있거나
표기가 다른 경우 로컬 찜이 해당 지역 목록에서 누락될 수 있다.

### 5.3 `GET /api/places/{contentId}`

활성 장소 한 건을 `{ "data": 장소 }`로 반환한다. ID가 없거나 비활성·미존재면
404 `NOT_FOUND`다.

선택 쿼리 `atEpochMillis`가 있으면 그 시각의 KST 날짜와 exact match된 혼잡 예측을 찾는다.
생략하면 오늘 날짜를 사용한다. 예측이 없거나 migration 008 전이면 기존 상세 객체만
정상 반환한다.

```json
{
  "congestion_forecast": {
    "forecast_date": "2026-09-08",
    "concentration_rate": 72.35,
    "level": "HIGH",
    "label": "혼잡 예상",
    "fetched_at": "2026-09-07T00:00:00.000Z",
    "source": "한국관광공사 관광지 집중률 예측",
    "basis": "해당 관광지의 과거 최고 혼잡 시기 대비 상대 예측값"
  }
}
```

`LOW < 40`, `MODERATE < 70`, `HIGH >= 70`은 앱 표시용 초기 구간이다. 이 값은
KT 이동통신 기반으로 해당 관광지의 과거 최고 집중 시기를 100으로 둔 향후 30일 예측이며,
실시간 혼잡이나 장소 간 절대 인원 비교에 사용하지 않는다. 추천 순위에도 반영하지 않는다.

`KMA_WEATHER_ENABLED=true`이고 장소가 자연 관광지 `cat1=A01` 또는 명시적 야외 태그로
확인되면 같은 `atEpochMillis`의 도착 뒤 첫 정시 단기예보도 조회한다. 동일 격자·예보시각의
최신 발표 캐시가 있으면 공급자를 다시 호출하지 않는다. 예보가 없거나 공급자 호출이 실패하면
`weather_forecast`만 생략하고 기존 장소 상세는 200으로 유지한다.

`public_parking_lots`가 준비돼 있고 반경 1km 안에 좌표가 확인된 공영주차장이 있으면
직선거리순 최대 3곳을 `nearby_parking_lots`로 추가한다. 이 값은 TourAPI의 장소 자체
`parking_info`와 별개이며, 테이블이 아직 없거나 값이 없으면 배열 자체를 생략한다.

```json
{
  "nearby_parking_lots": [{
    "parking_id": "4200000:117-2-000013",
    "name": "경포 공영주차장",
    "parking_type": "노외",
    "capacity": 120,
    "distance_meters": 245,
    "distance_basis": "STRAIGHT_LINE",
    "fee_summary": "유료 · 기본 30분 1,000원",
    "operation_summary": "평일+토요일+공휴일 · 평일 09:00~18:00",
    "accessible_parking": true,
    "reference_date": "2026-07-22",
    "source": "전국주차장정보표준데이터"
  }]
}
```

```json
{
  "weather_forecast": {
    "forecast_at": "2026-09-08T02:00:00.000Z",
    "condition_label": "비 예상",
    "temperature_c": 18,
    "precipitation_probability": 70,
    "wind_speed_mps": 3.2,
    "issued_at": "2026-09-07T23:00:00.000Z",
    "fetched_at": "2026-09-07T23:20:00.000Z",
    "source": "기상청 단기예보",
    "basis": "5km 격자의 도착 무렵 예보"
  }
}
```

강수형태가 있으면 하늘상태보다 우선해 `비·눈·소나기` 등으로 표시한다. 날씨는 5km 격자
예측이며 현장 관측값이나 방문 가능 보장이 아니다. 추천 제외·순위·체류시간에는 반영하지 않는다.

### 5.4 `GET /api/geocode`

Kakao Local 키워드 검색을 서버에서 대행한다.

| 이름 | 필수 | 규칙 |
|---|---:|---|
| `q` | 예 | 앞뒤 공백 제거 후 2~100자 |
| `latitude` | 아니오 | -90~90, `longitude`와 함께 전달 |
| `longitude` | 아니오 | -180~180, `latitude`와 함께 전달 |

좌표가 있으면 해당 지점 중심 20km 안에서 거리순으로, 없으면 정확도순으로
최대 10건을 요청한다.

```json
{
  "data": [
    {
      "id": "카카오 장소 ID",
      "name": "강릉역",
      "address": "강원특별자치도 강릉시 용지로 176",
      "category": "교통,수송 > 기차역",
      "latitude": 37.764,
      "longitude": 128.899,
      "kakaoMapUrl": "https://place.map.kakao.com/..."
    }
  ],
  "meta": {
    "query": "강릉역",
    "resultCount": 1,
    "provider": "KAKAO_LOCAL"
  }
}
```

Android는 출발지와 목적지 모두 사용자가 입력한 원문 검색어로 요청한다. 목적지에서는
응답 주소가 강원으로 확인된 결과를 먼저 정렬하고, 강원 밖 결과를 숨기지 않고
`현재 지원 지역 아님`으로 표시해 선택을 막는다. 주소가 비어 판별할 수 없는 결과는
선택을 허용하되 진행 직전 `/api/region` 좌표 검증으로 다시 확인한다. 현재 Android는
응답 중 `id`, `name`, `address`, 좌표만 사용한다.

### 5.5 `GET /api/region`

```http
GET /api/region?latitude=37.7519&longitude=128.8761
```

Kakao Local 좌표→행정구역 응답에서 행정동(`region_type=H`)을 우선하고,
없으면 첫 문서를 사용한다.

```json
{
  "data": {
    "province": "강원특별자치도",
    "address": "강원특별자치도 강릉시 ...",
    "isGangwon": true
  }
}
```

`isGangwon`은 시도명이 `강원`으로 시작하는지만 판정한다. Android는 근처 탐색
가능 지역 확인과 현재 좌표의 지명 표시에 이 API를 사용한다.

### 5.6 `POST /api/recommendations`

요청은 반드시 `Content-Type: application/json`이어야 한다.

#### 활성 `ARRIVAL_DEADLINE_V1` 계약

```json
{
  "mode": "ON_THE_WAY",
  "start": { "latitude": 37.7519, "longitude": 128.8761 },
  "destination": { "latitude": 37.7644, "longitude": 128.8996 },
  "arrivalDeadlineEpochMillis": 1787907600000,
  "timeModel": "ARRIVAL_DEADLINE_V1",
  "transport": "CAR",
  "categories": ["ATTRACTION", "CULTURE"]
}
```

도착 마감은 서버가 요청을 수신한 시각부터 정확히 15분 이상 24시간 이하여야 한다.
epoch는 안전한 정수여야 하며 `deadlineMinutes`, `extraTimeMinutes`,
`safetyBufferMinutes`를 함께 보내면 400이다. 서버는 수신시각을 한 번 고정하고
남은 분을 내림 계산하며 안전여유 10분을 주입한다.

V1 추천 항목에는 다음 필드가 반드시 포함된다.

```json
{
  "minimumStayMinutes": 15,
  "maximumStayMinutes": 35,
  "latestDepartureEpochMillis": 1787906400000,
  "operationStatus": "OPEN"
}
```

응답 `meta`에는 `timeModel`, `calculatedAtEpochMillis`,
`arrivalDeadlineEpochMillis`, `minimumStayMinutes=15`, `safetyBufferMinutes=10`이
포함된다. Android V1 파서는 이 값이나 추천별 최대 체류·출발 마감이 없으면 정상
결과로 조용히 대체하지 않고 계약 오류로 처리한다.

```text
maximumStayMinutes = floor5(
  floor((arrivalDeadline - serverReceivedAt) / 60초)
  - firstLegMinutes - secondLegMinutes - 10
)
latestDepartureAt = arrivalDeadline - secondLegMinutes - 10분
```

최대 체류 15분 미만은 제외한다. 영업 종료가 명확하면 최대 체류를 더 짧게 제한하고,
운영시간 해석이 불가능한 `UNKNOWN`은 후보로 남긴다.

#### Legacy 상대시간 계약

```json
{
  "mode": "ON_THE_WAY",
  "start": { "latitude": 37.7519, "longitude": 128.8761 },
  "destination": { "latitude": 37.7644, "longitude": 128.8996 },
  "extraTimeMinutes": 90,
  "safetyBufferMinutes": 15,
  "transport": "CAR",
  "categories": ["ATTRACTION", "CULTURE"]
}
```

| 필드 | 규칙 |
|---|---|
| `mode` | `ON_THE_WAY` 또는 `NEARBY` |
| `start` | 유효한 위도·경도 숫자 |
| `destination` | 유효한 위도·경도 숫자. `NEARBY`도 현재 필수 |
| `extraTimeMinutes` | Android가 사용하는 순수 경유 여유시간. 15~1440분 정수 |
| `deadlineMinutes` | 이전 클라이언트 호환용 전체 시간 예산. 15~1440분 정수 |
| `safetyBufferMinutes` | 0~60분 정수이며 요청에 포함한 시간 필드보다 작음 |
| `transport` | `CAR` 또는 `WALK` |
| `categories` | 내부 카테고리 배열. 생략/빈 배열이면 전체 |

`deadlineMinutes`와 `extraTimeMinutes`는 legacy 요청에서 정확히 하나만 보내야 한다.
현재 Android 활성 흐름은 이 형식을 보내지 않는다.

응답 예시:

```json
{
  "baseRoute": {
    "provider": "KAKAO_MOBILITY",
    "waypointCount": 0,
    "totalDrivingMinutes": 25,
    "totalDistanceMeters": 15100,
    "tollFareWon": 0,
    "legs": [{ "drivingMinutes": 25, "distanceMeters": 15100 }],
    "path": [
      { "latitude": 37.7519, "longitude": 128.8761 },
      { "latitude": 37.7644, "longitude": 128.8996 }
    ]
  },
  "data": [
    {
      "place": { "content_id": "123456", "name": "예시 관광지" },
      "route": {
        "firstLegMinutes": 18,
        "secondLegMinutes": 22,
        "directMinutes": 25,
        "detourMinutes": 15,
        "firstLegDistanceMeters": 8200,
        "secondLegDistanceMeters": 10100,
        "totalDistanceMeters": 18300,
        "path": [
          { "latitude": 37.75, "longitude": 128.87 },
          { "latitude": 37.76, "longitude": 128.89 }
        ],
        "provider": "KAKAO_MOBILITY"
      },
      "stayMinutes": 60,
      "totalMinutes": 100,
      "marginMinutes": 15,
      "operationStatus": "OPEN",
      "safetyLevel": "AVAILABLE"
    }
  ],
  "meta": {
    "candidateCount": 200,
    "corridorCandidateCount": 80,
    "routeCandidateCount": 20,
    "routeFailureCount": 0,
    "recommendationCount": 20,
    "routeProvider": "KAKAO_MOBILITY",
    "baseRouteMinutes": 25,
    "extraTimeMinutes": 90,
    "effectiveDeadlineMinutes": 115,
    "safetyBufferMinutes": 15,
    "corridorRadiusMeters": 1800,
    "baseRoute": { "waypointCount": 0, "provider": "KAKAO_MOBILITY" }
  }
}
```

`baseRoute`는 `CAR + ON_THE_WAY`, 또는 실제 도보 경로가 활성화된
`WALK + ON_THE_WAY`일 때 최상위와 `meta`에 모두 포함된다.
Android는 최상위 객체를 `RouteSummary`로 파싱하고 `meta.corridorRadiusMeters`를
결과 지도에 사용한다. `RouteSummary`는 provider, waypointCount,
totalDrivingMinutes, totalDistanceMeters, tollFareWon, legs, path를 보존한다.

`WALK`는 `KAKAO_WALK_ROUTE_ENABLED=true`일 때 `KAKAO_MAP_WALK`의 거리·path를
반환한다. 기능이 꺼져 있으면 `ESTIMATE`, 켜져 있지만 공급자 호출이 실패하면
`ESTIMATE_FALLBACK`이며 두 예상값 상태 모두 `meta.warning`을 포함한다.

#### 후보 조회와 상한

`CAR + ON_THE_WAY`:

1. Kakao Mobility에 경유지 없는 출발→목적 `baseRoute`를 요청한다.
2. `effectiveDeadlineMinutes = baseRoute.durationMinutes + extraTimeMinutes`를
   계산한다. corridor 반경은 `extraTimeMinutes × 20m`이며 800~8000m로
   제한한다.
3. `baseRoute.path`의 위·경도 bounds를 corridor만큼 넓혀 활성 장소를 최대
   500개 읽는다.
4. 각 장소에서 path 선분까지의 로컬 평면 근사 거리가 corridor 이내인 것만
   남긴다.
5. 카테고리를 적용하고 추정 우회시간이 짧은 최대 20개를 고른다.
6. 각 후보에 출발→후보 1곳→목적 Kakao 경로를 요청하고 시간 조건을 통과한
   최대 20개를 반환한다.

실제 도보 경로가 활성화된 `WALK + ON_THE_WAY`도 base path와 corridor를 사용한다.
그 밖의 모드/이동수단은 출발·목적 사각형에 자동차 약 ±0.22도, 도보 약 ±0.055도
패딩을 더한 기존 bounds를 사용한다.

따라서 `candidateCount`는 bounds에서 읽은 최대 500개의 개수,
`corridorCandidateCount`는 path 거리 필터 후 개수다. 둘 다 DB 전체 개수가
아니다.

#### Legacy 시간 공식

```text
effectiveDeadlineMinutes = baseRouteMinutes + extraTimeMinutes
우회 주행시간 = 후보 경유 주행시간 - baseRouteMinutes
남는 순수 여유 = extraTimeMinutes - (우회 주행시간 + 기본 머무름)
추천 조건 = 우회 주행시간 + 기본 머무름 + safetyBufferMinutes
            <= extraTimeMinutes
```

안전도:

| 남는 시간 | `safetyLevel` |
|---:|---|
| 20분 이상 | `COMFORTABLE` |
| 10~19분 | `AVAILABLE` |
| 0~9분 | `TIGHT` |

운영시간은 장소 도착 예상시각부터 기본 머무름이 끝나는 시각까지를 기준으로
단순 파싱한다. 그 사이 명확히 휴무/영업 종료로 판정된 `CLOSED` 장소는
제외한다. 데이터가 없거나 복잡해서 해석하지 못하면
`UNKNOWN`으로 추천에 남기며 앱이 `운영시간 확인 필요`를 표시한다.

`marginMinutes`는 위 식의 `남는 순수 여유`이며 사용자가 입력한 시간에서
우회와 머무름을 제외하고 남은 값이다. 교통과 실제 체류시간이 바뀔 수 있으므로
V1과 legacy 모두 교통·외부 내비 차이 때문에 `100% 도착 보장`으로 표현하지 않는다.

#### 자동차 경로의 현재 한계

- 장소마다 `origin → waypoint 1개 → destination`을 요청한다.
- 후보별 요청은 동시성 5로 실행한다.
- 일부 실패는 제외하고 `routeFailureCount`에 넣는다.
- 후보가 있는데 모든 경로가 실패하면 추천 API 전체가 500이다.
- `directMinutes`는 앞에서 받은 Kakao `baseRoute.durationMinutes`로 덮어쓰며,
  후보의 `detourMinutes`는 후보 경유 경로와 baseRoute의 차이다.
- 후보별 계산은 한 곳만 경유한다. 여러 후보 조합은 아래 `/api/route`가 별도로
  계산한다.

#### 도보 경로의 현재 계약과 한계

```text
속도 4.5km/h × 직선거리 × 도로계수 1.2
```

기능 플래그가 꺼졌거나 카카오 응답을 사용할 수 없을 때 위 예상값으로 전환한다.
활성 시에는 카카오맵 도보 경로 API의 `ACCESSIBLE` 경로를 후보 최대 8곳까지 사용하며,
provider는 `KAKAO_MAP_WALK`다. 공식 무료량 1,000건 중 700건에서 경고하고 800건에서
호출 전에 차단한다. 활성화 후 실패한 요청은 `ESTIMATE_FALLBACK`으로 구분한다.

실제 경로도 현장 통행 제한·신호 변화와 카카오맵 앱의 재계산 결과가 다를 수 있으므로
앱과 문구에서 100% 시간 보장을 표현하면 안 된다.

### 5.7 `POST /api/route`

이전 클라이언트가 경유지를 추가하거나 제거할 때 호출하는 차량 전용 통합 경로
계산 API다. 신규 Gate 2 제품 UI는 이 API를 호출하지 않지만 0~5개 호환 계약과
테스트는 유지한다.

```json
{
  "start": { "latitude": 37.7519, "longitude": 128.8761 },
  "destination": { "latitude": 37.7644, "longitude": 128.8996 },
  "waypoints": [
    {
      "contentId": "123456",
      "latitude": 37.758,
      "longitude": 128.887
    }
  ]
}
```

| 필드 | 규칙 |
|---|---|
| `start`, `destination` | 유효한 위도·경도 숫자 |
| `waypoints` | 생략 가능, 배열 0~5개, 각 항목에 유효한 좌표 필수 |
| `contentId` | Android가 함께 보내지만 현재 서버 검증/경로 요청은 좌표만 사용 |

6개 이상, 좌표 범위 오류, 잘못된 JSON은 Kakao 호출 전에 400으로 거부한다.
서버는 waypoint 배열 순서를 그대로 Kakao `waypoints=lon,lat|...`에 전달하며
순서를 최적화하지 않는다.

성공 응답:

```json
{
  "data": {
    "provider": "KAKAO_MOBILITY",
    "waypointCount": 1,
    "durationMinutes": 38,
    "distanceMeters": 19200,
    "tollFare": 0,
    "totalDrivingMinutes": 38,
    "totalDistanceMeters": 19200,
    "tollFareWon": 0,
    "legs": [
      { "drivingMinutes": 17, "distanceMeters": 8000 },
      { "drivingMinutes": 21, "distanceMeters": 11200 }
    ],
    "path": [
      { "latitude": 37.7519, "longitude": 128.8761 },
      { "latitude": 37.758, "longitude": 128.887 },
      { "latitude": 37.7644, "longitude": 128.8996 }
    ]
  }
}
```

`path`는 Kakao road vertex를 출발·목적과 합친 뒤 최대 200점으로 균등
샘플링한다. `legs`는 경유지 수+1개여야 하며 Kakao 응답이 이 계약을 만족하지
않으면 서버가 500을 반환한다. Android는 실패 시 단일 후보 우회값을 합친
`ESTIMATE` fallback을 표시하고 토스트를 띄운다. fallback은 Kakao 통합
경로와 동급 정확도가 아니다.

### 5.8 `DELETE /api/account`

선택형 로그인 사용자의 계정을 영구 삭제한다. 요청은
`Authorization: Bearer {Supabase access token}`만 사용하며 JSON 본문이나
클라이언트가 제시한 사용자 ID는 신뢰하지 않는다.

서버는 publishable key와 사용자 Bearer 토큰으로 `/auth/v1/user`를 조회한 뒤,
그 응답의 `id`만 service role 관리자 삭제 경로에 전달한다. 성공은 본문 없는
204, 토큰 누락·무효는 401, 인스턴스 기준 IP별 분당 3회 초과는 429다.
관리자 삭제 오류는 외부 응답 본문을 노출하지 않는 공통 500으로 바뀐다.

`profiles`와 `user_saved_places`는 `auth.users(id) on delete cascade`라 Auth 사용자
삭제와 함께 제거된다. service role 키는 Vercel 환경변수에만 저장하며 Android
APK나 응답에 포함하지 않는다.

## 6. TourAPI 동기화

### 6.1 기본 장소 동기화

경로: `GET /api/cron/tour-sync`
소스: TourAPI `KorService2/areaBasedList2`

고정 요청값:

```text
MobileOS=ETC
MobileApp=Tteumsae
_type=json
arrange=C
areaCode=32
numOfRows=100
```

한 실행에서 `TOUR_SYNC_MAX_PAGES`만큼 처리하며 기본값 10, 코드상 최대 25다.
각 페이지를 즉시 upsert하고 `sync_state(id='tour_api')`에 다음 페이지를
저장한다. 전체를 마치면 `next_page`를 1로 되돌린다. 오류 시 가능한 경우
`last_error`를 최대 500자로 저장한 뒤, 호출자에게는 공통 500만 반환한다.

성공 응답:

```json
{
  "status": "partial",
  "processedPages": 10,
  "savedPlaces": 1000,
  "totalCount": 2500,
  "nextPage": 11
}
```

`status`는 전체 순회를 마치면 `completed`, 이어서 처리해야 하면 `partial`이다.

### 6.2 운영정보 보강 동기화

경로: `GET /api/cron/tour-intro-sync`

기본 stage는 활성 장소의 `detailIntro2`를 보강한다. `stage=presentation`은
`detailCommon2`, `detailImage2`, `detailPetTour2`를 먼저 처리하고, 그 stage가 이미 끝난
장소부터 `detailInfo2` 반복 상세를 별도 단계로 처리한다. `detailInfo2`는 HTML과 중복을
제거해 제목·설명이 모두 있는 항목만 앱에 공개한다. 사진 응답의 `cpyrhtDivCd`는 URL과
함께 보존하며 상세 화면은 실제 대표 사진 URL과 일치할 때만 권리 유형을 표시한다.

모든 stage는 제한된 배치와 동시성으로 실행한다. 실패는 기존 정상값을 지우지 않고 재시도
시각과 오류 요약만 기록한다. 과거
`/api/cron/tour-detail-sync`는 예약되지 않은 레거시 경로였고 Vercel Hobby 함수
12개 한도를 지키기 위해 2026-08-28 제거했다.

### 6.3 Cron 일정

[`backend/vercel.json`](../backend/vercel.json)은 UTC를 사용한다.

| UTC | 한국시간(KST) | 작업 |
|---|---|---|
| 매일 18:20 | 다음 날 03:20 | 증분 카탈로그 동기화 (`tour-catalog-sync`) |
| 매일 22:20 | 다음 날 07:20 | 운영정보 보강 (`tour-intro-sync`) |
| 매일 22:40 | 다음 날 07:40 | 소개·사진·반복 상세 보강 (`stage=presentation`) |

세 작업 모두 최대 실행시간 60초다. 데이터가 많으면 한 번에 전체를 끝내지 않고
`sync_state` 커서를 다음 날 이어서 처리한다. Vercel Hobby는 같은 시간대 안의 분 단위
실행 순서를 보장하지 않으므로 현재 20분·40분 차이를 작업 선후관계로 간주하면 안 된다.

`stage=congestion`은 같은 서버 함수 안에 구현됐지만 아직 Cron 목록에는 없다. 별도 활용신청,
`TOUR_CONGESTION_API_SERVICE_KEY`, migration 008과 Preview 매칭률 검수가 끝난 후에만 예약한다.

### 6.4 무장애 여행정보 동기화

경로: `GET /api/cron/tour-intro-sync?stage=accessibility`

소스는 한국관광공사 `KorWithService2`의 `areaBasedSyncList2`와 `detailWithTour2`다.
강원도 `areaCode=32`, 표출 상태 `showflag=1` 목록을 기본 20개씩 읽고
`sync_state(id='tour_accessibility')`의 페이지 cursor를 갱신한다. 목록에 포함된 content ID만
상세 조회하므로 일반 장소 1,713곳을
전부 무장애 API에 대입하지 않는다. 동시성은 기존 `TOUR_SYNC_CONCURRENCY`와 코드상 최대 4를
따른다.

공급자 원문은 `enrichment_raw.accessibility`, 공개용 제목·설명은
`enrichment_raw.accessibilityItems`에 보존한다. `save_place_accessibility` RPC가 기존 intro,
common, media, info 값을 잃지 않도록 JSONB를 원자 병합한다. 상세 API는 공개용 배열만
`accessibility_items`로 반환하고 Android는 배열이 비어 있으면 섹션 자체를 만들지 않는다.
공식 필드 의미에 따라 `route`는 `접근로`, `publictransport`는 `대중교통`으로 표시한다.
`없음` 같은 공식 값도 임의로 가능·불가능으로 해석하지 않고 원문 의미를 유지한다.

현재 이 stage는 Vercel Cron에 예약하지 않았다. 공식 활용신청, migration 010 적용,
Preview 실응답과 강원도 content ID 매칭률을 확인한 뒤에만 운영 주기를 정한다.

### 6.5 주변 공영주차장 동기화

경로: `GET /api/cron/tour-intro-sync?stage=parking`

국토교통부·지방자치단체의 전국주차장정보표준데이터에서 강릉시 제공기관의 `공영`이면서 주소가 강원특별자치도
또는 구 강원도 표기이고 좌표가 유효한 행만 정규화한다. `sync_state(id='public_parking')`의
독립 페이지 cursor를 사용해 한 번에 100건을 읽고 `source_id` 충돌 시 최신 값으로 갱신한다.
원문과 운영·요금 세부값은 RLS와 권한이 적용된 서버 전용 테이블에 저장하고 공개 API에는
표시용 요약만 반환한다. 원천은 개별 기관 데이터가 합쳐져 시차가 있을 수 있으므로 앱에도
`reference_date`와 직선거리 기준을 함께 표시한다.

현재 stage는 Vercel Cron에 예약하지 않았다. `PUBLIC_PARKING_API_SERVICE_KEY` 활용신청,
migration 011, Preview 전체 순회와 폐업·삭제 행 갱신 정책 검증 뒤 운영 주기를 정한다.

## 7. Supabase 스키마

마이그레이션 적용 순서:

1. [`001_initial.sql`](../backend/migrations/001_initial.sql)
2. [`002_detail_sync_state.sql`](../backend/migrations/002_detail_sync_state.sql)
3. [`003_user_accounts.sql`](../backend/migrations/003_user_accounts.sql)
4. [`004_tour_enrichment.sql`](../backend/migrations/004_tour_enrichment.sql)
5. [`005_sync_runtime_safety.sql`](../backend/migrations/005_sync_runtime_safety.sql)
6. [`006_gate_1b_data_trust.sql`](../backend/migrations/006_gate_1b_data_trust.sql)
7. [`007_tour_detail_info.sql`](../backend/migrations/007_tour_detail_info.sql)
8. [`008_tour_congestion_forecasts.sql`](../backend/migrations/008_tour_congestion_forecasts.sql)
9. [`009_weather_forecast_cache.sql`](../backend/migrations/009_weather_forecast_cache.sql)
10. [`010_tour_accessibility.sql`](../backend/migrations/010_tour_accessibility.sql)
11. [`011_public_parking_lots.sql`](../backend/migrations/011_public_parking_lots.sql)
12. [`012_provider_usage_sources.sql`](../backend/migrations/012_provider_usage_sources.sql)

### 7.1 `public.places`

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `content_id` | text | PK |
| `source` | text | 기본값 `TOUR_API` |
| `name` | text | NOT NULL |
| `category` | text | NOT NULL |
| `content_type_id` | integer | NOT NULL |
| `area_code` | integer | 기본값 32 |
| `sigungu_code` | integer | nullable |
| `latitude`, `longitude` | double precision | NOT NULL |
| `address`, `image_url`, `tel` | text | nullable |
| `default_stay_minutes` | integer | 5~360 체크 |
| `is_active` | boolean | 기본값 true |
| `source_modified_at` | text | TourAPI 수정시각 원문 |
| `synced_at` | timestamptz | 기본값 now() |
| `raw` | jsonb | TourAPI 카탈로그 원문 |
| `enrichment_raw` | jsonb | intro/common/media/detailInfo2 원문과 공개용 정규화 결과 |
| `info_synced_at` | timestamptz | detailInfo2 정상 응답 확인 시각, 빈 응답도 완료로 기록 |
| `accessibility_synced_at` | timestamptz | detailWithTour2 정상 응답을 원자 병합한 시각 |

인덱스는 `category`, `(latitude, longitude)`, 활성 행의 `is_active`에 있다.

### 7.2 `public.sync_state`

| 컬럼 | 의미 |
|---|---|
| `id` | `tour_api`, `tour_details`, `tour_accessibility`, `public_parking` 등의 작업 ID, PK |
| `next_page` | 다음 실행에서 읽을 페이지 |
| `total_count` | 마지막 기본 동기화의 TourAPI 총 개수 |
| `last_processed_page` | 마지막 처리 페이지 |
| `last_item_count` | 마지막 페이지 항목 수 |
| `last_error` | 마지막 오류 요약 |
| `last_completed_at` | 전체 순회 완료시각 |
| `updated_at` | 상태 갱신시각 |

`places`, `sync_state`는 RLS가 활성화되어 있고 공개 정책은 마이그레이션에 없다. 서버는
`SUPABASE_SERVICE_ROLE_KEY`로 PostgREST를 호출해 RLS를 우회한다. service role
키를 Android, 브라우저, 로그, 문서에 노출하면 안 된다.

### 7.3 `public.profiles`

선택형 로그인 사용자의 비공개 프로필이다. `user_id`는 `auth.users(id)`를
참조하며 계정 삭제 시 함께 삭제된다. 닉네임은 공백 정리 후 1~40자,
아바타 URL은 최대 2048자다. `age_group`과 `gender`는 nullable 선택값이며
현재 추천 요청이나 점수에는 사용하지 않는다.

authenticated 역할은 본인 행만 조회·추가·수정할 수 있다. `user_id`, 생성시각,
수정시각은 클라이언트가 update할 수 없고 물리 delete 권한도 없다.

### 7.4 `public.user_saved_places`

로그인 사용자의 장소 저장 상태를 `(user_id, place_id)` 복합키로 보관한다.
`is_saved=false` 행도 남기는 tombstone 구조라 여러 기기의 저장 해제를 동기화할
수 있다. `place_id`는 `places(content_id)`를 참조하고 사용자나 장소 삭제 시
연관 행이 함께 삭제된다.

authenticated 역할은 본인 행의 `is_saved`, `saved_at`만 수정할 수 있고 물리
delete는 할 수 없다. [`verify-user-rls.js`](../backend/scripts/verify-user-rls.js)는
테스트 프로젝트에서 임시 사용자 2명을 만들어 본인 CRUD와 교차 사용자 차단을
실제로 확인한다.

### 7.5 Gate 1-B 운영·검수 데이터

- `provider_usage_daily`: KST 날짜·공급자·operation별 예약/성공/오류 집계
- `place_curations`: 출처 URL과 검수시각을 가진 운영시간·입장·주차 overlay
- `effective_places`: 원본 장소에 검수값을 우선 적용하는 서버 전용 view
- `get_gate_1b_ops_status`: 호출량, sync lease 상태, 데이터 품질을 묶는 service-role RPC

Kakao Mobility는 기본 7,000건부터 경고하고 8,000건에서 호출 전에 차단한다. 차단이나
공급자 429/쿼터 오류는 503과 `Retry-After`로 공개되며 초기화 기준은 KST 자정이다.
운영시간 파서는 평일·주말·입장 마감·자정 넘김을 보수적으로 판정하고 계절·공휴일·
상충 문구를 완전히 해석하지 못하면 `OPEN` 대신 `UNKNOWN`을 반환한다.

### 7.6 `public.tour_congestion_forecasts`

강릉 법정동 코드 `51/51150`의 관광지별 향후 30일 상대 집중률을 날짜별로 저장한다.
원천에는 TourAPI content ID가 없으므로 공백·유니코드를 정규화한 이름이 활성 비음식 장소
하나와 정확히 일치할 때만 `content_id`를 연결한다. 나머지는 `AMBIGUOUS` 또는 `UNMATCHED`로
보존하며 공개 상세에는 `MATCHED`만 조회된다. RLS를 활성화하고 service role에만 권한을 준다.

### 7.7 `public.weather_forecast_cache`

기상청 단기예보를 `nx`, `ny`, `forecast_at` 복합키로 캐시한다. 발표시각, 수집시각,
기온, 강수확률·형태, 하늘상태와 풍속을 저장하며 RLS와 권한 회수로 service role만 접근한다.
장소별 복제 대신 5km 격자별로 공유해 같은 시간대의 인접 장소 상세 호출을 한 번으로 줄인다.

### 7.8 `public.public_parking_lots`

전국주차장정보표준데이터 중 공영·강릉 제공기관·강원 주소·유효 좌표 조건을 통과한 행을 `source_id`로
upsert한다. 운영요일·시간, 요금, 주차면수, 장애인 전용 주차구역 여부, 데이터 기준일과
원문을 저장한다. `(latitude, longitude)` 인덱스로 장소 주변 경계 상자를 좁히고 서버에서
직선거리를 재계산한다. RLS를 활성화하고 service role에만 CRUD를 허용하며 Android와
공개 클라이언트가 원문 테이블을 직접 읽지 못하게 한다.

`GET /api/ops/status`는 공개 health와 분리되어 `CRON_SECRET` Bearer 인증 뒤에만
집계값을 반환한다. 좌표, 검색어, 사용자 식별자와 외부 응답 전문은 집계하지 않는다.

## 8. 변경 시 함께 확인할 파일

| 변경 종류 | 백엔드 | Android |
|---|---|---|
| 장소 필드 추가 | `lib/database.js`, 마이그레이션 | `data/TteumsaeApi.kt`, `domain/Models.kt` |
| 장소 지역 필터 변경 | `api/places.js`, `lib/database.js` | `TteumsaeApi.places`, `gangwonRegionCodes`, `SavedPlacesScreen` |
| 카테고리 추가 | `lib/validation.js`, `lib/tour-api.js` | `domain/Models.kt`, 필터 UI |
| 추천 요청 변경 | `lib/validation.js`, `api/recommendations.js` | `data/TteumsaeApi.kt` |
| 추천 응답 변경 | `lib/time-safe.js`, 경로 provider | `toRecommendation()` |
| 기준 경로/corridor 변경 | `api/recommendations.js`, `lib/routing.js`, `lib/kakao-mobility.js` | `RecommendationResult`, `RouteMap` |
| legacy 복수 경유 계약 변경 | `api/route.js`, `parseRouteRequest`, `fetchKakaoRoute` | `TteumsaeApi.route`, `RouteSummary` |
| Gate 2 한 곳 결과 변경 | `lib/time-safe.js`, `api/recommendations.js` | `ui/route/ResultsScreen.kt`, `RouteFlowViewModel` |
| 운영시간 규칙 변경 | `lib/time-safe.js`, `lib/tour-api.js` | 카드 상태 문구 |
| Cron 변경 | `vercel.json`, `api/cron/*` | 해당 없음 |

계약 변경에는 반드시 백엔드 Node 테스트와 Android JSON 파서 테스트를 함께
추가한다. 현재 소스의 `sigunguCode` 검증과 route/baseRoute/corridor를 포함한
백엔드 테스트 수는 실행 시점에 달라질 수 있으므로 `pnpm test`의 실제 결과를 배포
기록에 남긴다.

## 9. 알려진 데이터·API 부채

- 공개 API 사용자 인증과 구조화된 요청 추적은 없다. 추천/경로의 현재 IP 제한은
  서버리스 인스턴스별 best-effort라 분산·병렬 남용 방어가 아니다.
- 장소 목록은 총 개수를 제공하지 않는다.
- Android의 지역 카탈로그는 서버 코드 필터를 사용하지만 로컬 찜 사본은 주소
  문자열로 판정해 주소 누락·행정구역 표기 차이에 취약하다.
- `/api/route`는 인증 없이 Kakao Mobility 호출을 만들며 현재 IP 제한만 적용한다.
- 경유지 순서는 사용자가 추가한 순서이며 서버 최적화가 없다.
- Android의 `/api/route` 실패 fallback은 실제 통합 경로가 아닌 단일 후보
  우회값 합산이다.
- API는 이전 클라이언트를 위해 `deadlineMinutes`도 허용한다. 신규 Android는
  `extraTimeMinutes`만 보내므로 계약 변경 시 두 경로의 호환 테스트가 필요하다.
- corridor는 path까지의 평면 근사 거리이며 도로 등시간선이 아니다.
- 도보는 실제 길찾기가 아닌 추정이다.
- 영업시간 파서는 단순 요일·시간 범위만 안전하게 해석한다.
- TourAPI 원문 변경이나 복잡한 휴무 표현은 `UNKNOWN`으로 남는다.
- 무장애 정보는 상세 조건부 표시까지만 준비됐고, 실데이터 범위가 확인되기 전에는 필터나 추천에 쓰지 않는다.
- 주차장 전체 순회는 upsert까지 구현됐고, 다음 순회에서 사라진 폐업·삭제 행을 제거하는 정책은 Preview 검수 뒤 확정해야 한다.
- 미디어·반려동물 정보의 신규 전용 동기화 경로는 아직 구현하지 않았다.
- `vercel.json`의 `/downloads/tteumsae-latest-debug.apk` rewrite는 과거
  `v0.4.0` 파일을 가리키는 레거시 설정이다. 현재 APK 다운로드는 별도
  [`download/`](../download/) 프로젝트에서 관리하므로 수정 또는 제거해야 한다.
