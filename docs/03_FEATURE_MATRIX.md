# 기능 구현 현황표

기준일: `2026-08-16`
검토 기준: 이 저장소의 `android/` 소스
Android 기준 버전: `0.12.3` (`versionCode 24`)

이 문서는 디자인 시안이나 과거 APK가 아니라 현재 Android 소스가 실제로 수행하는 동작을 기준으로 작성했습니다. 기능을 변경할 때는 코드와 이 표를 같은 커밋에서 갱신하세요.

표 안의 `ui/`, `data/`, `domain/` 축약 경로는 `../android/app/src/main/java/com/tteumsae/app/`를 기준으로 합니다.

## 상태 정의

| 상태 | 의미 |
|---|---|
| `구현 및 코드 검증` | 사용자 흐름과 예외 처리가 코드에 연결돼 있고, 자동 테스트 또는 정적 검토 근거가 있음. 실기기 환경별 회귀 테스트는 별도 수행해야 함 |
| `구현, QA 필요` | 기능은 연결돼 있으나 GPS, 카카오 앱, 외부 네트워크, 기기 상태처럼 실기기 확인이 핵심인 상태 |
| `부분 구현/추정` | 화면이나 기본 동작은 있으나 정확도·완결성·문구가 제품 의도에 미달하거나 임시 계산을 사용함 |
| `미구현` | 화면에 준비 중으로 노출되거나 코드·리소스·배포 구성이 없음 |

## 1. 앱 구조와 공통 상태

| 기능 | 상태 | 현재 실제 동작 | 코드 근거 | 다음 버전에서 확인할 점 |
|---|---|---|---|---|
| 앱 진입과 화면 전환 | `부분 구현/추정` | 단일 `MainActivity`와 단일 `TteumsaeApp` 컴포저블이 `AppScreen` enum 상태로 홈·위치·시간·조건·로딩·결과·상세를 교체함. Navigation Compose나 저장 가능한 백스택은 없음 | `../android/app/src/main/java/com/tteumsae/app/MainActivity.kt`, `../android/app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt` | 시스템 뒤로가기가 앱 내부 이전 화면으로 연결되지 않으므로 `BackHandler` 또는 Navigation Compose 도입 여부 결정 |
| 메인 탭 | `구현 및 코드 검증` | `장소 찾기`, `장소 둘러보기`, `설정` 3개 탭. 탭을 누르면 각 루트 화면으로 직접 전환 | `ui/TteumsaeApp.kt`의 `BottomNavigation` | 탭별 상태 보존 정책과 시스템 뒤로가기 정책 확정 |
| 일시적 탐색 상태 | `부분 구현/추정` | 화면·모드·이름·시간·여유·이동수단 일부는 `rememberSaveable`; 좌표·추천 목록·카탈로그·선택 상세는 메모리 상태. 프로세스 종료 후 탐색은 복원되지 않음 | `ui/TteumsaeApp.kt`의 `TteumsaeApp` | 회전·프로세스 복원 실기기 QA. 필요하면 `ViewModel`/`SavedStateHandle`로 이동 |
| 브랜드 색상 | `구현 및 코드 검증` | 공식 기본색 `#E60F33`, 연한 배경은 동일 색상 10% 알파 사용 | `ui/theme/Theme.kt` | 신규 컴포넌트에서 임의 빨간색을 추가하지 말고 토큰 재사용 |
| 서버 키 보호 | `구현 및 코드 검증` | Android는 공개 Vercel API만 호출. TourAPI·카카오 REST 키는 앱 코드에 없음. 카카오 네이티브 지도 키만 `local.properties`에서 BuildConfig로 주입 | `app/build.gradle.kts`, `data/TteumsaeApi.kt` | 릴리스 빌드와 CI에서도 네이티브 키를 안전하게 주입 |

## 2. 홈과 GPS

