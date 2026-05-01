# list-recommendation-policy.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 현재 구현된 리스트 추천 API의 입력 데이터, 후보 필터, 점수 계산, fallback 정책, 응답 구조를 정리한다.

대상 코드:
- `RecommendationController`
- `ListRecommendationService`
- `ListRecommendationScorer` (`com.example.Capstone.recommendation.scorer`)
- `ListRecommendationModels` (`com.example.Capstone.recommendation.model`)
- `ListRecommendationRepository`
- `ListRecommendationRepositoryImpl`

구조 메모:
- `ListRecommendationScorer`는 점수 계산 전용 컴포넌트다.
- `ListRecommendationModels`는 추천 계산 중간값을 담는 내부 모델이며, 외부 응답 DTO가 아니다.
- 카테고리 신호는 현재 `restaurants.category_name` 단일 값을 `Restaurant.getCategoryNames()`로 읽어 만든다.

## 2. 기능 범위
- 추천 대상은 다른 사용자의 공개 리스트다.
- 내 리스트는 추천 대상에서 제외한다.
- 결과는 최대 20개 리스트다.
- 유사 유저는 내부 보조 신호로만 사용한다.

## 3. 현재 코드 기준
### 엔드포인트
- `GET /recommendations/lists`

현재 보안 설정 기준:
- `/recommendations/**`는 permitAll이 아니다.
- 현재 코드는 인증된 사용자 기준 개인화 추천 API로 동작한다.

### 사용자 프로필 입력
- 현재 사용자의 전체 활성 리스트를 사용한다.
- 제외 조건:
  - `UserList.isDeleted = true`
  - `UserList.isHidden = true`
  - `Restaurant.isDeleted = true`
  - `Restaurant.isHidden = true`

### 후보 리스트 입력
- 다른 사용자의 공개 리스트만 사용한다.
- 제외 조건:
  - `User.id = currentUserId`
  - `User.isDeleted = true`
  - `User.isHidden = true`
  - `UserList.isPublic = false`
  - `UserList.isDeleted = true`
  - `UserList.isHidden = true`
  - `Restaurant.isDeleted = true`
  - `Restaurant.isHidden = true`

### 최소 품질 조건
- 후보 리스트는 visible restaurant 기준 최소 5개 이상이어야 한다.

### 추천 흐름
1. 현재 사용자 활성 리스트 row를 읽는다.
2. 사용자 프로필을 만든다.
3. `dominantRegion`을 계산한다.
4. same-region 후보 공개 리스트를 먼저 조회한다.
5. same-region 후보가 20개 미만일 때만 fallback 후보를 추가한다.
6. 최종 응답은 same-region 결과를 먼저 채우고 부족한 개수만 fallback으로 뒤에 붙인다.

## 4. 점수 계산
```text
finalScore =
  0.55 * preferenceMatchScore +
  0.20 * qualityScore +
  0.10 * collaborativeScore +
  0.15 * regionScore
```

세부:
```text
preferenceMatchScore =
  0.45 * restaurantMatchScore +
  0.35 * categoryMatchScore +
  0.20 * scoreStyleMatchScore
```

보정 규칙:
- 0 유사도 후보는 `collaborativeScore` 50%만 반영
- `preferenceMatchScore == 0`이면 최종 점수에서 `0.10` 감점
- fallback `regionScore = 0.65`
- 후보 리스트 품질 보정 신호의 SQL 비율 계산은 `double precision`으로 수행한다.

## 5. 응답 구조
루트:
- `generatedAt`
- `baseRegionName`
- `limit`
- `items`

아이템:
- `rank`
- `listId`
- `title`
- `description`
- `regionName`
- `owner`
- `categorySummary`
- `restaurantCount`
- `recommendationScore`
- `fallbackRegion`
- `scoreDetails`

## 6. 추가 확인 필요
- cold start 추천 정책
- fallback tail에서 owner 편중을 더 눌러야 하는지
- 카테고리 alias 정규화

## 7. 후속 수정 후보
- 추천 로그 기반 오프라인 튜닝
- API 품질 검증 자동화
