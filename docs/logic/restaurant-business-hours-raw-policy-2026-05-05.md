# 식당 영업시간 처리 정책

## 저장 정책

영업시간은 `restaurants.business_hours_raw`에 저장한다.
저장값은 PC Map 상세 응답에서 요일별 영업시간 표시를 재구성하는 데 필요한 최소 JSON이다.

저장 대상은 아래 정보다.

- 요일
- 시작/종료 시간
- 브레이크타임
- 라스트오더
- 휴무 설명
- 정기/임시 휴무 텍스트
- 안내 문구

`영업 중`, `영업 전`, `영업 종료`, `브레이크타임`, `휴무` 같은 현재 상태는 저장하지 않는다.

## API 응답 정책

클라이언트는 `business_hours_raw`를 직접 파싱하지 않는다.
상세 API는 서버에서 파싱한 값을 내려준다.

- `businessHours`: 요일별 구조화 데이터
- `businessHoursDisplay`: 네이버 플레이스와 비슷하게 바로 표시할 수 있는 문구 데이터
- `currentBusinessStatus`: 조회 시점 기준 실시간 상태 계산 결과

## 표시 정책

상세 홈의 영업시간은 아래 조합으로 표시한다.

- 첫 줄: `businessHoursDisplay.statusLine`
- 접힌 줄: `businessHoursDisplay.summaryLine`
- 펼친 목록: `businessHoursDisplay.rows`
- 하단 안내: `businessHoursDisplay.noticeText`

예시:

- `영업 중 · 23:00에 영업 종료`
- `매일 09:00 - 23:00`
- `영업 중 · 20:50에 라스트오더`
- `화(5/5) 어린이날 11:30 - 22:00`
- `20:50 라스트오더`

## 제외 대상

- 클라이언트에서 raw JSON 직접 파싱
- 표시 요약을 DB 컬럼으로 저장
- 네이버의 현재 상태 문자열을 seed나 DB에 저장
