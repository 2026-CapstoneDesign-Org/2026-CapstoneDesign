# 주차장 DB 기준

## 범위

주차장 정적 정보 저장 구조와 실시간 주차 현황 처리 기준을 설명한다.

대상 코드:

- `Capstone/src/main/java/com/example/Capstone/domain/ParkingLot.java`
- `Capstone/src/main/java/com/example/Capstone/repository/ParkingLotRepository.java`
- `Capstone/src/main/java/com/example/Capstone/service/ParkingLotService.java`
- `Capstone/src/main/java/com/example/Capstone/service/ParkingLotSeedImportService.java`

## 데이터 원천

정적 주차장 정보는 seed import로 `parking_lots`에 저장한다. 현재 운영 조회는 저장된 DB 주차장을 우선 사용하고, 부족한 경우 외부 API 결과를 응답에만 fallback으로 합성한다.

실시간 주차 가능 대수는 서울시 실시간 도시데이터 `citydata` API에서 조회 시점에 가져온다. 실시간 값은 변동성이 크므로 `parking_lots`에 저장하지 않는다.

## `parking_lots`

주요 컬럼:

- `id`
- `parking_management_number`
- `parking_lot_name`
- `parking_lot_division`
- `parking_lot_type`
- `road_address`
- `lot_address`
- `parking_capacity`
- `alternate_no_division`
- `weekday_operating_hours`
- `saturday_operating_hours`
- `holiday_operating_hours`
- `lat`
- `lng`
- `basic_parking_time`
- `basic_parking_fee`
- `additional_unit_time`
- `additional_unit_fee`
- `phone_number`
- `created_at`
- `updated_at`

`parking_capacity`는 총 주차면수이며 현재 주차 가능 대수가 아니다.

## 중복 판정

`parking_management_number`는 단독 unique 값으로 신뢰하지 않는다. 같은 관리번호라도 이름, 주소, 좌표가 다른 행이 존재할 수 있다.

seed 재실행 시 동일 행 판정은 아래 조합으로 한다.

- 주차장관리번호
- 주차장명
- 주차장구분
- 주차장유형
- 도로명주소
- 지번주소
- 위도
- 경도

## 실시간 현황

서울시 실시간 도시데이터에서 `CUR_PRK_YN=Y`인 주차장만 실시간 현황 제공 대상으로 본다.

응답 합성 필드:

- `realtimeParkingAvailable`: 실시간 주차 가능 대수를 응답에 포함했는지 여부
- `currentParkingCount`: 현재 주차 가능 대수
- `currentParkingTime`: 현재 주차 가능 대수 업데이트 시각
- `realtimeSource`: `SEOUL_CITYDATA`
- `realtimeParkingCode`: 서울시 주차장 코드

실시간 값은 조회 응답에만 포함한다. 장기 저장이 필요해지면 `parking_lot_availability_snapshots` 같은 별도 테이블을 검토한다.
