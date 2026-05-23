# 주차장 안내 정책

## 목표

식당 상세 화면 또는 사용자 좌표 조회에서 가까운 주차장을 거리순으로 안내한다. 정적 주차장 DB를 우선 사용하고, 서울시 실시간 도시데이터 권역에서는 조회 시점의 실시간 주차 가능 대수를 보강한다.

## API

주변 주차장 거리순 조회:

```http
GET /restaurants/{restaurantId}/parking-lots?limit=10&parkingLotDivision=공영
GET /parking-lots/nearby?lat=37.57340269&lng=126.97588429&limit=10
```

주차장 CRUD:

- `GET /parking-lots`
- `GET /parking-lots/{parkingLotId}`
- `POST /parking-lots`
- `PATCH /parking-lots/{parkingLotId}`
- `DELETE /parking-lots/{parkingLotId}`

## 응답

`ParkingLotResponse`는 정적 정보와 거리, 선택적 실시간 정보를 함께 담는다.

- `id`
- `parkingLotName`
- `parkingLotDivision`
- `parkingLotType`
- `roadAddress`
- `lotAddress`
- `parkingCapacity`
- `lat`
- `lng`
- `basicParkingTime`
- `basicParkingFee`
- `additionalUnitTime`
- `additionalUnitFee`
- `phoneNumber`
- `distanceMeters`
- `realtimeParkingAvailable`
- `currentParkingCount`
- `currentParkingTime`
- `realtimeSource`
- `realtimeParkingCode`

일반 CRUD 응답에서는 `distanceMeters`와 실시간 필드가 `null`일 수 있다.

## 거리순 조회

- 기본 limit: 10
- 최대 limit: 50
- `parkingLotDivision`이 있으면 DB 주차장은 해당 구분만 조회한다.
- 좌표가 없는 주차장은 거리 계산 대상에서 제외한다.
- 정렬은 `distanceMeters`, `parkingLotName`, `id` 순이다.
- 거리 계산은 Haversine 공식을 사용한다.

예외:

- 식당이 없으면 404
- 식당 좌표가 없으면 400
- `limit < 1`이면 400

## 실시간 주차 현황

사용자가 주차장 조회를 요청하는 시점에만 서울시 실시간 도시데이터 API를 호출한다.

흐름:

1. 요청 좌표 기준 DB 주차장을 거리순으로 조회한다.
2. 요청 좌표가 지원 중인 서울 주요장소 반경 안이면 `citydata` API를 호출한다.
3. `PRK_STTS` 중 `CUR_PRK_YN=Y`인 주차장만 사용한다.
4. 이름과 주소가 기존 DB 주차장과 같으면 실시간 필드만 보강한다.
5. DB에 없는 실시간 주차장은 `id=null`, `parkingLotDivision=서울실시간` 응답으로 추가한다.
6. 전체 후보를 다시 거리순 정렬하고 limit만큼 반환한다.

실시간 API 실패는 사용자 조회 실패로 전파하지 않고, 기존 DB/fallback 결과만 반환한다.

## 기능 영향

주차장 안내는 식당 상세 부가 데이터다. 검색 정렬, 랭킹, 추천, 리스트 공개/비공개 정책에는 영향을 주지 않는다.
