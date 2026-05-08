# 식당 상세 주차/사진 응답 정책

기준일: 2026-05-08

## 상세 응답 필드

`GET /restaurants/{id}`는 상세 화면에 필요한 아래 데이터를 한 번에 내려준다.

- 메뉴: `menus`
- 영업시간: `businessHours`, `businessHoursDisplay`, `currentBusinessStatus`
- 전화번호: `phoneNumber`
- 편의정보: `conveniences`
- 주차 가능 여부: `parkingAvailable`
- 주변 주차장: `nearbyParkingLots`
- 식당 사진: `photos`

## 주차 정보 기준

- `parkingAvailable`은 식당 자체 편의정보에 `주차`가 있는지 나타내는 값이다.
- `nearbyParkingLots`는 식당 좌표 기준 가까운 주차장 안내 목록이다.
- `nearbyParkingLots`는 실시간 잔여 대수가 아니라 정적 주차장 데이터 기반 안내다.
- 기존 `GET /restaurants/{restaurantId}/parking-lots` API는 유지한다.

## 사진 응답 기준

- 식당별 사진은 `restaurant_photos`에서 최대 10개까지 내려준다.
- `restaurant_photos`가 비어 있으면 기존 `restaurants.image_url`을 `photos[0]` fallback으로 내려준다.
- 상세 응답의 최상위 `imageUrl`은 기존 대표 이미지 필드로 유지한다.

## 도메인 영향

- 리스트 내부 정렬, 랭킹, 추천, 공개/비공개 정책에는 영향을 주지 않는다.
- 주차와 사진 정보는 상세 화면 표시용 부가 데이터다.
- 검색과 리스트 추가 플로우는 기존처럼 분리한다.