| 기능 | 상태 | 현재 실제 동작 | API·외부 의존성 | 예외·경계 상태 | 코드 근거 |
|---|---|---|---|---|---|
| 홈 지도 | `구현, QA 필요` | 기본 카메라는 강릉 좌표 `(37.7645, 128.8996)`, 줌 13. 카카오맵 네이티브 키가 없으면 지도 모양 플레이스홀더와 설정 필요 문구 표시 | Kakao Map Android SDK 2.14.0 | SDK 초기화 오류 시 지도 중앙에 오류와 `지도 다시 시도` 제공 | `ui/TteumsaeApp.kt`의 `HomeScreen`, `MapBackground`, `KakaoMapSurface`; `TteumsaeApplication.kt` |
| 첫 안내 팝업 | `구현 및 코드 검증` | 앱 진입 시 서비스 설명 표시. `오늘 하루 보지 않기`를 체크하고 확인하면 기기 날짜를 SharedPreferences에 저장해 그날 재표시하지 않음 | 기기 로컬 날짜·SharedPreferences | 체크하지 않고 닫으면 다음 앱 실행에서 다시 표시 | `ui/TteumsaeApp.kt`의 `HomeIntroDialog`, `shouldShowHomeIntro`; `ui/HomeIntroTest.kt` |
| 홈 GPS 토글 | `구현, QA 필요` | 비활성 상태에서 누르면 권한 확인 후 위치를 구하고 지도 이동·현재 위치 마커 표시. 활성 상태에서 다시 누르면 마커와 선택 상태 제거. 탐색 화면을 다녀와도 루트 상태가 유지되는 동안 활성 상태 유지 | Android `LocationManager`, FINE/COARSE 권한, Kakao Map SDK | 60초 이내 마지막 위치 우선; 없으면 GPS/Network 단일 업데이트를 최대 12초 대기. 위치 서비스 꺼짐은 설정 안내, 획득 실패는 토스트 | `ui/CurrentLocation.kt`, `ui/TteumsaeApp.kt`의 `HomeScreen` |
| 위치 권한 거부 | `구현, QA 필요` | 일반 거부는 허용 요청 토스트, 다시 묻지 않음으로 판단하면 앱 설정 열기 다이얼로그 표시 | Android 런타임 권한 | 제조사·Android 버전별 최초 거부와 영구 거부 판별을 실기기에서 확인해야 함 | `ui/TteumsaeApp.kt`의 `deniedLocationPermissionNeedsSettings`, `LocationPermissionSettingsDialog` |
| 탐색 시작 CTA·검색바 | `구현 및 코드 검증` | 검색바와 `남는 시간으로 장소 찾기`가 동일하게 위치 설정 화면으로 이동. 홈 GPS가 활성화돼 있으면 좌표를 전달; 아니면 위치 화면에서 자동 GPS 요청 | GPS는 선택적 | GPS 없이 진입하면 `현재 위치` 미해결 상태라 위치 화면에서 권한 요청이 시작됨 | `ui/TteumsaeApp.kt`의 `HomeScreen`, `TteumsaeApp.onStart` |

## 3. 위치 설정과 탐색 모드

