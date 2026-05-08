# 식당 상세 정책

## 범위

이 문서는 식당 상세 홈/메뉴 화면과 영업시간 응답 기준을 다룬다.

## 상세 홈 응답

- 사진: 현재는 `restaurants.image_url`을 `photos[0]`로 사용한다.
- 주소: `restaurants.road_address`를 우선 사용하고 없으면 지번 주소를 사용한다.
- 전화번호: `phoneNumber`
- 상위 카테고리: `primaryCategoryName`
- 부가정보: 활성 태그를 `additionalInfoTags`로 내려준다.
- 영업시간: `businessHoursDisplay`를 화면 표시 기준으로 사용한다.

## 메뉴 응답

- 메뉴명: `menuName`
- 설명: `description`
- 가격: `priceText`, `priceValue`

## 영업시간 저장 정책

영업시간은 `restaurants.business_hours_raw`에 저장한다.

저장값은 PC Map 상세 응답에서 요일별 영업시간 표시를 재구성하는 데 필요한 최소 JSON이다.

저장 대상:

- 요일
- 시작/종료 시간
- 브레이크타임
- 라스트오더
- 휴무 설명
- 정기/임시 휴무 텍스트
- 안내 문구

저장하지 않는 값:

- `영업 중`
- `영업 전`
- `영업 종료`
- `브레이크타임`
- `휴무`
- 네이버의 현재 상태 문자열

현재 상태는 조회 시점의 Asia/Seoul 요일/시간과 `business_hours_raw`를 비교해 서버에서 계산한다.

## 영업시간 응답 정책

클라이언트는 `business_hours_raw`를 직접 파싱하지 않는다.

상세 API는 서버에서 파싱한 값을 내려준다.

- `businessHours`: 요일별 구조화 데이터
- `businessHoursDisplay.statusLine`: `영업 중 · 23:00에 영업 종료` 같은 첫 줄
- `businessHoursDisplay.summaryLine`: 접힌 상태 대표 줄
- `businessHoursDisplay.rows`: 펼친 상태 요일별 줄
- `businessHoursDisplay.noticeText`: 하단 안내 문구
- `currentBusinessStatus`: 상태 코드와 계산 기준 시각

표시 예시:

- `영업 중 · 23:00에 영업 종료`
- `매일 09:00 - 23:00`
- `영업 중 · 20:50에 라스트오더`
- `화(5/5) 어린이날 11:30 - 22:00`
- `20:50 라스트오더`

## 카테고리 기준

식당의 상위 카테고리는 `primary_category_name`에 저장한다.

네이버 공식 Local Search API 결과가 있으면 우선 사용하고, 없으면 seed category 기반 resolver로 보완한다.

## 제외

- 클라이언트에서 raw JSON 직접 파싱
- 표시 요약을 DB 컬럼으로 저장
- 현재 영업 상태 문자열을 seed나 DB에 저장
- 영업시간 이력 테이블 추가
