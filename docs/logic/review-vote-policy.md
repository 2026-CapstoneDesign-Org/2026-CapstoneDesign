# 리뷰 투표 정책

기준 날짜: 2026-05-12 (Asia/Seoul)

## 1. 목적
이 문서는 리뷰 좋아요/싫어요 투표와 리뷰 조회 응답에서 현재 로그인 사용자의 투표 상태를 제공하는 기준을 정리한다.

## 2. 현재 코드 기준 엔드포인트
- `GET /restaurants/{id}/reviews`
  - `{id}`는 조회 대상 식당 ID다.
  - 현재 로그인 사용자는 JWT access token에서 식별한다.
  - 각 리뷰 응답에 현재 로그인 사용자의 투표 상태를 포함한다.
- `GET /users/{id}/reviews`
  - `{id}`는 조회 대상 사용자 ID다.
  - 현재 로그인 사용자는 JWT access token에서 식별한다.
  - 조회 대상 사용자와 현재 로그인 사용자는 다를 수 있다.
- `POST /reviews/{id}/vote`
  - 현재 로그인 사용자가 리뷰에 `LIKE` 또는 `DISLIKE`를 등록하거나 변경한다.
- `DELETE /reviews/{id}/vote`
  - 현재 로그인 사용자의 해당 리뷰 투표를 취소한다.

## 3. 인증과 사용자 식별
- 프론트는 `Authorization: Bearer <accessToken>` 헤더로 JWT access token을 보낸다.
- 서버의 `JwtFilter`는 access token을 검증하고 JWT subject의 `userId`를 Spring Security principal로 설정한다.
- 컨트롤러는 `@AuthenticationPrincipal Long userId`로 현재 로그인 사용자를 받는다.
- 프론트는 리뷰 조회 요청에서 현재 사용자 ID를 body, query, path로 따로 보내지 않는다.

## 4. 응답 필드
리뷰 조회 응답에는 `myVoteType` 필드를 포함한다.

값의 의미:
- `LIKE`: 현재 로그인 사용자가 해당 리뷰에 좋아요를 누른 상태
- `DISLIKE`: 현재 로그인 사용자가 해당 리뷰에 싫어요를 누른 상태
- `null`: 현재 로그인 사용자가 해당 리뷰에 투표하지 않은 상태

적용 DTO:
- `ReviewResponse`
- `UserReviewResponse`

## 5. 저장 기준
- 투표 상태는 `review_votes` 테이블을 기준으로 계산한다.
- `review_votes`는 `(user_id, review_id)` unique 제약을 가진다.
- 한 사용자는 한 리뷰에 대해 하나의 투표 상태만 가질 수 있다.
- `myVoteType`은 별도 컬럼으로 저장하지 않고 조회 시 계산한다.

## 6. 이번 정책에서 변경하지 않는 범위
- 좋아요/싫어요 등록, 변경, 취소 정책
- 자기 리뷰 투표 금지 정책
- 리뷰 작성자 신뢰도 점수 계산
- 싫어요 10개 이상 자동 신고 정책
- 좋아요/싫어요 수 집계 방식