| 기능 | 상태 | 현재 실제 동작 | API·외부 의존성 | 예외·경계 상태 | 코드 근거 |
|---|---|---|---|---|---|
| 위치 설정 바텀시트 | `구현, QA 필요` | 지도 위 드래그 가능한 시트. 기본 peek 높이는 화면의 54%, 펼치면 90%. 시트가 완전히 숨겨지면 홈으로 복귀하며 하단 이전/다음 버튼도 함께 사라짐 | Compose Material3 BottomSheet | 시트를 일부만 내린 peek 상태에서는 하단 버튼 유지 | `ui/TteumsaeApp.kt`의 `MapSheetScreen`, `LocationScreen` |
| 경로 따라 갈 장소 | `구현, QA 필요` | 출발지와 강원도 목적지를 입력. 출발지는 강원도 밖도 가능하고 목적지만 강원도 여부를 검사. 이동수단은 자동차로 강제 | `/api/geocode`, `/api/region`; 이후 Kakao Mobility는 백엔드 추천에서 사용 | 목적지가 강원도 밖이면 토스트 후 현재 화면 유지. 출발·목적 좌표 확인 실패 시 서버 메시지 토스트 | `ui/TteumsaeApp.kt`의 `LocationScreen`, 루트 `onNext`; `data/TteumsaeApi.kt` |
| 근처에서 갈 장소 | `부분 구현/추정` | 모드 전환 시 시작 위치를 종료 위치로 복사하고 강원도 안인지 시작점을 검사. 시간 화면에서 도보/차량 선택 가능 | `/api/geocode`, `/api/region` | UI에는 `돌아올 곳` 입력이 있으나 다음 단계에서 백엔드는 항상 `resolvedEnd = resolvedStart`를 사용하므로 사용자가 고른 별도 복귀 위치가 무시됨. 독립 복귀지 지원 또는 필드 제거 필요 | `ui/TteumsaeApp.kt`의 `onModeChange`, `LocationScreen`, 루트 `onNext` |
| 장소 자동완성 | `구현, QA 필요` | 2자 이상 입력 후 350ms 디바운스, 최대 5개 결과 노출. 선택 결과는 이름·주소·좌표 저장. 선택된 텍스트를 누르면 지우고 다시 입력 | `GET /api/geocode?q=...` | 근처 모드와 목적지는 검색어 앞에 `강원 `을 자동 추가. 빈 결과 안내, 네트워크 실패 원인과 `다시 시도` 제공 | `ui/TteumsaeApp.kt`의 `LocationSearchField`; `data/TteumsaeApi.kt`의 `searchPlaces` |
| 현재 위치를 출발지로 사용 | `구현, QA 필요` | 위치 화면 진입 시 출발지가 `현재 위치`이고 좌표가 없으면 자동 요청. `현위치` 버튼은 선택/해제 토글. GPS 성공 후 `/api/region` 역지오코딩 주소로 이름을 교체하고 한 줄 말줄임 표시 | Android LocationManager, `GET /api/region` | 역지오코딩 실패 시 `현재 위치` 이름 유지. 영구 거부·위치 서비스 꺼짐은 설정 유도 | `ui/CurrentLocation.kt`; `ui/TteumsaeApp.kt`의 `LocationScreen`, `LocationSearchField` |
| 위치 입력 검증 | `구현 및 코드 검증` | 시작·종료 이름이 모두 있고, 현재 위치가 미해결 상태가 아니며, 위치 확인/GPS 진행 중이 아니어야 `다음` 활성화 | 위 위치 API | 문자열이 있어도 좌표가 없으면 다음 클릭 시 서버 검색으로 최종 해석 | `ui/TteumsaeApp.kt`의 `LocationScreen` bottomContent와 루트 `onNext` |

## 4. 시간·이동수단·추천 의도

| 기능 | 상태 | 현재 실제 동작 | 예외·한계 | 코드 근거 |
|---|---|---|---|---|
| 남은 시간 슬라이더 | `구현 및 코드 검증` | 15분~6시간, 15분 단위. 눈금 문구 15분·1시간·2시간·4시간·6시간은 값 비율에 맞게 배치. 드래그 중 thumb 위에 분 표시 | 화면 제목은 경로 모드에서 여전히 `목적지 도착까지 얼마나 시간이 남았나요?`로, 기획된 `여유 시간이 얼마나 되나요?` 문구와 다를 수 있음 | `ui/TteumsaeApp.kt`의 `TimeScreen`, `TimeSliderThumb` |
| 시간 직접 입력 | `구현 및 코드 검증` | 숫자 최대 4자리, 15~1440분(24시간). 유효하지 않으면 오류 문구와 `다음` 비활성 | 유효하지 않은 입력 동안 내부 기준값은 마지막 유효값으로 남지만 진행 버튼은 막힘 | `ui/TteumsaeApp.kt`의 `TimeScreen` |
| 안전 여유 | `부분 구현/추정` | 10·15·20·30분 중 하나 선택, 기본 15분. 사용 가능 시간 `deadline-buffer` 표시. 추천 요청에 그대로 전달 | 남은 시간보다 큰 여유를 선택해도 다음 진행 가능하고 사용 가능 시간이 0분으로만 보임. `buffer < deadline` 검증 필요 | `ui/TteumsaeApp.kt`의 `TimeScreen`; `domain/SearchCriteria` |
| 이동수단 | `구현 및 코드 검증` | 경로 모드는 자동차 고정. 근처 모드는 도보 또는 차량 토글 | 도보 추천 시간은 백엔드 추정값이며 실시간 보행 경로가 아님 | `ui/TteumsaeApp.kt`의 `TimeScreen`; `data/TteumsaeApi.kt`의 추천 파싱 |
| 추천 의도 단일 선택 | `구현 및 코드 검증` | `아무거나`, `식사`, `카페`, `산책·관광`, `실내 활동`, `지금은 음식 제외` 중 하나. 각각 카테고리 집합으로 변환해 추천 요청에 전달 | `실내 활동`은 문화시설+쇼핑, `산책·관광`은 관광지+레포츠로 단순 매핑. 편의·접근성 개인화는 없음 | `ui/TteumsaeApp.kt`의 `RecommendationIntent`, `ConditionsScreen`, `recommendationIntentFilters`; `ui/KakaoMapRouteTest.kt` |
| 탐색 방식 표시 | `구현 및 코드 검증` | 조건 화면에서는 현재 탐색 방식을 읽기 전용 카드로 표시하며 가짜 탭 전환 UI가 없음 | 모드를 바꾸려면 위치 화면으로 돌아가야 함 | `ui/TteumsaeApp.kt`의 `ConditionsScreen` |

