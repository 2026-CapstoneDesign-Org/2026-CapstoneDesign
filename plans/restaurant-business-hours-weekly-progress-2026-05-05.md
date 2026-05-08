# 요일별 영업시간 수집/DB 반영 진행 상황

작성일: 2026-05-05

## 목표

PC Map 상세 응답의 요일별 영업시간을 수집해 식당 상세 API에서 사용할 수 있게 DB에 저장한다.

## 완료한 작업

- `src/pcmap.js`에서 상세 Apollo state의 `newBusinessHours({"format":"restaurant"})`를 파싱하도록 보강했다.
- `businessStatusDescription.status`는 저장하지 않고 `businessHours` 배열만 정규화한다.
- `src/seed.js`, `src/combine_seed.js`에서 요일별 스케줄 payload를 우선 저장하도록 수정했다.
- 전체 seed를 재수집했다.
- 로컬 Docker `postgres-dev`의 `dev_db`에 반영했다.

## 저장 형태

`restaurants.opening_hours`에는 아래 구조의 JSON 문자열을 저장한다.

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

## 수집 결과

- 재수집 unique candidate: 645건
- selected store: 596건
- seed preview restaurant: 596건
- 요일별 영업시간 payload 포함 preview: 549건

## DB 반영 결과

- 기존 DB restaurants 전체: 622건
- 최신 preview와 `pcmap_place_id` 매칭: 546건
- `opening_hours` 저장: 502건
- `business_hours_summary` 저장: 502건
- `phone_number` 유지/갱신 후 채워진 값: 554건
- `BusinessStatusDescription` 또는 `status` payload 잔여: 0건

## 남은 차이

최신 preview에는 요일별 영업시간 549건이 있으나, 현재 DB와 `pcmap_place_id`가 매칭된 범위에서는 502건만 저장됐다.

나머지는 현재 로컬 DB의 식당 seed 상태와 최신 수집 결과가 완전히 같지 않아서 생긴 차이다. 전체 seed import를 다시 수행하면 더 많이 맞출 수 있지만, 이번 작업에서는 기존 식당 구조를 유지하고 영업시간 필드만 갱신했다.

## 검증

- `node -c src/pcmap.js`
- `node -c src/seed.js`
- `node -c src/combine_seed.js`
- `npm run test:tags`
- DB에서 `opening_hours like '%"status"%'` 결과 0건 확인
# 최신 기준

이 문서는 이전 진행 기록이다.
최신 진행 상황은 `plans/restaurant-business-hours-raw-progress-2026-05-05.md`를 따른다.
