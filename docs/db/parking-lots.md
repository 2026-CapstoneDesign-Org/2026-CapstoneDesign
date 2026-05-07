# 주차장 DB 기준

## 범위

이 문서는 `ParkingLot` 저장 구조와 공공데이터 기반 seed 적재 기준을 다룬다.

대상 파일:
- `Capstone/src/main/java/com/example/Capstone/domain/ParkingLot.java`
- `Capstone/src/main/java/com/example/Capstone/repository/ParkingLotRepository.java`
- `Capstone/src/main/java/com/example/Capstone/service/ParkingLotSeedImportService.java`
- `Capstone/src/main/java/com/example/Capstone/runner/ParkingLotSeedImportRunner.java`

## 데이터 원천

현재 단계에서는 공공데이터포털 `전국주차장정보표준데이터`만 사용한다.

- 원천: https://www.data.go.kr/data/15012896/standard.do
- 수집 방식: 포털 그리드 검색을 지역별로 분할 조회
- 대상:
  - 서울특별시 전역
  - 경기도 안산시, 용인시, 김포시, 광명시, 과천시, 안성시, 화성시, 안양시, 평택시, 성남시, 부천시, 수원시, 오산시, 광주시, 하남시, 구리시, 군포시

## `parking_lots`

주요 컬럼:
- `id`
- `parking_management_number`: 공공데이터 주차장관리번호
- `parking_lot_name`: 주차장명
- `parking_lot_division`: 주차장구분
- `parking_lot_type`: 주차장유형
- `road_address`: 소재지도로명주소
- `lot_address`: 소재지지번주소
- `parking_capacity`: 주차구획수
- `alternate_no_division`: 부제시행구분
- `weekday_operating_hours`: 평일 운영시간
- `saturday_operating_hours`: 토요일 운영시간
- `holiday_operating_hours`: 공휴일 운영시간
- `lat`
- `lng`
- `basic_parking_time`: 주차기본시간
- `basic_parking_fee`: 주차기본요금
- `additional_unit_time`: 추가단위시간
- `additional_unit_fee`: 추가단위요금
- `phone_number`: 전화번호
- `created_at`
- `updated_at`

## 식별 기준

`parking_management_number`는 단독 unique 값으로 신뢰하지 않는다.

표준데이터에서 같은 주차장관리번호가 서로 다른 주차장명/주소로 반복되는 행이 존재한다. 따라서 DB unique 제약은 두지 않고, seed 재실행 시 동일 행 판정은 아래 조합으로 한다.

- 주차장관리번호
- 주차장명
- 주차장구분
- 주차장유형
- 도로명주소
- 지번주소
- 위도
- 경도

기존 개발 DB에 예전 unique constraint가 남아 있으면 아래 SQL을 1회 실행한다.

```sql
ALTER TABLE parking_lots
DROP CONSTRAINT IF EXISTS ukog9j2e8h5adrur0fugevmboxs;
```

## 적재 결과 기준

현재 수집본 기준:

- 총 적재 행: 2,528건
- 서울특별시: 889건
- 용인시: 214건
- 주차장구분:
  - 공영 2,455건
  - 민영 73건
- 좌표 누락: 51건
- 필수값 누락: 0건
- 정확 동일 행 중복: 0건
- `주차장관리번호` 중복 그룹: 168개

좌표가 없는 행은 CRUD/목록에는 남기지만, 식당 기준 거리 조회에서는 제외한다.

## 제외한 정보

현재 단계에서는 아래 정보를 저장하지 않는다.

- 실시간 주차 가능 대수
- 혼잡도
- 민간 앱 제휴 정보
- 주차장 이미지
- 지도 제공자별 장소 ID

공공데이터의 `주차구획수`는 총 주차면수이며 현재 주차 가능 대수가 아니다.

## 검증 SQL

```sql
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE lat IS NULL OR lng IS NULL) AS missing_coordinates
FROM parking_lots;

SELECT parking_lot_division, COUNT(*)
FROM parking_lots
GROUP BY parking_lot_division
ORDER BY parking_lot_division;

SELECT COUNT(*) AS yongin_count
FROM parking_lots
WHERE COALESCE(road_address, '') || ' ' || COALESCE(lot_address, '') LIKE '%용인시%';
```
