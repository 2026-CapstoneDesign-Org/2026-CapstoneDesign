# 식당 상세 주차/사진 확장 계획

기준 날짜: 2026-05-08

## 목표

프론트 식당 상세 화면에서 메뉴, 영업시간, 전화번호, 주차 정보, 사진을 한 번에 사용할 수 있도록 백엔드 응답과 저장 구조를 보강한다.

## 최소 수정 방향

- 메뉴, 영업시간, 전화번호는 기존 `RestaurantDetailResponse` 필드를 유지한다.
- 네이버 편의정보의 `주차` 여부는 `restaurants.conveniences` 목록에서 파생해 `parkingAvailable`로 내려준다.
- 주변 주차장 안내는 기존 `ParkingLotResponse`를 재사용하되 상세 응답에는 제한된 개수만 포함한다.
- 식당 사진은 `restaurants`에 10개 컬럼을 추가하지 않고 `restaurant_photos` 별도 테이블로 저장한다.
- 상세 응답의 `photos`는 `restaurant_photos`에서 최대 10장 조회하고, 없으면 기존 `restaurants.image_url` 1장을 fallback으로 사용한다.

## 영향 범위

- `Restaurant`
- 신규 `RestaurantPhoto`
- 신규 `RestaurantPhotoRepository`
- `RestaurantDetailResponse`
- `RestaurantService`
- `RestaurantSeedImportService`
- Naver seed preview 생성 스크립트
- 관련 controller/service 테스트

## 도메인 규칙 점검

- 리스트 내부 정렬, `auto_score`, 최소 5개 기준은 건드리지 않는다.
- 검색/리스트 추가 플로우는 변경하지 않는다.
- 주차 정보는 추천/랭킹/신뢰도 계산 입력으로 사용하지 않는다.
- 공개/비공개 정책과 무관하다.
