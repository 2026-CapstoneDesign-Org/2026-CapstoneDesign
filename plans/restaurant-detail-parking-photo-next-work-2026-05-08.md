# 식당 상세 주차/사진 다음 작업

기준일: 2026-05-08

## 다음 확인 사항

- 기본 import에 사용할 `Capstone/Capstone/seed-data/restaurants-seed-preview.json`를 새 combine 결과로 교체할지 결정한다.
- 교체 시 현재 616개 row에서 596개 row로 줄어드는 이유를 먼저 확인한다.
- 기본 seed-data를 교체하지 않을 경우, 기존 616개 row를 유지하면서 `conveniences`, `photo_urls`만 보강하는 별도 변환을 적용할 수 있다.
- 운영 DB에 이미 들어간 식당에는 seed import 재실행 또는 별도 보강 스크립트가 필요하다.

## 프론트 연동 기준

- 상세 화면은 `GET /restaurants/{id}`의 response DTO를 기준으로 연결한다.
- 메뉴는 `menus`, 영업시간은 `businessHoursDisplay` 우선, 전화번호는 `phoneNumber`를 사용한다.
- 주차 뱃지는 `parkingAvailable`을 우선 사용하고, 상세 주차장 목록이 필요하면 `nearbyParkingLots`를 사용한다.
- 사진 슬라이더는 `photos` 배열을 사용한다.
