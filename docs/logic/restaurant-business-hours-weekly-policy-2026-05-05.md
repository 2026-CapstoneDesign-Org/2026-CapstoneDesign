# 요일별 영업시간 저장 정책

작성일: 2026-05-05

## 결정

영업시간은 원본 응답 전체를 그대로 저장하지 않고, 요일별 스케줄만 정규화해 `restaurants.opening_hours`에 JSON 문자열로 저장한다.

별도 테이블은 아직 만들지 않는다. 현재 목적은 식당 상세 표시와 현재 상태 계산 입력값 제공이므로, `restaurants`의 text 컬럼에 구조화 JSON을 저장하는 방식이 최소 수정이다.

## 저장 대상

저장하는 값:

- 요일
- 영업 시작/종료 시각
- 브레이크타임 시작/종료 시각
- 라스트오더 시각
- 요일별 휴무/특이 설명
- 익일 종료 여부
- 정기/비정기 휴무 보조 정보

저장하지 않는 값:

- `businessStatusDescription.status`
- `영업 중`
- `영업 전`
- `휴무`

위 값들은 현재 시각 기준 계산 결과이므로 저장하지 않는다.

## 저장 JSON 예시

```json
{
  "source": "pcmap_business_hours",
  "days": [
    {
      "day": "화",
      "businessHours": {
        "start": "16:00",
        "end": "24:00"
      },
      "breakHours": [],
      "lastOrderTimes": [
        {
          "type": "영업시간",
          "time": "23:00"
        }
      ],
      "description": null,
      "showEndsNextDay": false
    }
  ],
  "comingIrregularClosedDays": [],
  "comingRegularClosedDays": null,
  "freeText": null
}
```

## API 해석

- `openingHours`: 위 JSON 문자열
- `businessHoursSummary`: 요일별 스케줄을 짧게 풀어쓴 표시용 문자열
- 현재 영업 상태: 클라이언트 또는 별도 서버 로직에서 `openingHours`와 현재 시각을 비교해 계산

## 테이블 분리 기준

나중에 아래 기능이 필요하면 별도 `restaurant_business_hours` 테이블 분리를 검토한다.

- 지금 영업 중인 식당 DB 필터
- 요일/시간 조건 검색
- 영업시간 변경 이력 관리
- 배치 기반 현재 상태 캐시
# 최신 기준

이 문서는 이전 검토 기록이다.
최신 기준은 `docs/db/restaurant-business-hours-raw-schema-2026-05-05.md`와 `docs/logic/restaurant-business-hours-raw-policy-2026-05-05.md`를 따른다.
