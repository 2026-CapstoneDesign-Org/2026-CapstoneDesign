# 외부 fallback 식당 검색 및 등록 정책

## 목표

식당 검색에서 내부 DB 결과가 부족할 때 외부 pcmap 후보를 보여주되, 검색 조회만으로는 DB에 저장하지 않는다. 사용자가 특정 리스트에 외부 후보를 직접 추가할 때만 재검색과 상세 검증을 거쳐 저장한다.

## 검색 정책

- 검색 API는 내부 식당 결과가 부족할 때 pcmap fallback을 호출한다.
- fallback 응답은 `source=EXTERNAL_FALLBACK`, `restaurantId=null`, `externalPlaceId` 포함 형태다.
- 검색 단계에서는 `restaurants`, `restaurant_menu_items`, `list_restaurants`에 저장하지 않는다.
- fallback 후보가 이미 DB에 존재하는 경우에도 내부 키워드 매칭이 0건이면 외부 후보로 내려갈 수 있다. 이후 `pcmapPlaceId` 기반 내부 매핑 보강이 필요하다.

## 리스트 등록 정책

엔드포인트는 `POST /lists/{listId}/restaurants/external-fallback`이다.

등록 시 검증 순서는 다음과 같다.

1. 사용자의 리스트와 리스트 지역을 확인한다.
2. 요청의 `searchQuery`, `externalPlaceId`, 평가 점수를 검증한다.
3. pcmap 검색을 다시 수행해 요청한 `externalPlaceId` 후보가 실제 검색 결과에 존재하는지 확인한다.
4. pcmap detail을 조회해 detail `placeId`와 검색 후보가 일치하는지 확인한다.
5. detail 주소에서 지역을 해석하고 리스트 지역과 정확히 일치하는지 확인한다.
6. `businessType=restaurant`, 음식점 카테고리, 메뉴 신호를 조합해 식당으로 볼 수 있는지 확인한다.
7. 차단 카테고리, 차단 상호명, 차단 메뉴 raw type이면 저장하지 않는다.
8. 기존 `pcmapPlaceId` 또는 `name + address` 중복이 있으면 신규 저장하지 않고 기존 식당을 사용한다.
9. 검증을 통과하면 식당과 메뉴를 저장한 뒤 `list_restaurants`에 평가 점수와 함께 추가한다.

저장된 fallback 식당은 `is_hidden=false`, `is_deleted=false` 상태이므로 기존 리스트 점수 계산과 추천 후보에 참여한다.

## 구현 파일

- `UserListService`: 외부 fallback 등록 요청을 전용 검증 서비스로 위임한다.
- `ExternalFallbackRestaurantRegistrationService`: 재검색, detail 조회, 지역 검증, 음식점 검증, 중복 확인, 식당 및 메뉴 저장을 담당한다.
- `PcmapPlaceDetailClient`, `PcmapPlaceDetailClientImpl`: pcmap detail/menu 페이지를 조회하고 등록 검증에 필요한 값을 파싱한다.
- `RestaurantRegionResolver`: detail 주소에서 서비스 지역을 해석한다.
- `PcmapSearchClientImpl`: `placeList(...).businesses.items` 응답 구조를 처리한다.

## 확인된 E2E 결과

기준 좌표는 lat `37.2393490`, lng `127.1734445`이다. EC2 dev DB를 로컬 `localhost:5433/dev_db`로 복원한 뒤 로컬 API에서 확인했다.

| 검색어 | 결과 |
| --- | --- |
| 한신포차 | 내부 0건, 외부 fallback 5건 |
| 술집 | 내부 10건, fallback 미사용 |
| 역북 맛집 | 지역 내부 결과 10건, fallback 미사용 |
| 중식 | 내부 10건, fallback 미사용 |
| 중국집 | 내부 키워드 결과 0건, 외부 fallback 5건 |
| 봉구비어 | 외부 fallback 5건 |
| 버거킹 | 외부 fallback 5건 |
| 고반식당 | 외부 fallback 5건 |
| 이디야 | 외부 fallback 5건 |
| 빽다방 | 외부 fallback 5건 |

`한신포차` 등록 E2E:

- 검색 응답: `fallbackUsed=true`, `restaurantCount=5`
- 선택 후보: `externalPlaceId=1846459439`, `source=EXTERNAL_FALLBACK`
- 등록 요청: `POST /lists/1005/restaurants/external-fallback`
- 응답 상태: `201 Created`
- 저장 식당: `restaurant_id=667`, `pcmapPlaceId=1846459439`, `is_hidden=false`, `is_deleted=false`
- 리스트 등록: `list_restaurant_id=18074`, `autoScore=80.0`
- 메뉴 저장: `restaurant_menu_items=84`

## 남은 보강점

- fallback 후보의 `pcmapPlaceId`가 기존 DB에 있으면 검색 응답 단계에서 내부 `restaurantId`로 매핑하는 보강이 필요하다.
- `한신포차` detail의 `category_name`은 `포장마차`이고 현재 `primary_category_name`은 `기타`로 저장된다. 카테고리 정규화가 더 필요하면 resolver 규칙을 추가한다.
- 메뉴는 저장하지만 `restaurant_tags`는 현재 생성하지 않는다. 추천 품질을 높이려면 `Naver_seed`의 태그 추출 규칙을 Java 등록 경로에도 이식해야 한다.
