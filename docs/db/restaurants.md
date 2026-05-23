# 식당 DB 기준

## 범위

식당 상세, 검색, 추천, seed import에서 사용하는 식당 관련 저장 구조를 설명한다.

대상 코드:

- `Capstone/src/main/java/com/example/Capstone/domain/Restaurant.java`
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantMenuItem.java`
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantPhoto.java`
- `Capstone/src/main/java/com/example/Capstone/domain/Tag.java`
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantTag.java`

## `restaurants`

주요 컬럼:

- `id`
- `name`
- `address`
- `road_address`
- `category_name`
- `primary_category_name`
- `region_name`
- `region_city_name`
- `region_district_name`
- `region_county_name`
- `region_town_name`
- `region_filter_names`
- `lat`
- `lng`
- `image_url`
- `pcmap_place_id`
- `phone_number`
- `business_hours_raw`
- `conveniences`
- `menu_updated_at`
- `is_hidden`
- `is_deleted`
- `created_at`
- `updated_at`
- `deleted_at`

`image_url`은 대표 이미지이며, 별도 사진 데이터가 없을 때 상세 사진 fallback으로 사용한다.

`business_hours_raw`는 네이버 PC Map 상세 응답에서 추출한 요일별 영업시간 raw JSON이다. 서버는 이 값을 기준으로 `businessHours`, `businessHoursDisplay`, `currentBusinessStatus` 응답을 계산한다.

`conveniences`는 네이버 PC Map 편의정보 배열을 JSON text로 저장한다. 식당 자체 주차 가능 여부는 이 배열에 `주차`가 포함되어 있는지로 파생한다.

`pcmap_place_id`는 외부 검색 및 fallback 등록 기준 식별자이며 unique 제약을 둔다.

## `restaurant_menu_items`

식당별 메뉴를 저장한다.

- `id`
- `restaurant_id`
- `display_order`
- `menu_name`
- `normalized_menu_name`
- `menu_tag_key`
- `price_text`
- `price_value`
- `description`
- `created_at`
- `updated_at`

seed import 시 메뉴는 식당 단위 delete 후 replace 방식으로 다시 적재한다.

## `restaurant_photos`

식당별 복수 사진을 저장한다. 사진 10개를 저장하기 위해 `restaurants`에 `image_url_1` 같은 컬럼을 늘리지 않는다.

주요 컬럼:

- `id`
- `restaurant_id`
- `image_url`
- `source`
- `display_order`
- `created_at`
- `updated_at`

`restaurant_id + image_url` 조합은 중복 저장하지 않는다. 상세 응답은 `display_order`, `id` 순서로 최대 10개를 내려준다.

## 태그

`tags`는 메뉴 기반 태그 마스터이고, `restaurant_tags`는 식당과 태그의 연결 정보다.

`restaurant_tags.restaurant_id + tag_id` 조합은 unique다. 추천/검색에서 태그는 보조 정보로 사용하고, 현재 랭킹 점수의 직접 입력으로는 쓰지 않는다.

## 외부 fallback 등록

`POST /lists/{id}/restaurants/external-fallback`은 내부 DB에 없는 PC Map 후보를 사용자가 리스트에 추가할 때 `Restaurant` row를 생성한다.

저장 기준:

- 이름, 주소, 도로명주소, 카테고리, 좌표, 대표 이미지, `pcmapPlaceId`는 후보에서 가져온다.
- `regionName`은 대상 리스트 지역을 우선 사용한다.
- 메뉴, 사진, 태그 상세 데이터는 seed preview 또는 상세 수집 흐름에서 보강한다.

## 상세 화면 기준

상세 화면에서 직접 사용하는 식당 데이터:

- `road_address`: 있으면 표시 주소로 우선 사용한다.
- `image_url`: 사진 목록이 없을 때 fallback 사진으로 사용한다.
- `phone_number`: 상세 전화번호다.
- `primary_category_name`: 네이버 공식 Local Search API 또는 seed category 기반 상위 카테고리다.
- `business_hours_raw`: 영업시간 표시와 현재 영업 상태 계산 입력이다.
- `conveniences`: `parkingAvailable` 파생 입력이다.
