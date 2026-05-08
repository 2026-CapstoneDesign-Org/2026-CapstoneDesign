# 식당 상세 API 연동 다음 작업

기준 날짜: 2026-05-08

## 다음 확인 작업

- 프론트 상세페이지가 사용할 필드를 `RestaurantDetailResponse` 기준으로 매핑한다.
- 현재 운영 DB 또는 로컬 DB가 어떤 seed 파일로 적재되었는지 확인한다.
- 전화번호/영업시간이 필요한 화면이면 `Naver_pcmap_api/Naver_seed/output` 산출물을 백엔드 seed-data 또는 DB update SQL로 반영할지 결정한다.
- 외부 fallback으로 생성된 식당은 메뉴/태그/영업시간이 비어 있을 수 있으므로 상세 화면에서 빈 상태 UI를 준비한다.
- 주차장 영역은 `GET /restaurants/{restaurantId}/parking-lots`를 별도로 호출하도록 분리한다.
- 리뷰 영역은 `GET /restaurants/{id}/reviews`를 별도로 호출하되, 서비스 핵심이 리뷰 텍스트가 아니라 개인 리스트 기반 평가라는 점을 유지한다.

## 연동 우선순위

1. `GET /restaurants/{id}`로 상세 홈/메뉴 기본 화면 구성
2. `photos`, `imageUrl`이 비어 있는 식당의 기본 이미지 처리
3. `businessHoursDisplay.statusLine`, `summaryLine`, `rows` 기준 영업시간 UI 처리
4. `menus` 빈 배열 처리
5. `additionalInfoTags` 빈 배열 처리
6. 주차장 버튼 또는 섹션 진입 시 별도 API 호출
7. 리뷰 탭 또는 섹션 진입 시 별도 API 호출

## 잠재 리스크

- 백엔드 현재 seed-data에는 전화번호와 영업시간 raw가 비어 있어, 해당 seed를 그대로 적재한 DB에서는 상세 API가 `phoneNumber = null`, `businessHours = null`, `currentBusinessStatus.status = UNKNOWN` 형태로 내려갈 수 있다.
- `Naver_pcmap_api` 산출물과 백엔드 seed-data의 row 수가 다르므로 단순 덮어쓰기 전 데이터 차이를 검토해야 한다.
- 외부 fallback 식당은 생성 시점에 메뉴/태그 상세 데이터가 함께 생성되지 않는다.
- 실제 프론트엔드 소스는 현재 확인 범위에 없어서 UI 구현 완료 여부는 판단하지 않았다.
