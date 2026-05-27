# hidden-gem-recommendation-api-plan.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## Status
completed

## 작업명
숨은 맛집 추천 API 구현 계획

## 목적
지역을 입력받아 해당 동/읍/면 기준의 숨은 맛집 식당을 최대 10개 반환하는 GET 조회 API를 추가한다.

이번 문서는 구현 전 계획이며, 사용자 승인 전에는 코드 구현을 진행하지 않는다.

## 사용자 관점 결과
- 사용자는 동/읍/면 지역명 하나를 쿼리 파라미터로 넘겨 숨은 맛집 후보를 조회할 수 있다.
- 결과는 등록/평가 수가 상대적으로 적지만 점수가 높은 식당을 우선 보여준다.
- 삭제되었거나 운영상 숨김 처리된 데이터는 결과와 계산 입력에서 제외된다.

## 범위
- 숨은 맛집 추천 GET API 추가
- 동/읍/면 단위 지역 필터 정의
- 기존 리스트-식당 평가 데이터를 활용한 후보 집계
- 표본 수 왜곡을 줄이는 보정 점수 계산
- 인기 식당 과다 노출 방지용 평가 수 필터
- 응답 DTO 추가
- controller/service/repository 테스트 추가
- 관련 정책 문서 반영

## 비범위
- 개인화 추천 로직 변경
- 기존 `GET /recommendations/restaurants` 동작 변경
- 기존 랭킹 API 동작 변경
- 새 DB 테이블 또는 배치 스냅샷 테이블 생성
- 카테고리/지역 alias 표준화 정책 확정
- 외부 Pcmap fallback 또는 seed import 흐름 변경
- 인증 구조 변경

## 역할 분리
- 계획 포지션
  - 현재 문서, DB 구조, 기존 추천/랭킹 API 흐름을 분석한다.
  - 설계와 구현 단계를 이 문서에 기록한다.
- 검토 포지션
  - 이 문서를 기준으로 구조 변경 범위, 단순화 가능성, 정책 충돌 여부를 검토한다.
  - 문제가 있으면 구현 전에 이 문서를 먼저 수정한다.
- 구현 포지션
  - 사용자 승인 후 이 문서 기준으로만 구현한다.
  - 설계 변경이 필요하면 코드보다 이 문서를 먼저 수정한다.
- 테스트 포지션
  - 정상/경계/예외 케이스 테스트를 작성하고 실행한다.
  - 테스트 결과와 남은 이슈를 이 문서 또는 관련 정책 문서에 반영한다.

## 관련 문서/코드
문서:
- `GUIDE.md`
- `AGENTS.md`
- `DB.md`
- `LOGIC.md`
- `PLANS.md`
- `docs/db/lists.md`
- `docs/db/restaurants.md`
- `docs/logic/list-policy.md`
- `docs/logic/score-policy.md`
- `docs/logic/visibility-policy.md`
- `docs/logic/ranking-policy.md`
- `docs/logic/recommendation-policy.md`
- `docs/logic/validation-rules.md`
- `docs/logic/seed-import.md`
- `docs/current-gaps.md`

기존 코드:
- `Capstone/src/main/java/com/example/Capstone/controller/RecommendationController.java`
- `Capstone/src/main/java/com/example/Capstone/controller/RankingController.java`
- `Capstone/src/main/java/com/example/Capstone/service/RestaurantRecommendationService.java`
- `Capstone/src/main/java/com/example/Capstone/service/RankingService.java`
- `Capstone/src/main/java/com/example/Capstone/repository/RestaurantRepository.java`
- `Capstone/src/main/java/com/example/Capstone/repository/RestaurantRankingRepositoryImpl.java`
- `Capstone/src/main/java/com/example/Capstone/repository/RestaurantRecommendationRepositoryImpl.java`
- `Capstone/src/main/java/com/example/Capstone/domain/Restaurant.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ListRestaurant.java`

예상 추가 코드:
- `Capstone/src/main/java/com/example/Capstone/service/HiddenGemRecommendationService.java`
- `Capstone/src/main/java/com/example/Capstone/repository/HiddenGemRecommendationRepository.java`
- `Capstone/src/main/java/com/example/Capstone/repository/HiddenGemRecommendationRepositoryImpl.java`
- `Capstone/src/main/java/com/example/Capstone/repository/HiddenGemRestaurantRow.java`
- `Capstone/src/main/java/com/example/Capstone/dto/response/HiddenGemRestaurantResponse.java`
- `Capstone/src/main/java/com/example/Capstone/dto/response/HiddenGemRestaurantItemResponse.java`

