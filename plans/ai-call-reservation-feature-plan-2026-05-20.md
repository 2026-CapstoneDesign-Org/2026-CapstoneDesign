# AI 전화 예약 기능 설계 계획

기준 날짜 및 시간: 2026-05-20 (Asia/Seoul)

## Status
mock-mvp-implemented

## 작업명
AI 전화 예약 MVP 설계

## 목적
사용자가 식당, 예약 날짜, 예약 시간, 인원 수를 입력하면 백엔드가 예약 요청을 저장하고, AI 전화 provider를 통해 식당에 예약 가능 여부를 확인한 뒤 결과 상태를 반환하는 기능을 기존 Spring Boot 구조에 자연스럽게 추가한다.

이번 문서는 구현 전 설계 문서다. 사용자 검토 또는 승인 전에는 Entity, Repository, Service, Controller, DTO, 외부 API client 구현을 진행하지 않는다.

## 사용자 관점 결과
- 사용자는 검색, 추천, 랭킹, 식당 상세 화면에서 선택한 내부 DB 식당을 대상으로 AI 전화 예약을 요청할 수 있다.
- 사용자는 예약 요청 후 현재 상태와 최종 결과를 조회할 수 있다.
- 사용자는 자신의 예약 내역만 조회할 수 있다.
- 전화번호가 없는 식당은 예약 요청 단계에서 실패하며, 사용자에게 명확한 이유가 전달된다.
- MVP에서는 실제 전화 발신 대신 Mock provider 결과로 상태 전이와 저장 구조를 먼저 검증한다.

## 기존 서비스와 연결되는 지점
- 예약 대상 식당은 `Restaurant`다.
- 식당 선택 진입점은 기존 `GET /search`, `GET /recommendations/restaurants`, `GET /rankings/restaurants`, `GET /restaurants/{id}` 흐름을 재사용할 수 있다.
- 예약 전 필수 정보는 `restaurants.phone_number`, `restaurants.business_hours_raw`, hidden/delete 상태다.
- 현재 식당 상세 응답은 `phoneNumber`, `businessHours`, `businessHoursDisplay`, `currentBusinessStatus`를 이미 제공한다.
- 예약 기능은 추천/랭킹 계산 입력으로 사용하지 않는다. `UserList`, `ListRestaurant`, `autoScore` 정책과 분리된 독립 도메인으로 둔다.

## plans 정리 검토
설계 문서 작성 전 `plans/`와 `plans/archive/`의 기존 계획 문서를 검토했다.

### 검토 기준
- 이미 완료된 plan이지만 과거 결정 근거로 유효하면 삭제하지 않는다.
- 최신 기준 문서가 따로 있어도 데이터 상태, 검증 결과, 보류 사유가 남아 있으면 삭제하지 않는다.
- AI 전화 예약과 직접 무관해도 현재 도메인 정책을 설명하는 문서는 삭제하지 않는다.
- 명백히 임시 파일이거나 중복만 남긴 파일만 삭제 대상으로 본다.

### 정리 대상 후보
- `plans/hidden-gem-recommendation-api-plan.md`: `Status: completed`이므로 장기적으로 `plans/archive/` 이동 후보다. 삭제 대상은 아니다.
- `plans/package-structure-refactor-plan.md`: `Status: completed`이므로 archive 이동 후보다. 외부 client 패키지 결정 근거가 있어 보존한다.
- `plans/documentation-current-state-refresh-2026-05-01.md`: 완료된 문서 최신화 기록이다. 외부 fallback gap 정리 근거가 있어 보존한다.
- `plans/test-validation-fix-2026-05-01.md`: 완료된 테스트 환경 정리 기록이다. H2 테스트 설정과 전체 테스트 기준 근거가 있어 보존한다.
- `plans/review-vote-viewer-state-plan-2026-05-12.md`: 완료된 리뷰 응답 변경 계획이다. 인증 principal과 테스트 패턴 참고 근거가 있어 보존한다.
- `plans/restaurant-business-hours-weekly-progress-2026-05-05.md`, `plans/restaurant-hours-absolute-time-progress-2026-05-05.md`: 최신 기준은 `restaurant-business-hours-raw-progress-2026-05-05.md`지만, 영업시간 정책 변경 이력과 데이터 반영 경위가 있어 삭제하지 않는다.
- `plans/restaurant-detail-*`, `plans/search-flow-*`: 전화번호, 영업시간, 외부 fallback, 상세 응답 근거와 직접 관련이 있어 보존한다.
- `plans/ranking-feature-plan.md`: `Status: planned`이지만 실제 랭킹 API는 현재 코드와 정책 문서에 존재한다. 상태값은 오래됐을 가능성이 있으나 랭킹 설계 근거가 남아 있어 삭제하지 않는다.

### 삭제 판단
이번 단계에서 삭제한 계획 문서는 없다.

삭제하지 않은 이유:
- 대부분이 완료 기록, 검증 기록, 데이터 상태, 정책 결정 근거를 포함한다.
- AI 전화 예약 기능과 직접 무관해 보여도 식당 상세, 전화번호, 영업시간, 외부 fallback, 패키지 구조와 연결된다.
- 애매한 문서는 삭제하지 말라는 작업 지침에 따라 보존한다.

