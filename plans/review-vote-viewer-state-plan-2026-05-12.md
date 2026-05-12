# 리뷰 응답 내 현재 사용자 투표 상태 추가 계획

## 1. 배경
- 현재 리뷰 좋아요/싫어요 기능은 `ReviewVote` 기반으로 존재한다.
- 프론트에서 리뷰 목록을 받을 때 각 리뷰에 대해 현재 로그인 사용자가 이미 `LIKE` 또는 `DISLIKE`를 눌렀는지 판단할 수 있는 필드가 없다.
- 이로 인해 프론트는 리뷰 카드의 좋아요/싫어요 버튼 활성 상태를 서버 응답만으로 복원하기 어렵다.

## 2. 목표
- 리뷰 조회 응답에 현재 로그인 사용자의 투표 상태를 포함한다.
- 기존 좋아요/싫어요 등록, 변경, 취소 정책은 변경하지 않는다.
- 응답 필드는 단일 상태값으로 표현한다.
  - 권장 필드명: `myVoteType`
  - 값: `LIKE`, `DISLIKE`, `null`
  - `null` 의미: 현재 사용자가 해당 리뷰에 투표하지 않았거나, 자기 리뷰라서 투표 상태가 없음

## 3. 현재 코드 기준 확인 사항
- 리뷰 목록 API:
  - `GET /restaurants/{id}/reviews`
  - 현재 `ReviewResponse`에는 `likeCount`, `dislikeCount`만 있고 로그인 사용자의 투표 상태는 없다.
- 사용자 리뷰 목록 API:
  - `GET /users/{id}/reviews`
  - 현재 `UserReviewResponse`에도 `likeCount`, `dislikeCount`만 있다.
- 투표 API:
  - `POST /reviews/{id}/vote`
  - `DELETE /reviews/{id}/vote`
- 투표 저장 구조:
  - `ReviewVote`
  - `(user_id, review_id)` unique 제약
  - `VoteType`: `LIKE`, `DISLIKE`
- 현재 보안 설정상 리뷰 조회 API는 인증된 요청에서 사용되는 흐름으로 해석된다.

## 4. 변경 범위

### 4-1. 응답 DTO 변경
- `ReviewResponse`에 `myVoteType` 필드를 추가한다.
- `UserReviewResponse`도 프론트 재사용 가능성을 고려해 동일한 필드를 추가한다.
- 기존 정적 팩토리 메서드는 호환성을 위해 아래 중 하나로 정리한다.
  - 기존 메서드는 `myVoteType = null`로 유지
  - 새 메서드는 `myVoteType`을 인자로 받도록 추가

### 4-2. Controller 변경
- `GET /restaurants/{id}/reviews`에서 `@AuthenticationPrincipal Long userId`를 받아 서비스로 전달한다.
- `GET /users/{id}/reviews`도 현재 로그인 사용자의 관점에서 투표 상태가 필요하므로 `viewerUserId`를 서비스로 전달한다.
- 경로의 `{id}`는 리뷰 작성자 또는 식당 식별자이고, `viewerUserId`는 현재 로그인 사용자라는 점을 코드에서 혼동하지 않도록 변수명을 분리한다.

### 4-3. Service 변경
- 리뷰 목록 조회 시 리뷰 ID 목록을 만든다.
- 현재 사용자 ID와 리뷰 ID 목록으로 `ReviewVote`를 한 번에 조회한다.
- 조회 결과를 `reviewId -> VoteType` 맵으로 구성한다.
- 각 리뷰 응답 생성 시 해당 리뷰의 `myVoteType`을 포함한다.
- 기존 `likeCount`, `dislikeCount` 계산 방식은 이번 작업에서 변경하지 않는다.

### 4-4. Repository 변경
- `ReviewVoteRepository`에 배치 조회 메서드를 추가한다.
  - 예: `List<ReviewVote> findAllByUserIdAndReviewIdIn(Long userId, Collection<Long> reviewIds)`
- 리뷰가 없는 경우 빈 목록 처리를 명확히 한다.

## 5. 제외 범위
- 좋아요/싫어요 등록, 변경, 취소 정책 변경
- 리뷰 작성자 신뢰도 점수 계산식 변경
- 싫어요 10개 이상 자동 신고 정책 변경
- `likeCount` / `dislikeCount` 집계 최적화
- 비로그인 사용자의 공개 리뷰 조회 정책 변경

## 6. API 응답 예시

### 투표하지 않은 경우
```json
{
  "id": 10,
  "userId": 3,
  "nickname": "reviewer",
  "content": "맛있습니다.",
  "imageUrls": [],
  "likeCount": 4,
  "dislikeCount": 1,
  "myVoteType": null,
  "createdAt": "2026-05-12T14:30:00"
}
```

