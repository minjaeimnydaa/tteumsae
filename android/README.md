# 틈새 Android MVP

강원도 여행 중 목적지에 늦지 않고 들를 수 있는 장소를 추천하는 Android
네이티브 앱입니다.

## 현재 구현

- Kotlin + Jetpack Compose
- 이동 틈새 / 근처 틈새 모드
- 출발지와 목적지 카카오 키워드 검색
- Vercel 운영 백엔드 연결
- TourAPI 기반 강원도 장소 추천
- 차량 모드 카카오 실시간 이동시간 적용
- 도보 모드 거리 기반 추정시간 적용
- 카카오맵 Android SDK 기반 실제 지도 표시
- 안전 여유시간 필터와 결과 카드
- 카카오맵 앱 딥링크
- 네트워크 실패 메시지 및 재시도

서버용 TourAPI 키와 카카오 REST API 키는 Android 앱에 포함되지 않습니다.
앱은 공개된 틈새 백엔드 API만 호출합니다.
카카오맵 네이티브 앱 키는 Git에 포함되지 않는 `local.properties`의
`KAKAO_MAP_NATIVE_APP_KEY` 값으로 주입합니다.

## 운영 API

```text
https://tteumsae-backend.vercel.app
```

배포 환경별 주소를 분리하려면
`app/build.gradle.kts`의 `BuildConfig.API_BASE_URL`을 빌드 타입별로
설정합니다.

## 실행 환경

- Android Studio Ladybug 이상 권장
- JDK 17
- Android SDK 35

이 폴더를 Android Studio에서 열고 Gradle Sync 후 `app` 구성을
실행합니다.

## 주요 파일

```text
app/src/main/java/com/tteumsae/app/
├── MainActivity.kt
├── data/
│   ├── TteumsaeApi.kt
│   ├── DataContracts.kt
│   └── SamplePlaces.kt
├── domain/
│   ├── Models.kt
│   └── TimeSafeEngine.kt
└── ui/
    ├── TteumsaeApp.kt
    └── theme/Theme.kt
```

## 다음 출시 작업

- 결과 카드 선택과 지도 마커·카메라 동기화
- 설정 탭 최종 정보 구조와 디자인
- 온보딩과 위치 권한 영구 거부 안내
- 영업시간·휴무·입장 마감 정규화
- 고령자 동반·무장애 시설 데이터 연동
- 개인정보처리방침 및 위치기반서비스 고지
- 앱 아이콘, 스플래시, 접근성, 다크 모드
- targetSdk 36 전환과 실제 기기 회귀 테스트
- Play App Signing용 릴리스 서명과 AAB 생성
