# ranking-policy.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 전국/지역 식당 랭킹 API, 랭킹 입력 데이터 필터, 보정 점수 계산, 응답 구성을 다룬다.

대상 코드:
- `RankingController`
- `RankingService`
- `RestaurantRepository`
- `RestaurantRankingRepository`
- `RestaurantRankingRepositoryImpl`

## 2. 현재 코드 기준
### 엔드포인트
- `GET /rankings/restaurants`

요청 파라미터:
- `regionName`
- `category`
- `limit`

현재 보안 설정 기준:
- `/rankings/**`는 permitAll이 아니다.
- 현재 코드는 인증이 필요한 조회 API로 동작한다.

### 랭킹 계산 입력
기본 조인:
- `list_restaurants`
- `user_lists`
- `users`
- `restaurants`

현재 계산 제외 조건:
- `UserList.isDeleted = true`
- `UserList.isHidden = true`
- `User.isDeleted = true`
- `User.isHidden = true`
- `Restaurant.isDeleted = true`
- `Restaurant.isHidden = true`

현재 계산 포함 조건:
- `UserList.isPublic = false`도 포함

category filter:
- 현재 코드 기준 별도 `restaurant_categories` 테이블을 사용하지 않는다.
- `category` 요청 파라미터는 `restaurants.category_name` exact match로 필터링한다.

### 동일 사용자 / 동일 식당 1회 반영
- `user_id + restaurant_id` 기준으로 `max(auto_score)`만 남긴다.
- `evaluationCount`는 row 수가 아니라 축약 후 사용자 수다.

### 보정 점수
- 베이지안 평균 형태의 보정 점수를 사용한다.
- 보정 상수는 `5`
- SQL 비율 계산은 DB별 arbitrary precision 나눗셈 차이를 피하기 위해 `double precision`으로 수행한다.

### 정렬 기준
1. `adjustedScore` 내림차순
2. `evaluationCount` 내림차순
3. `averageAutoScore` 내림차순
4. `restaurantId` 오름차순

## 3. 관련 공통 검증
- autoScore 입력 해석
- hide / delete 필터
- `user_id + restaurant_id` 축약
- 보정 점수 정렬

자세한 공통 검증 규칙은 `docs/logic/validation-rules.md`를 본다.

## 4. 추가 확인 필요
- 랭킹 API를 공개 조회로 둘지
- 카테고리 exact match 유지 여부
- 데이터 증가 시 실시간 집계를 유지할지

## 5. 후속 수정 후보
- 카테고리 alias 정책
- 캐시 또는 스냅샷 읽기 모델
