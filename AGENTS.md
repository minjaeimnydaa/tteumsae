# 틈새 Codex 작업 지침

이 파일은 저장소 전체에 적용됩니다. 새 Codex 작업은 대화 기록보다 Git의 코드와
아래 문서를 우선해 현재 상태를 복원합니다.

## 시작할 때 읽는 순서

1. `docs/00_START_HERE.md` — 실행 환경과 현재 구현
2. `docs/09_NEXT_VERSION_PLAN.md` — 완료 Gate와 바로 다음 작업
3. `docs/08_QA_AND_KNOWN_ISSUES.md` — 자동 검증 및 외부 차단 사항
4. 변경 대상에 해당하는 `docs/02_ARCHITECTURE.md`, `docs/05_API_AND_DATA.md`
5. 도착 마감 흐름은 `docs/superpowers/specs/2026-08-26-deadline-aware-route-flow-design.md`
   와 `docs/superpowers/plans/2026-08-28-gate-2-arrival-deadline-flow.md`

과거 `superpowers/plans` 문서는 이력을 보존하기 위한 자료입니다. 현재 코드나
`09_NEXT_VERSION_PLAN.md`와 충돌하면 실행 지시로 사용하지 않습니다.

## 현재 작업 기준

- 공식 통합 기준은 `main`입니다.
- Gate 0, Gate 1, Gate 1-B와 Gate 2 코드는 완료됐습니다.
- 활성 흐름은 `HOME → LOCATION → LOADING → RESULTS → DETAIL`입니다.
- Gate 2의 운영 V1 smoke, GPS 거부 fallback, 실제 카카오맵 전환과 알림 생성까지 확인했습니다.
- 조직 소유 업로드 키·서명 AAB·release OAuth와 남은 실기기 경계를 확인한 뒤 Gate 3을 설계합니다.
- 앱 복귀 자동 재조회, 지오펜스, 복수 경유지 UI, 전국 확장은 현재 범위가 아닙니다.

## 변경 원칙

- 답변과 변경 전에 관련 코드·문서·필요한 공식 자료를 교차 검증합니다.
- 실제 키, `.env.local`, `local.properties`, `.vercel/`, 키스토어와 APK를 커밋하지 않습니다.
- Supabase migration, 운영 Cron, Vercel Production 승격은 명시적 승인 없이 실행하지 않습니다.
- Android UI는 `TteumsaeApp.kt`를 전면 재작성하지 않고 변경하는 책임부터 작은 파일로 분리합니다.
- 제품 UI는 경유지 한 곳만 제공하지만 서버 `/api/route`의 0~5곳 호환은 유지합니다.
- 기능을 바꾸면 구현 문서, QA와 변경 기록을 같은 커밋에서 갱신합니다.
- 새 기능과 버그 수정은 실패 테스트를 먼저 확인합니다.

## 검증 명령

Backend는 Node.js 24.x와 `backend/package.json`의 pnpm 버전을 사용합니다.

```powershell
cd backend
corepack enable
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

Android는 JDK 17과 Android SDK 36을 사용합니다.

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

지도, OAuth, 외부 카카오맵과 알림은 자동 테스트만으로 완료 처리하지 않고 실제
Android 기기 결과를 `docs/08_QA_AND_KNOWN_ISSUES.md`에 기록합니다.
