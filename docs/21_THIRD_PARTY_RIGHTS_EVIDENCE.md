# 틈새 — 제3자 지식재산 이용 근거 및 확인 자료 초안

작성·공식 자료 확인일: 2026-09-13 · 대상: Android `com.tteumsae.app` 0.13.1 / versionCode 34

code 34 갱신 상태: 공개 APK는 최종 수동 30E0 후보이며, 아래 주소 검색·결과 지도 보완과
실기기 검증 범위를 반영한다. 기존 code 32/33의 사진·라이선스 검증 이력은 실제 수행 버전을
유지한다. 이전 PDF를 보존하고 새 파일 `tteumsae-third-party-rights-0.13.1-code34.pdf`를 작성한다.

- 대상 APK: `output/release-0.13.1/tteumsae-v0.13.1-34-manual-release.apk`, 44,896,004바이트.
- SHA-256: `30E05594A326CC79386EBE838FBB0F15283C7ABFF1C76E7EB60A320D06A88F96`.
- 수동 Android 227/227·51 suites, lint 오류 0·경고 48 및 서명 release 빌드 성공.
- 최종 주소/시트 Maestro는 1/1 통과·73초·8장. 묶음 5→4→3과 중간 시트의 구성원 핀·
  카카오 로고 노출을 실제 캡처에서 확인했다. 전체 기기·모든 화면 검증으로 확대하지 않는다.
- 06:39 공개 APK 교체·익명 다운로드 해시 일치 확인은 원스토어 제출·승인과 별개다.
- 새 권리 근거 PDF: `output/pdf/tteumsae-third-party-rights-0.13.1-code34.pdf`, 5쪽,
  154,418바이트. 모든 페이지를 PNG로 렌더해 표·문단·출처 링크·쪽번호의 잘림 없이 검수했다.
  PDF SHA-256: `F287577B3624720B17E80BC87E95548092E9E17117B24447348BD72FEA909915`.
  원스토어 첨부 교체나 최종 신청은 이 문서 생성에 포함하지 않았다.

## 1. 자료의 목적과 범위

원스토어의 제3자 지식재산 사용 항목에 첨부할 설명자료 초안이다. 틈새는 지도 SDK/API,
공개 관광정보·사진, 오픈소스 라이브러리·폰트를 사용한다. 해당 자료의 소유권을
앱 운영자에게 이전받았다는 뜻이 아니라, 아래 공개 이용조건을 근거로 사용하는 구조다.

이 문서는 개별 권리자의 서명된 이용허락서, 계정별 약관 동의·API 승인 증명서 또는
원스토어의 사전 승인서가 아니다. 모든 사진의 개별 권리와 재배포 조건 이행을 보증하지
않으며, **이 설명자료 한 파일만으로 원스토어 증빙 요건을 충족하는지는 미확인**이다.
검토 중인 보완점은 5절에 별도로 명시한다. 앱 키·인증정보·개인 전화번호는 포함하지 않는다.

## 2. 지도·장소 검색·차량 경로

