# 식당 영업시간 raw 다음 작업

## 남은 확인

- 현재 로컬 DB에 없는 seed preview 식당 50건을 새 식당으로 가져올지 별도 결정이 필요하다.
- 상세 페이지 UI에서 `businessHours.days`를 요일별로 표시하고 `currentBusinessStatus`는 배지 형태로 표시한다.
- `business_hours_raw`가 없는 식당은 영업시간 미확인 상태로 처리한다.

## 주의

- `business_hours_raw`에는 현재 상태 문자열을 저장하지 않는다.
- `opening_hours`, `business_hours_summary` 컬럼을 다시 추가하지 않는다.
- 표시 요약은 DB 컬럼으로 저장하지 않고 응답 변환 또는 클라이언트 렌더링에서 만든다.