## 5. 추천 요청·로딩·오류

| 기능 | 상태 | 현재 실제 동작 | API·외부 의존성 | 예외·경계 상태 | 코드 근거 |
|---|---|---|---|---|---|
| 추천 요청 | `구현, QA 필요` | 최종 출발·목적 좌표를 보장한 뒤 모드, deadline, safety buffer, transport, categories를 JSON으로 전송. 서버 추천·경고를 메모리에 저장 | `POST /api/recommendations`; 필요 시 `GET /api/geocode` | 좌표가 없으면 앱 API 계층이 예외. HTTP 오류는 서버 `error.message`를 사용자 메시지로 사용 | `data/TteumsaeApi.kt`의 `recommendations`; `ui/TteumsaeApp.kt`의 `LoadingScreen` 호출부 |
| 로딩 화면 | `구현 및 코드 검증` | 지도 배경 위 체크리스트 표시. 성공 후 최소 500ms 뒤 결과 화면 이동 | 백엔드 및 지도 SDK | 별도 취소 버튼은 없음 | `ui/TteumsaeApp.kt`의 `LoadingScreen` |
| 추천 실패 | `구현 및 코드 검증` | `장소 추천에 실패했어요`와 원인 표시, `다시 시도`, `조건으로 돌아가기` 제공 | 네트워크/백엔드 | 앱 전체 오프라인 상태 감지기는 없고 요청 단위로만 안내 | `ui/TteumsaeApp.kt`의 `LoadingScreen`, `networkFailureMessage` |
| 추천 경고 | `구현 및 코드 검증` | 서버 `meta.warning`을 결과·상세에 노란 안내 상자로 표시 | 추천 API | 경고 문자열 의미는 백엔드 계약에 의존 | `data/TteumsaeApi.kt`의 `RecommendationResult`; `ui/TteumsaeApp.kt`의 결과·상세 |

## 6. 결과 지도·복수 경유지

