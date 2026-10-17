# Gate 1-A Runtime Safety Design

상태: 승인된 설계
기준일: `2026-08-28`

## 1. 목적

Vercel Hobby와 Supabase Free 구성을 유지하면서, TourAPI 동기화와 사용자 추천 요청이
중복 실행·무한 대기·과도한 Kakao 호출 때문에 불안정해지는 경로를 먼저 차단한다.

이 설계가 완료되면 다음을 보장한다.

- 같은 동기화 작업은 DB lease를 획득한 실행 하나만 처리한다.
- 모든 TourAPI·Kakao·Supabase 네트워크 호출은 제한 시간 안에 끝나거나 중단된다.
- 추천 요청 전체와 Cron 작업에는 Vercel 종료 시간보다 짧은 내부 deadline이 있다.
- 자동차 추천 한 건의 실제 Kakao Mobility 후보 호출은 최대 8개다.
- Cron 작업의 마지막 시작·종료·상태·요약을 `sync_state`에서 확인할 수 있다.

## 2. 검증된 현재 기준선

- `backend/vercel.json`은 모든 Function의 `maxDuration`을 60초로 제한한다.
- 카탈로그 Cron은 `20 18 * * *`, intro Cron은 `40 18 * * *`로 같은 UTC 시간대다.
- Vercel Hobby Cron은 일 1회까지만 지원하고 지정한 시간 안에서 최대 ±59분 오차가
  있으므로 현재 두 작업의 순서와 비중첩을 보장할 수 없다.
- Vercel Cron은 UTC를 사용하고 실패한 실행을 자동 재시도하지 않는다.
- `sync_state`에는 진행 커서가 있지만 lease와 실행 결과 요약이 없다.
- `database.js`, `tour-api.js`, `kakao-local.js`, `kakao-mobility.js`,
  `supabase-auth.js`의 `fetch()`에는 timeout이 없다.
- 자동차 추천은 환경변수 기본값과 상한이 모두 20이어서 추천 한 건이 기본 경로 1회와
  후보 경로 최대 20회를 Kakao Mobility에 요청할 수 있다.

공식 근거:

