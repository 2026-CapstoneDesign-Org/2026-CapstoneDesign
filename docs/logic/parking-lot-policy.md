# 주차장 안내 정책

## 목표

식당 상세 화면에서 사용자가 요청할 때 식당 좌표 기준 가까운 주차장을 안내한다.

주차장 데이터는 식당과 별도 도메인으로 저장한다. 식당 상세 기본 응답에 강하게 결합하지 않고, 별도 API로 조회한다.

## API

주변 주차장 거리순 조회:

```http
GET /restaurants/{restaurantId}/parking-lots?limit=10&parkingLotDivision=공영
```

주차장 CRUD:

- `GET /parking-lots`
- `GET /parking-lots/{parkingLotId}`
- `POST /parking-lots`
- `PATCH /parking-lots/{parkingLotId}`
- `DELETE /parking-lots/{parkingLotId}`

## 응답 기준

응답은 `ParkingLotResponse` 배열 또는 단건이다.

- `id`
- `parkingLotName`
- `parkingLotDivision`
- `parkingLotType`
- `roadAddress`
- `lotAddress`
- `parkingCapacity`
- `alternateNoDivision`
- `weekdayOperatingHours`
- `saturdayOperatingHours`
- `holidayOperatingHours`
- `lat`
- `lng`
- `basicParkingTime`
- `basicParkingFee`
- `additionalUnitTime`
- `additionalUnitFee`
- `phoneNumber`
- `distanceMeters`

`distanceMeters`는 식당 기준 거리 조회에서만 값이 있고, 일반 CRUD 응답에서는 `null`이다.

## 거리 조회 정책

- 기본 limit: 10
- 최대 limit: 50
- `parkingLotDivision`이 있으면 해당 구분만 조회한다.
- 좌표가 없는 주차장은 거리 계산 대상에서 제외한다.
- 정렬은 `distanceMeters` 오름차순, `parkingLotName` 오름차순, `id` 오름차순이다.
- 거리 계산은 애플리케이션 레벨 Haversine 공식을 사용한다.

예외:

- 식당이 없으면 404
- 식당 좌표가 없으면 400
- `limit < 1`이면 400

## CRUD 목록 정책

- 기본 limit: 50
- 최대 limit: 200
- `parkingLotDivision` 필터를 지원한다.
- 좌표 누락 데이터도 목록/단건/수정/삭제 대상에 포함한다.

## 주차 가능 여부 기준

현재 저장 데이터의 `parkingCapacity`는 총 주차면수다.

실시간 주차 가능 대수가 없으면 “주차가능”으로 표시하지 않는다. 화면 문구는 아래처럼 구분한다.

- 정적 데이터만 있음: `총 80면`, `현재 가능 대수 미제공`
- 실시간 데이터 있음: `현재 12면 가능`, `실시간 정보는 실제와 차이 가능`

실시간 가능 대수를 붙일 경우 정적 테이블과 분리한 `parking_lot_availability_snapshots` 같은 별도 구조를 우선 검토한다.

## 도메인 영향

이 기능은 식당 상세 부가 안내 기능이다.

- 리스트 내부 정렬에 사용하지 않는다.
- 추천, 랭킹, 신뢰도, 온도 계산에 사용하지 않는다.
- 검색과 리스트 추가 플로우를 변경하지 않는다.
- 공개/비공개 정책과 무관하다.
- 식당은 기존처럼 검색 결과 선택 기반으로 유지한다.