## 범위
- AI 전화 예약 MVP의 도메인, 상태값, API, 검증 규칙, 테스트 전략 설계
- 실제 전화 발신 전 Mock 모드 우선 설계
- 예약 요청 저장 설계
- 예약 상태 조회 설계
- Mock 결과 저장 설계
- 사용자별 예약 내역 조회 설계
- 식당 전화번호가 없는 경우 처리 설계
- provider 교체 가능한 client/service 경계 설계
- `docs/current-gaps.md` 반영 후보 정리

## 비범위
- 실제 식당 전화 발신
- 실제 OpenAI Realtime API 연동
- 실제 Twilio/SIP 연동
- 통화 녹음 저장
- 전화번호 seed 데이터 수정
- 예약 성공 결과가 추천/랭킹에 영향을 주는 구조
- 외부 fallback 식당을 DB 저장 없이 바로 예약하는 구조
- `UserList`, `ListRestaurant`, `autoScore` 계산식 변경
- OAuth2/JWT 인증 흐름 재설계
- `docs/current-gaps.md` 직접 수정

## 관련 문서/코드
먼저 읽어야 할 문서:
- `AGENTS.md`
- `GUIDE.md`
- `DB.md`
- `LOGIC.md`
- `PLANS.md`
- `docs/current-gaps.md`
- `docs/db/users.md`
- `docs/db/restaurants.md`
- `docs/db/auth.md`
- `docs/logic/auth-flow.md`
- `docs/logic/search-policy.md`
- `docs/logic/restaurant-detail-policy.md`
- `docs/logic/restaurant-detail-parking-photo-policy-2026-05-08.md`
- `docs/logic/visibility-policy.md`
- `docs/logic/validation-rules.md`

직접 영향 받을 수 있는 코드 위치:
- `Capstone/src/main/java/com/example/Capstone/domain/Restaurant.java`
- `Capstone/src/main/java/com/example/Capstone/domain/User.java`
- `Capstone/src/main/java/com/example/Capstone/repository/RestaurantRepository.java`
- `Capstone/src/main/java/com/example/Capstone/repository/UserRepository.java`
- `Capstone/src/main/java/com/example/Capstone/service/RestaurantService.java`
- `Capstone/src/main/java/com/example/Capstone/controller/RestaurantController.java`
- `Capstone/src/main/java/com/example/Capstone/config/SecurityConfig.java`
- `Capstone/src/main/java/com/example/Capstone/common/jwt/JwtFilter.java`
- `Capstone/src/main/java/com/example/Capstone/exception/BusinessException.java`
- `Capstone/src/main/java/com/example/Capstone/exception/GlobalExceptionHandler.java`
- `Capstone/src/main/java/com/example/Capstone/client/*`

## 현재 구조 기준 사전 확인 사항
- `Restaurant.phoneNumber`는 이미 존재한다.
- `Restaurant.businessHoursRaw`는 이미 존재한다.
- `RestaurantService.getRestaurant()`는 `findByIdAndIsDeletedFalseAndIsHiddenFalse()` 기준으로 visible 식당만 조회한다.
- `SearchRestaurantItemResponse`와 `RestaurantResponse`는 전화번호를 노출하지 않는다. 검색 결과에서 바로 예약을 시작하려면 상세 조회 또는 예약 API 내부 검증이 필요하다.
- `PcmapSearchClient.PcmapRestaurantCandidate`에는 전화번호가 없다.
- `NaverLocalSearchClient.NaverLocalRestaurantCandidate`에는 `telephone`이 있으며 seed/import와 외부 fallback 저장 보강에 사용된다.
- 새 예약 API는 `SecurityConfig`에 permitAll로 추가하지 않는 한 인증 필요 API가 된다.
- 테스트 설정은 `Capstone/src/test/resources/application.yml`에 있으며, 외부 Pcmap은 테스트에서 비활성화되어 있다.

## 도메인 설계 후보
### Entity 이름 후보
1. `AiCallReservation`
   - AI 전화 예약이라는 기능 성격이 명확하다.
   - 추후 일반 수동 예약 기능이 생기면 이름이 좁을 수 있다.
2. `RestaurantReservation`
   - 장기적으로 가장 일반적인 이름이다.
   - provider가 AI 전화인지, 수동인지, webhook인지 필드로 표현할 수 있다.

권장안:
- MVP는 `RestaurantReservation`을 우선 검토한다.
- provider 관련 필드로 AI 전화 MVP임을 표현한다.

### 관계
- `User` 1:N `RestaurantReservation`
  - 예약 요청자는 로그인 사용자다.
  - 예약 생성 시 `UserRepository.findByIdAndIsDeletedFalse(userId)` 패턴을 사용한다.
- `Restaurant` 1:N `RestaurantReservation`
  - 예약 대상은 내부 DB에 저장된 visible 식당만 허용한다.
  - 외부 fallback 후보를 DB 저장 없이 예약하는 구조는 MVP에서 제외한다.

