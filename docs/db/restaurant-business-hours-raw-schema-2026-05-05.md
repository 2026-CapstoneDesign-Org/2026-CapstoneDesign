# 식당 영업시간 DB 컬럼 기준

## 목표

식당 영업시간은 현재 영업 상태가 아니라 요일별 절대 시간 정보만 저장한다.

## 컬럼

- `restaurants.business_hours_raw`
  - 타입: `TEXT`
  - 저장값: PC Map 상세 응답에서 필요한 영업시간 필드만 덜 파싱한 JSON 문자열
  - 포함 정보: 요일, 시작/종료, 브레이크타임, 라스트오더, 휴무 설명, 정기/임시 휴무 텍스트

## 제거된 컬럼

- `restaurants.opening_hours`
- `restaurants.business_hours_summary`

위 두 컬럼은 표시용 요약과 원본 저장 역할이 섞일 수 있어 제거한다.
표시 문자열이 필요하면 `business_hours_raw`를 파싱해서 API 응답 또는 클라이언트에서 만든다.

## 상태값 기준

`영업 중`, `영업 전`, `영업 종료`, `휴무`, `브레이크타임` 같은 현재 상태는 저장하지 않는다.
상세 페이지 조회 시점의 Asia/Seoul 현재 요일/시간과 `business_hours_raw`를 비교해 서버에서 계산한다.

## 로컬 DB 반영 결과

- `business_hours_raw` 컬럼 추가 완료
- `opening_hours`, `business_hours_summary` 컬럼 삭제 완료
- 현재 상태 payload 저장 건수: 0건
- 기존 로컬 DB 매칭 기준 `business_hours_raw` 저장 건수: 502건