| 기능 | 상태 | 현재 실제 동작 | 정확도·예외 | 코드 근거 |
|---|---|---|---|---|
| 추천 목록 | `구현, QA 필요` | 후보 번호, 카테고리, 이름, 안전 배지, 추천 이유, 운영 상태, 머무는 시간·전체 예상·남는 시간, 태그, 상세 보기 표시 | 서버 정렬 순서를 그대로 사용. 결과 카드 대표 이미지는 아직 없음 | `ui/TteumsaeApp.kt`의 `ResultsScreen`, `RecommendationCard` |
| 결과 지도 경로 | `부분 구현/추정` | 추천 중 우회시간이 가장 짧은 한 건의 `routePoints`를 붉은 경로로 표시. 출발·도착 라벨 표시 | 경유지 선택을 바꿔도 지도 경로선 자체는 선택한 복수 경유지 경로로 재계산되지 않음 | `ui/TteumsaeApp.kt`의 `ResultsScreen`, `RouteMap` |
| 후보 핀 | `구현, QA 필요` | 좌표가 있는 모든 추천을 지도에 표시. 비선택은 파란 원형 카테고리 아이콘, 선택은 브랜드 빨간색과 순서 배지. 핀 클릭으로 선택/해제 | 밀집 지역 마커 겹침·클러스터링·접근성 설명은 없음 | `ui/TteumsaeApp.kt`의 `createCandidateMarkerBitmap`, `drawCategoryMarkerIcon`, `KakaoMapSurface` |
| 붉은 후보 영역 | `부분 구현/추정` | 기준 경로의 점을 최대 약 10구간으로 샘플링해 원형 폴리곤을 겹쳐 그림. 반경은 `deadline × 20m`, 최소 800m·최대 8km | 실제 도로 기준 도달 가능 영역이나 등시간선이 아니며, 영역 안의 모든 지점이 시간 조건을 만족한다는 뜻이 아님. 사용자 문구에 `예상 범위` 명시 필요 | `ui/TteumsaeApp.kt`의 `RouteMap`, `KakaoMapSurface` |
| 복수 경유지 선택 | `부분 구현/추정` | 최대 5곳. 지도 핀 또는 카드의 `추가`로 토글. 출발→목적 벡터에 투영한 순서로 자동 정렬. 선택 수·예상 총시간·여유를 하단 고정 영역에 표시 | 실제 도로 최적 순서가 아니라 직선 투영 순서. 정상 결과에서 좌표가 없는 후보는 정렬 목록에서 탈락해 선택이 유지되지 않지만 별도 오류 문구가 없음 | `ui/TteumsaeApp.kt`의 `orderWaypointIdsAlongRoute`, `ResultsScreen`; `ui/KakaoMapRouteTest.kt` |
| 복수 선택 시간 검증 | `부분 구현/추정` | 첫 선택 후보의 두 구간에서 우회분을 뺀 값을 기본 직행시간으로 보고, 각 선택 후보의 `detour+stay`를 합산. 예상 여유가 안전 여유보다 작으면 추가 차단 | 선택 조합을 Kakao Mobility로 다시 호출하지 않는 보수적/단순 합산값. 교통·경유 순서 간 상호작용 미반영 | `ui/TteumsaeApp.kt`의 `selectedRouteEstimate`, `ResultsScreen`; `ui/KakaoMapRouteTest.kt` |
| 카카오맵 복수 경유 안내 | `구현, QA 필요` | 선택이 1개 이상이면 `https://map.kakao.com/link/by/car/{출발}/{경유지...}/{목적지}` 형식으로 최대 5곳과 최종 목적지를 전달 | 카카오맵/브라우저 URL 처리와 실제 경유지 순서·최종 목적지 표시를 실기기에서 확인해야 함. URL 실행 실패는 토스트 | `ui/TteumsaeApp.kt`의 `openKakaoMapMultiRoute`, `buildKakaoMapMultiRouteUrl`; `ui/KakaoMapRouteTest.kt` |
| 결과 없음 회복 UI | `부분 구현/추정` | `여유시간 30분 늘리기`(근처 모드는 `주변 반경 넓혀 다시 찾기` 문구), 조건 해제, 다른 장소 검색, 시간·조건 직접 수정 제공 | 근처의 `반경 넓히기`도 실제로는 deadline만 30분 증가. 별도 반경 파라미터가 없음. 24시간 상한 | `ui/TteumsaeApp.kt`의 `ResultsScreen`, `extendedDeadlineMinutes` |

## 7. 추천 상세와 영업 상태

