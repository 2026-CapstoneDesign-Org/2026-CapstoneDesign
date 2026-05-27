# AI 전화 예약 Provider Webhook / Retry / Idempotency 설계

기준 날짜 및 시간: 2026-05-20 (Asia/Seoul)

## Status
call-attempt-retry-timeout-base-implemented

## 작업명
AI 전화 예약 실제 provider 연동 전 webhook 운영 설계

## 목적
현재 Mock 기반 AI 전화 예약 기능을 실제 전화 provider와 AI provider로 확장하기 전에, provider webhook 수신 구조, 중복 이벤트 처리, 보안 검증, 실패/재시도 정책을 문서로 확정 후보화한다.

이번 작업 흐름에서는 실제 OpenAI API, Twilio/SIP/전화 provider를 구현하지 않는다. 2026-05-20 후속 단계에서 실제 provider가 아닌 mock/test provider용 webhook endpoint skeleton만 추가했다.

## 현재 구현 요약
현재 코드 기준:
- 예약 도메인은 `RestaurantReservation` 단일 Entity다.
- 예약 상태는 `REQUESTED`, `CALLING`, `CONFIRMED`, `UNAVAILABLE`, `NEEDS_CONFIRMATION`, `FAILED`, `CANCELED`다.
- 예약 생성은 `POST /restaurants/{restaurantId}/reservations/ai-call`에서 수행한다.
- 예약 목록/상세/취소는 본인 예약만 가능하다.
- Mock provider는 `ReservationCallProvider`의 `MockReservationCallProvider` 구현이다.
- Mock provider는 실제 전화를 걸지 않고 `provider=MOCK`, `providerCallId=mock-{reservationId}`, `providerStatus=MOCK_WAITING_RESULT`만 저장한다.
- Mock 결과 반영 API는 `POST /admin/reservations/{reservationId}/mock-result`이며, 기존 관리자 API와 같은 `@PreAuthorize("hasRole('ADMIN')")`로 제한한다.
- 기본 식당 seed preview 616개 row의 `phone_number`는 예약 테스트 편의를 위해 `01000000000`로 통일되어 있다.
- 전체 테스트는 2026-05-20 기준 `bash ./gradlew test`로 통과했다.

## 범위
- provider webhook endpoint 후보 설계
- mock/test provider webhook endpoint skeleton
- provider raw payload를 내부 표준 이벤트로 변환하는 구조 설계
- idempotency key와 event ledger 설계
- call attempt 기록과 retry/timeout 대상 식별 기반
- 중복/역순/재전송 이벤트 처리 정책
- 전화 연결 실패, provider 장애, AI 판단 실패의 retry/timeout 정책
- webhook 보안 검증 방식 후보
- 관리자 Mock API의 환경별 운영 정책

## 비범위
- 실제 전화 provider webhook controller 구현
- 실제 OpenAI Realtime API 연동
- 실제 Twilio/SIP/전화 provider 연동
- 실제 식당 전화 발신
- 통화 녹음 또는 transcript 저장 구현
- 추천/랭킹/리스트 점수에 예약 결과 반영
- seed 전화번호 추가 수정

## 관련 문서/코드
먼저 읽어야 할 문서:
- `AGENTS.md`
- `GUIDE.md`
- `DB.md`
- `LOGIC.md`
- `docs/db/reservations.md`
- `docs/logic/reservation-policy.md`
- `docs/logic/seed-import.md`
- `docs/current-gaps.md`
- `plans/ai-call-reservation-feature-plan-2026-05-20.md`

