# 리뷰 저장 구조

기준 날짜: 2026-05-12 (Asia/Seoul)

## 1. 목적
이 문서는 현재 코드 기준 리뷰, 리뷰 이미지, 리뷰 투표 저장 구조를 정리한다.

## 2. 현재 코드 기준 엔티티
- `Review`
- `ReviewImage`
- `ReviewVote`

## 3. `reviews`
리뷰 본문과 노출 상태를 저장한다.

주요 필드:
- `id`
- `user_id`
- `restaurant_id`
- `content`
- `is_hidden`
- `is_deleted`
- `created_at`
- `updated_at`
- `deleted_at`

관계:
- `User` 1:N `Review`
- `Restaurant` 1:N `Review`

현재 코드 기준 삭제는 물리 삭제가 아니라 `isDeleted`, `deletedAt` 기반 soft delete로 처리한다.

## 4. `review_images`
리뷰에 첨부된 이미지 URL을 저장한다.

주요 필드:
- `id`
- `review_id`
- `image_url`
- `created_at`

관계:
- `Review` 1:N `ReviewImage`

## 5. `review_votes`
리뷰 좋아요/싫어요 상태를 저장한다.

주요 필드:
- `id`
- `user_id`
- `review_id`
- `vote_type`
- `created_at`

제약:
- `(user_id, review_id)` unique
- `vote_type`: `LIKE`, `DISLIKE`

현재 리뷰 조회 응답의 `myVoteType`은 이 테이블을 기준으로 계산한다.

## 6. 이번 작업 기준 변경 사항
- DB 스키마는 변경하지 않는다.
- 리뷰 응답에 현재 로그인 사용자의 투표 상태를 포함하기 위해 `review_votes`를 조회한다.
- `myVoteType`은 저장 필드가 아니라 응답 전용 계산 필드다.