| 기능 | 상태 | 현재 실제 동작 | 정확도·예외 | 코드 근거 |
|---|---|---|---|---|
| 추천 상세 | `구현, QA 필요` | 경로 지도, 카테고리·장소명·저장, 추천 이유·경고, 여유 요약, 출발→머무름→도착 시간선, 운영시간·휴무·태그 표시 | 대표 이미지는 표시하지 않음. 장소 소개·전화·홈페이지 등 상세정보 없음 | `ui/TteumsaeApp.kt`의 `DetailScreen` |
| 운영 상태 | `부분 구현/추정` | 서버 상태가 `OPEN`이면 `도착 예상 시간에 운영 중`, 그 외에는 `운영시간 확인 필요`. 상세 하단에 정보 변동 가능 안내 | Android enum은 `OPEN`, `UNKNOWN`만 보유. 닫힘을 별도 UI로 표현하지 않으며 최종 필터 정확도는 백엔드에 의존 | `domain/Models.kt`, `data/TteumsaeApi.kt`, `ui/TteumsaeApp.kt`의 `OperationStatusText` |
| 단일 장소 카카오 안내 | `구현, QA 필요` | 경로 모드는 출발→선택 장소 1곳→최종 목적지를 `kakaomap://route`로 전달. 근처 모드는 출발→선택 장소를 도보/차량으로 전달. 앱 미설치 시 모바일 웹 시도 | 근처 모드는 장소 방문 후 복귀지까지의 왕복 안내를 만들지 않음. 좌표 누락 시 토스트 후 중단 | `ui/TteumsaeApp.kt`의 `DetailScreen`, `openKakaoMapRoute`, `buildKakaoMapRouteQuery` |
| 추천 장소 저장 | `구현 및 코드 검증` | 하트로 로컬 저장/해제. 장소 핵심 필드를 SharedPreferences JSON으로 저장 | 계정 동기화·백업·기기간 동기화 없음. 저장 직렬화에 `openingHours`, `closedDays`가 포함되지 않아 재실행 후 해당 상세값은 소실 | `ui/TteumsaeApp.kt`의 `loadSavedPlaces`, `storeSavedPlaces` |

## 8. 장소 둘러보기·저장

| 기능 | 상태 | 현재 실제 동작 | API·외부 의존성 | 예외·경계 상태 | 코드 근거 |
|---|---|---|---|---|---|
| TourAPI 장소 카탈로그 | `구현, QA 필요` | 탭 진입 시 첫 100개를 가져와 2열 카드로 표시. 스크롤 끝 6개 전부터 다음 페이지 자동 요청, ID 중복 제거 | `GET /api/places?page=N&pageSize=100` | 첫 요청 실패는 전체 오류와 재시도. 추가 페이지 실패는 토스트만 표시하고 사용자가 다시 스크롤하면 재시도 가능 | `ui/TteumsaeApp.kt`의 `SavedPlacesScreen`, 루트 `LaunchedEffect`; `data/TteumsaeApi.kt` |
| 목록 개수·도움말 | `구현 및 코드 검증` | 서버 전체 수가 아니라 현재 로드·필터된 `현재 N개 표시 중`. 정보 아이콘 tooltip에 스크롤 시 계속 로드됨을 안내 | Material tooltip의 터치 제스처를 기기 접근성 환경에서 확인 필요 | `ui/TteumsaeApp.kt`의 `SavedPlacesScreen` |
| 목록 검색·카테고리 | `부분 구현/추정` | 현재까지 로드한 카탈로그에서 이름·주소 문자열 검색, 카테고리 로컬 필터 | 서버 전체 검색이 아니므로 아직 로드하지 않은 장소는 검색되지 않음. 검색 중 자동으로 전 페이지를 탐색하지 않음 | `ui/TteumsaeApp.kt`의 `visiblePlaces` 계산 |
| 정렬 | `구현 및 코드 검증` | `저장 우선순`은 저장한 장소를 최근 저장 순으로 위에, 나머지는 이름순으로 표시. 토글하면 전체 이름순 | 별도의 인기순·거리순은 없음 | `ui/TteumsaeApp.kt`의 `SavedSort`, `visiblePlaces` |
| 카드 이미지 | `구현, QA 필요` | TourAPI `image_url`을 앱에서 직접 내려받고 메모리 LRU 캐시. URL 없음/실패 시 브랜드 기본 이미지. 8초 연결·읽기 제한 | 디스크 이미지 캐시·재시도·이미지 라이브러리 없음. 이미지 출처/저작권 표기는 별도 정책 필요 | `ui/TteumsaeApp.kt`의 `SavedPlaceImage` |
| 카드 텍스트·태그 축약 | `구현 및 코드 검증` | 이름은 최대 폭 155dp, 한 줄 말줄임. 태그는 문자 예산 18 내에서 노출하고 나머지는 `+ N` | 디자인 규칙의 155px과 Android 155dp가 동일한지 확인 필요 | `ui/TteumsaeApp.kt`의 `SavedPlaceCard`, `compactTags`; `ui/KakaoMapRouteTest.kt` |
| 저장 토글·되돌리기 | `구현 및 코드 검증` | 카탈로그의 모든 장소를 하트로 저장 가능. 저장 해제 시 Snackbar `되돌리기` 제공. 탭 이름과 달리 저장한 장소만 보여주는 화면은 아님 | 저장 상태는 이 기기에만 존재 | `ui/TteumsaeApp.kt`의 `SavedPlacesScreen` |
| 카탈로그 상세 | `부분 구현/추정` | 이미지·카테고리·이름·주소·추천 머무는 시간·태그 표시. CTA는 카카오맵에서 장소명 검색 | 좌표 기반 길 안내가 아니라 이름 검색. 상세 화면에서는 저장/해제 버튼이 없고 영업시간·휴무를 표시하지 않음 | `ui/TteumsaeApp.kt`의 `SavedPlaceDetailScreen`, `openKakaoMap` |

