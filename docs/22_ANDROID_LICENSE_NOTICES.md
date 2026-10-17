# Android 오프라인 이용 고지

기준: 2026-09-13 수동 출시 후보. 이 문서는 라이선스 원문 수록의 구현·증거를 설명하며,
원스토어 심사 통과나 모든 제3자 권리의 법률 검토 완료를 보장하지 않는다.

## 구현

- 설정 → 앱 정보 → `오픈소스 라이선스`에서 문서 목록·원문과 포함 모듈/버전을 읽는다.
- `ui/settings/LicenseNoticesDialog.kt`가 APK의 `assets/licenses/`만 읽는다.
- 네트워크 요청, HTML/JavaScript 실행, WebView, 새 라이브러리 추가 없음.
- `목록`/뒤로는 문서에서 목록으로, `닫기`/뒤로는 목록에서 설정으로 복귀한다.
- 원문 읽기는 IO 스레드, 긴 본문은 문단별 스크롤로 표시한다. 원문의 번역/요약이 아니다.

## 포함 근거

해당 release의 `release-artifact-dependencies.xml` 중 **package roots**를 중복 제거한
110개 런타임 JVM/Android 좌표가 `catalog.json`에 연결된다. SDK metadata의 BOM·공통
멀티플랫폼 메타데이터 전체를 APK에 포함된 별도 실행 바이너리로 세지 않는다.

`scripts/collect-android-license-evidence.ps1`는 이 모델과 Gradle 캐시의 정확한 모듈/버전
디렉터리를 읽어 POM의 라이선스명/URL, AAR·JAR 및 중첩 classes.jar의 LICENSE/NOTICE를
JSON으로 출력한다. 파일 쓰기·의존성 변경·새 Gradle 실행은 하지 않는다.

| 원문 | 증거 |
|---|---|
| AndroidX Apache 전문 | 캐시 내 해당 AAR/JAR 42개에 실린 동일 LICENSE.txt를 한 사본으로 수록; 모듈별 원래 내부 경로를 catalog에 유지 |
| SLF4J 2.0.17 MIT | 실제 JAR META-INF/LICENSE.txt의 저작권과 전문 |
| Supabase Kotlin 3.5.0 MIT | 공식 저장소 3.5.0 태그 LICENSE; Copyright 2025 Jan Tennert 포함 |
| Pretendard OFL 1.1 | 기존 android/third_party_licenses/Pretendard-OFL.txt와 본문 대조; 저작자·Reserved Font Name 포함 |
| Apache 2.0 전문 | 공식 JSpecify v1.0.0 LICENSE의 부록 포함 전문. 해당 POM에 Apache가 확인된 모듈에 연결 |
| Kotlin 2.3.20 NOTICE | 상위 배포 license/NOTICE.txt 원문이며 Kotlin Compiler라고 명시된 제목도 변조하지 않음 |
| KakaoMaps Android SDK 고지 | SDK 제공사의 공식 Android v2 License 페이지 전체 고지를 HTML 태그만 제거해 평문 수록; 저작권·BSD/MIT/Apache/OFL/MPL/Boost 등 원문 포함 |

Guava listenablefuture 1.0의 POM은 guava-parent 26.0-android를 상속하며, 그 부모 POM의
Apache 2.0 선언을 확인했다. JSpecify의 캐시 POM이 없어 공식 v1.0.0 LICENSE로 확인했다.
SLF4J는 캐시 POM 대신 실제 JAR 내부 MIT 원문을 사용했다.
카카오 SDK 자체를 Apache/MIT 오픈소스라고 잘못 분류하지 않고 서비스 이용 조건과
제공사 제3자 고지로 구분한다.

공식 원문:

- https://apis.map.kakao.com/android_v2/license/
- https://github.com/supabase-community/supabase-kt/blob/3.5.0/LICENSE
- https://github.com/jspecify/jspecify/blob/v1.0.0/LICENSE
- https://github.com/JetBrains/kotlin/blob/v2.3.20/license/NOTICE.txt
- https://github.com/orioncactus/pretendard/blob/main/LICENSE
- https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/26.0-android/guava-parent-26.0-android.pom

## 검증과 남은 범위

- 원문 TXT는 Git 줄바꿈 변환과 공백 정리에서 제외해 제공 원문의 형식을 보존한다.
  계약 테스트의 runtime 대조는 해당 release lint 모델이 생성된 Android 빌드 뒤 실행한다.
- `node --test scripts/licenses-contract.test.mjs`: 구현 전 2/2 실패 → 구현 후 2/2 통과.
- 이 테스트는 실제 runtime 좌표 전체와 catalog 일치, 원문 파일 존재/길이/안전한 이름,
  모듈별 문서 참조, Pretendard 본문 보존, 설정 연결·로컬 읽기·WebView 없음 등을 확인한다.
- code32 Android 전체190/190, lint0오류/48경고, 서명 release 빌드·실기기 설치 성공.
  당시 Maestro에서 라이선스 목록, Pretendard/Supabase/Kakao 원문과 목록·뒤로·닫기를 실제 확인했다.
  코드/의존성/고지를 바꾸지 않은 code33에서도 수동·자동 전체195/195, 수동 lint0오류/48경고,
  서명 수동 APK 업데이트 설치를 통과했다. code33은 접힌 검색 시트의 SDK 로고 위치 보완이다.
  네트워크 차단 상태와 큰 글자 설정은 미검증이다. 오프라인 구현은 assets 전용 읽기와
  위 Node 계약 테스트로 확인했으며 이를 네트워크 차단 실기기 테스트로 표현하지 않는다.
- 카카오 공통 고지는 버전별 내부 네이티브 구성의 독립 SBOM이 아니다. MPL 등 소스 제공
  조건의 개별 적용 범위를 이 작업만으로 완결했다고 주장하지 않는다. 제공사 연락처·원천
  프로젝트 URL과 고지 전문을 보존했다.
- Kotlin 내부 재패키징된 제3자 코드의 추가 NOTICE 전수 확인은 GitHub API 429로 완료하지
  못했다. 배포물 POM/현재 JAR에 없는 별도 상위 소스 고지까지 전부 확보한 것으로 보지 않는다.
- 의존성/폰트/지도 SDK를 갱신하면 evidence와 catalog를 다시 대조해야 한다. 현재 문서를
  모든 미래 버전에 자동으로 적용하지 않는다.

지도·관광 사진·서비스 API의 데이터 이용 권리는 별도
[제3자 권리 증빙](21_THIRD_PARTY_RIGHTS_EVIDENCE.md)에서 검토한다.