예상 테스트:
- `Capstone/src/test/java/com/example/Capstone/controller/RecommendationControllerTest.java`
- `Capstone/src/test/java/com/example/Capstone/service/HiddenGemRecommendationServiceTest.java`
- `Capstone/src/test/java/com/example/Capstone/repository/HiddenGemRecommendationRepositoryTest.java`

## 현재 구조 분석
### 기존 API 흐름
- `RecommendationController`는 `/recommendations` 하위에 추천 조회 API를 둔다.
- `GET /recommendations/restaurants`는 인증 사용자 기준 개인화 식당 추천이다.
- `RankingController`는 `GET /rankings/restaurants`에서 `regionName`, `category`, `limit` 쿼리 파라미터를 받아 서비스에 위임한다.
- 현재 보안 설정상 `/recommendations/**`, `/rankings/**`는 `permitAll`이 아니므로 인증이 필요한 API로 동작한다.

### 기존 집계 방식
- 랭킹과 추천 집계는 `list_restaurants`, `user_lists`, `users`, `restaurants`를 조인한다.
- `UserList.isDeleted/isHidden`, `User.isDeleted/isHidden`, `Restaurant.isDeleted/isHidden`은 계산 제외 조건이다.
- `UserList.isPublic = false`는 랭킹과 식당 추천 계산에서 제외 조건이 아니다.
- 동일 사용자와 동일 식당의 여러 평가 입력은 `user_id + restaurant_id` 기준 `max(auto_score)` 1회로 축약한다.
- `autoScore`는 100점 스케일이며 공식은 `(tasteScore * 0.6 + valueScore * 0.2 + moodScore * 0.2) * 10`이다.

### 지역 저장 구조
- `Restaurant.regionName`은 현재 seed 기준 `용인시 처인구`처럼 시/구 또는 군 단위 대표 지역이다.
- `Restaurant.regionTownName`은 `삼가동`, `김량장동`, `이동읍`처럼 동/읍/면 단위 값을 저장한다.
- `Restaurant.regionFilterNames`는 검색 보조용 JSON text 목록이다.

## 사전 확인 사항
- 이번 API의 지역 기준은 `Restaurant.regionTownName` exact match로 정의한다.
- `regionFilterNames`는 검색 보조 신호로 유지하고, 숨은 맛집 API의 계산 필터로 사용하지 않는다.
- 외부 fallback으로 생성된 식당은 현재 `regionTownName`을 채우지 않으므로, seed 또는 관리자 정제로 동/읍/면 값이 들어간 식당만 이 API 후보가 된다.
- 입력 지역이 비어 있으면 `400 BAD_REQUEST`로 처리한다.
- 입력 지역이 존재하지 않거나 해당 지역에 후보가 없으면 `200 OK`와 빈 `items`를 반환한다.
- 인증 공개 여부는 이번 범위에서 바꾸지 않는다. 현재 `SecurityConfig` 기준으로 인증 필요한 조회 API로 둔다.

## API 스펙
### 엔드포인트
```text
GET /recommendations/restaurants/hidden-gems?regionTownName={dongEupMyeon}
```

선정 이유:
- 기존 추천 API 네임스페이스인 `/recommendations`를 유지한다.
- 기존 개인화 식당 추천 `/recommendations/restaurants`와 충돌하지 않는다.
- 숨은 맛집은 식당 추천의 하위 성격이므로 `/restaurants/hidden-gems`로 둔다.

### 요청 파라미터
- `regionTownName`
  - 필수
  - 동/읍/면 단위 문자열
  - 앞뒤 공백 제거 후 사용
  - 누락, `null`, blank면 `400 BAD_REQUEST`
  - 구현 시 Controller에서는 `@RequestParam(required = false)`로 받고, Service에서 `BusinessException(400)`으로 검증한다.
  - 이유: 현재 `GlobalExceptionHandler`는 `MissingServletRequestParameterException`을 별도 처리하지 않으므로, 누락/blank 입력의 에러 응답 형식을 서비스 검증으로 통일한다.

### 응답 구조
```json
{
  "generatedAt": "2026-05-01T21:30:00",
  "regionTownName": "김량장동",
  "limit": 10,
  "items": [
    {
      "rank": 1,
      "restaurantId": 101,
      "restaurantName": "식당명",
      "address": "경기 용인시 처인구 ...",
      "regionName": "용인시 처인구",
      "regionTownName": "김량장동",
      "recommendationScore": 92.15,
      "adjustedScore": 91.20,
      "averageAutoScore": 96.00,
      "evaluationCount": 2
    }
  ]
}
```

