# lists.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 `UserList`, `ListRestaurant` 저장 구조를 다룬다.

대상 파일:
- `Capstone/src/main/java/com/example/Capstone/domain/UserList.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ListRestaurant.java`
- `Capstone/src/main/java/com/example/Capstone/service/UserListService.java`
- `Capstone/src/main/java/com/example/Capstone/dto/request/AddExternalRestaurantRequest.java`

## 2. 현재 코드 기준
### 2-1. `user_lists`
주요 컬럼:
- `id`
- `user_id`
- `title`
- `description`
- `region_name`
- `is_public`
- `is_representative`
- `is_hidden`
- `is_deleted`
- `created_at`
- `updated_at`
- `deleted_at`

현재 코드 해석:
- 한 사용자는 여러 리스트를 가진다.
- 리스트는 하나의 `regionName`을 가진다.
- `isPublic`은 저장 필드이지만 실제 해석은 기능별로 다르다.
- `isRepresentative`는 대표 리스트 상태를 저장한다.
- `isHidden`, `isDeleted`는 계산 입력과 노출 모두에 영향을 준다.

### 2-2. `list_restaurants`
주요 컬럼:
- `id`
- `list_id`
- `restaurant_id`
- `taste_score`
- `value_score`
- `mood_score`
- `auto_score`
- `created_at`
- `updated_at`

현재 코드 해석:
- 한 행은 리스트 안의 식당 1개와 3종 점수 1세트를 나타낸다.
- `autoScore`는 저장형 계산 컬럼이다.
- 추천과 랭킹은 `list_restaurants`를 사용자-식당 상호작용 입력으로 사용한다.

## 3. 서비스 로직으로만 보장되는 규칙
- 리스트 생성 시 초기 식당 5개 이상
- 동일 리스트 내 동일 식당 중복 금지
- 리스트와 식당 지역 exact match
- 제거 후 5개 미만 금지
- 사용자당 대표 리스트 1개 유지
- 내부 식당 추가 시 기존 visible 식당만 허용
- 외부 fallback 식당 추가 시 Pcmap 후보를 재조회해 검증한 뒤 기존 식당 재사용 또는 신규 식당 생성

## 4. 로직 문서와 함께 봐야 하는 지점
- 리스트 정책: `docs/logic/list-policy.md`
- 점수 정책: `docs/logic/score-policy.md`
- 공개/비공개 해석: `docs/logic/visibility-policy.md`
- 외부 검색 fallback: `docs/logic/search-policy.md`

## 5. 추가 확인 필요
- 리스트 상세 조회의 owner/public 계약
- `list_restaurants(list_id, restaurant_id)` DB unique 제약 추가 여부
- 점수 수정 API path variable 이름 정리

## 6. 후속 수정 후보
- 입력 규칙 중 일부를 DB 제약으로 승격할지 검토