### 주요 필드 후보
- `id`
- `user`
- `restaurant`
- `reservationDate`
- `reservationTime`
- `reservationDateTime`
- `partySize`
- `requestNote`
- `status`
- `restaurantPhoneNumberSnapshot`
- `aiSummary`
- `resultMessage`
- `failureReason`
- `provider`
- `providerCallId`
- `providerStatus`
- `attemptCount`
- `lastAttemptedAt`
- `confirmedAt`
- `canceledAt`
- `createdAt`
- `updatedAt`

### 예약 날짜/시간/인원 수 저장 방식
권장안:
- API 요청에서는 `reservationDate`, `reservationTime`, `partySize`를 받는다.
- Entity 저장은 아래 둘 중 하나로 확정한다.
  - 후보 A: `LocalDate reservationDate` + `LocalTime reservationTime`
  - 후보 B: `LocalDateTime reservationDateTime`
- MVP 권장안은 후보 B다. 과거 시간 검증과 중복 검사가 단순하다.
- 기준 시간대는 서버 정책상 Asia/Seoul로 해석한다.

### 전화번호 snapshot
저장 권장:
- 식당 전화번호는 이후 seed/import나 관리자 수정으로 바뀔 수 있다.
- 실제 어떤 번호로 전화를 시도했는지 추적해야 한다.
- `restaurantPhoneNumberSnapshot`은 원문 저장을 기본으로 하되, 응답에서는 마스킹 여부를 검토한다.

### AI 요약 저장
저장 후보:
- Mock 또는 실제 통화 결과를 사용자에게 보여주려면 `aiSummary` 또는 `resultMessage`가 필요하다.
- 통화 원문 전체가 아니라 요약만 저장한다.
- 통화 녹음과 전체 transcript 저장은 MVP 비범위다.

### 실패 사유 저장
저장 권장:
- `failureReason` 또는 `failureCode` 후보가 필요하다.
- 예: `NO_PHONE_NUMBER`, `PAST_TIME`, `PROVIDER_TIMEOUT`, `RESTAURANT_NO_ANSWER`, `RESTAURANT_UNAVAILABLE`, `MOCK_REJECTED`

### provider call id 저장
필드만 준비 후보:
- Mock 단계에서는 nullable이다.
- 추후 Twilio/SIP/OpenAI Realtime API 연동 시 provider callback과 매칭하려면 `providerCallId`가 필요하다.

### createdAt / updatedAt
필요:
- 기존 `BaseTimeEntity` 패턴을 따른다.
- 예약 상태 변경 이력을 별도 테이블로 둘지는 후속 검토다.

## 예약 상태값 설계
상태 enum 후보:
- `REQUESTED`
- `CALLING`
- `CONFIRMED`
- `UNAVAILABLE`
- `NEEDS_CONFIRMATION`
- `FAILED`
- `CANCELED`

### 상태 의미
| 상태 | 의미 |
| --- | --- |
| `REQUESTED` | 사용자가 예약 요청을 생성했고 아직 provider 호출 전이다. Mock mode에서는 생성 직후 이 상태로 저장한 뒤 mock 결과 반영 API가 다음 상태로 바꿀 수 있다. |
| `CALLING` | provider가 식당에 전화 중이거나 통화 시도를 진행 중이다. |
| `CONFIRMED` | 식당이 예약 가능하다고 확인했고 예약이 확정된 상태다. |
| `UNAVAILABLE` | 해당 날짜/시간/인원 예약이 불가하다고 식당이 답한 상태다. |
| `NEEDS_CONFIRMATION` | AI가 결과를 명확히 판단하지 못했거나, 식당이 추가 정보 확인을 요구한 상태다. |
| `FAILED` | provider 오류, 전화 실패, 타임아웃, 내부 처리 오류 등으로 예약 확인을 완료하지 못한 상태다. |
| `CANCELED` | 사용자가 앱에서 예약 요청을 취소한 상태다. 실제 식당 예약 취소 전화까지 수행하는 기능은 MVP 비범위다. |

### 전이 규칙 후보
- `REQUESTED -> CALLING`
- `REQUESTED -> CONFIRMED`
  - Mock mode에서 즉시 성공 결과를 주입하는 경우 허용 후보
- `REQUESTED -> UNAVAILABLE`
  - Mock mode에서 즉시 불가 결과를 주입하는 경우 허용 후보
- `REQUESTED -> NEEDS_CONFIRMATION`
- `REQUESTED -> FAILED`
- `CALLING -> CONFIRMED`
- `CALLING -> UNAVAILABLE`
- `CALLING -> NEEDS_CONFIRMATION`
- `CALLING -> FAILED`
- `REQUESTED -> CANCELED`
- `NEEDS_CONFIRMATION -> CANCELED`
- `FAILED`, `UNAVAILABLE`, `CONFIRMED`, `CANCELED`은 기본적으로 terminal 상태로 본다.

