# 환경설정 및 서비스 접근권한 인수인계

작성 기준: 2026-08-16 통합 소스

이 문서는 새 담당자가 비밀값을 Git에 넣지 않고 Android, Vercel,
Supabase와 Kakao 연동을 복구하도록 안내한다. 이 문서에는 환경변수 **이름만**
기록한다. 실제 키·비밀번호·복구 코드는 조직의 비밀관리 도구 또는 일회성 보안
채널로 전달한다.

## 1. 환경별 구성 요약

| 실행 위치 | 설정 파일/관리 화면 | 필요한 설정 |
|---|---|---|
| Android 로컬 빌드 | `android/local.properties` | SDK 경로, Kakao 네이티브 앱 키 |
| Android 앱 코드 | `android/app/build.gradle.kts` | 백엔드 기준 URL, 앱 버전 |
| 백엔드 로컬 | `backend/.env.local` | Vercel 환경변수의 로컬 사본 |
| 백엔드 운영 | Vercel Project Settings | TourAPI, Kakao REST, Supabase, Cron 설정 |
| 데이터베이스 | Supabase SQL Editor/CLI | 마이그레이션 2개 |
| Kakao Android | Kakao Developers 플랫폼 설정 | 패키지명과 빌드 서명 키 해시 |

## 2. 절대 커밋하지 않는 항목

루트 [`.gitignore`](../.gitignore)가 다음을 제외한다.

```text
**/local.properties
**/.env*
!**/.env.example
**/.vercel/
**/*.jks
**/*.keystore
**/*.apk
**/*.aab
**/recovery-codes.txt
```

커밋 전에는 반드시 다음을 실행한다.

```powershell
git status --short
git diff --cached
```

실제 키처럼 보이는 문자열이 없는지도 백엔드 검사로 확인한다.

```powershell
cd backend
pnpm run check
```

이 검사는 64자리 16진수 인증키 형태를 탐지하지만 모든 종류의 비밀값을 찾는
완전한 보안 스캐너는 아니다. `.env`, 키스토어, 복구 코드와 콘솔 스크린샷은
사람이 추가로 확인해야 한다.

과거 대화·스크린샷에 실제 TourAPI 키가 공유된 이력이 있으므로, 인수인계 전에
기존 키의 노출 범위를 점검하고 가능하면 재발급·교체한다. 기존 값을 이 문서나
이슈에 다시 적지 않는다.

## 3. Android 로컬 설정

### 3.1 `local.properties`

[`android/local.properties.example`](../android/local.properties.example)을 복사한다.

```powershell
cd android
Copy-Item local.properties.example local.properties
```

새 파일에 다음 **두 이름**을 설정한다.

```properties
sdk.dir=새 컴퓨터의 Android SDK 절대 경로
KAKAO_MAP_NATIVE_APP_KEY=Kakao Developers 네이티브 앱 키
```

주의:

- `KAKAO_MAP_NATIVE_APP_KEY`는 Kakao REST API 키가 아니다.
- 네이티브 앱 키는 `BuildConfig.KAKAO_MAP_NATIVE_APP_KEY`로 APK에 포함되는
  클라이언트 식별자다. 서버 비밀키처럼 취급하지는 않지만 Git에는 넣지 않는다.
- 값이 비어 있으면 앱이 빌드되더라도 지도 영역에 앱 키 설정 필요 안내가 나온다.
- 서버용 `KAKAO_REST_API_KEY`를 이 파일에 넣으면 안 된다.

현재 Android 식별값:

| 항목 | 값/위치 |
|---|---|
| 패키지·applicationId | `com.tteumsae.app` |
| minSdk | 26 |
| compileSdk / targetSdk | 35 / 35 |
| API 기준 URL | `android/app/build.gradle.kts`의 `BuildConfig.API_BASE_URL` |
| 현재 API 기준 URL | `https://tteumsae-backend.vercel.app` |