필수 응답 의미:
- `restaurantId`: 식당 ID
- `restaurantName`: 식당 이름
- `address`: `roadAddress`가 있으면 도로명 주소, 없으면 일반 주소
- `regionName`: 현재 저장된 대표 지역명
- `regionTownName`: 이번 API의 동/읍/면 기준 지역
- `recommendationScore`: 숨은 맛집 정렬용 최종 점수
- `adjustedScore`: 표본 수 보정 후 품질 점수
- `averageAutoScore`: 사용자-식당 축약 후 평균 `autoScore`
- `evaluationCount`: 사용자-식당 축약 후 평가 사용자 수

## 데이터 흐름
1. Controller가 `regionTownName`을 `@RequestParam(required = false)` 쿼리 파라미터로 받는다.
2. Service가 `regionTownName`을 trim하고 blank 여부를 검증한다.
3. Repository가 해당 `regionTownName`의 활성 평가 데이터를 집계한다.
4. Repository는 동일 사용자-동일 식당을 최고 `autoScore` 1회로 축약한다.
5. Service가 해당 동/읍/면 내 평가 수 분포를 계산해 인기 식당 제외 기준을 정한다.
6. Service가 표본 수 보정 점수와 숨은 맛집 점수를 계산한다.
7. Service가 점수 기준으로 정렬 후 최대 10개만 응답 DTO로 조립한다.

## 필터링 방식
### 기본 입력 집합
```sql
list_restaurants lr
join user_lists ul on ul.id = lr.list_id
join users u on u.id = ul.user_id
join restaurants r on r.id = lr.restaurant_id
```

### 계산 제외 조건
- `ul.is_deleted = false`
- `ul.is_hidden = false`
- `u.is_deleted = false`
- `u.is_hidden = false`
- `r.is_deleted = false`
- `r.is_hidden = false`
- `r.region_town_name = :regionTownName`

### 계산 포함 조건
- `ul.is_public = false`도 기존 랭킹/식당 추천과 동일하게 계산에 포함한다.
- 리스트나 사용자 정보는 응답에 노출하지 않는다.

### 평가 수 정의
```text
evaluationCount = count(user_restaurant_best rows by restaurant)
```

동일 사용자가 같은 식당을 여러 리스트에 담은 경우:
```sql
group by user_id, restaurant_id
max(auto_score) as user_best_auto_score
```

## 점수 기준
### 1. 품질 점수 보정
단순 평균 왜곡을 줄이기 위해 베이지안 평균 형태의 보정 점수를 사용한다.

```text
adjustedScore = (v / (v + m)) * R + (m / (v + m)) * C
```

값 정의:
- `R`: 식당별 평균 `autoScore`
- `v`: 식당별 `evaluationCount`
- `C`: 요청 지역 내 전체 사용자-식당 축약 row 평균 `autoScore`
- `m`: 보정 상수, 1차 기본값 `3`

판단:
- 기존 랭킹 API의 `m = 5`보다 낮게 둔다.
- 숨은 맛집은 소수 평가 식당을 후보로 삼으므로, 지나치게 평균으로 수렴시키지 않되 1개 표본의 과대평가는 줄인다.

### 2. 인기 식당 제외 기준
아래 조건을 모두 만족하는 식당만 숨은 맛집 후보로 본다.

```text
evaluationCount >= 2
evaluationCount <= popularityCutoff
```

`popularityCutoff`:
```text
popularityCutoff = min(10, max(3, ceil(regionAverageEvaluationCount)))
```

판단:
- 평가 수 1개만 있는 식당은 우연한 고득점 왜곡이 커서 제외한다.
- 지역 평균 평가 수 이하를 "상대적으로 적은 등록 수"의 1차 기준으로 둔다.
- 절대 상한 10을 둬 인기 식당이 결과에 과도하게 포함되는 것을 막는다.
- 후보가 10개 미만이어도 결과를 억지로 채우기 위해 인기 식당을 추가하지 않는다.

### 3. 숨은 맛집 최종 점수
품질 점수를 주 기준으로 두고, 평가 수가 cutoff에 가까울수록 숨은 정도를 낮게 반영한다.

```text
hiddennessScore =
  1 - ((evaluationCount - 2) / max(1, popularityCutoff - 2))

recommendationScore =
  ((adjustedScore / 100) * 0.85 + hiddennessScore * 0.15) * 100
```