- [Vercel Cron usage and pricing](https://vercel.com/docs/cron-jobs/usage-and-pricing)
- [Vercel Cron jobs](https://vercel.com/docs/cron-jobs)
- [Vercel Function limits](https://vercel.com/docs/functions/limitations)
- [Supabase Database Functions](https://supabase.com/docs/guides/database/functions)
- [PostgreSQL INSERT ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html)

## 3. 범위

### 포함

1. 두 일일 Cron의 UTC 실행 시간대를 4시간 분리한다.
2. `sync_state` 확장과 제한된 Supabase RPC로 원자적 lease claim/finish를 구현한다.
3. 공통 외부 요청 timeout과 전체 작업 deadline을 구현한다.
4. Kakao Mobility 후보 상한을 8개로 낮춘다.
5. timeout과 중복 실행의 API 응답 계약을 고정한다.
6. 관련 자동 테스트와 운영 문서를 갱신한다.

### 제외

- 단일 Cron 오케스트레이터, 외부 큐, 별도 워커
- 일일 Kakao 호출량·429·5xx의 영구 집계
- 운영시간 파서 고도화와 운영시간 보강률 대시보드
- 지난 축제·날짜 불완전 축제 필터
- 알파 핵심 장소 100~300개 수동 검수
- 도착 마감 1곳 신규 제품 플로우

제외 항목은 `Gate 1-B 데이터 품질·관측성` 또는 Gate 2 설계에서 다룬다.

## 4. 채택한 접근

### 4.1 분리된 Cron 유지

카탈로그와 intro 작업은 독립 endpoint로 유지한다.

| 작업 | Vercel UTC 표현식 | Hobby 실행 창 | 한국 시각 실행 창 |
|---|---|---|---|
| 카탈로그 | `20 18 * * *` | 18:00~18:59 UTC | 다음 날 03:00~03:59 KST |
| intro | `20 22 * * *` | 22:00~22:59 UTC | 다음 날 07:00~07:59 KST |

시간 창 사이가 최소 3시간이므로 60초 Function이 정상 종료하는 한 두 정기 실행은
겹치지 않는다. 수동 호출·재배포·비정상 지연에 의한 중복은 DB lease가 차단한다.

단일 오케스트레이터는 채택하지 않는다. 한 단계의 지연이나 실패가 다른 단계의 실행 기회를
없애고, 60초 안에서 단계별 예산과 재개 정책이 더 복잡해지기 때문이다.

### 4.2 `sync_state` 기반 원자적 lease

새 migration `005_sync_runtime_safety.sql`은 `public.sync_state`에 다음 열을 추가한다.

| 열 | 타입 | 의미 |
|---|---|---|
| `lease_token` | `text` nullable | 현재 실행 소유자의 UUID |
| `lease_expires_at` | `timestamptz` nullable | crash 후 자동 회수가 가능한 시각 |
| `last_started_at` | `timestamptz` nullable | 마지막으로 claim에 성공한 시각 |
| `last_finished_at` | `timestamptz` nullable | 마지막 finish 시각 |
| `last_status` | `text` nullable | `completed`, `partial`, `failed` 중 하나 |
| `last_duration_ms` | `bigint` nullable | 마지막 실행 소요시간 |
| `last_run_summary` | `jsonb` | 처리량과 실패 수의 작은 JSON 요약 |

새 테이블은 만들지 않는다. 기존 작업별 `sync_state.id`와 진행 커서를 그대로 활용하면
마이그레이션과 운영 조회가 단순하다.

두 PostgreSQL Function을 추가한다.

```text
claim_sync_job(p_id text, p_token text, p_now timestamptz, p_lease_seconds integer)
  -> boolean

finish_sync_job(p_id text, p_token text, p_status text,
                p_summary jsonb, p_finished_at timestamptz)
  -> boolean
```

`claim_sync_job`은 `INSERT ... ON CONFLICT DO UPDATE ... WHERE` 한 문장으로 행 생성 또는
만료 lease 교체를 수행한다. lease가 살아 있으면 행을 바꾸지 않고 `false`를 반환한다.
기본 lease는 90초로 60초 Function 제한보다 길고, 프로세스가 강제 종료돼도 자동 만료된다.

`finish_sync_job`은 `id`와 `lease_token`이 모두 일치할 때만 상태·소요시간·요약을 기록하고
lease를 비운다. 오래된 실행이 새 실행의 lease를 해제할 수 없다.

두 Function은 기본 `security invoker`와 빈 `search_path`를 사용하고 객체 이름을 완전
수식한다. `public`, `anon`, `authenticated`의 실행 권한을 회수하고 `service_role`에만
실행 권한을 준다.

### 4.3 서버 코드 lease 경계

`lib/sync-lease.js`는 다음 하나의 책임만 가진다.

```text
runWithSyncLease({ jobId, leaseSeconds, run, claim, finish, now, token })
```

처리 순서:

1. UUID token을 만든다.
2. DB claim을 호출한다.
3. claim 실패 시 작업을 실행하지 않고 `{ status: "skipped", reason: "already_running" }`을
   반환한다.
4. claim 성공 시 실제 작업을 실행한다.
5. 정상 결과의 `completed` 또는 `partial`과 요약을 finish에 전달한다.
6. 오류 시 `failed`와 민감정보가 제거된 오류 분류를 best-effort로 기록하고 원래 오류를
   다시 던진다.
7. finish 자체가 실패하면 원래 작업 결과를 성공으로 위장하지 않는다. lease는 90초 뒤
   만료되고 호출은 서버 오류로 종료한다.

카탈로그는 `tour_catalog_delta`, intro는 `tour_intro` 행을 claim한다. 인증 실패 요청은
DB에 접근하지 않는다. 수동 Bearer 호출도 Cron과 같은 lease를 사용한다.

### 4.4 외부 요청 timeout과 전체 deadline

`lib/fetch-policy.js`에 timeout 정책과 오류 타입을 집중시킨다.

| 경계 | 제한 |
|---|---:|
| Supabase REST/RPC 한 요청 | 5초 |
| Kakao Local 한 요청 | 5초 |
| Kakao Mobility 한 요청 | 8초 |
| TourAPI 한 요청 | 8초 |
| 추천 endpoint 전체 | 25초 |
| Cron 네트워크 작업 절대 제한 | 50초 |
| Cron 새 단위 작업 시작 중단 | 45초 |

```text
fetchWithTimeout(url, init, { provider, timeoutMs, signal, fetchImpl })
createDeadline(timeoutMs)
UpstreamTimeoutError(provider)
```

`fetchWithTimeout`은 호출자 signal과 자체 timeout signal을 결합한다. timeout이면
`UpstreamTimeoutError`로 정규화하고, 호출자가 취소한 경우에는 원래 abort를 유지한다.
응답 body 파싱도 전체 deadline 안에서 끝나야 한다.

추천 handler는 25초 signal을 DB 조회, 기본 경로, 후보 경로에 전달한다. Cron은 45초가
지나면 새 페이지나 새 장소를 시작하지 않고 현재까지 결과를 `partial`로 저장한다. 이미
진행 중인 외부 요청은 50초 signal이 중단하며, 남은 시간은 finish와 HTTP 응답에 사용한다.

Vercel `maxDuration=60`은 유지한다. 플랫폼 종료 시간 자체를 작업 제어 수단으로 사용하지
않는다.

### 4.5 Kakao Mobility 후보 상한

`KAKAO_ROUTE_CANDIDATE_LIMIT`은 계속 지원하지만 서버가 허용하는 범위는 1~8이다.
미설정·잘못된 값·8 초과 값의 최종 상한은 8이다.

처리 순서는 유지한다.

1. 최대 500개 DB 장소 조회
2. corridor 밖 장소 제거
3. 카테고리·운영 가능성·직선 추정 기준 1차 정렬
4. 상위 최대 8개만 Kakao Mobility 실제 경로 요청
5. 안전여유 조건을 적용하고 최대 20개 응답 형식은 호환 유지

후보가 8개 이하이면 현재와 같은 결과를 유지한다. 후보가 많을 때는 비용과 지연을
제한하는 대신 1차 휴리스틱 밖의 장소가 결과에서 빠질 수 있다. Gate 3 실제 경로 검증에서
추천 누락률을 측정한 뒤 상한 조정을 다시 판단한다.

### 4.6 오류 응답

- 공개 추천·경로 API의 upstream/전체 deadline 초과는 HTTP 504와
  `error.code="UPSTREAM_TIMEOUT"`을 반환한다.
- 응답에는 provider 이름, URL, 외부 응답 body, 키를 포함하지 않는다.
- 기타 내부 오류는 기존 HTTP 500 `INTERNAL_ERROR` 계약을 유지한다.
- Cron lease 중복은 정상적인 운영 상태이므로 HTTP 200과
  `{ status: "skipped", reason: "already_running" }`을 반환한다.
- Cron 작업 실패는 기존처럼 HTTP 500을 반환하고 `sync_state.last_status="failed"`를
  best-effort로 기록한다. Vercel이 재시도하지 않으므로 진행 커서는 성공 단위까지만 저장한다.

## 5. 파일 경계

### 새 파일

- `backend/migrations/005_sync_runtime_safety.sql`: lease 열과 claim/finish RPC
- `backend/lib/fetch-policy.js`: timeout·deadline·정규화 오류
- `backend/lib/sync-lease.js`: DB lease 실행 경계
- `backend/tests/fetch-policy.test.js`: timeout과 취소 구분
- `backend/tests/sync-lease.test.js`: claim·finish·중복·오류 계약
- `backend/tests/sync-runtime-migration.test.js`: SQL 열·원자성·권한 계약

### 수정 파일

- `backend/lib/database.js`: signal 전달과 RPC adapter
- `backend/lib/tour-api.js`: TourAPI timeout/signal
- `backend/lib/kakao-local.js`: Kakao Local timeout/signal
- `backend/lib/kakao-mobility.js`: Mobility timeout/signal
- `backend/lib/supabase-auth.js`: Supabase Auth timeout/signal
- `backend/lib/http.js`: `UPSTREAM_TIMEOUT` 504 변환
- `backend/lib/tour-sync.js`: deadline 뒤 신규 작업 시작 차단
- `backend/api/recommendations.js`: 25초 deadline과 후보 8개 상한
- `backend/api/route.js`: 25초 deadline 전달
- `backend/api/cron/tour-catalog-sync.js`: lease와 45/50초 경계
- `backend/api/cron/tour-intro-sync.js`: lease와 45/50초 경계
- `backend/vercel.json`: intro Cron을 `20 22 * * *`로 이동
- `backend/scripts/check-project.js`: 새 필수 파일 등록
- 기존 provider·추천·Cron 테스트: signal과 새 응답 계약 회귀
- Backend/배포/QA 문서: 실제 설정과 적용 순서 갱신

## 6. 데이터 흐름

### Cron

```text
Vercel GET + CRON_SECRET
  -> 인증
  -> claim_sync_job(jobId, token, now, 90)
     -> false: 200 skipped
     -> true: 45초 신규 작업 시작 경계 + 50초 abort signal 생성
        -> 성공 단위마다 기존 진행 커서 저장
        -> completed 또는 partial 결과 생성
        -> finish_sync_job(jobId, token, status, summary, finishedAt)
        -> JSON 응답
```

### 사용자 추천

```text
요청 검증·rate limit
  -> 25초 deadline 생성
  -> Kakao 기본 경로(8초)
  -> Supabase 후보 조회(5초)
  -> corridor/조건 1차 필터
  -> 최대 8개 Kakao 후보 경로(각 8초, 기존 제한 동시성)
  -> 안전 조건 계산
  -> 정상 JSON 또는 504 UPSTREAM_TIMEOUT
```

## 7. 테스트 전략

### Migration 계약

- lease·상태 열이 `if not exists`로 추가된다.
- claim은 lease 없음·만료에서만 token을 교체한다.
- finish는 동일 token에서만 lease를 비운다.
- status와 lease 초 값의 허용 범위를 SQL에서 제한한다.
- RPC는 `service_role`만 실행할 수 있다.

### 순수 단위 테스트

- timeout이 실제 fetch abort와 `UpstreamTimeoutError`를 만든다.
- 호출자 abort는 timeout으로 오분류하지 않는다.
- claim 실패에서는 작업과 finish가 호출되지 않는다.
- 성공·partial·실패가 정확한 summary와 token으로 finish된다.
- 오래된 token의 finish 실패를 성공 처리하지 않는다.

### Handler 회귀

- 인증 실패 Cron은 claim하지 않는다.
- 같은 job의 두 번째 호출은 200 skipped다.
- 45초 이후 새 페이지·장소를 시작하지 않고 partial을 반환한다.
- 추천 후보가 20개 이상이어도 Kakao 후보 호출은 8개 이하다.
- provider timeout은 공개 API에서 504이며 내부 상세를 숨긴다.
- 후보 일부 실패와 전체 실패의 기존 계약을 유지한다.
- `vercel.json`의 두 Cron 시간대가 4시간 떨어져 있다.

### 전체 검증

```powershell
cd backend
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

Android API 계약을 바꾸지 않는 작업이지만 마지막 체크포인트에서는 Android
`testDebugUnitTest lintDebug assembleDebug`도 회귀한다.

## 8. 배포 순서와 롤백

1. 별도 Supabase 테스트 프로젝트 또는 빈 복구 환경에 migration 001~005를 순서대로 적용한다.
2. claim 경쟁 호출 2개 중 하나만 `true`인지 SQL/RPC로 확인한다.
3. Production Supabase에 migration 005를 먼저 적용한다.
4. Vercel Preview에서 환경변수를 연결하고 두 Cron endpoint를 수동 Bearer 호출한다.
5. 같은 job을 동시에 두 번 호출해 하나가 skipped인지 확인한다.
6. timeout·partial·상태 기록을 확인한 뒤 Production으로 명시적으로 승격한다.
7. Vercel Cron 목록에서 UTC 스케줄 두 개와 다음 실행을 확인한다.

애플리케이션 롤백 시 migration 005의 nullable 열과 Function은 남겨도 기존 코드에 영향을
주지 않는다. 긴급 시 이전 Vercel 배포로 롤백하고 Cron을 일시 비활성화한다. 열 삭제나
down migration은 데이터 보존과 재배포 안전을 위해 수행하지 않는다.

## 9. 완료 조건

- Backend 전체 자동 테스트와 project check가 통과한다.
- 동시 claim에서 실행 소유자는 하나뿐이다.
- 모든 외부 서비스 호출이 공통 timeout 정책을 사용한다.
- 추천 한 건의 Kakao 후보 경로 호출이 최대 8개다.
- Cron은 45초 이후 신규 작업을 시작하지 않고 60초 전에 응답한다.
- 마지막 Cron 상태와 요약을 `sync_state`에서 조회할 수 있다.
- 기존 Android 요청·응답 필드는 제거하거나 의미를 바꾸지 않는다.
- 원격 Preview와 Production 검증 전에는 로컬 완료를 운영 완료로 표시하지 않는다.