Preview 백엔드를 쓰려면 Git에 임시 URL을 커밋하지 말고 로컬 브랜치 또는 별도
build type의 `API_BASE_URL`로 분리한다.

### 3.2 Kakao Android 플랫폼 등록

Kakao Developers에서 틈새 앱의 Android 플랫폼에 다음을 등록해야 실제 지도가
표시된다.

```text
패키지명: com.tteumsae.app
키 해시: 해당 APK를 서명한 인증서의 Kakao 키 해시
```

새 컴퓨터의 기본 디버그 키스토어는 기존 컴퓨터와 다를 수 있다. 두 방법 중
하나를 선택한다.

1. 기존 `%USERPROFILE%\.android\debug.keystore`를 비밀관리 도구로 안전하게
   전달해 팀 테스트 APK의 서명을 동일하게 유지한다.
2. 새 디버그 키스토어를 사용하고 그 키 해시를 Kakao Developers에 추가한다.

기본 디버그 키스토어 정보는 Android 도구의 관례상 alias `androiddebugkey`,
store/key password `android`다. Git Bash와 OpenSSL이 있다면 다음으로 Kakao용
키 해시를 얻을 수 있다.

```bash
keytool -exportcert \
  -alias androiddebugkey \
  -keystore "$HOME/.android/debug.keystore" \
  -storepass android \
  -keypass android \
  | openssl sha1 -binary \
  | openssl base64
```

확인 절차:

1. Kakao Developers 앱의 Android 플랫폼에 패키지명과 키 해시를 저장한다.
2. `local.properties`에 같은 Kakao 앱의 네이티브 앱 키를 넣는다.
3. `clean assembleDebug`로 다시 빌드한다.
4. 실기기에 설치해 홈 지도가 실제 타일과 POI를 표시하는지 확인한다.

Play Store용 릴리스는 디버그 인증서와 서명이 다르다. 출시 전 다음 키 해시를
각각 등록한다.

- 로컬 업로드 키 또는 직접 서명 릴리스 키
- Google Play App Signing이 실제 배포 APK를 서명하는 앱 서명 키

키스토어 파일, alias, 비밀번호는 Git이 아닌 비밀관리 도구에 보관한다. 현재
저장소에는 릴리스 `signingConfig`와 Play App Signing 절차가 아직 없다.

## 4. 백엔드 환경변수

기준 템플릿은 [`backend/.env.example`](../backend/.env.example)이다.

### 4.1 필수 변수

| 이름 | 비밀 여부 | 사용처 | 누락 시 영향 |
|---|---:|---|---|
| `TOUR_API_SERVICE_KEY` | 예 | TourAPI 기본·상세 동기화 | 두 Cron 실패 |
| `KAKAO_REST_API_KEY` | 예 | Kakao Local 검색·지역, Kakao Mobility | 위치 검색·지역·차량 추천 실패 |
| `SUPABASE_URL` | 아니오에 가까움 | PostgREST 기준 주소 | 장소·추천·동기화 실패 |
| `SUPABASE_SERVICE_ROLE_KEY` | 매우 중요 | 서버 전용 DB 전체 접근 | 장소·추천·동기화 실패 |
| `CRON_SECRET` | 예 | 두 Cron Bearer 검증 | 예약/수동 동기화 인증 불가 |

`SUPABASE_SERVICE_ROLE_KEY`는 RLS를 우회한다. Android, 프런트엔드, 공개 로그,
문서, 이슈에 절대 노출하지 않는다.

### 4.2 선택 조정 변수

| 이름 | 기본값 | 코드상 상한 | 의미 |
|---|---:|---:|---|
| `TOUR_SYNC_MAX_PAGES` | 10 | 25 | 기본 동기화 한 실행의 최대 페이지 |
| `TOUR_DETAIL_SYNC_BATCH_SIZE` | 10 | 10 | 상세 동기화 한 실행의 장소 수 |
| `KAKAO_ROUTE_CANDIDATE_LIMIT` | 20 | 20 | 차량 경로를 실제 계산할 후보 수 |

