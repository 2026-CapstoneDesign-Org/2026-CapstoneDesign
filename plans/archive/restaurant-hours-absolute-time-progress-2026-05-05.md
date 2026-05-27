# 영업시간 절대 시각 파싱 반영

작성일: 2026-05-05

## 목표

`영업 중`, `영업 전`, `휴무` 같은 현재 상태값을 저장하지 않고, 영업시간 관련 절대 시각 정보만 DB에 저장한다.

## 저장 기준

- 저장하지 않는 값:
  - `newBusinessHours.status`
  - `영업 중`
  - `영업 전`
  - `휴무`
- 저장하는 값:
  - `14:50에 영업 시작`
  - `21:00에 영업 종료`
  - `15:00에 브레이크타임`
  - `20:30에 라스트오더`

## 저장 형태

`restaurants.opening_hours`는 JSON 문자열로 저장한다.

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

`restaurants.business_hours_summary`는 `entries[].text`를 요약 표시한다.

## DB 반영 결과

- 적용 대상: 로컬 Docker `postgres-dev` 컨테이너의 `dev_db`
- 원본: `Naver_seed/output/pcmap-area-seed-result.json`
- 전체 식당: 622건
- `opening_hours`: 546건
- `business_hours_summary`: 546건
- `phone_number`: 554건

## 검증

- `opening_hours`에 `NewBusinessHours` 또는 `status` payload가 남지 않았다.
- `business_hours_summary`에 `영업 중`, `영업 전`, `휴무` 상태값이 남지 않았다.
- 시드 스크립트 문법 검증을 통과했다.
- `npm run test:tags`를 통과했다.

## 후속 기준

현재 영업 상태는 저장값이 아니다.

클라이언트 또는 서버가 `opening_hours.entries[].time`과 현재 시각을 비교해 `영업 중`, `영업 전`, `휴무`를 별도로 계산해야 한다.
# 최신 기준

이 문서는 이전 진행 기록이다.
최신 진행 상황은 `plans/restaurant-business-hours-raw-progress-2026-05-05.md`를 따른다.