관련 코드:
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantReservation.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationStatus.java`
- `Capstone/src/main/java/com/example/Capstone/service/ReservationService.java`
- `Capstone/src/main/java/com/example/Capstone/service/ReservationProviderEventService.java`
- `Capstone/src/main/java/com/example/Capstone/controller/ReservationController.java`
- `Capstone/src/main/java/com/example/Capstone/controller/ReservationProviderWebhookController.java`
- `Capstone/src/main/java/com/example/Capstone/controller/AdminController.java`
- `Capstone/src/main/java/com/example/Capstone/client/reservation/*`
- `Capstone/src/main/java/com/example/Capstone/config/SecurityConfig.java`
- `Capstone/src/main/java/com/example/Capstone/common/jwt/JwtFilter.java`

## Webhook Endpoint 후보
권장 후보:

```http
POST /webhooks/reservations/call-providers/{provider}
```

대안:

```http
POST /webhooks/reservations/call-provider
POST /webhooks/reservations/{provider}
```

권장 이유:
- 사용자 인증 API인 `/reservations/**`와 provider webhook API인 `/webhooks/**`를 분리한다.
- provider별 signature 검증, mapper, 운영 로그를 분기하기 쉽다.
- 실제 provider가 늘어도 path로 provider를 명확히 식별할 수 있다.

Spring Security 후보:
- `/webhooks/reservations/call-providers/**`는 JWT 인증 대상에서 제외할 수 있다.
- 단, `permitAll` 자체가 보안 허용을 의미하면 안 된다.
- webhook controller 내부 첫 단계에서 provider signature/timestamp/idempotency 검증을 강제한다.
- signature 검증 실패는 `401 UNAUTHORIZED` 또는 `403 FORBIDDEN` 후보로 처리한다.

현재 구현:
- `POST /webhooks/reservations/call-providers/mock`
- `reservation.webhook.mock.enabled=true`일 때만 controller bean이 등록된다.
- 사용자 JWT가 아니라 mock provider HMAC signature와 timestamp를 검증한다.
- 테스트 설정에서만 기본 활성화되어 있고, 운영 기본값은 비활성화다.
- 실제 전화 provider endpoint가 아니라 mock/test provider skeleton이다.

## 내부 표준 이벤트 구조
provider별 raw payload를 바로 `RestaurantReservation`에 반영하지 않는다.

권장 흐름:

```text
Provider raw webhook
-> ReservationWebhookController
-> ReservationWebhookSecurityVerifier
-> ProviderWebhookMapper
-> ReservationProviderEvent
-> ReservationProviderEventService
-> RestaurantReservation 상태 전이
```

표준 이벤트 후보:

```text
ReservationProviderEvent
- provider
- providerCallId
- providerEventId
- reservationId
- eventType
- providerStatus
- occurredAt
- receivedAt
- targetStatus
- failureCode
- failureReason
- retryable
- aiSummary
- resultMessage
- rawPayloadHash
```

`reservationId`는 provider metadata로 함께 보내는 것이 좋지만, 신뢰 기준은 `providerCallId`와 event ledger가 우선이다. provider payload의 `reservationId`는 편의 식별자이며, 위조 가능성을 고려해 signature 검증 후에도 `providerCallId` 매칭과 함께 사용한다.

## Provider Event Type 후보
내부 표준 event type 후보:

| Event type | 의미 | 예약 상태 영향 후보 |
| --- | --- | --- |
| `CALL_QUEUED` | provider가 통화 작업을 접수함 | `REQUESTED` 유지 또는 providerStatus만 갱신 |
| `CALL_STARTED` | 전화 시도 시작 | `REQUESTED -> CALLING` |
| `CALL_CONNECTED` | 식당과 통화 연결됨 | `CALLING` 유지, providerStatus 갱신 |
| `CALL_CONNECTION_FAILED` | 연결 자체 실패 | retry 가능하면 `CALLING` 유지, 최종 실패면 `FAILED` |
| `CALL_NO_ANSWER` | 식당 부재중/무응답 | retry 가능하면 `CALLING` 유지, 최종 실패면 `FAILED` |
| `CALL_BUSY` | 통화 중 | retry 가능하면 `CALLING` 유지, 최종 실패면 `FAILED` |
| `CALL_ENDED` | 통화 종료 | 단독으로 최종 상태를 만들지 않고 후속 result event 대기 |
| `RESERVATION_CONFIRMED` | 예약 가능/확정 | `CALLING -> CONFIRMED` 또는 `REQUESTED -> CONFIRMED` |
| `RESERVATION_UNAVAILABLE` | 해당 조건 예약 불가 | `CALLING -> UNAVAILABLE` 또는 `REQUESTED -> UNAVAILABLE` |
| `RESERVATION_NEEDS_CONFIRMATION` | AI 판단 불명확/추가 확인 필요 | `CALLING -> NEEDS_CONFIRMATION` |
| `AI_PARSE_FAILED` | 통화는 되었지만 결과 파싱 실패 | `NEEDS_CONFIRMATION` 우선 후보 |
| `PROVIDER_TRANSIENT_ERROR` | provider 일시 장애 | retry 가능하면 `CALLING` 유지, 최종 실패면 `FAILED` |
| `PROVIDER_FATAL_ERROR` | 인증/번호 오류 등 재시도 무의미 | `FAILED` |

`CALL_ENDED`는 최종 예약 결과가 아니다. provider가 통화 종료와 AI 분석 결과를 별도 이벤트로 보낼 수 있으므로, 종료 이벤트만으로 `CONFIRMED`/`UNAVAILABLE`을 만들지 않는다.

## 상태 전이 원칙
현재 `ReservationStatus` 전이 규칙을 우선 유지한다.

허용 후보:
- `REQUESTED -> CALLING`
- `REQUESTED -> CONFIRMED`
- `REQUESTED -> UNAVAILABLE`
- `REQUESTED -> NEEDS_CONFIRMATION`
- `REQUESTED -> FAILED`
- `CALLING -> CONFIRMED`
- `CALLING -> UNAVAILABLE`
- `CALLING -> NEEDS_CONFIRMATION`
- `CALLING -> FAILED`

terminal 상태:
- `CONFIRMED`
- `UNAVAILABLE`
- `FAILED`
- `CANCELED`

terminal 상태 이후 늦게 도착한 provider event는 기본적으로 무시한다. 단, 같은 최종 상태를 반복 전달한 중복 이벤트는 성공 응답 `200 OK`로 처리하되 상태를 다시 바꾸지 않는다.

## Idempotency 설계
### 후보 비교
`providerEventId` 기준:
- 장점: provider가 재전송해도 같은 이벤트를 정확히 식별 가능
- 단점: provider가 event id를 제공하지 않으면 사용할 수 없음
- 평가: 제공된다면 최우선 기준

`providerCallId + eventType + occurredAt` 기준:
- 장점: event id가 없을 때 fallback 가능
- 단점: 같은 call에서 같은 type이 여러 번 발생할 수 있고 timestamp 정밀도 문제가 있음
- 평가: fallback 기준

`reservationId` 기준:
- 장점: 내부 예약 row 매칭이 쉬움
- 단점: 한 예약에 여러 이벤트가 정상적으로 들어오므로 idempotency key로는 부적합
- 평가: 매칭 보조값이지 중복 판단 기준은 아님

권장 idempotency key:

```text
provider + ":" + providerEventId
```

fallback:

```text
provider + ":" + providerCallId + ":" + eventType + ":" + occurredAtEpochMillis + ":" + rawPayloadHash
```

### Event Ledger 테이블 후보
후속 Entity 후보:

```text
ReservationProviderEvent
```

테이블 후보:

```text
reservation_provider_events
```

주요 필드 후보:
- `id`
- `reservation_id`
- `provider`
- `provider_call_id`
- `provider_event_id`
- `idempotency_key`
- `event_type`
- `provider_status`
- `target_status`
- `failure_code`
- `failure_reason`
- `retryable`
- `occurred_at`
- `received_at`
- `processed_at`
- `processing_status`
- `duplicate_of_event_id`
- `raw_payload_hash`
- `raw_payload`
- `signature_verified`
- `created_at`
- `updated_at`

Index / constraint 후보:
- unique `idempotency_key`
- index `(reservation_id, occurred_at, id)`
- index `(provider, provider_call_id)`
- index `(processing_status, received_at)`

### 중복 이벤트 처리
처리 순서 후보:
1. raw body로 signature 검증
2. provider별 mapper로 내부 표준 이벤트 생성
3. idempotency key 계산
4. `reservation_provider_events.idempotency_key` unique insert 시도
5. 이미 존재하면 기존 processing status 확인
6. 기존 이벤트가 `PROCESSED` 또는 `IGNORED`면 상태 전이 없이 `200 OK`
7. 기존 이벤트가 `FAILED_RETRYABLE`이면 내부 재처리 job에서만 재처리하고 webhook 요청에서는 중복 응답 처리
8. 새 이벤트면 reservation row를 lock하고 상태 전이 검증
9. 처리 결과를 event ledger에 저장

역순 이벤트 처리:
- event `occurredAt` 또는 provider sequence가 현재 처리된 최종 이벤트보다 오래됐고 상태가 이미 terminal이면 무시한다.
- terminal 상태를 다른 terminal 상태로 덮어쓰지 않는다.
- `CALL_STARTED` 같은 진행 이벤트가 `CONFIRMED` 이후 도착하면 `IGNORED_LATE_EVENT`로 기록한다.

## Retry / Timeout 설계
### retry 대상 후보
자동 retry 가능:
- `CALL_NO_ANSWER`
- `CALL_BUSY`
- `CALL_CONNECTION_FAILED`
- `PROVIDER_TRANSIENT_ERROR`
- `PROVIDER_TIMEOUT`
- provider rate limit이 짧은 시간 내 회복 가능한 경우

자동 retry 비대상:
- `RESERVATION_UNAVAILABLE`
- `RESERVATION_CONFIRMED`
- `RESERVATION_NEEDS_CONFIRMATION`
- `AI_PARSE_FAILED`
- 잘못된 전화번호
- provider 인증 오류
- provider 정책 차단
- 사용자가 취소한 예약

### retry 횟수 / 간격 후보
권장 MVP 후보:
- 최대 시도 횟수: 3회
- 첫 시도 실패 후 1분 뒤 2차 시도
- 2차 실패 후 5분 뒤 3차 시도
- 3차 실패 시 `FAILED`

대안:
- 더 보수적으로 최대 2회만 시도
- 영업시간 외로 추정되면 자동 retry 없이 `NEEDS_CONFIRMATION`
- 식당 busy/no-answer만 retry하고 provider 장애는 별도 job에서 재시도

후속 필드 후보:
- 현재 `RestaurantReservation.attemptCount`, `lastAttemptedAt`, `providerStatus`, `failureReason`을 우선 활용한다.
- 정교한 retry가 필요하면 `reservation_call_attempts` 테이블을 추가한다.

`reservation_call_attempts` 후보 필드:
- `id`
- `reservation_id`
- `attempt_number`
- `provider`
- `provider_call_id`
- `started_at`
- `ended_at`
- `status`
- `failure_code`
- `failure_reason`
- `next_retry_at`
- `created_at`
- `updated_at`

### timeout 기준 후보
전화 provider 시작 후 webhook이 오지 않는 경우:
- `CALLING` 진입 후 10분 동안 최종 이벤트가 없으면 timeout 후보
- retry 가능 횟수가 남아 있으면 재시도 예약
- retry 횟수를 초과하면 `FAILED` + `failureReason=PROVIDER_TIMEOUT`

AI 분석 결과 대기:
- `CALL_ENDED` 후 2분 동안 결과 이벤트가 없으면 `NEEDS_CONFIRMATION` 후보
- provider 장애가 명확하면 `FAILED`

### 사용자에게 보이는 상태
기다릴 수 있는 상태:
- `REQUESTED`: 요청 접수 / provider 시작 전
- `CALLING`: 전화 시도 중, retry scheduled 포함 가능

사용자 확인/운영자 개입이 필요한 상태:
- `NEEDS_CONFIRMATION`: 통화 내용이 모호하거나 AI 결과 검증 필요

즉시 최종 결과로 보여줄 상태:
- `CONFIRMED`: 예약 확정
- `UNAVAILABLE`: 예약 불가
- `FAILED`: 처리 실패
- `CANCELED`: 사용자가 취소

## Failure Code 후보
`failureReason` 문자열만으로는 운영 분석이 어렵기 때문에 후속 단계에서 별도 `failureCode` 후보를 둔다.

후보:
- `NO_ANSWER`
- `BUSY`
- `CALL_CONNECTION_FAILED`
- `INVALID_PHONE_NUMBER`
- `PROVIDER_TIMEOUT`
- `PROVIDER_RATE_LIMITED`
- `PROVIDER_AUTH_FAILED`
- `PROVIDER_INTERNAL_ERROR`
- `AI_PARSE_FAILED`
- `AMBIGUOUS_RESTAURANT_RESPONSE`
- `USER_CANCELED`
- `MAX_RETRY_EXCEEDED`

현재 Entity에는 `failureCode`가 없으므로, MVP 확장 전 필드 추가 여부를 결정해야 한다.

## Webhook 보안 설계
### 후보 비교
HMAC signature:
- 장점: raw body 위변조 검증 가능, webhook 표준에 가깝다.
- 단점: raw body 보존과 provider별 signature base string 구현이 필요하다.
- 평가: 권장 1순위

Shared secret header:
- 장점: 구현이 단순하다.
- 단점: payload 무결성 검증이 약하다.
- 평가: HMAC을 지원하지 않는 provider의 최소 fallback

Timestamp 검증:
- 장점: replay 공격 방어에 유용하다.
- 단점: 서버/provider 시간 차이를 고려해야 한다.
- 평가: HMAC과 함께 필수 후보. 허용 skew는 5분 후보

IP allowlist:
- 장점: 네트워크 레벨 방어에 도움된다.
- 단점: provider IP 변경, proxy 환경에서 운영 부담이 있다.
- 평가: 보조 수단. 단독 인증으로 사용하지 않는다.

mTLS:
- 장점: 강한 인증.
- 단점: 인프라 복잡도가 높다.
- 평가: 현재 MVP 후속 단계에서는 과함.

권장 조합:
- provider별 HMAC signature 검증
- timestamp 5분 window 검증
- idempotency key 기반 replay 방어
- IP allowlist는 운영 인프라가 준비되면 보조 적용

### Spring Security 적용 후보
미래 구현 후보:
- `SecurityConfig`에서 `/webhooks/reservations/call-providers/**`를 `permitAll`에 추가한다.
- JWT 인증은 적용하지 않는다.
- controller 또는 filter에서 raw body 기반 signature 검증을 먼저 수행한다.
- signature 검증 실패 시 도메인 service를 호출하지 않는다.

주의:
- `permitAll`은 provider webhook이 JWT 없이 들어올 수 있게 하는 설정일 뿐이다.
- 실제 보호는 `ReservationWebhookSecurityVerifier`가 담당한다.
- webhook endpoint와 사용자 예약 API는 controller/service 경계를 분리한다.

환경변수 후보:
- `reservation.webhook.providers.twilio.signature-secret`
- `reservation.webhook.providers.twilio.allowed-clock-skew-seconds=300`
- `reservation.webhook.providers.twilio.ip-allowlist`
- `reservation.webhook.max-body-size`

## 관리자 Mock API 운영 정책
현재 API:

```http
POST /admin/reservations/{reservationId}/mock-result
```

현재 제한:
- 관리자 JWT 필요
- `@PreAuthorize("hasRole('ADMIN')")`

환경별 후보:

| 환경 | 허용 정책 후보 |
| --- | --- |
| local | 허용. 관리자 토큰 필요 |
| test | 허용. 테스트 fixture에서 관리자 토큰으로 검증 |
| dev | 기본 허용 후보. 관리자 토큰 + audit log 필요 |
| prod | 기본 비활성화 권장. 긴급 운영용으로 열 경우 명시 feature flag 필요 |

운영 실수 방지 후보:
- `reservation.mock-result-api.enabled=false`를 prod 기본값으로 둔다.
- endpoint 또는 service 진입부에서 feature flag를 확인한다.
- prod에서 enable하려면 별도 배포 설정과 감사 로그를 요구한다.
- Mock 결과 반영 시 `provider=MOCK_ADMIN` 또는 별도 audit event를 남긴다.
- 요청 body에 `adminReason` 필드를 추가할지 검토한다.
- 실제 provider webhook이 안정화되면 prod에서는 Mock API를 제거하거나 내부망/admin-only 운영 도구로만 제한한다.

권장안:
- local/test/dev에서는 관리자 권한 + 명시 설정으로 유지한다.
- prod에서는 기본 비활성화한다.
- 실제 provider 도입 후에는 provider webhook 결과를 정식 경로로 보고, Mock API는 장애 대응 / QA 전용으로 낮춘다.

## DB 변경 후보
이번 단계에서는 DB migration을 만들지 않는다.

후속 후보:
- `reservation_provider_events` 테이블 추가
- `reservation_call_attempts` 테이블 추가
- `restaurant_reservations.failure_code` 컬럼 추가 검토
- `restaurant_reservations.next_retry_at` 컬럼 추가 검토
- `provider_call_id` index와 event ledger unique key 확정

## 구현 단계 후보
1. 설계 검토
   - provider 후보(Twilio/SIP/OpenAI Realtime 조합) 결정
   - provider event id 제공 여부 확인
   - signature 검증 방식 확인
2. DB 구조 추가
   - `reservation_provider_events` 우선 추가
   - 필요 시 `reservation_call_attempts`, `failure_code`, `next_retry_at` 추가
3. Webhook mapper 구현
   - provider raw payload를 내부 표준 이벤트로 변환
   - raw payload 직접 도메인 반영 금지
4. Webhook security verifier 구현
   - HMAC signature
   - timestamp skew
   - replay/idempotency
5. Event processing service 구현
   - event ledger 저장
   - reservation row lock
   - 상태 전이
   - 중복/역순 이벤트 무시
6. Retry scheduler 구현
   - retryable failure 조회
   - attemptCount / call attempts 반영
   - 최종 실패 처리
7. 관리자 Mock API 운영 제한 보강
   - prod feature flag
   - audit log
   - 필요 시 `adminReason`

## 검증 방법 후보
문서 단계:
- `git diff --check`

후속 구현 단계:
- signature 검증 단위 테스트
- timestamp window 테스트
- duplicate webhook 테스트
- out-of-order webhook 테스트
- terminal 상태 덮어쓰기 방지 테스트
- retry count / timeout 테스트
- USER token이 webhook 또는 admin mock API를 호출하지 못하는 E2E 테스트
- 전체 `bash ./gradlew test`

## 리스크
- provider마다 event id, signature base string, retry semantics가 다를 수 있다.
- raw payload 저장은 개인정보/통화정보 보관 리스크가 있다.
- webhook이 지연 또는 역순으로 도착할 수 있다.
- retry가 과하면 식당에 중복 전화가 갈 수 있다.
- terminal 상태를 뒤늦은 이벤트가 덮어쓰면 사용자에게 잘못된 예약 결과가 보일 수 있다.
- prod에서 Mock API가 열려 있으면 상태 조작 위험이 있다.

## Progress
- [x] 계획 작성 완료
- [ ] 사용자 검토 / 승인 완료
- [x] 구현 시작
- [x] 중간 검증 완료
- [x] 최종 검증 완료
- [x] 문서 반영 완료

## 결정 사항 / 변경 로그
- 2026-05-20: 실제 provider webhook은 사용자 JWT 인증과 분리된 `/webhooks/**` 계열로 설계한다.
- 2026-05-20: provider raw payload는 내부 표준 이벤트로 변환한 뒤 도메인에 반영한다.
- 2026-05-20: idempotency는 `provider + providerEventId`를 우선 기준으로 보고, provider event id가 없으면 call id/type/occurredAt/payload hash 조합을 fallback 후보로 둔다.
- 2026-05-20: `reservation_provider_events` event ledger 테이블을 우선 후보로 둔다.
- 2026-05-20: retry는 최대 3회, 1분/5분 backoff를 기본 후보로 둔다.
- 2026-05-20: webhook 보안은 HMAC signature + timestamp window + idempotency replay 방어 조합을 권장한다.
- 2026-05-20: 관리자 Mock API는 prod 기본 비활성화, local/test/dev 관리자 전용 유지 후보로 둔다.
- 2026-05-20: `docs/db/reservations.md`에는 provider event ledger와 call attempt 테이블 후보를 연결하고, `docs/current-gaps.md`에는 provider별 구현값과 prod Mock API 운영 제한을 미확정 항목으로 남긴다.
- 2026-05-20: 실제 webhook endpoint를 열지 않고 내부 표준 이벤트 command, event ledger Entity/Repository, `ReservationProviderEventService`, mock/test용 HMAC verifier를 추가한다.
- 2026-05-20: 같은 idempotency key는 중복 처리하지 않고, terminal 예약에 늦게 도착한 event는 `IGNORED_STALE`, provider 식별자 불일치는 `REJECTED_INVALID`, 보안 검증 실패는 상태 변경 없이 `REJECTED_SECURITY`로 처리한다.
- 2026-05-20: `bash ./gradlew test` 전체 테스트 통과를 확인했다.
- 2026-05-20: mock/test provider 전용 `POST /webhooks/reservations/call-providers/mock` endpoint skeleton을 추가했다. endpoint는 `reservation.webhook.mock.enabled=true`일 때만 등록되며, raw payload HMAC/timestamp 검증 후 내부 표준 이벤트 처리 구조를 호출한다.
- 2026-05-20: `ReservationCallAttempt` / `ReservationCallAttemptService` / `ReservationRetryPolicy`를 추가해 provider event 기반 전화 시도 기록, retry 가능 실패, 최종 실패, timeout 후보 조회 기반을 구현했다.
- 2026-05-20: `ReservationRetryTimeoutScheduler` / `ReservationRetryTimeoutSchedulerService` / `ReservationSchedulerExecutionGuard`를 추가해 실제 provider 호출 없이 retry/timeout scheduler 골격을 구현했다. `reservation.scheduler.enabled=true`일 때만 scheduler bean을 등록하며, 현재 retry는 Mock/no-op dispatch만 수행한다.

## 완료 조건
- 실제 provider 구현 전 필요한 webhook, idempotency, retry, security 설계가 문서화되어 있다.
- 예약 정책 문서와 current-gaps에 후속 반영 후보가 정리되어 있다.
- 실제 OpenAI/전화 provider webhook endpoint 구현이 없다. mock/test provider endpoint skeleton만 허용한다.
- seed 전화번호와 추천/랭킹 로직을 수정하지 않는다.
