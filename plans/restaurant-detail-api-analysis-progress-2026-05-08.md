# 식당 상세 API 연동 분석 진행 기록

기준 날짜: 2026-05-08

## 목표

현재 백엔드 API를 프론트 상세페이지에 연동할 때 사용할 수 있는 요소와 구현 완료 범위를 확인한다.

## 확인 범위

- 식당 상세 기본 API: `GET /restaurants/{id}`
- 식당 주변 주차장 API: `GET /restaurants/{restaurantId}/parking-lots`
- 식당 리뷰 API: `GET /restaurants/{id}/reviews`
- 식당 상세 정책 문서: `docs/logic/restaurant-detail-policy.md`
- 주차장 안내 정책 문서: `docs/logic/parking-lot-policy.md`
- 식당 DB 기준 문서: `docs/db/restaurants.md`
- 백엔드 seed 데이터와 Naver Pcmap seed 산출물의 상세 데이터 보유 현황

## 현재 구현 상태

- `RestaurantDetailResponse`는 상세 홈/메뉴 화면용 필드를 이미 제공한다.
- 응답에는 식당 기본정보, 주소, 좌표, 대표 이미지, 사진 배열, 전화번호, 영업시간 구조화 응답, 현재 영업 상태, 카테고리, 활성 태그, 메뉴 목록이 포함된다.
- 영업시간은 `business_hours_raw`를 서버에서 파싱하고, 현재 상태는 Asia/Seoul 기준 조회 시점에 계산한다.
- 대표 이미지는 현재 `restaurants.image_url` 하나를 `photos[0]`로 변환한다.
- 메뉴는 `restaurant_menu_items`를 `displayOrder`, `id` 기준 오름차순으로 내려준다.
- 부가정보 태그는 활성 태그만 내려주며 `isPrimary`, `matchedMenuCount` 기준 정렬을 사용한다.
- 주변 주차장은 상세 기본 응답에 포함하지 않고 별도 API로 거리순 조회한다.
- 리뷰 목록도 상세 기본 응답에 포함하지 않고 별도 API로 조회한다.

## 데이터 상태

- 현재 백엔드 `Capstone/Capstone/seed-data/restaurants-seed-preview.json` 기준:
  - 식당 616건
  - 이미지 보유 551건
  - 전화번호 0건
  - `business_hours_raw` 0건
  - `primary_category_name` 0건
- 별도 `Naver_pcmap_api/Naver_seed/output/restaurants-seed-preview.json` 산출물 기준:
  - 식당 596건
  - 전화번호 534건
  - `business_hours_raw` 549건
  - `primary_category_name` 596건
- 따라서 실제 상세 API에서 전화번호/영업시간이 채워지는지는 현재 DB가 어떤 seed 산출물로 적재되었는지에 따라 달라진다.

## 검증

아래 focused 테스트가 통과했다.

```powershell
.\gradlew.bat test --tests "com.example.Capstone.controller.RestaurantControllerTest" --tests "com.example.Capstone.controller.RestaurantParkingLotControllerTest" --tests "com.example.Capstone.service.support.RestaurantBusinessHoursResolverTest"
```
