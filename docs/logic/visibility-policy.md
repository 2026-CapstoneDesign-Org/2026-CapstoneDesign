# visibility-policy.md

기준 날짜 및 시간: 2026-04-13 18:48:26 (Asia/Seoul)

## 1. 범위
이 문서는 공개/비공개, hide, soft delete가 현재 조회와 계산에 어떻게 반영되는지 정리한다.

대상 코드:
- `User`
- `UserList`
- `Restaurant`
- `RankingService`
- `RestaurantRecommendationService`
- `HiddenGemRecommendationService`
- `ListRecommendationService`
- `RestaurantRecommendationRepositoryImpl`
- `HiddenGemRecommendationRepositoryImpl`
- `ListRecommendationRepositoryImpl`
- `RestaurantRankingRepositoryImpl`
- `AdminService`

## 2. 개념 구분
- `isPublic`
  - 리스트 노출 범위를 나타내는 필드
  - 모든 계산에서 동일한 제외 조건으로 쓰이지는 않는다
- `isHidden`
  - 운영상 비노출
  - 현재 추천/랭킹 입력에서도 제외 조건으로 사용된다
- `isDeleted`
  - soft delete
  - 현재 일반 조회와 추천/랭킹 입력에서 제외 조건으로 사용된다

## 3. 식당
- `Restaurant.isDeleted = true`
- `Restaurant.isHidden = true`

숨김 또는 삭제 식당은 일반 조회, 랭킹, 추천에서 모두 제외된다.

## 4. 사용자
- `User.isDeleted = true`
- `User.isHidden = true`

hide/delete 사용자 데이터는 랭킹과 추천 집계 입력에 참여하지 않는다.

## 5. 리스트
### 5-1. 랭킹
- `isDeleted = false`, `isHidden = false`만 요구한다.
- `isPublic = false`는 제외 조건이 아니다.

### 5-2. 식당 추천
- 현재 사용자의 프로필 입력은 활성 리스트 전체를 사용한다.
- `isPublic`은 보지 않고 `isDeleted = false`, `isHidden = false`만 확인한다.

### 5-3. 리스트 추천
- 현재 사용자의 프로필 입력은 활성 리스트 전체를 사용한다.
- 후보 리스트는 `isPublic = true`, `isDeleted = false`, `isHidden = false`만 사용한다.

### 5-4. 숨은 맛집 추천
- `isDeleted = false`, `isHidden = false` 리스트만 계산에 포함한다.
- `isPublic = false`는 제외 조건이 아니다.

### 5-5. 리스트 상세 조회
- 현재 `GET /lists/{id}`는 owner/public 검증이 문서 기준으로 확정되지 않았다.

## 6. 추가 확인 필요
- 리스트 상세 조회 공개 계약
- follow 기능에서 hidden 사용자 취급
- 랭킹 API 공개 여부

## 7. 후속 수정 후보
- API별 노출 정책 문서 세분화
