# 식당 상세와 검색 API 기준

## 식당 상세 홈

- 사진: 현재는 `restaurants.image_url`을 `photos[0]`로 사용한다.
- 주소: `restaurants.road_address`를 우선 사용하고 없으면 지번 주소를 사용한다.
- 영업시간: `businessHoursDisplay`를 화면 표시 기준으로 사용한다.
- 전화번호: `phoneNumber`
- 부가정보: 활성 태그를 `additionalInfoTags`로 내려준다.

## 영업시간 응답

DB에는 `business_hours_raw`를 저장하지만 public 상세 응답에서는 raw 문자열을 직접 사용하지 않는다.

- `businessHours`: 요일별 원본성 구조 데이터
- `businessHoursDisplay.statusLine`: `영업 중 · 23:00에 영업 종료` 같은 첫 줄
- `businessHoursDisplay.summaryLine`: 접힌 상태 대표 줄
- `businessHoursDisplay.rows`: 펼친 상태 요일별 줄
- `businessHoursDisplay.noticeText`: 네이버 하단 안내 문구
- `currentBusinessStatus`: 상태 코드와 계산 기준 시각

## 식당 상세 메뉴

- 메뉴명: `menuName`
- 설명: `description`
- 가격: `priceText`, `priceValue`

## 검색

검색 타입은 식당, 유저, 지역을 분리한다.
지역 검색은 지역 자체 상세가 아니라 선택 지역의 메인 랭킹으로 연결되는 진입점이다.

## 카테고리

식당의 상위 카테고리는 `primary_category_name`에 저장한다.
네이버 공식 Local Search API 결과가 있으면 우선 사용하고, 없으면 seed category 기반 resolver로 보완한다.
