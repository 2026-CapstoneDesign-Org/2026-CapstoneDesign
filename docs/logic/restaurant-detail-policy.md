# 식당 상세 정책

## 목표

`GET /restaurants/{id}`는 상세 화면에 필요한 식당 기본 정보, 메뉴, 영업시간, 전화번호, 편의정보, 주차 안내, 사진을 한 번에 제공한다.

## 상세 응답 필드

- 메뉴: `menus`
- 영업시간: `businessHours`, `businessHoursDisplay`, `currentBusinessStatus`
- 전화번호: `phoneNumber`
- 편의정보: `conveniences`
- 식당 자체 주차 가능 여부: `parkingAvailable`
- 주변 주차장: `nearbyParkingLots`
- 사진: `photos`

## 영업시간

영업시간은 `restaurants.business_hours_raw`에 저장한다. 서버는 조회 시점의 Asia/Seoul 요일과 시간을 기준으로 현재 상태를 계산한다.

클라이언트는 raw JSON을 직접 파싱하지 않고 서버가 내려주는 표시용 필드를 사용한다.

- `businessHours`: 요일별 구조화 데이터
- `businessHoursDisplay.statusLine`: 첫 줄 상태 문구
- `businessHoursDisplay.summaryLine`: 요약 문구
- `businessHoursDisplay.rows`: 요일별 표시 행
- `businessHoursDisplay.noticeText`: 안내 문구
- `currentBusinessStatus`: 상태 코드와 계산 기준 시각

현재 영업 상태 문자열은 DB에 저장하지 않는다.

## 메뉴

메뉴 응답은 `restaurant_menu_items` 기준이다.

- 메뉴명: `menuName`
- 설명: `description`
- 가격: `priceText`, `priceValue`
- 표시 순서: `displayOrder`

## 사진

식당별 사진은 `restaurant_photos`에서 최대 10개까지 내려준다. 사진 데이터가 없으면 기존 `restaurants.image_url`을 `photos[0]` fallback으로 사용한다.

대표 이미지 `imageUrl`은 기존 클라이언트 호환을 위해 유지한다.

## 주차

`parkingAvailable`은 식당 자체 편의정보에 `주차`가 있는지 나타낸다.

`nearbyParkingLots`는 식당 좌표 기준 주변 주차장 안내 목록이다. 주차장 안내는 식당 도메인과 별도이며, 기존 `GET /restaurants/{restaurantId}/parking-lots` API도 유지한다.

서울시 실시간 도시데이터 권역 안에서 조회하면 주변 주차장 응답에 실시간 주차 가능 대수 정보가 보강될 수 있다. 이 값은 조회 시점 외부 API 응답이며 식당 상세 저장값이나 추천 점수 입력으로 사용하지 않는다.

## 영향 범위

식당 상세 보강 데이터는 검색 정렬, 랭킹, 추천, 공개/비공개 정책에 영향을 주지 않는다.