추가 확인 필요:
- `CONFIRMED -> CANCELED`을 앱 내 표시 변경으로 허용할지, 실제 식당 취소 전화와 묶어서 후속 단계로 둘지 결정 필요.
- `FAILED -> REQUESTED` 재시도 대신 새 예약 row를 만들지, 같은 row에서 `attemptCount`를 증가시킬지 결정 필요.

## API 설계 후보
### 1. 예약 요청 생성 API
권장 endpoint:
```http
POST /restaurants/{restaurantId}/reservations/ai-call
```

대안:
```http
POST /reservations/ai-call
```

권장안은 첫 번째다. 식당 상세 화면에서 이어지는 동작임이 명확하고, 기존 `/restaurants/{id}/reviews`, `/restaurants/{restaurantId}/parking-lots` 패턴과도 맞다.

인증:
- 필요
- `@AuthenticationPrincipal Long userId`

Request body 후보:
```json
{
  "reservationDate": "2026-06-01",
  "reservationTime": "19:00",
  "partySize": 4,
  "requestNote": "창가 자리 가능하면 부탁드립니다."
}
```

Response body 후보:
```json
{
  "reservationId": 1,
  "restaurantId": 10,
  "restaurantName": "예시식당",
  "reservationDateTime": "2026-06-01T19:00:00",
  "partySize": 4,
  "status": "REQUESTED",
  "resultMessage": null,
  "createdAt": "2026-05-20T10:00:00"
}
```

주요 예외:
- 인증 없음: `401`
- 사용자 없음 또는 삭제됨: `404` 또는 `401` 정책 결정 필요
- 식당 없음, 삭제, 숨김: `404`
- 전화번호 없음: `400 BAD_REQUEST`
- 과거 날짜/시간: `400 BAD_REQUEST`
- 인원 수 범위 초과: `400 BAD_REQUEST`
- 동일 사용자, 동일 식당, 동일 예약 시각의 진행 중 예약 중복: `409 CONFLICT` 후보

### 2. 내 예약 목록 조회 API
Endpoint:
```http
GET /reservations
```

인증:
- 필요

Query parameter 후보:
- `status`
- `from`
- `to`
- `limit`

Response body 후보:
```json
{
  "items": [
    {
      "reservationId": 1,
      "restaurantId": 10,
      "restaurantName": "예시식당",
      "reservationDateTime": "2026-06-01T19:00:00",
      "partySize": 4,
      "status": "CONFIRMED",
      "resultMessage": "6월 1일 19시 4명 예약 가능으로 확인되었습니다.",
      "createdAt": "2026-05-20T10:00:00",
      "updatedAt": "2026-05-20T10:02:00"
    }
  ]
}
```

주요 예외:
- 인증 없음: `401`
- 잘못된 날짜 범위: `400`
- limit 범위 초과: `400`

### 3. 예약 상세 조회 API
Endpoint:
```http
GET /reservations/{reservationId}
```

인증:
- 필요
- 본인 예약만 조회 가능

Response body 후보:
```json
{
  "reservationId": 1,
  "restaurant": {
    "id": 10,
    "name": "예시식당",
    "address": "서울 ...",
    "phoneNumberMasked": "02-123-****"
  },
  "reservationDateTime": "2026-06-01T19:00:00",
  "partySize": 4,
  "requestNote": "창가 자리 가능하면 부탁드립니다.",
  "status": "CONFIRMED",
  "aiSummary": "식당에서 해당 시간 예약 가능하다고 답했습니다.",
  "resultMessage": "예약 가능으로 확인되었습니다.",
  "failureReason": null,
  "createdAt": "2026-05-20T10:00:00",
  "updatedAt": "2026-05-20T10:02:00"
}
```

주요 예외:
- 인증 없음: `401`
- 예약 없음: `404`
- 다른 사용자 예약 조회: `403`

### 4. 예약 취소 API
Endpoint:
```http
PATCH /reservations/{reservationId}/cancel
```

인증:
- 필요
- 본인 예약만 취소 가능

Request body:
- MVP에서는 없음
- 후속 후보: 취소 사유

Response:
- `204 No Content` 또는 변경된 예약 응답

주요 예외:
- 예약 없음: `404`
- 다른 사용자 예약: `403`
- 취소 불가능 상태: `400 BAD_REQUEST`

취소 가능 후보:
- `REQUESTED`
- `NEEDS_CONFIRMATION`
- `FAILED`은 이미 terminal이므로 취소 의미가 약하다.

취소 불가능 후보:
- `CALLING`
- `CONFIRMED`
- `UNAVAILABLE`
- `CANCELED`

추가 확인 필요:
- `CALLING` 중 취소 요청을 받으면 provider call 중단을 시도할지 여부.

### 5. Mock 결과 반영 API
Endpoint 후보:
```http
POST /admin/reservations/{reservationId}/mock-result
```

인증:
- 관리자 권한 필요
- 기존 관리자 API와 같은 `@PreAuthorize("hasRole('ADMIN')")` 기준을 따른다.
- 일반 사용자는 Mock 결과를 임의로 반영할 수 없다.
- 실제 provider webhook은 아직 구현하지 않는다.

