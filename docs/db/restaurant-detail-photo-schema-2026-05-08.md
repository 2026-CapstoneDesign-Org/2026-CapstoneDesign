# 식당 상세 주차/사진 DB 기준

기준일: 2026-05-08

## 목표

식당 상세 화면에서 메뉴, 영업시간, 전화번호, 주차 정보, 복수 사진을 프론트에 전달할 수 있도록 현재 `restaurants` 중심 구조를 최소 확장한다.

## restaurants 변경 기준

- `phone_number`는 기존 상세 전화번호 응답으로 계속 사용한다.
- `business_hours_raw`는 기존 영업시간 파싱 입력값으로 계속 사용한다.
- `image_url`은 대표 이미지이자 사진 데이터가 없을 때의 fallback으로 유지한다.
- `conveniences`는 네이버 PC Map 편의정보 배열을 JSON text로 저장한다.
- 주차 가능 여부는 `conveniences`에 `주차`가 포함되어 있는지로 파생한다.
- 실시간 잔여 주차 가능 대수는 현재 저장하지 않는다.

## restaurant_photos 추가 기준

- 식당별 사진은 `restaurant_photos` 별도 테이블로 저장한다.
- 사진 10개를 저장하기 위해 `restaurants`에 `image_url_1` 같은 컬럼을 늘리지 않는다.
- 주요 컬럼은 `restaurant_id`, `image_url`, `source`, `display_order`다.
- `restaurant_id + image_url` 조합은 중복 저장하지 않는다.
- 상세 응답은 `display_order`, `id` 순서로 최대 10개를 조회한다.

## seed import 기준

- `restaurants-seed-preview.json`의 `conveniences` 배열을 `restaurants.conveniences`에 저장한다.
- `restaurants-seed-preview.json`의 `photo_urls` 배열을 `restaurant_photos`에 최대 10개까지 저장한다.
- `photo_urls`가 없으면 기존 `restaurants.image_url` 하나를 fallback 사진으로 사용할 수 있다.