빈 값, 숫자가 아니거나 0 이하이면 기본값을 사용한다. 한도를 늘리려면 환경변수만
바꾸면 안 되고 코드의 `Math.min` 상한, Vercel 실행시간, 외부 API 쿼터와 비용을
함께 검토해야 한다.

### 4.3 Vercel 환경 범위

운영 프로젝트의 Production에 최소 필수 다섯 변수를 모두 설정한다. Preview와
Development를 사용할 때는 운영 DB·Cron을 무심코 공유하지 않도록 별도 값을
사용하거나 필요한 변수만 제한적으로 설정한다.

```powershell
cd backend
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
```

연결 대상은 Vercel 팀 `jaturi`의 `tteumsae-backend` 프로젝트다. `.vercel/`과
`.env.local`은 Git에 포함하지 않는다. 환경변수를 바꾼 뒤에는 새 Production
배포가 필요하다.

값을 출력하지 않고 이름만 확인하는 방법:

```powershell
pnpm dlx vercel env ls
```

운영 `/api/health`는 세 묶음의 설정 존재 여부를 확인한다. 실제 권한·쿼터·DB
연결까지 확인하는 요청은 아니므로 스모크 테스트를 별도로 수행한다.

## 5. Supabase 설정과 권한

새 Supabase 프로젝트로 복구할 때 SQL Editor 또는 관리되는 마이그레이션 도구로
아래 순서를 지킨다.

1. [`backend/migrations/001_initial.sql`](../backend/migrations/001_initial.sql)
2. [`backend/migrations/002_detail_sync_state.sql`](../backend/migrations/002_detail_sync_state.sql)

그다음 새 프로젝트의 URL과 service role 키를 Vercel 환경변수에 설정하고
기본·상세 Cron을 순서대로 실행한다.

확인할 테이블:

```text
public.places
public.sync_state
```

두 테이블은 RLS가 활성화되어 있어야 하며, Android나 익명 사용자를 위한 공개
정책을 만들지 않는다. 앱은 Vercel API를 통해서만 데이터를 읽는다.

## 6. 서비스 접근권한 전달표

비밀번호 공유 대신 각 서비스의 팀/협업자 초대 기능을 사용한다. 새 담당자가
아래 검증을 완료한 뒤 이전 담당자의 권한을 줄이거나 제거한다.

| 서비스 | 현재 대상 | 새 담당자 최소 권한 | 별도 전달 항목 | 인수 확인 |
|---|---|---|---|---|
| GitHub | `minjaeimnydaa/tteumsae` | 저장소 Write 이상 | 보호 브랜치·PR 규칙 | clone, branch push 가능 |
| Vercel 백엔드 | 팀 `jaturi` / `tteumsae-backend` | 배포·환경변수 확인 가능 역할 | 팀 초대 | 프로젝트 연결, 배포·로그 조회 |
| Vercel APK 페이지 | 팀 `jaturi`의 APK 다운로드 프로젝트 | 배포 가능 역할 | 정확한 프로젝트 선택 | Preview 후 Production 배포 |
| Supabase | 틈새 운영 DB 프로젝트 | Developer 이상, 비밀은 필요 시에만 | 프로젝트 초대 | 테이블·로그·SQL 접근 확인 |
| Kakao Developers | 틈새 Kakao 앱 | 앱 편집 가능한 팀원 | 앱 팀 초대 | 네이티브/REST 키 종류, 플랫폼 확인 |
| Kakao Mobility | 같은 Kakao 앱의 길찾기 제품 | 제품 설정·쿼터 확인 | 제품 활성화 상태 | 서버 REST 키로 테스트 경로 성공 |
| 공공데이터포털 | 한국관광공사 국문 관광정보 서비스 | 활용신청 관리 가능 계정 | 계정 소유권 또는 새 키 | 승인·만료일·쿼터 확인 |
| Google Play Console | `com.tteumsae.app` 앱 | 릴리스에 필요한 최소 역할 | 조직 초대 | 테스트 트랙·앱 서명 접근 확인 |
| 문의 메일 | 현재 개인 Gmail | 메일 수신·응답 담당 | 향후 회사 주소 | 실제 메일 앱 연결 확인 |