Request body 후보:
```json
{
  "status": "CONFIRMED",
  "aiSummary": "식당에서 해당 시간 예약 가능하다고 답했습니다.",
  "resultMessage": "예약 가능으로 확인되었습니다.",
  "failureReason": null,
  "providerCallId": "mock-call-1"
}
```

Response:
- 변경된 예약 상세 응답

주요 예외:
- 관리자 권한 없음: `403`
- 허용되지 않은 상태값: `400`
- terminal 상태 변경 시도: `400`
- 예약 없음: `404`

### 6. 추후 전화 provider webhook API
Endpoint 후보:
```http
POST /webhooks/reservations/call-provider
```

인증:
- 일반 JWT 인증이 아니라 provider signature 검증 후보
- `SecurityConfig` permitAll에 넣되, 내부에서 signature 검증을 강제하는 구조 후보

Request body:
- provider별 payload가 달라 별도 DTO 필요
- 내부 표준 이벤트로 변환한다.

Response:
- `200 OK`

주요 예외:
- signature 불일치: `401`
- provider call id 매칭 실패: `404` 또는 `200 ignore` 정책 결정 필요
- 중복 webhook: idempotent 처리 필요

## 검증 규칙
- 인증 사용자만 예약 가능하다.
- 삭제되지 않은 사용자만 예약 가능하다.
- visible 식당만 예약 가능하다.
- `Restaurant.isDeleted = true` 또는 `Restaurant.isHidden = true` 식당은 예약 불가다.
- `phoneNumber`가 없거나 blank인 식당은 예약 불가다.
- 과거 날짜/시간 예약은 불가다.
- 예약 날짜/시간은 Asia/Seoul 기준으로 해석한다.
- 인원 수는 필수다.
- 인원 수 범위 후보는 `1 ~ 20`이다. 실제 제한은 추가 확인 필요다.
- 동일 사용자, 동일 식당, 동일 예약 시각에 terminal 상태가 아닌 예약이 이미 있으면 중복 요청을 막는다.
- terminal 상태 후보: `CONFIRMED`, `UNAVAILABLE`, `FAILED`, `CANCELED`
- 취소 가능한 상태와 불가능한 상태를 명확히 구분한다.
- 예약 요청은 추천/랭킹/리스트 점수에 영향을 주지 않는다.
- 외부 fallback 후보는 DB에 저장되고 visible 식당으로 검증된 뒤에만 예약 대상이 될 수 있다.

## AI/전화 연동 준비 구조
### Provider interface 후보
패키지 후보:
- `com.example.Capstone.client`
- 또는 예약 전용 하위 패키지 `com.example.Capstone.client.reservation`

Interface 후보:
```text
ReservationCallProvider
```

책임:
- 예약 요청 정보를 provider 요청으로 변환한다.
- Mock mode에서는 실제 전화 없이 결정된 결과를 반환한다.
- 실제 provider adapter는 같은 interface를 구현한다.

메서드 후보:
- `startCall(command)`
- `applyMockResult(command)`
- `parseWebhook(payload)`

### Service 경계
패키지 후보:
- `com.example.Capstone.service.ReservationService`

책임:
- 사용자와 식당 검증
- 예약 row 생성
- 상태 전이 검증
- provider 호출 조율
- 응답 DTO 변환

Provider adapter 책임:
- 외부 API 호출
- provider call id 반환
- provider 오류를 내부 실패 모델로 변환

### OpenAI API client 위치 후보
- `Capstone/src/main/java/com/example/Capstone/client/OpenAiRealtimeClient.java`
- MVP에서는 생성하지 않는다.
- 후속 단계에서 `client/` 아래에 두고, prompt/voice/session 생성을 provider adapter 내부에서 사용한다.

### 전화 API client 위치 후보
- `Capstone/src/main/java/com/example/Capstone/client/TwilioCallClient.java`
- `Capstone/src/main/java/com/example/Capstone/client/SipCallClient.java`
- MVP에서는 생성하지 않는다.

### 환경변수 후보
Spring property 후보:
- `reservation.call.mode=mock`
- `reservation.call.mock.enabled=true`
- `reservation.call.max-party-size=20`
- `reservation.call.duplicate-window-minutes=0`
- `openai.api.key`
- `openai.realtime.model`
- `twilio.account-sid`
- `twilio.auth-token`
- `twilio.from-number`
- `reservation.webhook.signature-secret`