## 9. 설정·정책·지원

| 기능 | 상태 | 현재 실제 동작 | 예외·한계 | 코드 근거 |
|---|---|---|---|---|
| 위치 권한 설정 | `구현, QA 필요` | 현재 허용 상태를 표시하고 Android 앱 상세 설정으로 이동. 앱 복귀 `ON_RESUME`에서 상태 갱신 | 위치 서비스 GPS 자체 상태는 표시하지 않음 | `ui/TteumsaeApp.kt`의 `SettingsTabScreen` |
| 카카오맵 상태 | `구현, QA 필요` | 설치 여부를 검사해 설치됨이면 앱 실행, 없으면 Play Store 검색 후 웹 Play Store 검색 | 제조사 앱스토어·브라우저 없음 등 실패 시 토스트 | `ui/TteumsaeApp.kt`의 `isKakaoMapAvailable`, `openKakaoMapHome`, `openKakaoMapInstallPage` |
| 캐시 지우기 | `구현, QA 필요` | 확인 후 메모리 이미지 캐시와 앱 `cacheDir` 내용을 삭제. 저장 장소는 유지 | 지도 SDK 내부 데이터 전체가 삭제된다는 보장은 없음 | `ui/TteumsaeApp.kt`의 `clearAppCache`, `SettingsTabScreen` |
| 저장 장소 전체 삭제 | `구현 및 코드 검증` | 저장 개수 표시, 0개면 비활성. 확인 다이얼로그 후 로컬 저장 목록 전체 삭제 | 계정/서버 데이터는 없음 | `ui/TteumsaeApp.kt`의 `SettingsTabScreen` |
| 문의 메일 | `구현, QA 필요` | `minjaeimnyda@gmail.com`에 제목 `[틈새] 앱 문의`, 앱 버전이 포함된 메일 작성 화면 실행 | 메일 앱이 없으면 토스트. 출시 전 회사/지원 메일로 변경 필요 | `ui/TteumsaeApp.kt`의 `CONTACT_EMAIL`, `openContactEmail` |
| 개인정보처리방침 | `미구현` | 설정에 행은 있으나 URL이 빈 문자열이고 `준비 중`; 클릭 시 준비 중 토스트 | Play Store 제출 전 공개 HTTPS 문서와 실제 URL 필수 | `ui/TteumsaeApp.kt`의 `PRIVACY_POLICY_URL`, `openPolicy` |
| 위치기반서비스 이용약관 | `미구현` | 설정에 행은 있으나 URL이 빈 문자열이고 `준비 중`; 클릭 시 준비 중 토스트 | 위치 기반 기능 출시 전 법률 검토·공개 문서·동의 흐름 필요 | `ui/TteumsaeApp.kt`의 `LOCATION_TERMS_URL`, `openPolicy` |
| 앱 정보 | `구현 및 코드 검증` | BuildConfig 버전과 `한국관광공사 TourAPI · 카카오맵` 출처 표시 | 오픈소스 라이선스, 사업자·운영자 정보 없음 | `ui/TteumsaeApp.kt`의 `SettingsTabScreen` |
| 계정·푸시·소셜 로그인 | `미구현` | 관련 화면·권한·백엔드 연결 없음 | 현재 저장 데이터는 로컬 전용 | Android 전체 소스 |