정렬 기준:
1. `recommendationScore` 내림차순
2. `adjustedScore` 내림차순
3. `evaluationCount` 오름차순
4. `averageAutoScore` 내림차순
5. `restaurantId` 오름차순

응답 반올림:
- `recommendationScore`, `adjustedScore`, `averageAutoScore`는 소수점 둘째 자리까지 반올림한다.

## 구현 단계
1. `RecommendationController`에 `GET /recommendations/restaurants/hidden-gems` 메서드를 추가한다.
   - `regionTownName`은 `@RequestParam(required = false)`로 받아 누락 입력도 서비스 검증으로 보낸다.
2. `HiddenGemRecommendationService`를 추가한다.
   - `regionTownName` 누락/null/blank 입력 검증
   - 평가 수 cutoff 계산
   - 점수 계산
   - rank 부여와 응답 DTO 조립
3. `HiddenGemRecommendationRepository`와 구현체를 추가한다.
   - native SQL 기반으로 기존 랭킹/추천 repository 패턴을 따른다.
   - `region_town_name` exact match를 사용한다.
   - hide/delete 필터와 사용자-식당 축약 규칙을 적용한다.
4. DTO를 추가한다.
   - `HiddenGemRestaurantResponse`
   - `HiddenGemRestaurantItemResponse`
5. 테스트를 추가한다.
   - controller binding/응답 구조/blank region 예외
   - service cutoff/정렬/limit
   - repository 지역 필터, hide/delete 제외, 사용자-식당 축약
6. 문서를 갱신한다.
   - `docs/logic/recommendation-policy.md`에 숨은 맛집 API 정책 추가
   - 작업별 검증 결과는 이 plan 문서에 우선 반영한다.
   - `docs/logic/validation-rules.md`는 공통 기반 검증만 유지하며, 숨은 맛집 전용 검증은 새 공통 규칙으로 승격할 때만 반영한다.
   - 미확정 정책이 생기면 `docs/current-gaps.md`에 unresolved only로 기록
7. 구현과 테스트에서 추가 문제가 없으면 수정 기록을 정리한다.
   - 이 plan 문서의 `결정 사항 / 변경 로그`에 구현 완료 기록을 남긴다.
   - `docs/logic/recommendation-policy.md`에 최종 API 정책과 계산 기준을 반영한다.
   - 남은 이슈가 없으면 `docs/current-gaps.md`에는 새 항목을 추가하지 않는다.
   - 남은 이슈가 있으면 확정 정책과 섞지 않고 `docs/current-gaps.md`에 unresolved 항목으로만 남긴다.

## 검증 방법
실행 위치:
```text
Capstone/
```

공통 기반 검증:
```powershell
.\gradlew.bat test --tests "com.example.Capstone.domain.ListRestaurantAutoScoreTest"
```

작업별 테스트:
```powershell
.\gradlew.bat test --tests "com.example.Capstone.controller.RecommendationControllerTest" --tests "com.example.Capstone.service.HiddenGemRecommendationServiceTest" --tests "com.example.Capstone.repository.HiddenGemRecommendationRepositoryTest"
```

최종 회귀 검증:
```powershell
.\gradlew.bat test
```

검증 케이스:
- 지역 기준 필터링이 `regionTownName` exact match로 동작한다.
- 등록/평가 수는 적지만 점수가 높은 식당이 우선 노출된다.
- 평가 수가 1개인 식당은 고득점이어도 제외된다.
- 지역 평균 평가 수와 절대 상한을 초과하는 인기 식당은 제외된다.
- 결과는 최대 10개로 제한된다.
- 숨김/삭제된 사용자, 리스트, 식당 데이터는 계산에서 제외된다.
- 같은 사용자가 같은 식당을 여러 리스트에 담아도 최고 점수 1회만 반영된다.
- `regionTownName` blank 요청은 `400 BAD_REQUEST`가 된다.
- `regionTownName` 누락 요청은 `400 BAD_REQUEST`가 된다.
- 후보가 없는 지역은 `200 OK`와 빈 `items`를 반환한다.

## 검증 결과
2026-05-01 Java 17 환경:
```powershell
$env:JAVA_HOME='C:\Users\wowjd\.jdks\ms-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests "com.example.Capstone.controller.RecommendationControllerTest" --tests "com.example.Capstone.service.HiddenGemRecommendationServiceTest" --tests "com.example.Capstone.repository.HiddenGemRecommendationRepositoryTest"
.\gradlew.bat test --tests "com.example.Capstone.e2e.HiddenGemRecommendationE2ETest" --rerun-tasks
.\gradlew.bat test --tests "com.example.Capstone.domain.ListRestaurantAutoScoreTest"
.\gradlew.bat test
```

