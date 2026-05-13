# 숨은 맛집 좌표 응답 진행 상황 - 2026-05-13

## 목표
- 프론트 지도 배치를 위해 숨은 맛집 추천 응답 아이템에 `lat`, `lng`를 포함한다.

## 검토
- 현재 전달 경로는 `HiddenGemRecommendationRepositoryImpl` -> `HiddenGemRestaurantRow` -> `HiddenGemRecommendationService` -> `HiddenGemRestaurantItemResponse`다.
- `restaurants` 테이블과 `Restaurant` 엔티티에는 이미 `lat`, `lng`가 있어 DB 마이그레이션은 필요 없다.
- 공개/비공개 계산 정책, 최소 평가 수, 정렬 점수 계산은 변경하지 않는다.

## 실행
- native query select에 `r.lat`, `r.lng`를 추가한다.
- row/response record에 `BigDecimal lat`, `BigDecimal lng`를 추가한다.
- service 응답 변환에서 row 좌표를 그대로 전달한다.
- 관련 controller/service/repository/e2e 테스트 기대값을 보강한다.
- 2026-05-13 추가: 프론트 지역 검색 응답과의 파라미터 이름 차이를 줄이기 위해 숨은 맛집 컨트롤러에서 `regionName`, `regionKeyword`, `townName` alias를 `regionTownName`과 같은 지역값으로 받도록 했다.

## 테스트
- 통과: `.\gradlew.bat test --tests "com.example.Capstone.controller.RecommendationControllerTest" --tests "com.example.Capstone.service.HiddenGemRecommendationServiceTest"`
- 통과: `.\gradlew.bat test --tests "com.example.Capstone.controller.RecommendationControllerTest"`
- 차단: `.\gradlew.bat test --tests "com.example.Capstone.repository.HiddenGemRecommendationRepositoryTest" --tests "com.example.Capstone.e2e.HiddenGemRecommendationE2ETest"`
  - 사유: `application-db.yml`이 `jdbc:postgresql://localhost:5433/dev_db`를 사용하지만 현재 PostgreSQL 연결이 실패했다.
  - 추가 확인: Docker daemon도 실행 중이 아니어서 로컬 `db-dev` 컨테이너 기동이 불가했다.
