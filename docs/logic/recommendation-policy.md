# recommendation-policy.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 현재 구현된 식당 추천 API의 입력 데이터, 후보 필터, 점수 계산, 튜닝 규칙, 응답 구조를 정리한다.

대상 코드:
- `RecommendationController`
- `RestaurantRecommendationService`
- `HiddenGemRecommendationService`
- `RestaurantRecommendationScorer` (`com.example.Capstone.recommendation.scorer`)
- `RestaurantRecommendationModels` / `recommendation.model.restaurant.*` (`com.example.Capstone.recommendation.model`)
- `RestaurantRecommendationRepository`
- `RestaurantRecommendationRepositoryImpl`
- `HiddenGemRecommendationRepository`
- `HiddenGemRecommendationRepositoryImpl`

구조 메모:
- `RestaurantRecommendationScorer`는 점수 계산 전용 컴포넌트다.
- `RestaurantRecommendationModels`는 추천 계산 중간값을 담는 내부 모델이며, 외부 응답 DTO가 아니다.

## 2. 기능 범위
- 개인화 식당 추천 대상은 식당이다.
- 개인화 식당 추천 결과는 상위 4개 식당만 반환한다.
- 숨은 맛집 추천 결과는 요청 동/읍/면 기준 상위 10개 식당까지 반환한다.
- 계산 방식은 실시간 계산이다.

## 3. 현재 코드 기준
### 엔드포인트
- `GET /recommendations/restaurants`
- `GET /recommendations/restaurants/hidden-gems`

현재 보안 설정 기준:
- `/recommendations/**`는 permitAll이 아니다.
- 현재 코드는 인증이 필요한 추천 API로 동작한다.
- `GET /recommendations/restaurants`는 인증된 사용자 기준 개인화 추천 API다.
- `GET /recommendations/restaurants/hidden-gems`는 요청한 `regionTownName` 기준 숨은 맛집 추천 API다.

### 개인화 추천 입력
- 현재 사용자 전체 활성 리스트를 기준으로 프로필을 계산한다.
- 입력 조인:
  - `users`
  - `user_lists`
  - `list_restaurants`
  - `restaurants`

제외 조건:
- `User.isDeleted = true`
- `User.isHidden = true`
- `UserList.isDeleted = true`
- `UserList.isHidden = true`
- `Restaurant.isDeleted = true`
- `Restaurant.isHidden = true`

포함 조건:
- `UserList.isPublic = false`도 계산 포함

### 개인화 사용자-식당 축약
- 같은 사용자가 같은 식당을 여러 리스트에 담아도 `user_id + restaurant_id` 기준 `max(autoScore)` 1회만 사용한다.

### 개인화 주요 프로필 구성
- `ownedRestaurantIds`
- `restaurantBestScore`
- `categoryPreference`
- `regionPreference`
- `topLikedRestaurants`
- `dominantRegion`

카테고리 입력:
- 현재 코드 기준 별도 `restaurant_categories` 테이블을 사용하지 않는다.
- `Restaurant.getCategoryNames()`가 `restaurants.category_name` 단일 값을 리스트로 감싸 추천 카테고리 신호를 만든다.

### 개인화 후보 선택
- same-region 후보를 먼저 조회한다.
- same-region 후보가 4개 미만일 때만 fallback 지역 후보를 추가한다.

## 4. 숨은 맛집 추천
### 엔드포인트
- `GET /recommendations/restaurants/hidden-gems`

요청 파라미터:
- `regionTownName`
  - 필수
  - 동/읍/면 단위 문자열
  - 누락, `null`, blank면 `400 BAD_REQUEST`

지역 기준:
- `Restaurant.regionTownName` exact match를 사용한다.
- `Restaurant.regionName`은 응답의 대표 지역명으로만 노출한다.
- `regionFilterNames`는 이 API의 계산 필터로 사용하지 않는다.

계산 입력:
- `list_restaurants`
- `user_lists`
- `users`
- `restaurants`

제외 조건:
- `User.isDeleted = true`
- `User.isHidden = true`
- `UserList.isDeleted = true`
- `UserList.isHidden = true`
- `Restaurant.isDeleted = true`
- `Restaurant.isHidden = true`

포함 조건:
- `UserList.isPublic = false`도 계산 포함

사용자-식당 축약:
- 같은 사용자가 같은 식당을 여러 리스트에 담아도 `user_id + restaurant_id` 기준 `max(autoScore)` 1회만 사용한다.

숨은 맛집 후보 조건:
```text
evaluationCount >= 2
evaluationCount <= popularityCutoff
```

`popularityCutoff`:
```text
popularityCutoff = min(10, max(3, ceil(regionAverageEvaluationCount)))
```

보정 품질 점수:
```text
adjustedScore = (v / (v + m)) * R + (m / (v + m)) * C
```

값 정의:
- `R`: 식당별 평균 `autoScore`
- `v`: 식당별 `evaluationCount`
- `C`: 요청 동/읍/면 내 전체 사용자-식당 축약 row 평균 `autoScore`
- `m`: 보정 상수 `3`

최종 추천 점수:
```text
hiddennessScore =
  1 - ((evaluationCount - 2) / max(1, popularityCutoff - 2))

recommendationScore =
  ((adjustedScore / 100) * 0.85 + hiddennessScore * 0.15) * 100
```

정렬 기준:
1. `recommendationScore` 내림차순
2. `adjustedScore` 내림차순
3. `evaluationCount` 오름차순
4. `averageAutoScore` 내림차순
5. `restaurantId` 오름차순

응답 루트:
- `generatedAt`
- `regionTownName`
- `limit`
- `items`

응답 아이템:
- `rank`
- `restaurantId`
- `restaurantName`
- `address`
- `regionName`
- `regionTownName`
- `recommendationScore`
- `adjustedScore`
- `averageAutoScore`
- `evaluationCount`

## 5. 개인화 식당 추천 점수 계산
```text
finalScore =
  0.45 * userPreferenceScore +
  0.20 * categoryFitScore +
  0.20 * rankingAdjustmentScore +
  0.05 * collaborativeScore +
  0.10 * regionScore
```

보정 규칙:
- 0 유사도 후보 감점 `0.25`
- `userPreferenceScore = 0`이면 `collaborativeScore` 50%만 반영
- fallback `regionScore = 0.55`
- 후보 ranking 보정 신호의 SQL 비율 계산은 `double precision`으로 수행한다.

## 6. 개인화 식당 추천 응답 구조
루트:
- `generatedAt`
- `baseRegionName`
- `limit`
- `items`

아이템:
- `rank`
- `restaurantId`
- `restaurantName`
- `regionName`
- `imageUrl`
- `categories`
- `finalScore`
- `userPreferenceScore`
- `categoryFitScore`
- `rankingAdjustmentScore`
- `collaborativeScore`
- `regionScore`
- `fallbackRegion`

## 7. 추가 확인 필요
- cold start 기본 추천 정책
- same-region 우선 체감 강화 필요 여부
- 카테고리 alias 정규화

## 8. 후속 수정 후보
- 추천 snapshot / cache 계층 검토
- end-to-end 추천 품질 검증 자동화
- 숨은 맛집 추천 품질 검증 자동화
- 숨은 맛집 `popularityCutoff`와 보정 상수 `m = 3`의 운영 데이터 기반 튜닝 검토
