# score-policy.md

기준 날짜 및 시간: 2026-04-13 18:48:26 (Asia/Seoul)

## 1. 범위
이 문서는 3종 점수와 `autoScore` 계산식을 현재 코드 기준으로 정리한다.

대상 코드:
- `ListRestaurant`
- `AddRestaurantRequest`
- `UpdateScoreRequest`
- `UserListService`

## 2. 현재 점수 구조
입력 점수:
- `tasteScore`
- `valueScore`
- `moodScore`

DTO 기준 입력 범위:
- 각 점수는 필수다.
- 각 점수는 `1.0 ~ 10.0` 범위를 사용한다.

## 3. `autoScore` 계산식
```text
autoScore = (tasteScore * 0.6 + valueScore * 0.2 + moodScore * 0.2) * 10
```

해석:
- 맛 비중 60
- 가성비 비중 20
- 분위기 비중 20
- 최종 점수는 100점 스케일로 저장

## 4. 저장과 재계산
- `autoScore`는 저장형 컬럼이다.
- 리스트에 식당을 추가할 때 계산한다.
- 점수를 수정할 때 다시 계산한다.
- 리스트 상세 / 대표 리스트 조회 응답에서는 `autoScore`가 높은 식당부터 반환한다.
- 추천과 랭킹에서는 정규화(`score / 100.0`)해서 사용한다.

## 5. 관련 공통 검증
- 생성/수정 시 동일 공식 유지
- 현재 공통 기반 테스트: `ListRestaurantAutoScoreTest`

## 6. 추가 확인 필요
- 점수 수정 API 경로 변수 이름 정리
- 화면/응답에서 `autoScore`를 어떤 스케일로 보여줄지 일관성 정리

## 7. 후속 수정 후보
- 점수 표시 정책 별도 문서화
