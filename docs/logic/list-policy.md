# list-policy.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 리스트 생성, 수정, 삭제, 대표 리스트 처리, 리스트-식당 연결 규칙의 현재 구현을 정리한다.

대상 코드:
- `UserListController`
- `UserListService`
- `CreateListRequest`
- `AddRestaurantRequest`
- `AddExternalRestaurantRequest`
- `UpdateListRequest`
- `UpdateScoreRequest`

## 2. 현재 구현된 기능
- 리스트 생성
- 내 리스트 목록 조회
- 리스트 상세 조회
- 리스트 수정
- 리스트 삭제
- 대표 리스트 지정
- 리스트 공개/비공개 전환
- 리스트에 식당 추가
- 외부 검색 결과 식당을 리스트에 추가
- 리스트에서 식당 제거
- 리스트 안 식당 점수 수정

## 3. 현재 백엔드에서 강제되는 규칙
### 3-1. 리스트 생성
- `title`, `regionName`, `restaurants`는 필수다.
- 초기 식당 목록은 최소 5개여야 한다.
- 초기 식당 목록 안의 `restaurantId`는 중복될 수 없다.
- 초기 식당들은 모두 리스트 지역과 exact match여야 한다.
- 초기 식당은 `isDeleted = false`, `isHidden = false` 식당만 허용한다.

### 3-2. 리스트 정보 수정
- 현재 `updateList()`는 제목과 설명만 수정한다.
- 수정 API는 식당 구성이나 지역을 바꾸지 않는다.

### 3-3. 식당 추가
- 리스트 owner만 수정할 수 있다.
- 식당은 존재해야 하며 `isDeleted = false`, `isHidden = false`여야 한다.
- 리스트와 식당 지역은 exact match여야 한다.
- 동일 리스트 내 동일 식당 중복 추가는 금지된다.

### 3-4. 외부 검색 결과 식당 추가
- 엔드포인트: `POST /lists/{id}/restaurants/external-fallback`
- 리스트 owner만 수정할 수 있다.
- 요청은 `searchQuery`, `externalPlaceId`, `tasteScore`, `valueScore`, `moodScore`를 받는다.
- 서비스는 `searchQuery`로 Pcmap 후보를 다시 조회하고, 그중 `externalPlaceId`가 같은 후보가 있을 때만 진행한다.
- 외부 후보의 주소 / 도로명 주소 / 전체 주소를 합친 문자열에 리스트 지역 토큰이 모두 포함되어야 한다.
- `pcmapPlaceId`가 같은 기존 식당이 있으면 그 식당을 재사용한다.
- 기존 식당을 재사용할 때는 `isDeleted = false`, `isHidden = false`여야 하며, 기존 식당의 `regionName`은 리스트 지역과 exact match여야 한다.
- 기존 식당이 없으면 외부 후보 정보로 `Restaurant`를 생성하고, `regionName`은 대상 리스트의 지역으로 저장한다.
- 식당을 확정한 뒤 동일 리스트 내 동일 식당 중복 여부를 검사한다.
- 점수 입력 범위와 `autoScore` 계산은 일반 식당 추가와 동일하다.

관련 테스트:
- `UserListServiceExternalFallbackTest`

### 3-5. 식당 제거
- 리스트 owner만 제거할 수 있다.
- 제거 후 리스트 식당 수가 5개 미만이 되면 실패한다.

### 3-6. 점수 수정
- 리스트 owner만 수정할 수 있다.
- 입력받은 `tasteScore`, `valueScore`, `moodScore`로 `autoScore`를 다시 계산한다.
- 현재 경로 변수 이름은 `{restaurantId}`지만 실제로는 `list_restaurants.id`를 조회한다.

### 3-7. 대표 리스트
- 대표 리스트를 새로 지정하면 같은 사용자의 기존 대표 리스트는 모두 `false`로 바꾼다.
- 대표 리스트를 비공개로 전환하려고 하면 실패한다.

## 4. 기능별 공개/계산 해석
- 랭킹 계산에서는 비공개 리스트도 포함된다.
- 식당 추천에서는 현재 사용자의 비공개 리스트도 프로필 계산에 포함된다.
- 리스트 추천 후보는 공개 리스트만 사용한다.
- `isHidden = true`, `isDeleted = true`는 노출과 계산 모두에서 제외 기준으로 본다.

## 5. 관련 검증
공통 기반 검증:
- 리스트 최소 5개 규칙
- 동일 리스트 내 동일 식당 중복 금지
- 리스트와 식당 지역 exact match
- `autoScore` 재계산

작업별 검증:
- 외부 fallback 식당 후보 재확인과 지역 토큰 검증

자세한 공통 검증 규칙은 `docs/logic/validation-rules.md`를 본다.

## 6. 추가 확인 필요
- 리스트 상세 조회에서 owner/public 체크를 어디까지 강제할지
- 대표 리스트 삭제 시 후속 승격 정책을 둘지
- 점수 수정 API path variable 이름을 실제 의미와 맞출지
- 외부 fallback으로 생성된 식당의 후속 정제 / seed 편입 정책을 둘지

## 7. 후속 수정 후보
- 리스트 상세 공개 정책 확정
- `list_restaurants(list_id, restaurant_id)` DB unique 제약 검토
