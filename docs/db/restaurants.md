# restaurants.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 `Restaurant`, `RestaurantMenuItem`, `Tag`, `RestaurantTag` 저장 구조를 다룬다.

대상 파일:
- `Capstone/src/main/java/com/example/Capstone/domain/Restaurant.java`
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantMenuItem.java`
- `Capstone/src/main/java/com/example/Capstone/domain/Tag.java`
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantTag.java`

## 2. 현재 코드 기준
### 2-1. `restaurants`
주요 컬럼:
- `id`
- `name`
- `address`
- `road_address`
- `category_name`
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
- `menu_updated_at`
- `is_hidden`
- `is_deleted`
- `created_at`
- `updated_at`
- `deleted_at`

현재 코드 해석:
- `categoryName`은 식당의 단일 카테고리 문자열이다.
- 현재 코드에는 별도 `RestaurantCategory` 엔티티가 없다.
- `roadAddress`가 있으면 응답 표시 주소는 `roadAddress`를 우선 사용한다.
- `regionFilterNames`는 JSON text로 저장되는 지역 검색 보조 이름 목록이다.
- `pcmapPlaceId`는 외부 검색/seed 기준 식당 식별자로 쓰이며 unique 제약이 있다.

### 2-2. `restaurant_menu_items`
주요 컬럼:
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

### 2-3. `tags`
주요 컬럼:
- `id`
- `tag_key`
- `tag_name`
- `parent_tag_key`
- `is_active`
- `created_at`
- `updated_at`

### 2-4. `restaurant_tags`
주요 컬럼:
- `id`
- `restaurant_id`
- `tag_id`
- `matched_menu_count`
- `is_primary`
- `created_at`
- `updated_at`

## 3. 저장 구조 관점에서 읽어야 할 점
- `pcmapPlaceId`는 unique 제약이 있다.
- `region_filter_names`는 JSON text 기반 메타데이터다.
- 카테고리는 `restaurants.category_name` 문자열 exact match 구조이며 별도 마스터/alias 테이블은 없다.
- `restaurant_id + tag_id` 조합은 unique다.

## 4. 추천 / 랭킹에서 직접 쓰이는 부분
- `restaurants.region_name`
- `restaurants.region_town_name`
- `restaurants.is_hidden`, `restaurants.is_deleted`
- `restaurants.category_name`

현재 코드 기준:
- 랭킹의 category filter는 `restaurants.category_name`을 사용한다.
- 식당 추천과 리스트 추천의 category preference는 `Restaurant.getCategoryNames()`를 통해 `categoryName` 단일 값을 사용한다.
- 숨은 맛집 추천의 지역 filter는 동/읍/면 단위 `restaurants.region_town_name` exact match를 사용한다.
- 통합 검색은 식당 이름, 주소, 도로명 주소, 지역 필드, `categoryName`, 메뉴명, 활성 태그명을 검색 신호로 사용한다.
- `restaurant_menu_items`, `tags`, `restaurant_tags`는 통합 검색에는 쓰이지만 추천/랭킹 점수의 직접 입력으로는 쓰이지 않는다.

## 5. seed import와의 관계
- seed import는 식당, 메뉴, 태그, 식당-태그 데이터를 적재한다.
- 식당 카테고리는 별도 카테고리 파일이 아니라 식당 preview row의 `category_name`으로 적재한다.
- 메뉴와 식당-태그는 식당별 delete 후 replace 방식으로 다시 적재한다.
- 운영 규칙은 `docs/logic/seed-import.md`에서 다룬다.

## 6. 외부 fallback 생성과의 관계
- `POST /lists/{id}/restaurants/external-fallback`는 Pcmap 후보가 기존 DB에 없으면 `Restaurant` row를 생성한다.
- 이때 `name`, 주소, 도로명 주소, 카테고리, 좌표, 이미지, `pcmapPlaceId`를 후보에서 가져오고 `regionName`은 대상 리스트 지역으로 저장한다.
- 외부 fallback으로 생성된 식당은 현재 메뉴 / 태그 상세 데이터가 함께 생성되지 않는다.

## 7. 추가 확인 필요
- 메뉴 / 태그를 일반 API 응답으로 노출할지
- 카테고리 alias 정규화를 둘지
- 식당 식별자 강화가 필요한지
- 외부 fallback으로 생성된 식당을 seed preview 또는 관리자 정제 흐름으로 편입할지

## 8. 후속 수정 후보
- 추천 입력에 메뉴 / 태그 활용 검토
- 카테고리 정규화 정책 별도 문서화
