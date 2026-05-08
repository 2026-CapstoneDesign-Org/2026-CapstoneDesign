# 식당 상세/검색 다음 작업 메모

작성일: 2026-05-05

## 바로 이어서 할 작업

- 운영/배포 DB가 따로 있으면 `seed-data/restaurant-detail-fields-migration-20260505.sql`을 해당 DB에도 적용한다.
- 운영/개발 환경에 네이버 공식 지역 검색 API 키를 설정한다.
  - `NAVER_SEARCH_CLIENT_ID`
  - `NAVER_SEARCH_CLIENT_SECRET`
- 기존 seed를 다시 생성하거나 보강해 `primary_category_name`, `phone_number`를 채운다.
- 고정 영업시간 스케줄 원본이 확보되면 `opening_hours`, `business_hours_summary`를 채운다.
- 클라이언트 식당 상세 페이지에서 `GET /restaurants/{restaurantId}` 응답을 홈/메뉴 탭에 연결한다.
- 검색 화면은 통합 검색 응답의 `restaurants`, `users`, `regions` 배열을 각각 맛집/유저/지역 탭에 연결한다.

## 남은 리스크

- 네이버 공식 지역 검색 API는 영업시간, 메뉴, 상세 사진을 제공하지 않는다.
- 현재 로컬 DB 기준 PC Map 산출물에는 고정 영업시간 스케줄이 없어 `opening_hours`와 `business_hours_summary`가 모두 비어 있다.
- 현재 로컬 DB 기준 68개 식당은 PC Map 상세 산출물에 전화번호 원본이 없어 `phone_number`가 비어 있다.
- 외부 fallback으로 새로 생성된 식당은 메뉴/태그/영업시간이 비어 있을 수 있다.
- `opening_hours`는 외부 상세 데이터 형태가 일정하지 않아 JSON 문자열로 저장될 수 있다.
- 현재 영업 상태는 저장하지 않고, 고정 영업시간 스케줄과 현재 시각을 비교해 클라이언트 또는 별도 서버 로직에서 계산해야 한다.
- 상위 카테고리 resolver는 키워드 기반이므로 일부 업장은 `기타`로 분류될 수 있다.

## 이후 보강 후보

- 리뷰/사진 테이블이 확정되면 상세 응답의 `photos` 배열에 리뷰 사진을 합친다.
- 외부 fallback 식당의 메뉴/태그/영업시간 보강 배치 작업을 별도 기능으로 분리한다.
- 카테고리 resolver의 오분류 사례를 수집해 규칙을 좁은 범위에서 보완한다.