| 구분 | 실제 사용 | 공식 이용 근거와 제한 |
|---|---|---|
| Kakao Maps SDK for Android v2 | `com.kakao.maps.open:android:2.14.0`. 홈·결과 지도, 핀·경로 표시 | [공식 SDK 소개](https://apis.map.kakao.com/android_v2/)와 [시작 안내](https://apis.map.kakao.com/android_v2/docs/getting-started/)가 앱 키 인증 후 SDK 사용을 안내한다. SDK 사용은 공개 이용조건에 따른 이용권이며, 지도·상표 소유권을 취득하는 것은 아니다. |
| Kakao Local API | 장소 키워드 및 도로명·지번 주소 검색. 사용자가 선택한 주소 좌표를 경로 계산에 사용. 역지오코딩 구현도 존재하나 수동 출시 UI에서 자동 GPS 조회는 사용하지 않음 | [공식 Local 개발 가이드](https://developers.kakao.com/docs/latest/ko/local/dev-guide#address-coord), [플랫폼 서비스 약관](https://developers.kakao.com/terms/ko/site-terms) 및 [운영정책](https://developers.kakao.com/terms/ko/site-policies). 등록 앱·인증·서비스별 사용조건을 따라야 하며 키 공유, 상표 무단 사용, 소유권 고지 삭제를 허용하는 포괄 권한이 아니다. |
| Kakao Mobility 길찾기 API | 서버가 자동차 기준 경로·경유 시간 계산. 앱은 계산 결과를 표시하고 카카오맵으로 길 안내 연결 | [공식 제품 안내](https://developers.kakaomobility.com/)와 [디벨로퍼스 운영정책](https://policy.kakaomobility.com/viewer/?pageCode=DEVELOPERS_TERMS)은 공개 API/SDK를 자체 앱에 연동할 수 있는 조건과 무료 한도·초과 이용 절차를 안내한다. 카카오모빌리티의 공식·제휴 서비스로 오인시키면 안 된다. 별도 제휴계약 체결을 주장하지 않는다. |

실제 코드 위치:

- SDK 선언·공식 저장소: `android/app/build.gradle.kts`, `android/settings.gradle.kts`.
- SDK 초기화: `android/app/src/main/java/com/tteumsae/app/TteumsaeApplication.kt`.
- 지도 표시: `android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt`의 `MapView`, `KakaoMap`.
- 서버 호출: `backend/lib/kakao-local.js`, `backend/lib/kakao-mobility.js`.
- 외부 지도 연결: `android/app/src/main/java/com/tteumsae/app/platform/ExternalNavigation.kt`.

code 34 추가 사용과 표시:

- `backend/lib/kakao-local.js`의 주소형 검색은 Kakao Local
  `v2/local/search/address.json`을 사용한다. 도로명·지번 주소를 모두 지원한다는 공식 설명과
  코드의 주소 후보 정규화를 대조했다. 주소 결과는 장소와 구분하고 선택한 전체 주소를 표시한다.
- 결과 지도는 지도와 목록을 계속 유지하는 3단계 시트와 SDK viewport padding을 사용한다.
  로고 bottom margin을 중복 적용하지 않으며, 명시적 묶음 탐색 시 새 시트 여백을 적용한 뒤
  구성원 좌표를 맞춘다. 일반 지도 제스처는 대기 중 묶음 초점 요청을 취소한다.
- 최종 30E0의 `tmp/ux-v34-20260913/final30-balanced.png`, `final30-cluster-settled.png`,
  `final30-cluster-four.png`, `final30-cluster-three.png`를 실제 검수했다. 구성원 핀 몸체·꼬리가
  시트와 상단 전체 경로 버튼에 가리지 않고, 카카오 로고 전체가 지도 위에 보였다.
- 전체 목록·펼친 검색 입력은 지도를 덮는 화면으로 구분한다. 이 관찰은 SDK 권리 양도나
  모든 화면·기기의 상시 표시, 법적 면제 또는 스토어 승인 보장을 뜻하지 않는다.

다음은 code 34에서도 유지되는 이전 검색 시트 보완과 실제 검증 이력이다.

[카카오 지도 SDK 로고 표시 정책](https://apis.map.kakao.com/android_v2/docs/getting-started/precautions/)과
[Logo 공식 참조](https://apis.map.kakao.com/android_v2/reference/com/kakao/vectormap/Logo.html)는
일시적인 오버레이 가림을 제외하고 지도 이용 중 로고가 인지되도록 노출할 것을 안내한다.
code 33은 접힌 110dp 검색 시트에서 지도를 조작할 때 로고가 계속 가려지는 문제에 한정해
SDK `Logo.setPosition`으로 위치를 보정한다. 카메라·지도 padding·경로 계산은 변경하지 않는다.
펼친 입력 화면은 일시 오버레이로 구분하며 모든 화면에서 항상 로고가 노출된다고 주장하지 않는다.
QA 담당자는 04:37 code 33의 접힌 110dp 검색 시트에서 핸들이 보이고 카카오 로고가 시트
위로 온전히 보임을 실제 캡처로 확인했다. 다시 끌어올려 입력 화면이 펼쳐지고, 뒤로 이동해
홈으로 복귀하면 정상 로고 위치가 복구됨도 확인했다. 증거는 Git 제외
`tmp/release-qa-final-20260913/release-33-collapsed-search.png` 및
`tmp/release-qa-final-20260913/release-33-home-restored.png`다.
기존 2026-09-13 결과 지도 캡처 `tourist-07-map.png`에서는 오른쪽 하단
카카오 로고가 지도 안에 보이고 하단 카드에 가려지지 않음을 확인했다. 해당 표본은
`tmp/release-qa-final-20260913/2026-09-13_033901/Manual release - tourist example at 06 hour/takeScreenshot/`
아래에 보관한다. 이 표본을 code 33의 모든 화면 검증으로 확대하지 않는다.

계정별 앱 등록·승인 화면은 이 코드 조사에 첨부하지 않았다. API가 동작한다는 사실만으로
특정 계정의 소유권·모든 약관 동의·별도 계약을 증명하지 않는다. 필요하면 등록 앱명과
패키지명이 보이는 화면을 **키를 가린 상태로** 별도 부록에 추가한다.

## 3. 한국관광공사 TourAPI 관광정보·사진

[한국관광공사 국문 관광정보 서비스 공식 개방 페이지](https://www.data.go.kr/data/15101578/openapi.do)는
관광정보·사진을 OpenAPI로 제공하고 모바일 앱 등에서 활용할 수 있다고 명시한다.
사진은 공공누리 1유형·3유형이 제공되며, 피사체의 명예·인격권을 침해하는 이용과 기업
CI·BI 이용을 금지한다고 별도로 설명한다. 따라서 페이지의 일반적인 이용허락범위 표시를
모든 사진의 무조건적인 가공·브랜딩 허용으로 해석하지 않는다.

| 사진 유형 | 확인한 공식 조건 | 틈새에서 지켜야 할 범위 |
|---|---|---|
| 공공누리 제1유형 | [출처표시 유형 안내](https://www.kogl.or.kr/info/licenseType1.do) | 출처·저작권자 및 이용조건 표시. 온라인에서 가능한 출처 링크 제공. 기관의 후원·제휴로 오인시키지 않음. |
| 공공누리 제3유형 | [출처표시·변경금지 유형 안내](https://www.kogl.or.kr/info/licenseType3.do) | 출처표시 외에 변경·2차적 저작물 작성 제한을 준수. 사진별 유형을 확인하지 않고 1유형으로 간주하지 않음. |

실제 처리·고지:

- `backend/lib/tour-api.js`: `detailImage2`의 원본/썸네일 URL, 이미지명,
  `cpyrhtDivCd`를 정규화하여 `image_attributions`로 보존한다.
- `backend/lib/database.js`: 공개 장소 응답에 해당 메타데이터를 포함한다.
- `android/app/src/main/java/com/tteumsae/app/ui/route/DetailPresentation.kt`:
  상세 정보 출처는 한국관광공사 TourAPI로 표시한다. 실제 표시 이미지 URL이 메타데이터와
  일치하고 유형이 명확할 때만 공공누리 유형을 표시한다. 메타데이터가 없거나 상충하면
  특정 유형 대신 `개별 이용조건 확인`을 표시한다. URL이 다른 사진의 권리를 재사용하지 않는다.
- 결과 상세 및 저장 장소 상세에 출처 문구와 공식 개방 페이지의 이용조건 링크를 연결했다.
  설정에도 데이터 출처를 표시한다. QA 담당자는 code 32 최종 APK 실기기에서 사진의
  전체 구도와 출처 노출을 확인했다(2026-09-13). 같은 code 32에서 공식 이용조건의
  `data.go.kr/data/15101578/openapi.do` 외부 페이지를 열고 같은 상세 화면으로 복귀하는
  실제 왕복도 확인했다. 모든 사진의 개별 권리 검증을 뜻하지 않는다.
- 사진은 서버가 전달한 URL에서 로딩한다. 이는 출처 경로에 대한 설명이며,
  원격 로딩만으로 저작권 이용조건이 면제되는 것은 아니다.

운영자는 2026-09-13 TourAPI를 본인 계정으로 활용신청했다고 답변했다. 이는 사용자 확인
사실이며 승인증·계정 화면 원본은 이 자료에 첨부하지 않았다. 운영 승인 자료와 전체
카탈로그의 사진별 권리 증빙을 확인 완료로 표시하지 않는다. 공급자가 제공한 개별 조건이
있는 사진은 그 조건이 우선한다.
관광사진을 틈새 앱 로고로 사용했다는 근거는 없으며, 앱 로고는 아래 4절의 사용자 제공 파일이다.

### 3.1 상세 화면의 보조 공공데이터

| 자료 | 코드의 실제 사용 | 공식 개방 근거와 확인 범위 |
|---|---|---|
| 기상청 단기예보 | `backend/lib/kma-weather.js`, 장소 도착 무렵 날씨 참고 정보 | [공식 데이터셋](https://www.data.go.kr/data/15084084/openapi.do)의 이용허락범위는 공공저작물 출처표시 제1유형. 앱은 기상청 단기예보 출처를 표시하며, 현장 날씨·안전을 보장하지 않음. |
| 한국관광공사 관광지 집중률 방문자 추이 예측 | `backend/lib/tour-congestion.js`, 일치한 관광지의 상대 혼잡 참고 정보 | [공식 데이터셋](https://www.data.go.kr/data/15128555/openapi.do)은 이용허락범위 제한 없음으로 표시. KT 이동통신 데이터 기반 예측치라는 설명을 유지하며, 틈새가 이동통신 원시자료를 소유·수집한다는 뜻이 아님. |
| 한국관광공사 무장애 여행 정보 | `backend/lib/tour-accessibility.js`, 확보된 장소의 접근성 안내 | [공식 데이터셋](https://www.data.go.kr/data/15101897/openapi.do)은 모바일 앱 활용과 선별된 정보 개방을 안내. 사진이 포함되면 별도 1·3유형 조건을 따름. 틈새의 해당 기능은 정규화된 접근성 텍스트 중심. |
| 전국주차장정보표준데이터 | `backend/lib/public-parking.js`, 강릉 공영주차장의 주변 정보 | [공식 표준데이터](https://www.data.go.kr/data/15012896/standard.do)와 실제 `tn_pubr_prkplce_info_api` 호출을 대조. 이번 공개 페이지 열람에서는 통합 데이터의 별도 이용허락범위 문구를 확인하지 못했으므로, 타 지자체 개별 파일의 조건을 강릉 자료에 그대로 적용하지 않음. 앱은 제공기관·기준일을 표시함. |

위 공개 데이터 설명과 계정별 활용승인·API 사용량 조건은 구분한다. 이번 문서 작성은
추가 활용신청·약관 동의 또는 데이터 수집을 실행하지 않았다.

## 4. 주요 오픈소스·폰트와 앱 로고

버전은 `android/app/build.gradle.kts`의 직접 선언 기준이다. 전이 의존성 전체 목록이나
최종 APK의 모든 구성요소에 대한 완전한 라이선스 명세를 대신하지 않는다.

| 구성요소 | 실제 사용·버전 | 공식 프로젝트의 라이선스 |
|---|---|---|
| AndroidX Compose / Material 아이콘 / Activity / Lifecycle / Room | UI·아이콘·상태·로컬 저장. Compose BOM 2024.12.01, Activity 1.10.0, Lifecycle 2.8.7, Room 2.8.4 | [AndroidX 프로젝트 LICENSE](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt), Apache-2.0. 개별 아티팩트의 별도 NOTICE·포함 구성요소는 추가 확인 대상. |
| kotlinx.coroutines | 비동기 처리, 1.9.0 | [해당 버전 LICENSE](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/LICENSE.txt), Apache-2.0. |
| Ktor Android client | HTTP 통신, 직접 선언 3.0.3 | [해당 버전 LICENSE](https://github.com/ktorio/ktor/blob/3.0.3/LICENSE), Apache-2.0. 최종 해석 버전은 전이 의존성 해석 결과 확인 필요. |
| supabase-kt auth / postgrest | 선택 로그인·프로필, BOM 3.5.0 | [해당 버전 LICENSE](https://github.com/supabase-community/supabase-kt/blob/3.5.0/LICENSE), MIT; 권리자 표기는 Jan Tennert. SDK 라이선스와 Supabase 호스팅 서비스 계약은 별개. |
| Pretendard 폰트 | `res/font/pretendard_*.otf` 4개, `ui/theme/Theme.kt` | [프로젝트 LICENSE](https://github.com/orioncactus/pretendard/blob/main/LICENSE), SIL OFL-1.1. 로컬 파일의 정확한 배포 버전은 미확인. |

기존 release 빌드 산출물
`android/app/build/intermediates/lint_vital_report_lint_model/release/generateReleaseLintVitalReportModel/release-artifact-dependencies.xml`
에는 전이 의존성으로 coroutines 1.10.2, Ktor core 3.4.2(Android 엔진 3.0.3),
Lifecycle 2.10.0, Kotlin stdlib 2.3.20 등이 해석된 것으로 기록돼 있다. 위 표의 직접 선언
버전과 다르므로 최종 고지 작성 시 해당 실제 버전의 라이선스를 다시 대조해야 한다.
Okio, Kermit, multiplatform-settings, SLF4J, JSpecify 등 추가 구성요소도 포함된다.
`android/app/build/outputs/sdk-dependencies/release/sdkDependencies.txt`에도 목록이 있다.
이 빌드 산출물들은 참고용이며 최종 제출 APK 재빌드 시 목록을 재확인한다.

code 32 소스에는 [Android 오프라인 이용 고지](22_ANDROID_LICENSE_NOTICES.md)가 추가됐다.
최종 release 해석 목록의 110개 런타임 JVM/Android 모듈을 `assets/licenses/catalog.json`에
연결하고 라이선스·고지 원문 7개와 폰트 고지를 제공한다. 설정의 `오픈소스 라이선스`에서
네트워크 없이 목록·원문을 읽을 수 있도록 구현했다. 카카오 SDK 자체의 이용조건과 SDK에
포함된 제3자 고지는 구분하며, 카카오 SDK를 Apache/MIT 자체로 분류하지 않는다.
계약 검증 테스트는 2/2 통과했다. QA 담당자는 code 32 최종 APK 실기기에서 라이선스 목록,
폰트·카카오 고지 원문과 뒤로 이동을 포함한 55초 Maestro 흐름 통과를 확인했다(2026-09-13).
이 고지는 code 33에도 유지되며 04:26 실기기에서 목록·Pretendard 원문·목록 및 뒤로 이동을
재확인했다. 네트워크 차단 상태에서 검사한 것은 아니며, 오프라인은 자산 전용 구현과 파일
계약으로 확인했다. 이 검증을 모든 네이티브 구성의 독립적인 전체 감사로 해석하지 않는다.

재배포 조건의 핵심:

- [Apache-2.0 제4조](https://www.apache.org/licenses/LICENSE-2.0): 수령인에게 라이선스 사본을
  제공하고, 해당되는 원본 NOTICE·저작권 고지를 보존한다. 수정한 파일에는 변경 사실을
  표시한다. 라이브러리 사용이 해당 프로젝트 상표에 대한 일반적인 사용권을 주지는 않는다.
- MIT: 해당 소프트웨어의 사본·중요 부분에 원 저작권 고지와 허가문을 포함해야 한다.
- Pretendard OFL: 폰트의 앱 포함·배포는 조건부 허용되며 저작권 고지와 OFL을 함께 제공해야
  한다. 폰트 단독 판매 및 수정본의 예약된 글꼴명 사용에 별도 제한이 있다. 앱 자체 전체를
  OFL로 바꾸어야 한다는 뜻은 아니다.

앱 로고는 사용자가 앱 로고라고 지정해 전달한 `KakaoTalk_20260908_220323636.png`를
기준으로 사용한다. 적용 위치는 `android/app/src/main/res/drawable-nodpi/brand_logo.png` 및
`download/logo.png`다. 사용자는 2026-09-13 **본인·팀이 제작했고 사용 가능**하다고 답변했다.
이는 사용자 확인 사실이며, 별도로 서명한 저작권 양도서나 개별 팀원의 권리 확인 서류를
받았다는 뜻은 아니다.

## 5. 제출 전 보완·확인 사항

| 항목 | 확인된 현상 | 필요한 후속 조치 |
|---|---|---|
| 사진 출처의 완결성 | 최신 소스는 메타데이터 유무와 관계없이 상세 사진의 TourAPI 출처를 표시하며, 상세 2곳에서 공식 이용조건 링크를 제공함. code 32 실기기에서 출처 노출·공식 외부 페이지 왕복 확인 | 개별 사진의 원출처·유형을 계속 대조. 일반 안내 문구가 미확인 사진의 권리 허락을 대신하지 않음. 없는 작가명·연도를 만들어 넣지 않음. |
| 제3유형 사진 표시 | 공통 `SavedPlaceImage` 호출 3곳은 실제 표시 URL에 일치하는 명확한 Type1만 Crop, Type3·미확인·상충 메타데이터는 Fit으로 변경함. code 32 실기기 저장 상세에서 전체 구도 확인 | 사진 표시 최소 보완 완료. Fit 사용만으로 전체 개별 이용조건 충족을 보증하지 않음. |
| 지도 로고 | code 33의 접힌 검색 시트 보정을 유지. 최종 code 34의 중간 결과 시트·묶음 5→4→3에서 로고 전체 노출과 구성원 핀 가림 해소를 실제 캡처로 확인 | 전체 목록·펼친 입력이 지도를 덮는 화면과 구분하며 모든 화면의 상시 노출로 표현하지 않음. |
| OSS·폰트 고지 | code 32의 110개 모듈 목록·원문 7개 고지를 code 34에 유지. 이번 주소·UI 변경은 의존성·폰트·고지를 변경하지 않음. 계약 테스트 2/2, code 32 전체 고지 흐름 및 code 33 목록·Pretendard 원문·뒤로 실기기 재확인 | 이 고지 흐름을 code 34에서 새로 전부 검사했다고 표현하지 않음. 네트워크 차단·큰 글자 고지 화면 검증은 별도. 제공사 공통 고지를 내부 네이티브 구성의 독립 SBOM으로 해석하지 않으며, Kotlin 재패키징 코드의 별도 상위 NOTICE 전수 확인 등 한계는 docs22에 명시. |
| 기타 보조 공공데이터 | 기상·집중률·무장애·공영주차장의 공식 출처를 3.1절에 추가 | 계정별 승인 자료는 별도. 공영주차장 통합 데이터의 구체적 이용조건은 추가 확인 범위. |
| API 계정별 증빙 | 공개 약관과 코드의 실제 사용만 대조 | 키를 가린 앱 등록/활용 승인 화면 또는 권리자가 발행한 서류를 필요 범위에서 추가. 이 문서에 계정 승인 완료·별도 계약 체결을 허위 기재하지 않음. |

사진 보완 자동 검증: `PhotoRightsPresentationTest` 신규 6개 및 `DetailPresentationTest`
10개, 총 16/16 통과. 실제 이미지 URL·썸네일 대응, 메타데이터 누락·불일치·상충 시 안전
표시와 출처 문구를 검증했다. code 32 통합 검증은 Android 190개 실패 0개 및
실기기 라이선스·사진 흐름을 통과했다. 이후 code 33 수동 빌드는 Android 195/195
(44 suites), lint 오류 0개·경고 48개와 서명 release 빌드를 통과했다. 보존된 자동 위치
모드도 195/195와 debug 빌드를 통과했으나 배포하지 않는다. code 33은 04:18 업데이트
설치 후 기존 카카오 로그인·예약 알림이 유지됨을 확인했다. 04:33 예약 알림은 04:35:59에
수신했고, 04:36 탭 후 카카오맵의 경포호수광장 → 경포대 경로 전달을 확인했다. 약 3분
지연된 사례이며 정시 알림 보장으로 표현하지 않는다. 안내 시작이나 실제 운행은 수행하지
않았다. 이 결과도 전체 사진의 개별 권리 검토 완료를 의미하지 않는다.

## 6. 제출 판단

공식 SDK/API 이용조건과 공개 라이선스는 **사용 근거를 설명하는 자료**로 묶을 수 있다.
다만 공개 약관 URL 목록은 개별 이용승인·사진 권리 검토·앱 내 고지 이행을 대체하지 않는다.
원스토어 제출 파일로 확정하려면 위 보완 상태를 실제 최종 APK에 맞게 갱신하고, 필요한
계정별 증빙을 합친 뒤 제출자가 최종 확인해야 한다. 원스토어가 서명 허락서나 별도 자료를
요구하면 이 초안만으로 심사 통과를 보장할 수 없다.