## 10. 출시 준비 현황

| 항목 | 상태 | 현재 근거 | 출시 전 필요한 작업 |
|---|---|---|---|
| 버전 | `구현 및 코드 검증` | `versionName 0.12.3`, `versionCode 24`, 패키지 `com.tteumsae.app` | 배포마다 versionCode 증가 규칙 적용 |
| Debug APK | `구현, QA 필요` | Gradle `assembleDebug` 구성 존재 | 최신 후보 핀 변경을 포함해 다시 빌드하고 실기기 회귀 테스트 |
| Release 빌드 | `부분 구현/추정` | release에 R8/ProGuard 설정만 있음 | 릴리스 빌드 자체 검증, 난독화 후 Kakao SDK·JSON 파싱 확인 |
| 릴리스 서명·AAB | `미구현` | `signingConfig` 및 키 관리 구성 없음 | 업로드 키 생성·보안 보관, Play App Signing, `bundleRelease` 자동화 |
| 앱 아이콘·스플래시 | `미구현` | `res`에 launcher mipmap/drawable이 없고 Manifest에 icon/roundIcon 지정 없음 | 브랜드 런처 아이콘, Android 12 SplashScreen, adaptive icon 추가 |
| 정책 URL·동의 | `미구현` | 두 정책 상수가 비어 있음 | 공개 정책 URL 연결, 위치 권한 사전 설명과 동의 기록 정책 확정 |
| 접근성 | `부분 구현/추정` | 일부 아이콘 contentDescription과 48dp 현위치 버튼이 있으나 지도 핀·카드·tooltip·색상 대비 전체 감사 없음 | TalkBack, 글자 확대, 터치 영역, 색상 대비, 동적 글꼴 회귀 테스트 |
| 다크 모드 | `미구현` | lightColorScheme만 정의 | 지원 여부를 결정하고 미지원이면 명시, 지원 시 전체 지도/컴포넌트 검증 |
| Android 버전 대응 | `부분 구현/추정` | min 26, compile/target 35 | Play 요구사항 시점의 target SDK 확인, Android 8~최신 실기기 QA |
| 자동 테스트 | `부분 구현/추정` | 도메인·API 문자열·홈 팝업·위치 시작·카카오 경로 단위 테스트 소스 존재 | 현재 한글/공백 경로의 `testDebugUnitTest` 클래스 로딩 실패 해결. UI·instrumentation·실서버 계약 테스트 추가 |
| 오프라인/네트워크 UX | `부분 구현/추정` | 지도·검색·추천·카탈로그별 오류 또는 토스트 일부 존재 | 전역 연결 상태, 재시도 일관성, 느린 네트워크·서버 429/5xx·이미지 실패 QA |
| 개인정보·보안 | `부분 구현/추정` | 서버 비밀키는 APK에 없음. 위치는 요청 시 사용하고 저장 장소는 로컬 JSON | 데이터 안전 양식, 백업 정책(`allowBackup=true`), 위치 데이터 전송 고지, 로그·분석 정책 검토 |

## 11. 변경 시 함께 확인할 파일

| 변경 종류 | 최소 동반 검토 파일 |
|---|---|
| 화면·문구·흐름 | `ui/TteumsaeApp.kt`, 이 문서, `04_SCREEN_FLOWS.md`, 관련 UI 테스트 |
| 추천 요청/응답 | `data/TteumsaeApi.kt`, `domain/Models.kt`, 백엔드 API 계약, `05_API_AND_DATA.md` |
| 카카오 지도·딥링크 | `ui/TteumsaeApp.kt`, `ui/CurrentLocation.kt`, `ui/KakaoMapRouteTest.kt`, 카카오 플랫폼 패키지·키 해시 |
| 저장 카드·카탈로그 | `ui/TteumsaeApp.kt`의 SharedPreferences 직렬화, `/api/places`, 이미지 URL 정책 |
| 릴리스 버전 | `app/build.gradle.kts`, 다운로드 페이지, CHANGELOG, 배포 APK/AAB 검증 기록 |