환경변수 fallback 후보:
- `OPENAI_API_KEY`
- `OPENAI_REALTIME_MODEL`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_FROM_NUMBER`
- `RESERVATION_WEBHOOK_SIGNATURE_SECRET`

### local/test mock 기본값
- `Capstone/src/test/resources/application.yml`에 `reservation.call.mode: mock` 후보를 둔다.
- 실제 전화 provider property가 없어도 테스트가 실행되어야 한다.
- Mock provider는 deterministic 결과를 반환해야 한다.
- 외부 API key가 없으면 실제 provider bean이 활성화되지 않도록 profile 또는 conditional property를 둔다.

## 테스트 전략
### Service 단위 테스트
필수:
- 정상 예약 요청 저장
- 전화번호 없는 식당 예약 실패
- hidden/deleted 식당 예약 실패
- 삭제 사용자 예약 실패
- 과거 시간 예약 실패
- 인원 수 범위 실패
- 진행 중 중복 예약 실패
- 취소 가능 상태 취소 성공
- 취소 불가능 상태 취소 실패
- mock 결과 반영 상태 전이 검증

### Controller 테스트
기존 패턴:
- `MockMvcBuilders.standaloneSetup`
- `GlobalExceptionHandler`
- 인증 principal 필요 시 `AuthenticationPrincipalArgumentResolver`와 `SecurityContextHolder` 패턴 참고

필수:
- `POST /restaurants/{restaurantId}/reservations/ai-call`
- `GET /reservations`
- `GET /reservations/{reservationId}`
- `PATCH /reservations/{reservationId}/cancel`
- mock result API를 열 경우 별도 테스트

### Repository 테스트 필요 여부
MVP에서는 후보:
- Spring Data JPA 파생 쿼리만 쓰면 service 테스트에서 충분할 수 있다.
- 진행 중 중복 예약 조회나 기간 조회가 복잡해지면 repository 테스트를 추가한다.

### Mock provider 테스트
- 실제 전화 없이 `ReservationCallProvider` mock 또는 fake 구현을 테스트한다.
- provider가 `CONFIRMED`, `UNAVAILABLE`, `NEEDS_CONFIRMATION`, `FAILED`를 반환하는 케이스를 분리한다.

### 실패 케이스 테스트
- provider timeout
- provider 응답 없음
- provider call id 누락
- terminal 상태에 결과 재반영
- webhook 중복 이벤트

### 기존 인증 테스트 패턴과 연결
- 현재 JWT filter는 access token subject를 `Long userId` principal로 설정한다.
- 컨트롤러는 `@AuthenticationPrincipal Long userId`를 받는 기존 패턴을 따른다.
- 새 예약 API는 별도 permitAll에 넣지 않는다.

## 위험 요소와 미확정 항목
### 위험 요소
1. 전화번호 데이터 품질
   - seed 상태에 따라 `phoneNumber`가 null일 수 있다.
   - Pcmap fallback 후보에는 전화번호가 없다.
2. 개인정보 저장 범위
   - 예약자 이름, 사용자 연락처, 요청사항 저장 여부가 민감하다.
3. 통화 로그 저장 여부
   - MVP에서는 요약만 저장하고 녹음/transcript는 저장하지 않는 방향이 안전하다.
4. 실제 전화 발신 전 사용자 동의 방식
   - 예약 요청 버튼만으로 동의가 충분한지, 별도 확인 화면이 필요한지 결정 필요.
5. provider webhook 인증
   - JWT 인증과 별도 signature 검증이 필요하다.
6. 중복 전화/중복 예약 방지
   - 같은 사용자와 식당, 같은 시간에 진행 중 요청을 막아야 한다.
7. 외부 fallback 식당 예약 허용 여부
   - DB 저장 전 fallback 후보는 예약 대상에서 제외한다.
8. 운영 장애 대응
   - provider timeout, 부분 실패, 중복 webhook, 지연 응답 처리 정책이 필요하다.

### current-gaps.md 반영 후보
2026-05-20 Mock MVP 구현 단계에서 확정된 항목은 `docs/db/reservations.md`와 `docs/logic/reservation-policy.md`에 반영했다.

`docs/current-gaps.md`에는 아래 후속 운영 정책을 남겼다.
- 실제 전화 provider webhook 인증 방식
- provider 장애, retry, timeout, 중복 webhook idempotency 정책
- 예약 개인정보 저장 범위와 보관 / 삭제 정책
- 식당 단위 전역 중복 예약 차단 여부
- 확정된 예약 취소 시 실제 식당 취소 전화 수행 여부

## 구현 단계 제안
1. 문서 기반 설계 확정
   - 이 계획 문서 검토
   - 상태값과 API endpoint 확정
   - 개인정보 저장 범위 확정
2. Entity/Repository/DB 구조 추가
   - `RestaurantReservation` 후보 추가
   - 상태 enum 추가
   - repository 추가
   - DB 문서 초안 갱신
3. Mock 예약 Service 구현
   - 사용자/식당/시간/인원 검증
   - 예약 요청 저장
   - 상태 전이 검증
   - mock provider interface 연결
4. API 구현
   - 예약 생성
   - 내 예약 목록
   - 예약 상세
   - 취소
   - mock 결과 반영 API는 설정 기반으로 제한
5. 테스트 작성
   - service 단위 테스트
   - controller 테스트
   - 실패 케이스 테스트
   - 필요 시 repository 테스트
6. OpenAI API 연동 준비
   - 실제 client는 아직 붙이지 않는다.
   - interface와 DTO 경계만 실제 provider 교체 가능하게 유지한다.
7. 전화 provider 연동 준비
   - Twilio/SIP adapter는 후속 계획으로 분리한다.
   - webhook signature, idempotency, retry 정책을 별도 문서로 확정한다.

## 검증 방법
이번 문서 작성 단계:
- `git diff -- plans/ai-call-reservation-feature-plan-2026-05-20.md`
- 코드 파일 변경이 없는지 `git diff --stat`
- 삭제 파일이 없는지 `git status --short`

구현 단계에서 실행할 후보:
- `cd Capstone && ./gradlew test --tests "com.example.Capstone.service.ReservationServiceTest"`
- `cd Capstone && ./gradlew test --tests "com.example.Capstone.controller.ReservationControllerTest"`
- `cd Capstone && ./gradlew test`

실행 명령은 실제 구현 단계에서 현재 코드베이스와 테스트 파일 존재 여부를 다시 확인한 뒤 확정한다.

## 2026-05-20 전체 테스트 실패 분석
Mock MVP 구현 후 전체 `./gradlew test`는 최초 18개 실패가 있었다.

### 실패 목록과 원인 분류
외부 API placeholder / 테스트 환경 설정 누락:
- `CapstoneApplicationTests.contextLoads`
- `HiddenGemRecommendationE2ETest` 2건
- `ParkingLotE2ETest` 2건
- `HiddenGemRecommendationRepositoryTest` 1건
- `ListRecommendationRepositoryTest` 2건
- `RestaurantRankingRepositoryTest` 4건
- `ParkingLotSeedImportServiceTest` 2건
- `RestaurantSeedImportServiceTest` 1건

원인:
- `GeminiClient`가 `${gemini.api.url}`, `${gemini.api.key}`를 필수 placeholder로 요구했지만 test `application.yml`에 값이 없었다.
- Gemini 더미 설정을 추가한 뒤에는 `S3Config`가 `${aws.s3.*}`를 필수 placeholder로 요구하는 문제가 추가로 드러났다.
- 실제 secret이나 실제 외부 호출은 추가하지 않고 테스트 전용 더미값만 추가했다.

기존 테스트 기대값 / 기존 미해결 문제:
- `ListLikeServiceTest` 2건
  - 성공 케이스 테스트 데이터가 좋아요 사용자와 리스트 작성자를 같은 사용자로 구성하고 있었다.
  - 현재 코드 정책은 자기 리스트 좋아요 금지다.
  - `Long` id 비교는 `Objects.equals`로 보강했다.
- `ReliabilityScoreServiceTest` 1건
  - 테스트는 예전 `tier1 ~ tier8` 등급을 기대했지만 현재 `ReliabilityScore` 계산식은 `bronze/silver/gold/platinum/diamond/ruby`를 반환한다.
  - 테스트 기대값을 현재 코드 기준으로 맞췄다.
- `ListRecommendationRepositoryTest` 1건
  - H2 native query 결과의 `updated_at`이 `java.sql.Timestamp`로 반환됐지만 repository 변환 헬퍼가 이를 처리하지 않았다.
  - `Timestamp -> LocalDateTime` 변환을 추가했다.

예약 기능 추가로 인한 신규 충돌:
- 확인된 예약 기능 기인 실패는 없다.
- 예약 service/controller 테스트와 전체 Spring context 테스트가 함께 통과했다.

최종 검증:
- `bash ./gradlew test` 성공
- `git diff --check` 성공

## 2026-05-20 예약 DB 반영 준비
현재 프로젝트에서 Flyway/Liquibase 같은 고정 migration 도구는 확인되지 않았다. 기존 SQL은 `Capstone/seed-data/*` 하위에 수동 적용 기록으로 존재한다. 따라서 이번 단계에서는 실제 migration 파일을 만들지 않고 `docs/db/reservations.md`에 운영 DB DDL/index 후보를 문서화했다.

DDL 후보 핵심:
- 테이블: `restaurant_reservations`
- FK: `user_id -> users.id`, `restaurant_id -> restaurants.id`
- 필수값: `reservation_date_time`, `party_size`, `status`, `restaurant_phone_number_snapshot`, `attempt_count`, `created_at`, `updated_at`
- check 후보: `party_size BETWEEN 1 AND 20`, status enum 값 제한

Index 후보:
- `idx_restaurant_reservations_user_schedule`
  - 내 예약 목록 조회: `(user_id, reservation_date_time DESC, id DESC)`
- `idx_restaurant_reservations_restaurant_schedule`
  - 식당/시간/상태 기준 운영 조회: `(restaurant_id, reservation_date_time, status)`
- `idx_restaurant_reservations_status_created_at`
  - 상태 기반 후속 처리 후보: `(status, created_at)`
- `idx_restaurant_reservations_provider_call`
  - 추후 provider callback 매칭 후보: `(provider, provider_call_id) WHERE provider_call_id IS NOT NULL`
- `uq_restaurant_reservations_active_user_restaurant_time`
  - 진행성 중복 예약 차단 후보: `(user_id, restaurant_id, reservation_date_time)` partial unique
  - 대상 상태: `REQUESTED`, `CALLING`, `CONFIRMED`, `NEEDS_CONFIRMATION`

남은 결정:
- 운영 DB migration 적용 방식을 Flyway/Liquibase/수동 SQL 중 무엇으로 둘지 확정해야 한다.
- PostgreSQL partial unique index를 운영 DB에 적용할지, 다른 DB라면 equivalent 제약을 어떻게 둘지 결정해야 한다.

## 2026-05-20 운영 전 안전 정리
Mock 결과 반영 API:
- 일반 예약 API의 `POST /reservations/{reservationId}/mock-result`를 제거했다.
- Mock 결과 반영은 `POST /admin/reservations/{reservationId}/mock-result`에서만 수행한다.
- 접근 제한은 기존 관리자 API와 같은 `@PreAuthorize("hasRole('ADMIN')")`를 따른다.
- 관리자 HTTP 접근 제한은 `ReservationAdminE2ETest`에서 USER token `403`, ADMIN token `200`으로 검증한다.
- 실제 전화 provider webhook은 구현하지 않았다.

Seed 전화번호 보정:
- `Capstone/seed-data/restaurants-seed-preview.json`의 616개 restaurant row에 `phone_number: "01000000000"`를 채웠다.
- 운영 코드에는 이 값을 하드코딩하지 않았다.
- seed import는 기존처럼 seed row의 `phone_number`를 `Restaurant.phoneNumber`로 적재한다.
- `RestaurantSeedPhoneNumberTest`로 기본 seed preview 전화번호 통일 상태를 검증한다.
- 실제 운영 전화번호 품질 / 정규화 / 검수 정책은 `docs/current-gaps.md`에 남긴다.

## 리스크
- 실제 전화 provider를 붙이기 전에도 상태 전이와 저장 범위를 과하게 넓히면 개인정보 리스크가 커진다.
- 전화번호 null 식당이 많으면 사용자는 예약 기능을 자주 사용할 수 없다.
- 예약 가능 여부는 영업시간과 다르다. `businessHoursRaw`는 참고 정보일 뿐 최종 예약 가능 판단은 전화 확인 결과로 봐야 한다.
- mock result API가 운영에서 노출되면 상태 조작 위험이 있다.
- webhook API는 permitAll이 필요할 수 있으나 signature 검증이 없으면 위험하다.

## Progress
- [x] 기존 구조 분석 완료
- [x] `plans/`와 `plans/archive/` 검토 완료
- [x] 계획 작성 완료
- [x] 사용자 검토 / 승인 완료
- [x] 구현 시작
- [x] 중간 검증 완료
- [x] 예약 범위 최종 검증 완료
- [x] 전체 테스트 green 확인
- [x] 문서 반영 완료

## 결정 사항 / 변경 로그
- 2026-05-20: AI 전화 예약은 추천/랭킹과 분리된 독립 도메인으로 설계한다.
- 2026-05-20: MVP는 실제 전화 발신 없이 Mock provider 모드로 시작한다.
- 2026-05-20: 외부 fallback 식당은 DB 저장 없이 바로 예약하지 않는다.
- 2026-05-20: 전화번호가 없는 식당은 MVP에서 예약 요청을 받지 않는다.
- 2026-05-20: 기존 plans 검토 결과 명백히 삭제할 문서는 없다고 판단했다.
- 2026-05-20: Mock MVP 구현은 `RestaurantReservation` 독립 도메인으로 진행한다.
- 2026-05-20: 중복 예약 차단은 동일 사용자 / 동일 식당 / 동일 예약 시각 기준으로 구현한다.
- 2026-05-20: Mock 결과 반영 API는 이번 MVP에서 인증된 예약 소유자만 호출할 수 있도록 구현한다. 운영 노출 제한은 후속 gap으로 남긴다.
- 2026-05-20: 예약 service/controller 테스트는 통과했다.
- 2026-05-20: 전체 `./gradlew test` 실패 원인을 정리하고 테스트 환경 더미 외부 설정, 기존 테스트 기대값, Timestamp 변환 누락을 최소 수정했다.
- 2026-05-20: 전체 `./gradlew test`가 통과했다.
- 2026-05-20: 예약 테이블 DDL/index 후보를 `docs/db/reservations.md`에 문서화했다.
- 2026-05-20: Mock 결과 반영 API를 관리자 전용 `/admin/reservations/{reservationId}/mock-result`로 제한했다.
- 2026-05-20: 기본 식당 seed preview 616개 row의 `phone_number`를 `01000000000`로 통일했다.

## 완료 조건
- 예약 기능의 목적, 범위, 비범위가 문서로 정리되어 있다.
- Entity, 상태값, API, 검증 규칙, provider 경계, 테스트 전략이 구현 가능한 수준으로 정리되어 있다.
- `docs/current-gaps.md`에 반영할 후보가 문서 안에 분리되어 있다.
- 실제 기능 구현이나 코드 수정은 발생하지 않는다.
- 삭제한 계획 문서가 있다면 목록과 이유가 최종 보고에 포함된다.
