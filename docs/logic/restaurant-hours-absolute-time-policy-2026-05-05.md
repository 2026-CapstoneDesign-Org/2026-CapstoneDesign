# 영업시간 절대 시각 저장 정책

작성일: 2026-05-05

## 원칙

영업 상태와 영업시간 원본을 분리한다.

- 영업시간 원본: DB 저장 대상
- 현재 영업 상태: 계산 결과

## DB 저장 대상

`opening_hours`에는 절대 시각을 포함한 이벤트만 저장한다.

예시:

- `14:50에 영업 시작`
- `21:00에 영업 종료`
- `15:00에 브레이크타임`
- `20:30에 라스트오더`

저장 JSON:

```json
{
  "source": "pcmap_absolute_time",
  "entries": [
    {
      "time": "21:00",
      "text": "21:00에 영업 종료"
    }
  ]
}
```

## DB 저장 금지

아래 값은 현재 시점 상태이므로 저장하지 않는다.

- `영업 중`
- `영업 전`
- `휴무`
- `newBusinessHours.status`

## API 해석

- `openingHours`: 절대 시각 이벤트 JSON 문자열
- `businessHoursSummary`: 절대 시각 이벤트 표시 요약
- 현재 상태가 필요하면 클라이언트 또는 서버가 현재 시각과 비교해 계산한다.
# 최신 기준

이 문서는 이전 검토 기록이다.
최신 기준은 `docs/db/restaurant-business-hours-raw-schema-2026-05-05.md`와 `docs/logic/restaurant-business-hours-raw-policy-2026-05-05.md`를 따른다.