결과:
- 숨은 맛집 대상 테스트 통과
- 랜덤 포트 Spring Boot 서버 + JWT 인증 HTTP E2E 테스트 통과
- `ListRestaurantAutoScoreTest` 통과
- 전체 테스트 통과

참고:
- 기본 `java`는 Java 11이라 Spring Boot Gradle plugin 요구사항을 만족하지 못했다.
- 검증은 `C:\Users\wowjd\.jdks\ms-17`을 `JAVA_HOME`으로 지정해 수행했다.
- 2026-05-02 추가 최종 검증에서 `.\gradlew.bat test --rerun-tasks` 전체 테스트를 강제 재실행해 통과했다.

## 리스크
- `regionTownName`이 없는 기존/외부 fallback 식당은 후보에서 빠진다.
- `popularityCutoff`의 기본값은 1차 MVP 기준이며 실제 데이터 분포를 본 뒤 조정될 수 있다.
- 지역 단위 입력 alias나 주소 기반 역매칭은 이번 범위에서 다루지 않는다.
- 현재 보안 설정상 인증 필요한 API가 되며, 공개 조회로 바꾸려면 별도 정책 결정과 `SecurityConfig` 변경이 필요하다.
- native SQL 집계는 기존 패턴과 맞지만, DB별 함수 차이를 피하려면 계산 일부를 Java service에서 유지하는 편이 안전하다.

## Progress
- [x] 계획 포지션: 구조 분석 완료
- [x] 계획 포지션: plan 문서 작성 완료
- [x] 검토 포지션: plan 검토 완료
- [x] 사용자 검토 / 승인 완료
- [x] 구현 시작
- [x] 테스트 작성 완료
- [x] 테스트 실행 완료
- [x] 문서 반영 완료
- [x] 수정 기록 정리 완료

## 결정 사항 / 변경 로그
- 2026-05-01: 숨은 맛집 API는 기존 추천 네임스페이스 아래 `GET /recommendations/restaurants/hidden-gems`로 제안한다.
- 2026-05-01: 동/읍/면 기준은 현재 코드의 `Restaurant.regionTownName` exact match로 정의하고, 요청 파라미터도 `regionTownName`으로 둔다.
- 2026-05-01: 동일 사용자-동일 식당 평가는 기존 랭킹/추천과 동일하게 최고 `autoScore` 1회만 반영한다.
- 2026-05-01: 단순 평균 대신 베이지안 보정 점수와 평가 수 기반 hiddenness 점수를 함께 사용한다.
- 2026-05-01: 검토 포지션 지적에 따라 `regionTownName` 누락 입력도 서비스 검증으로 `400 BAD_REQUEST` 처리하도록 명시했다.
- 2026-05-01: 검토 포지션 지적에 따라 테스트 명령 실행 위치를 `Capstone/`으로 명시했다.
- 2026-05-01: 구현과 테스트에서 추가 문제가 없을 경우 수정 기록을 plan과 관련 정책 문서에 정리하도록 마무리 기준을 추가했다.
- 2026-05-01: 사용자 후속 진행 요청에 따라 구현을 시작한다.
- 2026-05-01: `GET /recommendations/restaurants/hidden-gems` API, 전용 service/repository/DTO, controller/service/repository 테스트를 추가했다.
- 2026-05-01: 숨은 맛집 정책을 `docs/logic/recommendation-policy.md`, `docs/db/restaurants.md`, `docs/logic/visibility-policy.md`에 반영했다.
- 2026-05-01: Java 17 환경에서 대상 테스트, 공통 `ListRestaurantAutoScoreTest`, 전체 테스트가 통과했다.
- 2026-05-02: 랜덤 포트 HTTP 서버와 JWT 인증을 사용하는 `HiddenGemRecommendationE2ETest`를 추가하고 전체 테스트를 강제 재실행해 통과했다.

## 완료 조건
- plan 문서가 사용자 승인된 상태다.
- API, 서비스, 리포지토리, DTO가 plan 기준으로 구현되어 있다.
- 정상/경계/예외 테스트가 추가되어 통과한다.
- 관련 정책 문서가 코드와 모순 없이 갱신되어 있다.
- 구현과 테스트에서 추가 문제가 없을 경우 수정 기록이 이 plan 문서와 관련 정책 문서에 정리되어 있다.
- 남은 미확정 항목은 `docs/current-gaps.md`에 unresolved only로 기록되어 있다.
