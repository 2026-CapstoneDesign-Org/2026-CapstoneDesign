# 식당 상세 필드 DB 기준

## 목적

식당 상세 홈/메뉴 화면에 필요한 최소 필드를 서버-클라이언트 API로 바로 내려주기 위한 DB 기준이다.

## restaurants 컬럼

- `road_address`: 상세 홈 주소 표시 우선값
- `image_url`: 리뷰/추가 사진이 없는 현재 단계의 대표 사진
- `phone_number`: 상세 홈 전화번호
- `primary_category_name`: 네이버 공식 Local Search API 또는 seed category 기준 상위 카테고리
- `business_hours_raw`: 요일별 영업시간 raw JSON 문자열

## 영업시간 기준

`business_hours_raw`에는 요일, 시작/종료, 브레이크타임, 라스트오더, 휴무 설명만 저장한다.
`영업 중`, `영업 전`, `휴무` 같은 현재 상태값은 저장하지 않는다.

## 제거된 컬럼

- `opening_hours`
- `business_hours_summary`

표시 요약은 저장 컬럼으로 두지 않고, API 응답 변환 또는 클라이언트 렌더링에서 처리한다.

## 관련 테이블

- `restaurant_menu_items`: 메뉴명, 설명, 가격
- `restaurant_tags`, `tags`: 상세 홈 부가정보 태그
