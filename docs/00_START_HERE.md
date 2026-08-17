# 새 담당자 시작 안내

기준일: `2026-08-16`
Android 버전: `0.12.3` (`versionCode 24`)
Android 패키지: `com.tteumsae.app`

이 저장소가 Android 앱, 백엔드, 다운로드 페이지와 인수인계 문서의 유일한 기준 원본입니다. 다른 폴더에 남은 APK나 과거 소스 사본을 기준으로 작업하지 마세요.

## 1. 현재 수준을 먼저 이해하기

틈새는 다음이 실제로 연결된 기능형 MVP입니다.

- Kotlin·Jetpack Compose Android 앱
- Kakao Map Android SDK 지도
- GPS 권한과 현재 위치
- Vercel Functions 백엔드
- Supabase 장소 DB
- 한국관광공사 TourAPI 동기화
- Kakao Local 검색·역지오코딩
- Kakao Mobility 차량 경로
- 이동/근처 추천과 카카오맵 딥링크

다만 Play Store 제출 준비는 끝나지 않았습니다. 릴리스 서명, AAB, 개인정보처리방침, 위치 약관, 스토어 리소스와 실기기 회귀 테스트가 남아 있습니다.

또한 현재 소스에는 마지막 후보 핀 아이콘 변경이 포함돼 있지만 배포된 `v0.12.3` APK에는 포함되지 않았습니다. 인수 후 최신 APK를 다시 빌드하세요.

## 2. 필요한 프로그램

- Git
- Android Studio Ladybug 이상 권장
- JDK 17 또는 Android Studio 내장 JBR
- Android SDK Platform 35와 SDK Build Tools
- Node.js 20 이상
- Corepack 또는 pnpm

복제 경로는 한글과 공백이 없는 `C:\dev\tteumsae`를 권장합니다. 기존 한글 경로에서는 Gradle JUnit 워커가 테스트 클래스를 읽지 못하는 `ClassNotFoundException`이 발생했습니다.

주요 버전:

| 항목 | 버전 |
|---|---|
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Java/JVM | 17 |
| Compose BOM | 2024.12.01 |
| Kakao Maps Android SDK | 2.14.0 |
| Android SDK | min 26 / compile 35 / target 35 |

## 3. 저장소 복제 후 보안 원칙

Git에 실제 키를 추가하지 마세요. 다음 파일은 각 개발자가 직접 만들거나 서비스에서 내려받습니다.

- `android/local.properties`
- `backend/.env.local`
- Android 디버그·릴리스 키스토어
- Vercel의 `.vercel/` 연결 폴더

키 값은 메신저나 문서가 아닌 승인된 비밀 관리 채널로 전달합니다. 자세한 소유권과 환경변수는 [환경과 접근 권한](06_ENVIRONMENT_AND_ACCESS.md)을 따릅니다.

## 4. Android 준비와 첫 빌드

1. Android Studio에서 `android/` 폴더를 엽니다.
2. `android/local.properties.example`을 `android/local.properties`로 복사합니다.
3. 로컬 Android SDK 경로와 카카오 네이티브 앱 키를 입력합니다.

```properties
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
KAKAO_MAP_NATIVE_APP_KEY=별도_전달받은_네이티브_앱_키
```

4. Gradle Sync 후 다음 명령을 실행합니다.

```powershell
cd C:\dev\tteumsae\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin
.\gradlew.bat assembleDebug
```

APK 위치:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

`compileDebugUnitTestKotlin`은 테스트 소스 컴파일만 확인합니다. 실제 `testDebugUnitTest`는 현재 환경에서 클래스 로딩 오류가 있으므로 [QA 문서](08_QA_AND_KNOWN_ISSUES.md)를 확인하세요.

## 5. 카카오 Android 플랫폼 확인

카카오 네이티브 지도는 다음 두 값이 일치해야 합니다.

- 패키지명: `com.tteumsae.app`
- 설치 APK를 서명한 키의 키 해시

새 컴퓨터가 새 디버그 키스토어를 만들면 키 해시도 달라집니다. 다음 중 하나를 선택합니다.

1. 기존 디버그 키스토어를 보안 채널로 전달받습니다.
2. 새 디버그 키 해시를 Kakao Developers 애플리케이션에 추가합니다.

키스토어는 Git에 올리지 않습니다.

## 6. 백엔드 준비와 검증

```powershell
cd C:\dev\tteumsae\backend
corepack enable
pnpm install --frozen-lockfile
pnpm test
pnpm run check
```

로컬에서 실제 외부 서비스를 호출해야 할 때만 `.env.example`을 참고해 `.env.local`을 준비합니다. 키 값은 Vercel 운영 환경에서 직접 확인하거나 소유자에게 접근 권한을 요청합니다.

Vercel 연결이 필요한 경우:

```powershell
pnpm dlx vercel login
pnpm dlx vercel link
pnpm dlx vercel env pull .env.local
```

팀 `jaturi`, 프로젝트 `tteumsae-backend`를 선택합니다.

## 7. 첫 실행 스모크 테스트

실제 Android 기기에 디버그 APK를 설치하고 다음 순서로 확인합니다.

1. 앱 이름 `틈새`와 홈 지도 표시
2. GPS 권한 허용 후 현재 위치 표시
3. GPS 버튼을 다시 눌러 비활성화
4. 출발지와 강원도 목적지 검색
5. 시간·안전 여유·추천 의도 입력
6. 추천 결과와 지도 후보 핀 표시
7. 경유지 최대 5개 선택·해제
8. 카카오맵에 출발지·경유지·최종 목적지 전달
9. 근처 모드에서 강원도 밖 위치 차단
10. 장소 둘러보기 이미지·추가 로딩·저장 동작
11. 설정의 위치 권한, 카카오맵 실행, 캐시 삭제, 문의 메일

상세한 케이스는 [QA와 알려진 문제](08_QA_AND_KNOWN_ISSUES.md)를 사용합니다.

## 8. 작업 시작 전 읽을 순서

1. [제품 범위](01_PRODUCT_AND_SCOPE.md)
2. [아키텍처](02_ARCHITECTURE.md)
3. [기능 구현표](03_FEATURE_MATRIX.md)
4. [화면 흐름](04_SCREEN_FLOWS.md)
5. [API와 데이터](05_API_AND_DATA.md)
6. [QA와 알려진 문제](08_QA_AND_KNOWN_ISSUES.md)
7. [다음 버전 계획](09_NEXT_VERSION_PLAN.md)
8. [결정 기록](10_DECISION_LOG.md)

기능을 수정하면 구현표·QA·변경 기록도 같은 커밋에서 갱신합니다.