### 좋아요를 누른 경우
```json
{
  "id": 10,
  "userId": 3,
  "nickname": "reviewer",
  "content": "맛있습니다.",
  "imageUrls": [],
  "likeCount": 4,
  "dislikeCount": 1,
  "myVoteType": "LIKE",
  "createdAt": "2026-05-12T14:30:00"
}
```

### 싫어요를 누른 경우
```json
{
  "id": 10,
  "userId": 3,
  "nickname": "reviewer",
  "content": "맛있습니다.",
  "imageUrls": [],
  "likeCount": 4,
  "dislikeCount": 1,
  "myVoteType": "DISLIKE",
  "createdAt": "2026-05-12T14:30:00"
}
```

## 7. 테스트 계획

### 7-1. 서비스 단위 테스트
- 현재 사용자가 특정 리뷰에 `LIKE`를 누른 경우 `myVoteType = LIKE`가 내려오는지 확인한다.
- 현재 사용자가 특정 리뷰에 `DISLIKE`를 누른 경우 `myVoteType = DISLIKE`가 내려오는지 확인한다.
- 현재 사용자가 투표하지 않은 리뷰는 `myVoteType = null`인지 확인한다.
- 여러 리뷰가 섞여 있을 때 리뷰별 상태가 정확히 매핑되는지 확인한다.
- 자기 리뷰는 기존 정책상 투표할 수 없으므로 상태값은 `null`로 유지되는지 확인한다.

### 7-2. 전체 테스트
- `Capstone/` 기준으로 전체 테스트를 실행한다.
  - 현재 확인된 명령: `.\gradlew.bat test`
- 테스트 실행 시 Java 17 환경을 사용한다.

## 8. 문서 반영 계획
- 리뷰 좋아요/싫어요 응답 정책을 문서화한다.
- 현재 리뷰 정책 문서가 없으면 `docs/logic/review-vote-policy.md`를 새로 만들고 `LOGIC.md`에서 연결한다.
- DB 저장 구조가 문서에 누락되어 있으면 `DB.md` 또는 `docs/db/*`에 `reviews`, `review_votes` 관련 현재 코드 기준 내용을 반영한다.
- 확정되지 않은 정책은 `docs/current-gaps.md`에만 남긴다.

## 9. 위험 요소와 확인 필요 사항
- 응답 필드명이 프론트 컨벤션과 다를 수 있다.
  - 현재 계획은 `myVoteType`을 권장한다.
  - 프론트가 boolean 형태를 선호하면 `likedByMe`, `dislikedByMe`로 변경 가능하다.
- `GET /users/{id}/reviews`에서 `{id}`와 현재 로그인 사용자 ID가 다를 수 있다.
  - 이 경우 `{id}`는 조회 대상 사용자, `viewerUserId`는 투표 상태 기준 사용자로 처리한다.
- 기존 투표 변경 시 신뢰도 점수 반영 방식은 이번 작업 범위에서 변경하지 않는다.

## 10. 진행 상태
- [x] 기존 리뷰/투표 구조 확인
- [x] 변경 방향 수립
- [x] 서브에이전트 계획 검토
- [x] 사용자 구현 진행 요청
- [x] DTO 변경
- [x] Controller 변경
- [x] Service 변경
- [x] Repository 변경
- [x] 테스트 추가
- [x] 문서 반영
- [x] 전체 테스트 실행

## 11. 구현 변경 기록
- `ReviewResponse`, `UserReviewResponse`에 `myVoteType` 응답 필드를 추가했다.
- `ReviewController`의 리뷰 목록 조회 API에서 `@AuthenticationPrincipal Long userId`를 받아 서비스로 전달하도록 변경했다.
- `ReviewService`에서 조회된 리뷰 ID 목록을 기준으로 현재 로그인 사용자의 `ReviewVote`를 한 번에 조회하고 `reviewId -> VoteType`으로 매핑하도록 변경했다.
- `ReviewVoteRepository`에 현재 사용자와 리뷰 ID 목록 기준 배치 조회 메서드를 추가했다.
- `ReviewServiceTest`를 추가해 `LIKE`, `DISLIKE`, 미투표 `null`, 조회 대상 사용자와 현재 로그인 사용자 분리 동작을 검증했다.
- `docs/logic/review-vote-policy.md`, `docs/db/reviews.md`를 추가하고 `LOGIC.md`, `DB.md`에서 연결했다.

## 12. 검증 기록
- 2026-05-12: `.\gradlew.bat test --tests com.example.Capstone.service.ReviewServiceTest` 통과.
- 2026-05-12: `.\gradlew.bat test` 통과. 전체 103개 테스트, 실패 0, 에러 0, 스킵 0.