현재 문의 주소는
[`TteumsaeApp.kt`](../android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt)의
`CONTACT_EMAIL` 상수에 직접 들어 있다. 회사 메일을 준비하면 코드와 스토어
연락처를 함께 바꾼다.

## 7. 서비스별 인수 체크리스트

### 7.1 GitHub

- [ ] 기본 브랜치와 원격 주소 확인
- [ ] 최신 통합 소스가 push되어 있는지 확인
- [ ] Actions/Dependabot/브랜치 보호 규칙 확인
- [ ] 이슈·PR·릴리스 담당자 지정
- [ ] Git 기록에 비밀값이 없는지 확인

### 7.2 Vercel

- [ ] 백엔드와 APK 다운로드 프로젝트를 혼동하지 않음
- [ ] Production 도메인 확인
- [ ] 환경변수 이름과 대상 환경 확인
- [ ] Cron 두 개와 최근 실행 로그 확인
- [ ] Functions의 최대 실행시간 60초 확인
- [ ] 환경변수 변경 뒤 재배포

### 7.3 Kakao

- [ ] 네이티브 앱 키와 REST API 키를 구분
- [ ] Android 패키지명 확인
- [ ] 디버그·릴리스·Play 앱 서명 키 해시 등록
- [ ] Kakao Map Android SDK 지도 표시 확인
- [ ] Kakao Local 키워드·지역 API 확인
- [ ] Kakao Mobility Directions 제품·쿼터 확인

### 7.4 TourAPI

- [ ] `KorService2` 활용신청 승인 상태 확인
- [ ] 키 만료일·일일 트래픽 확인
- [ ] 기본 `areaBasedList2` 호출 확인
- [ ] `detailIntro2`, `detailImage2`, `detailPetTour2` 호출 확인
- [ ] 강원도 `areaCode=32` 데이터 수 확인

### 7.5 Google Play

- [ ] 앱 생성 및 패키지명 소유 상태 확인
- [ ] Play App Signing과 업로드 키 보관 책임자 지정
- [ ] 내부/비공개 테스트 트랙 접근 확인
- [ ] 개인정보처리방침, 위치 고지, 데이터 보안 양식 담당자 지정

## 8. 키 교체 순서

키 노출이 의심되면 이전 키를 먼저 문서에서 찾으려 하지 말고 서비스에서 새 키를
만든다.

1. 새 키 발급 또는 재생성
2. Vercel/로컬 보안 저장소에 새 값 등록
3. Production 재배포
4. API 스모크 테스트
5. 이전 키 폐기
6. Vercel·외부 API 로그에서 비정상 사용 점검

Kakao 네이티브 앱 키 자체를 교체하면 Android를 다시 빌드·배포해야 한다.
TourAPI, Kakao REST, Supabase service role, Cron 키는 서버 환경변수 교체와
백엔드 재배포로 처리한다.

## 9. 현재 미완료 설정

- 개인정보처리방침 URL과 위치기반서비스 이용약관 URL이 앱에서 빈 문자열이며
  설정 탭에 `준비 중`으로 보인다.
- 릴리스 키스토어와 Gradle `signingConfig`가 없다.
- Play App Signing용 키 해시 등록 완료 여부를 저장소만으로 확인할 수 없다.
- APK 다운로드 프로젝트의 `.vercel` 연결 정보는 보안·로컬 파일이라 Git에 없다.
- 백엔드 공개 API 인증·Rate Limit이 없다.
- 운영 서비스의 실제 소유자·복구 담당·2단계 인증 상태는 Git으로 확인할 수
  없으므로 인수 회의에서 별도 확인해야 한다.
