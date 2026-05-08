# 식당 상세/검색 연동 진행 상황

작성일: 2026-05-05

## 목표

식당 상세 페이지 홈/메뉴 탭에서 필요한 사진, 주소, 영업시간, 전화번호, 부가정보, 메뉴 정보를 서버 응답으로 바로 제공한다.

검색 결과는 맛집/유저/지역 탭으로 나눠 사용할 수 있는지 확인하고, 식당 결과에 상위 카테고리를 포함한다.

## 완료한 작업

- restaurants에 상세 표시용 컬럼을 추가했다.
  - `primary_category_name`
  - `phone_number`
  - `opening_hours`
  - `business_hours_summary`
- 식당 상세 응답 DTO를 추가했다.
  - 홈 영역: 사진, 주소, 전화번호, 영업시간, 부가정보 태그
  - 메뉴 영역: 메뉴명, 설명, 가격
- 현재 사진 데이터는 `restaurants.image_url`을 `photos` 배열에 넣어 반환한다.
- 향후 리뷰 사진 등 다른 이미지 소스를 `photos` 배열에 추가할 수 있도록 응답 구조를 분리했다.
- 부가정보는 활성 restaurant tag를 반환하도록 연결했다.
- 네이버 공식 지역 검색 API로 카테고리/전화번호 보강을 시도하는 클라이언트를 추가했다.
- 원본 카테고리에서 한식/양식/중식/일식 등 상위 카테고리를 계산하는 resolver를 추가했다.
- 일반 검색 식당 결과에 `primaryCategoryName`을 추가했다.
- 검색 응답 구조가 이미 `restaurants`, `users`, `regions`로 분리되어 있어 맛집/유저/지역 탭 구성이 가능함을 확인했다.
- 시드 생성 스크립트에서 전화번호, 영업시간, 상위 카테고리를 내보내도록 보강했다.
- 태그 추출 규칙에 메뉴 태그와 편의정보 태그를 추가했다.

## 도메인 규칙 확인

- 사용자 직접 식당 입력 API는 추가하지 않았다.
- 리스트 식당 추가는 검색 결과 선택 흐름을 유지했다.
- 리스트 지역과 식당 지역 exact match 규칙은 변경하지 않았다.
- 리스트 내부 정렬과 메인 랭킹은 섞지 않았다.
- 공개/비공개 값을 검색/랭킹/추천 계산 필터로 새로 사용하지 않았다.
- 수동 순위, display rank, drag sort, 순번 저장 컬럼은 추가하지 않았다.

## 검증

- 백엔드: `.\gradlew.bat test`
- 네이버 시드: `npm run test:tags`

## DB 적용

- 적용 일시: 2026-05-05
- 적용 대상: 로컬 Docker `postgres-dev` 컨테이너의 `dev_db`
- 적용 SQL: `Capstone/seed-data/restaurant-detail-fields-migration-20260505.sql`
- 적용 결과:
  - `primary_category_name`
  - `phone_number`
  - `opening_hours`
  - `business_hours_summary`
- 백필 결과:
  - restaurants 전체 622건
  - `primary_category_name` 622건 채움
  - 전화번호/영업시간은 현재 기존 데이터에 원본 값이 없어 0건
- 확인한 상위 카테고리 분포:
  - 한식 165건
  - 카페/디저트 121건
  - 기타 76건
  - 양식 52건
  - 분식 50건
  - 일식 48건
  - 중식 45건
  - 술집 36건
  - 치킨 29건

## 영업시간 DB 보강 정정

- 적용 일시: 2026-05-05
- 원본 데이터: `Naver_seed/output/pcmap-area-seed-result.json`
- 매칭 기준: `restaurants.pcmap_place_id`
- 적용 대상: 로컬 Docker `postgres-dev` 컨테이너의 `dev_db`
- 1차 적용 결과:
  - restaurants 전체 622건
  - `opening_hours` 566건 채움
  - `business_hours_summary` 566건 채움
  - `phone_number` 554건 채움
- 정정 사유:
  - PC Map 산출물의 `newBusinessHours`는 고정 영업시간이 아니라 현재 시점의 영업 상태다.
  - `영업 중`, `영업 전`, `휴무` 같은 값은 저장하지 않고, 고정 스케줄과 현재 시각을 비교해 클라이언트 또는 별도 서버 로직에서 도출해야 한다.
- 정정 적용:
  - `opening_hours` 566건 비움
  - `business_hours_summary` 566건 비움
  - `phone_number` 554건은 정적 정보라 유지
- 정정 후 DB 상태:
  - restaurants 전체 622건
  - `opening_hours` 0건
  - `business_hours_summary` 0건
  - `phone_number` 554건
