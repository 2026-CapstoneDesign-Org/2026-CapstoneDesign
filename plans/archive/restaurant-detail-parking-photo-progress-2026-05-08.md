# 식당 상세 주차/사진 구현 진행 상황

기준일: 2026-05-08

## 완료

- `Restaurant`에 `conveniences` 저장 필드를 추가했다.
- `conveniences`에 `주차`가 포함되어 있으면 `parkingAvailable`이 true가 되도록 파생 로직을 추가했다.
- 식당별 복수 사진 저장을 위해 `RestaurantPhoto`와 `RestaurantPhotoRepository`를 추가했다.
- 상세 응답 `RestaurantDetailResponse`에 `conveniences`, `parkingAvailable`, `nearbyParkingLots`를 추가했다.
- 상세 응답 `photos`는 `restaurant_photos` 최대 10개를 우선 사용하고, 없으면 기존 `imageUrl`을 fallback으로 사용한다.
- `RestaurantSeedImportService`가 seed의 `conveniences`, `photo_urls`를 읽어 저장하도록 수정했다.
- 네이버 seed combine 단계에서 `conveniences`, `photo_urls`를 생성하도록 수정했다.
- 컨트롤러 테스트와 seed import 통합 테스트를 보강했다.

## 검증

- `./gradlew.bat test --tests "com.example.Capstone.service.RestaurantSeedImportServiceTest" --tests "com.example.Capstone.controller.RestaurantControllerTest"` 성공.
- `./gradlew.bat test --tests "com.example.Capstone.service.ParkingLotServiceTest" --tests "com.example.Capstone.controller.RestaurantParkingLotControllerTest" --tests "com.example.Capstone.e2e.ParkingLotE2ETest"` 성공.
- `node --check Naver_pcmap_api/Naver_seed/src/combine_seed.js` 성공.
- `npm run seed:combine` 성공.

## 보류

- 현재 `Capstone/Capstone/seed-data/restaurants-seed-preview.json`는 기존 616개 row 기준이고, 새로 combine한 네이버 output은 596개 row 기준이다.
- row 수가 달라 기본 seed-data를 자동 덮어쓰지는 않았다.
