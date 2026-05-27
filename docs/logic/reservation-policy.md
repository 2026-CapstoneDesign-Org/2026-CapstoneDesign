# AI 전화 예약 정책

기준 날짜 및 시간: 2026-05-26 (Asia/Seoul)

## 1. 범위
이 문서는 AI 전화 예약 MVP의 현재 코드 기준 동작을 정리한다.

대상 코드:
- `ReservationController`
- `ReservationProviderWebhookController`
- `ClawOpsInboundWebhookController`
- `ReservationService`
- `ReservationProviderEventService`
- `ReservationCallAttemptService`
- `ReservationRetryTimeoutScheduler`
- `ReservationRetryTimeoutSchedulerService`
- `ReservationSchedulerExecutionGuard`
- `RestaurantReservation`
- `ReservationStatus`
- `ReservationProviderEvent`
- `ReservationCallAttempt`
- `RestaurantReservationRepository`
- `ReservationProviderEventRepository`
- `ReservationCallAttemptRepository`
- `ReservationCallProvider`
- `SafeReservationCallProvider`
- `MockReservationCallProvider`
- `NoopOpenAiRealtimeClient`
- `NoopPhoneProviderClient`
- `ReservationProviderProperties`
- `OpenAiRealtimeProperties`
- `TwilioVoiceProperties`
- `ClawOpsProperties`
- `ClawOpsCreateCallRequest`
- `ClawOpsCreateCallResponse`
- `ClawOpsRestClient`
- `ClawOpsPhoneProviderClient`
- `ClawOpsSidecarClient`
- `ClawOpsSidecarPhoneProviderClient`
- `ClawOpsSidecarCallRequest`
- `ClawOpsSidecarCallResponse`
- `ClawOpsSidecarReadinessResponse`
- `ClawOpsSidecarInternalSignature`
- `ClawOpsRealCallPreflightService`
- `ClawOpsWebhookMapper`
- `ClawOpsWebhookSecurityVerifier`
- `InternalReservationProviderEventController`
- `ClawOpsRealCallPreflightRequest`
- `ClawOpsRealCallPreflightResponse`
- `ClawOpsAgentProviderEventRequest`
- `MockReservationProviderWebhookMapper`
- `MockReservationProviderWebhookSecurityVerifier`

## 2. 현재 구현된 기능
- AI 전화 예약 요청 생성
- 내 예약 목록 조회
- 예약 상세 조회
- 예약 취소
- Mock 결과 반영
- Mock/test provider webhook
- ClawOps 번호 인바운드 fallback webhook
- 전화 시도(call attempt) 기록
- retry/timeout 대상 식별 기반
- retry/timeout scheduler 골격
- ClawOps 실제 발신 전 preflight 검증
- ClawOps sidecar runtime dry-run HTTP 계약
- ClawOps sidecar readiness preflight
- ClawOps sidecar -> Spring internal event endpoint skeleton
- Voice Agent prompt / result schema 설계 샘플

기본 예약 경로에서는 실제 식당 전화 발신, OpenAI Realtime API, Twilio/SIP/ClawOps 연동을 하지 않는다. 실제 provider는 dev-only 수동 승인 게이트를 통과한 PoC에서만 별도 취급한다.

실제 전화 provider webhook은 아직 구현하지 않았지만, mock/test provider용 webhook endpoint 골격과 내부 표준 이벤트 처리 구조는 준비되어 있다.
retry/timeout scheduler 골격은 준비되어 있지만 실제 provider 호출 대신 Mock/no-op 흐름만 수행한다.

2026-05-24 기준 ClawOps는 실제 provider PoC 후보로 검토되었다. 결론은 provider abstraction을 유지한 채 ClawOps adapter를 먼저 PoC하는 방향이며, Twilio + OpenAI Realtime bridge 계획은 삭제하지 않고 비교 / 대체 후보로 유지한다.
현재 코드에는 `CLAWOPS` mode, 설정 properties, REST 요청/응답 계약 DTO, fake-server 전용 REST client, call adapter, status callback mapper, HMAC verifier skeleton이 추가되어 있다. dev allowlist 조건에서 실제 ClawOps call create를 1회 시도했으나 `NOOP_CLAWOPS_HTTP_ERROR`로 종료되었고, 성공 call id 저장이나 추가 재시도는 없었다. 이후 `reservation.provider.runtime`으로 `DIRECT_REST`와 `CLAWOPS_SIDECAR`를 분리했고, sidecar runtime은 local fake/dry-run HTTP 계약과 internal event endpoint skeleton까지만 구현했다. 실제 Voice Agent 실행, ClawOps API 호출, OpenAI API 호출, 전화 발신은 아직 없다.

## 3. API
### 3-1. 예약 요청 생성
```http
POST /restaurants/{restaurantId}/reservations/ai-call
```

인증:
- 필요
- JWT access token의 subject를 `@AuthenticationPrincipal Long userId`로 받는다.

요청:
- `reservationDate`
- `reservationTime`
- `partySize`
- `requestNote`

현재 동작:
- 요청 사용자가 삭제되지 않은 사용자여야 한다.
- 대상 식당은 `isDeleted = false`, `isHidden = false` 식당이어야 한다.
- 대상 식당에 전화번호가 있어야 한다.
- 과거 시점 예약은 거부한다.
- 인원 수는 `1 ~ 20`이어야 한다.
- 동일 사용자 / 동일 식당 / 동일 예약 시각의 충돌 가능 예약이 있으면 거부한다.
- 생성된 예약은 `REQUESTED` 상태로 저장한다.
- Mock provider 시작 메타데이터를 저장하지만 실제 전화는 걸지 않는다.

### 3-2. 내 예약 목록 조회
```http
GET /reservations
```

인증:
- 필요

현재 동작:
- 현재 로그인 사용자의 예약만 반환한다.
- 정렬은 `reservationDateTime desc`, `id desc`다.

### 3-3. 예약 상세 조회
```http
GET /reservations/{reservationId}
```

인증:
- 필요

현재 동작:
- 예약 소유자만 조회할 수 있다.
- 다른 사용자의 예약이면 `403 FORBIDDEN`으로 처리한다.

### 3-4. 예약 취소
```http
PATCH /reservations/{reservationId}/cancel
```

인증:
- 필요

현재 동작:
- 예약 소유자만 취소할 수 있다.
- 취소 가능 상태는 `REQUESTED`, `NEEDS_CONFIRMATION`이다.
- 취소 성공 시 `CANCELED` 상태가 된다.
- 실제 식당에 취소 전화를 거는 기능은 현재 없다.

### 3-5. Mock 결과 반영
```http
POST /admin/reservations/{reservationId}/mock-result
```

인증:
- 필요
- 관리자 권한 필요
- 기존 관리자 API와 같이 `@PreAuthorize("hasRole('ADMIN')")` 기준으로 제한한다.

현재 동작:
- 일반 사용자는 Mock 결과를 반영할 수 없다.
- 관리자/test 운영 용도로만 Mock 결과를 반영할 수 있다.
- 실제 전화 provider 호출 없이 상태와 결과 메시지를 저장한다.
- `REQUESTED`, `CANCELED`은 Mock 결과 상태로 직접 반영할 수 없다.

### 3-6. ClawOps 실제 발신 preflight
```http
POST /admin/reservations/clawops-real-call/preflight
```

인증:
- 필요
- 관리자 권한 필요
- 기존 관리자 API와 같이 `@PreAuthorize("hasRole('ADMIN')")` 기준으로 제한한다.

요청:
- `reservationId`: 특정 예약 기준 검증이 필요할 때 사용
- `targetPhoneNumber`: 특정 테스트 번호 기준 검증이 필요할 때 사용

현재 동작:
- 실제 ClawOps API, OpenAI API, 전화 provider를 호출하지 않는다.
- 특정 예약이 주어지면 예약의 `restaurantPhoneNumberSnapshot`을 기준으로 검증한다.
- 특정 번호만 주어지면 예약 대상 검증 없이 테스트 번호 기준 preflight로 처리하고 `RESERVATION_NOT_SELECTED` 경고를 반환한다.
- 응답에는 실제 발신 가능 후보 여부, 차단 사유, 경고, 마스킹된 실제 대상 번호, 마스킹된 DB phone snapshot, dev target override 적용 여부, provider mode, provider runtime, active profile, scheduler 활성 여부, 예약 id / 식당 id / 예약 상태, ClawOps 설정 누락 항목 이름을 반환한다.
- API key, webhook signing key 같은 secret 원문은 응답과 로그에 남기지 않는다.
- 실제 발신 후보가 되려면 dev profile, prod profile 미포함, `CLAWOPS` mode, `calling-enabled=true`, `real-call-enabled=true`, allowlist 활성화 / 비어 있지 않음 / 대상 번호 일치, runtime별 ClawOps 필수 설정, scheduler 비활성화 조건을 모두 만족해야 한다.
- `DIRECT_REST` runtime에서는 기존처럼 non-local ClawOps endpoint와 direct REST 필수 설정을 확인한다.
- `CLAWOPS_SIDECAR` runtime에서는 Spring-side sidecar base URL / internal signing key를 확인하고, Spring ClawOps API key는 필수값으로 보지 않는다.
- `CLAWOPS_SIDECAR` runtime에서 sidecar base URL은 local dry-run endpoint(`localhost`, `127.0.0.1`, `::1`)일 때만 Spring HTTP 계약 호출 후보가 된다. non-local sidecar endpoint는 현재 단계에서 preflight 차단 / no-op 처리한다.
- `clawops.sidecar-readiness-required=true`이면 Spring은 fake/dry-run sidecar의 `GET /internal/clawops-agent/readiness`를 HMAC 서명과 timestamp header로 호출한다. `ready=false`, HTTP 오류, network 오류, timeout은 preflight 차단 사유가 된다.
- sidecar readiness 응답은 secret 원문을 받지 않고 `ready`, profile, realCallEnabled, allowlistCount, configured boolean, agentRuntime, warnings 같은 상태값만 사용한다.
- 예약 기준 preflight에서는 기본적으로 예약 상태가 `REQUESTED`이고 전화번호 snapshot이 allowlist에 있을 때만 테스트 예약 후보로 본다.
- dev PoC에서 서버 DB의 `restaurantPhoneNumberSnapshot`을 수정할 수 없는 경우에만 `reservation.provider.dev-target-phone-override-enabled=true`와 `reservation.provider.dev-target-phone-override-number`로 provider 직전 대상 번호를 allowlist 테스트 번호로 override할 수 있다. 이 override는 DB snapshot을 변경하지 않고, prod profile에서는 어떤 설정 조합으로도 동작하지 않는다.
- dev target override가 통과하려면 dev profile, prod profile 미포함, `CLAWOPS` mode, `calling-enabled=true`, `real-call-enabled=true`, allowlist 활성화 / 비어 있지 않음, override enabled, override number 존재, override number allowlist 포함, ClawOps 필수 설정을 모두 만족해야 한다.

주요 차단 사유:
- `DEV_PROFILE_REQUIRED`
- `PROD_PROFILE_BLOCKED`
- `CLAWOPS_MODE_REQUIRED`
- `CALLING_DISABLED`
- `REAL_CALL_DISABLED`
- `ALLOWLIST_DISABLED`
- `ALLOWLIST_EMPTY`
- `TARGET_PHONE_REQUIRED`
- `TARGET_NOT_ALLOWLISTED`
- `DEV_TARGET_PHONE_OVERRIDE_DISABLED`
- `DEV_TARGET_PHONE_OVERRIDE_NUMBER_MISSING`
- `DEV_TARGET_PHONE_OVERRIDE_NOT_ALLOWLISTED`
- `CLAWOPS_REQUIRED_SETTINGS_MISSING`
- `CLAWOPS_REAL_ENDPOINT_REQUIRED`
- `CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED`
- `CLAWOPS_SIDECAR_READINESS_NOT_READY`
- `CLAWOPS_SIDECAR_READINESS_HTTP_ERROR`
- `CLAWOPS_SIDECAR_READINESS_NETWORK_ERROR`
- `CLAWOPS_SIDECAR_READINESS_TIMEOUT`
- `SCHEDULER_ENABLED`
- `RESERVATION_NOT_FOUND`
- `RESERVATION_NOT_REQUESTED`
- `TARGET_PHONE_MISMATCHES_RESERVATION_SNAPSHOT`

비범위:
- 실제 ClawOps call 생성
- 실제 OpenAI / Voice Agent 실행
- 실제 provider webhook endpoint 공개
- 수동 승인 없이 발신을 시작하는 기능

### 3-7. Mock provider webhook
```http
POST /webhooks/reservations/call-providers/mock
```

인증:
- 사용자 JWT 인증을 사용하지 않는다.
- provider webhook 검증용 HMAC signature와 timestamp를 검증한다.
- 현재 mock/test provider 전용이며 `reservation.webhook.mock.enabled=true`일 때만 controller bean이 등록된다.
- 테스트 설정에서는 활성화되어 있지만, 운영 기본값은 비활성화다.

필수 header:
- `X-Mock-Reservation-Timestamp`
- `X-Mock-Reservation-Signature`

현재 동작:
- raw payload를 먼저 signature 검증한다.
- signature 또는 timestamp 검증 실패 시 `401 UNAUTHORIZED`로 응답하고 예약 상태를 변경하지 않는다.
- 검증 통과 후 JSON payload를 `MockReservationProviderWebhookRequest`로 해석한다.
- provider payload를 직접 `RestaurantReservation`에 반영하지 않고 `ReservationProviderEventCommand`로 변환한 뒤 `ReservationProviderEventService`를 호출한다.
- 정상 이벤트는 `reservation_provider_events`에 기록하고 허용된 상태 전이를 수행한다.
- 같은 idempotency key는 `IGNORED_DUPLICATE`로 응답하고 상태를 다시 바꾸지 않는다.
- 존재하지 않는 예약, provider call id 불일치, 허용되지 않는 상태 전이는 `REJECTED_INVALID`로 기록하고 상태를 변경하지 않는다.
- terminal 상태 예약에 늦게 도착한 이벤트는 `IGNORED_STALE`로 기록하고 상태를 덮어쓰지 않는다.

비범위:
- 실제 Twilio/SIP/OpenAI Realtime webhook 수신
- 실제 provider signature 규격
- 실제 전화 발신 또는 AI API 호출
- 실제 전화 provider 호출 retry 실행

### 3-8. ClawOps 번호 인바운드 fallback webhook
```http
GET /webhooks/reservations/call-providers/clawops/inbound
POST /webhooks/reservations/call-providers/clawops/inbound
```

인증:
- 사용자 JWT 인증을 사용하지 않는다.
- ClawOps 전화번호의 인바운드 라우팅 / 온보딩 fallback 확인용 endpoint다.

현재 동작:
- 예약 DB, provider event ledger, call attempt를 수정하지 않는다.
- ClawOps가 GET 또는 POST로 호출하면 정적인 Voice XML을 반환한다.
- 응답은 짧은 한국어 안내 후 통화를 종료하는 XML이다.
- ClawOps 콘솔의 전화번호 Webhook URL에는 Swagger UI 주소가 아니라 이 endpoint의 공개 HTTPS URL을 등록해야 한다.
- dev 배포 도메인이 `https://wagu.uk`이면 등록 후보 URL은 `https://wagu.uk/webhooks/reservations/call-providers/clawops/inbound`다.

비범위:
- ClawOps status callback 처리
- ClawOps webhook signature 검증
- 예약 상태 전이
- 녹음 / transcript / summary 저장

### 3-9. ClawOps sidecar internal event endpoint
```http
POST /internal/reservations/provider-events/clawops-agent
```

인증:
- 사용자 JWT 인증을 사용하지 않는다.
- `X-Request-Timestamp`, `X-Internal-Signature`를 controller에서 직접 검증한다.
- signature base string은 `timestamp + "\n" + rawBody`이고 HMAC-SHA256 Base64를 사용한다.
- timestamp가 `reservation.provider.webhook-max-clock-skew-seconds` 범위를 벗어나거나 signature가 다르면 `401 UNAUTHORIZED`로 응답한다.

요청:
- `provider`
- `reservationId`
- `providerCallId`
- `sidecarCallId`
- `eventType`
- `providerStatus`
- `occurredAt`
- `retryable`
- `failureReason`
- `aiSummary`
- `resultMessage`
- `idempotencyKey`
- `rawPayloadHash`

현재 동작:
- 사용자 API와 분리된 internal endpoint skeleton이다.
- `provider`가 없으면 `CLAWOPS_SIDECAR`로 처리한다.
- `eventType`이 없으면 dry-run 기본 이벤트인 `CALL_QUEUED`로 처리한다.
- `idempotencyKey`는 `ReservationProviderEventCommand.providerEventId`로 매핑해 기존 event ledger idempotency를 재사용한다.
- `CALL_QUEUED` + target status 없음 이벤트는 `reservation_provider_events` ledger에만 `PROCESSED`로 기록하고 예약 상태, provider, provider call id를 변경하지 않는다.
- duplicate idempotency key는 기존처럼 `IGNORED_DUPLICATE`로 응답한다.

비범위:
- 실제 ClawOps / OpenAI / 전화 provider callback 공개
- recording 저장 / recording webhook / 녹음 파일 보관
- 실제 raw transcript 저장
- sidecar event delivery retry / ack

### 3-10. Voice Agent prompt / result schema
현재 단계는 Python sidecar에서 사용할 AI 상담원 prompt와 결과 schema를 설계한 상태다. 실제 Python Voice Agent 실행, OpenAI API 호출, ClawOps API 호출, 전화 발신은 하지 않는다.

설계 파일:
- `sidecars/clawops-voice-agent/prompts/reservation_agent_prompt.md`
- `sidecars/clawops-voice-agent/contracts/reservation_result_schema.json`
- `sidecars/clawops-voice-agent/contracts/samples/*.json`
- `sidecars/clawops-voice-agent/contracts/spring-event-payloads/*.json`
- `sidecars/clawops-voice-agent/reservation_result_mapper.py`
- `sidecars/clawops-voice-agent/spring_event_dispatch_candidate.py`
- `sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`

AI 상담원 역할:
- 식당 예약 문의를 대행하는 AI 상담원이다.
- 통화 초반에 "예약 가능 여부 확인을 위해 전화드린 AI 예약 도우미"임을 짧고 자연스럽게 밝힌다.
- 날짜, 시간, 인원 수를 시스템에서 받은 값 그대로 전달한다.
- 예약자명과 연락처는 항상 시스템에서 제공되는 값으로 전제한다.
- 식당이 예약자명이나 연락처를 요구하면 제공할 수 있지만, 예약자명 / 연락처 값이 없어서 실패하는 시나리오는 기본 설계에서 제외한다.
- 요청 조건을 임의로 바꾸거나 대체 시간을 임의 생성하지 않는다.
- 식당이 대체 시간, 예약금, 추가 개인정보, 특수 조건을 제시하면 사용자 확인이 필요하므로 `NEEDS_CONFIRMATION` 후보로 분류한다.

결과 status 후보:
- `CONFIRMED`: 요청한 날짜 / 시간 / 인원 수로 명확히 예약 확정
- `UNAVAILABLE`: 요청 조건이 명확히 불가이고 대체 확인 대상이 없음
- `NEEDS_CONFIRMATION`: 대체 시간 제안, 예약금 / 추가 개인정보 / 특수 조건, 직원 이해 부족, 모호한 응답 등 사용자 확인 필요
- `FAILED`: 전화 연결 실패, provider / sidecar 기술 실패처럼 통화 자체를 완료하지 못한 경우

결과 schema 필드:
- `resultStatus`
- `summary`
- `confirmedDateTime`
- `partySize`
- `reservationNameProvided`
- `phoneNumberProvided`
- `restaurantRequestedNameOrPhone`
- `alternativeTimeSuggested`
- `alternativeDateTime`
- `failureReason`
- `transcriptSummary`

해석 기준:
- `reservationNameProvided`, `phoneNumberProvided`는 값의 존재 여부가 아니라 통화 중 식당에 전달했는지 여부다.
- `restaurantRequestedNameOrPhone`은 식당이 예약자명 또는 연락처를 요구했는지 여부다.
- `transcriptSummary`는 AI가 생성한 통화 요약 후보이며 녹음 기반 transcript가 아니다.
- 통화 녹음 기능, recording webhook, 녹음 파일 저장 / 보관 정책은 이번 설계 범위에 없다.

Spring internal event mapping 후보:
- `CONFIRMED` -> `ReservationProviderEventType.RESERVATION_CONFIRMED`, target status `CONFIRMED`
- `UNAVAILABLE` -> `ReservationProviderEventType.RESERVATION_UNAVAILABLE`, target status `UNAVAILABLE`
- `NEEDS_CONFIRMATION` -> `ReservationProviderEventType.RESERVATION_NEEDS_CONFIRMATION`, target status `NEEDS_CONFIRMATION`
- `FAILED` -> 연결 / provider 오류 종류에 따라 `CALL_CONNECTION_FAILED`, `CALL_NO_ANSWER`, `CALL_BUSY`, `PROVIDER_TRANSIENT_ERROR`, `PROVIDER_FATAL_ERROR` 중 하나로 변환한다. retry 가능 여부와 최종 실패 전이는 Spring retry / timeout 정책이 판단한다.
- 모델 출력이 schema validation에 실패하거나 판단이 모호하면 `AI_PARSE_FAILED` 또는 `RESERVATION_NEEDS_CONFIRMATION` 후보로 처리하고, 바로 `CONFIRMED`로 전이하지 않는다.
- `reservation_result_mapper.py`는 sidecar 내부 dry-run 순수 mapper로, schema 결과를 Spring `ClawOpsAgentProviderEventRequest` shape의 dict로 바꾼다. Spring HTTP 호출과 DB 접근은 하지 않는다.
- `spring_event_dispatch_candidate.py`는 mapper 결과를 Spring internal event path, raw JSON body, timestamp, HMAC header 후보로 감싼다. 이 단계에서도 실제 Spring endpoint로 HTTP 전송하지 않는다.
- dry-run `/internal/clawops-agent/calls` 응답은 샘플 AI 결과를 사용해 `springEventPreview`를 만들 수 있다. preview에는 payload와 header 이름만 포함하고 실제 signature 값은 노출하지 않는다.
- `contracts/spring-event-payloads/*.json`은 Python mapper/builder 출력과 Spring `ClawOpsAgentProviderEventRequest` DTO 호환성을 함께 검증하는 공유 fixture다.
- Spring E2E 테스트는 같은 fixture를 읽어 `/internal/reservations/provider-events/clawops-agent` test endpoint로 보내고, event ledger/idempotency와 예약 상태 전이 후보가 안전하게 처리되는지 확인한다.
- contract drift guard는 `python3 sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`로 실행한다. 이 guard는 Python contract tests, Spring internal event fixture E2E, fixture/document 민감값 스캔을 실행한다.
- Spring event fixture를 수정할 때는 Python builder fixture 일치 테스트와 Spring E2E fixture 기대값을 같은 변경에서 갱신해야 한다.
- `CONFIRMED` 결과라도 `confirmedDateTime` 또는 `partySize`가 기존 예약 요청과 충돌하면 `RESERVATION_CONFIRMED`로 보내지 않고 `RESERVATION_NEEDS_CONFIRMATION` + `AI_RESULT_CONFLICT`로 보낸다.
- 필수 필드 누락, status 오류, boolean / partySize 타입 오류, 확정 결과의 핵심 필드 누락은 `AI_PARSE_FAILED` 후보로 보낸다.
- event payload에는 `provider`, `reservationId`, `providerCallId`, `sidecarCallId`, `eventType`, `providerStatus`, `occurredAt`, `retryable`, `failureReason`, `aiSummary`, `resultMessage`, `idempotencyKey`, `rawPayloadHash`를 채운다.

### 3-10. 실제 연동 전 local-only 승인 게이트
현재 단계는 실제 ClawOps API 호출, 실제 OpenAI API 호출, 실제 전화 발신, 실제 Voice Agent 실행을 하지 않는다. 다음 실제 연동 후보 단계로 넘어가더라도 아래 조건을 모두 통과하기 전에는 실제 호출을 시작하지 않는다.

필수 조건:
- active profile에 `dev`가 포함되어야 한다.
- active profile에 `prod`가 포함되면 안 된다.
- retry/timeout scheduler는 비활성화되어야 한다.
- provider mode는 `CLAWOPS`, provider runtime은 `CLAWOPS_SIDECAR`여야 한다.
- allowlist는 동의받은 테스트 번호 1개만 포함해야 한다.
- dev target override를 쓰면 allowlist 번호와 동일해야 한다.
- sidecar endpoint는 local/private endpoint여야 한다.
- Spring internal signing key와 sidecar signing key는 로컬 환경변수 기준으로 일치해야 한다.
- ClawOps/OpenAI secret은 sidecar 로컬 환경변수에만 있어야 하며 Spring 필수 설정으로 두지 않는다.
- `python3 sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`가 통과해야 한다.
- Spring preflight, sidecar readiness, dry-run call 검증이 순서대로 통과해야 한다.

이번 Phase 12는 실제 secret 주입 단계가 아니다. 후속 실제 연동 단계가 별도로 승인되면 사용자가 로컬 환경변수에 직접 값을 넣어야 하며, 문서 / 코드 / 테스트 / 로그에는 실제 secret 값을 남기지 않는다.

실제 전화 발신은 후속 단계에서 사용자가 아래 문구를 직접 제공한 경우에만 1회 테스트 번호 대상으로 후보가 된다.

```text
승인: ClawOps Voice Agent 테스트 번호 1회 발신
```

즉시 중단 조건:
- prod profile 감지
- scheduler enabled
- allowlist가 비어 있거나 2개 이상
- 테스트 번호 소유자 동의 없음
- dev target override와 allowlist 불일치
- sidecar endpoint가 local/private가 아님
- signing key 불일치
- contract drift guard, preflight, readiness, dry-run 중 하나라도 실패
- secret, JWT, bearer token, 원본 전화번호가 fixture / 문서 / 로그에 노출

롤백 절차:
- `RESERVATION_PROVIDER_CALLING_ENABLED=false`
- `RESERVATION_PROVIDER_REAL_CALL_ENABLED=false`
- sidecar process 중지
- sidecar secret 환경변수 제거
- allowlist 비우기 또는 동의받은 테스트 번호 1개만 유지
- scheduler disabled 확인
- mock/no-op provider 회귀 테스트 실행
- contract drift guard 재실행

### 3-11. Voice Agent wiring 후보 경계
현재 sidecar의 기본 runtime은 `dry-run`이며, SDK 미설치 환경에서도 dry-run 테스트가 통과해야 한다. `real-agent` runtime은 local dev PoC 전용 후보로만 사용한다.

sidecar runtime 후보:
- `SIDECAR_RUNTIME_MODE=dry-run`: 기본값이며 현재 유일한 구현 모드
- `SIDECAR_RUNTIME_MODE=real-agent`: 후속 실제 연동 후보 모드
- `SIDECAR_REAL_AGENT_ENABLED=false`: 기본 hard stop
- `SIDECAR_REAL_AGENT_APPROVAL_REQUIRED=true`: 실제 발신 승인 문구를 선행 조건으로 두는 후보
- `SIDECAR_REQUIRE_SPRING_PREFLIGHT=true`: 실제 연동 후보에서 Spring preflight를 선행 조건으로 유지

현재 코드 기준 `dryRun=true` 요청은 기존 dry-run 경로를 유지한다. `dryRun=false`이고 `real-agent` runtime, real-agent enabled, approval granted, Spring preflight 통과, allowlist, secret configured, SDK ready 조건을 모두 만족할 때만 sidecar가 real-agent runner를 호출한다. readiness는 sidecar process / dry-run 준비 상태 확인용으로 분리되어 있으며 real-agent 실행 차단 사유를 readiness 실패로 표현하지 않는다.

후속 실제 SDK wiring 위치 후보:
- `clawops_agent_sidecar.py`: HTTP endpoint, HMAC 검증, allowlist, runtime flag gate만 담당
- `real_agent_interface.py`: 후속 Voice Agent runner의 request / result shape와 SDK-free fake implementation을 정의
- `real_agent_adapter.py`: SDK lazy import adapter gate와 승인 / preflight 의존 조건 평가를 담당
- `real_agent_sdk_runner.py`: SDK-backed runner boundary와 lazy import helper를 정의한다. SDK object 생성은 module import 시점이 아니라 승인된 실행 함수 내부에서만 일어난다.
- `reservation_result_mapper.py`: AI 결과 schema를 Spring event payload로 변환하는 순수 mapper로 유지
- `spring_event_dispatch_candidate.py`: Spring internal event body/header 생성기로 유지
- `spring_event_delivery_policy.py`: 실제 HTTP 전송 없이 Spring event delivery / retry / ack 후보를 local fake result로만 분류

실제 secret 값은 별도 승인된 real-agent 실행에서만 sidecar 로컬 환경변수로 사용한다. Spring에는 ClawOps/OpenAI API key를 두지 않고 sidecar 내부에서만 사용한다.

Phase 14 기준 fake real-agent implementation은 `CONFIRMED`, `UNAVAILABLE`, `NEEDS_CONFIRMATION`, `FAILED` 샘플 결과만 반환한다. 이 결과는 기존 `reservation_result_mapper.py`와 `spring_event_dispatch_candidate.py`를 통해 Spring internal event candidate로 변환되는지 테스트한다. 실제 ClawOps SDK import, OpenAI SDK import, Voice Agent 실행, secret 주입, 전화 발신은 여전히 없다.

Phase 15 기준 real-agent adapter skeleton은 아래 조건을 평가하되 실제 실행은 하지 않는다.
- runtime mode가 `real-agent`
- `SIDECAR_REAL_AGENT_ENABLED=true`
- Spring preflight 통과 후보
- allowlist 통과
- 승인 게이트 통과 후보
- 필요한 real-agent 환경변수 이름 후보 존재: `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, `CLAWOPS_FROM_NUMBER`, `OPENAI_API_KEY`
- SDK 설치 후보

차단 사유 후보:
- `REAL_AGENT_DISABLED`
- `APPROVAL_REQUIRED`
- `SPRING_PREFLIGHT_REQUIRED`
- `SDK_NOT_INSTALLED`
- `SECRET_MISSING`
- `TARGET_NOT_ALLOWLISTED`
- `REAL_AGENT_NOT_IMPLEMENTED`

기본 gate 평가에서는 구현 readiness를 명시하지 않으면 `REAL_AGENT_NOT_IMPLEMENTED`로 차단한다. sidecar call endpoint는 승인된 real-agent 요청에서만 implementation ready 후보로 평가한다. 실제 SDK import는 module import 시점에 하지 않으며 함수 내부 lazy import로만 수행한다.

Phase 16 기준 sidecar dry-run call 응답에는 `realAgentGatePreview`를 진단용으로 포함한다. 이 preview는 adapter gate를 평가만 하며 `RealAgentAdapter.run_reservation_call()`을 실행하지 않는다. 포함 필드는 runtime mode, realAgentEnabled, approvalRequired, springPreflightRequired, allowed / blocked, blockReasons, missingRequiredEnvNames, sdkInstalled 정도로 제한한다. secret 값, signing key 값, JWT, 원본 전화번호는 preview에 포함하지 않는다.

Phase 17 기준 Spring `ClawOpsSidecarCallResponse`는 `realAgentGatePreview`를 business field로 추가하지 않고 unknown field ignore 정책으로 호환한다. 이 preview는 진단용 optional field이며 예약 상태 전이, provider 상태 판단, retry 판단에 사용하지 않는다.

Phase 19~20 기준 sidecar -> Spring event delivery와 retry / ack는 운영 구현이 아니라 local/fake 정책 후보만 둔다. `spring_event_delivery_policy.py`는 fake transport 결과를 `ACKED`, `DUPLICATE_ACKED`, `RETRYABLE_FAILURE`, `NON_RETRYABLE_FAILURE`로 분류한다. 실제 HTTP delivery client, retry loop, background worker, scheduler, queue는 만들지 않는다.

현재 `real_agent_sdk_runner.py`는 승인된 local real-agent 실행 경로에서 prompt load, OpenAI Realtime session config, ClawOpsAgent config, result tool 등록, outbound call 후보, result wait, disconnect-finally를 수행한다. SDK import와 object 생성은 실행 함수 내부 lazy boundary 뒤에 있다. `MockedRealAgentSdkRunner`는 SDK object 없이 sample 결과를 같은 mapper / dispatch candidate 경로에 태우는 테스트 double이며, confirmed / unavailable / needs-confirmation / failed / tool result missing / 예약 조건 충돌 케이스를 모두 실제 HTTP 전송 없이 검증한다.

### 3-12. 실제 SDK / secret / 발신 전 최종 readiness 기준
현재 dry-run / fake 완료 범위:
- Spring sidecar client는 local dry-run 요청과 unknown diagnostic field ignore를 검증했다.
- Python sidecar readiness와 dry-run call endpoint가 있다.
- `realAgentGatePreview`는 진단용 optional field이며 adapter를 실행하지 않는다.
- 예약 결과 schema, mapper, Spring dispatch candidate, shared fixture, Spring internal event endpoint skeleton, event ledger / idempotency 흐름이 있다.
- sidecar -> Spring delivery / retry / ack는 local fake policy 후보로만 검증했다.
- contract drift guard가 Python tests, Spring fixture E2E, sidecar 문서 / 코드 민감값 스캔을 묶어 실행한다.

실제 연동 전 남은 작업 분류:
- SDK wiring 상태: `sidecars/clawops-voice-agent/requirements-real-agent.txt`에 real-agent용 opt-in `clawops[agent,openai]`와 `websockets>=13,<16` dependency를 명시했다.
- secret 환경변수 상태: 실제 값은 sidecar 로컬 환경변수에만 둔다. 문서 / 코드 / 테스트 / 로그에는 값을 남기지 않는다.
- 실제 Voice Agent 실행 상태: local approved execution path는 준비되었지만, 각 실제 발신 시도는 별도 승인과 안전 체크가 필요하다.
- 실제 전화 발신 승인 필요: allowlist 테스트 번호 1회 발신마다 승인 문구가 필요하다.
- 문서 / 테스트만 가능: fake policy 테스트, guard 개선, readiness checklist 유지, runbook 업데이트.

환경변수 이름은 문서화할 수 있지만 실제 값은 남기지 않는다.
- Spring: `RESERVATION_PROVIDER_RUNTIME`, `RESERVATION_PROVIDER_CALLING_ENABLED`, `RESERVATION_PROVIDER_REAL_CALL_ENABLED`, `CLAWOPS_SIDECAR_BASE_URL`, `CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY`
- Spring safety: `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`, `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED`, `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER`
- sidecar runtime: `SIDECAR_PROFILE`, `SIDECAR_RUNTIME_MODE`, `SIDECAR_REAL_CALL_ENABLED`, `SIDECAR_REAL_AGENT_ENABLED`, `SIDECAR_REAL_AGENT_APPROVAL_REQUIRED`, `SIDECAR_REQUIRE_SPRING_PREFLIGHT`
- sidecar networking / auth: `SIDECAR_ALLOWED_TARGET_NUMBERS`, `SIDECAR_INTERNAL_SIGNING_KEY`, `SIDECAR_BIND_HOST`, `SIDECAR_PORT`, `SPRING_INTERNAL_BASE_URL`, `SPRING_INTERNAL_SIGNING_KEY`
- ClawOps / OpenAI future secrets: `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, `CLAWOPS_FROM_NUMBER`, `OPENAI_API_KEY`

실제 secret 값이 없거나 configured 상태가 아니면 local real-agent 실행 직전 `이제 사용자가 로컬 환경변수에 직접 값을 넣어야 하는 타이밍입니다`라고 보고하고 멈춘다.

real-agent 전용 가상환경에서 `requirements-real-agent.txt` 설치와 import availability 확인은 완료했다. `clawops[agent,openai]` 설치로 `clawops`와 `openai` module import가 가능함을 확인했고, OpenAI Realtime WebSocket 준비를 위해 OpenAI 2.x metadata의 realtime extra bound와 같은 `websockets>=13,<16`을 명시했다. 직접 `openai` dependency는 추가하지 않는다.

설치된 SDK surface 확인 결과:
- `clawops.ClawOps`, `clawops.AsyncClawOps`, `client.calls.create(...)`, `client.webhooks.verify(...)`는 Python 3.9 venv에서 import / signature 확인이 가능하다.
- `clawops.agent`는 Python 3.9.6 venv에서 `enum.StrEnum` 부재로 import 실패한다.
- Python 3.12.13 venv `.codex/venvs/clawops-voice-agent-real-agent-py312`에서는 `clawops.agent.ClawOpsAgent`, `clawops.agent.OpenAIRealtime`, OpenAI Realtime resource module import가 가능하다.
- 명시적 `websockets>=13,<16` 추가 후 Python 3.12.13 venv에서 `websockets` import와 `real_agent_sdk_runner.check_real_agent_sdk_surface()` ready 상태를 확인했다.

`RealAgentSdkRunner` 구현 경계:
- `real_agent_adapter.py` gate가 통과한 sidecar call endpoint에서만 실행한다.
- lazy import 위치는 `real_agent_sdk_runner.py` 내부 실행 함수로 제한한다.
- 실행 경계는 prompt load, OpenAI Realtime session config, ClawOpsAgent config, result tool, call config, result wait, disconnect-finally로 나눈다.
- 입력은 기존 `RealAgentCallRequest`의 reservation id, sidecar call id, allowlist 통과 target, restaurant name, reservation datetime, party size, request note만 사용한다.
- ClawOps Agent SDK mode 후보는 `OpenAIRealtime` session, `ClawOpsAgent`, `@agent.tool` result reporter, `agent.call(...)`, result tool 제출 / call end race, `agent.disconnect()` 순서다.
- 현재 `RealAgentSdkRunner`는 ClawOps builtin tool을 비활성화하고 `submit_reservation_call_result` custom tool을 필수 결과 제출 경로로 둔다. AI가 결과 tool을 제출하면 sidecar가 call hangup을 수행한다.
- AI 결과는 tool handler가 받은 JSON 문자열을 `reservation_result_schema.json`으로 검증한 뒤 `reservation_result_mapper.py`로 넘긴다.
- tool 미호출, schema 오류, 원 예약 조건 충돌, 대체 시간 / 예약금 / 추가 개인정보 요청은 확정이 아니라 `NEEDS_CONFIRMATION` 또는 `AI_PARSE_FAILED`로 보낸다.

2026-05-27 local dev PoC에서는 `dev,db,key` profile과 `ddl-auto=validate`로 Spring을 실행하고, Python 3.12 sidecar real-agent 경로를 승인 조건에서 1회 시도했다. Spring preflight와 sidecar readiness는 통과했고, Spring은 sidecar real-agent call 후보를 `CALLING`으로 받았다. ClawOps 통화 기록상 발신 완료는 확인했지만 AI가 결과 tool을 제출하지 않아 Spring에는 `AI_FAILED` 후보 이벤트가 기록되었다. 예약 확정 상태 전이는 아직 확인되지 않았고, scheduler는 비활성 상태였으며 재발신은 수행하지 않았다.

실제 발신은 사용자가 아래 문구를 직접 제공한 뒤에만 진행할 수 있다.

```text
승인: ClawOps Voice Agent 테스트 번호 1회 발신
```

샘플 시나리오:
- 예약 성공
- 예약 불가
- 대체 시간 제안
- 식당이 예약자명 / 전화번호 요구
- 전화 연결 실패
- 직원이 AI 예약 문의 내용을 이해하지 못함
- AI가 판단하기 애매한 경우

## 4. 상태값
현재 상태 enum:
- `REQUESTED`
- `CALLING`
- `CONFIRMED`
- `UNAVAILABLE`
- `NEEDS_CONFIRMATION`
- `FAILED`
- `CANCELED`

상태 의미:
- `REQUESTED`: 사용자가 예약 요청을 생성했고 실제 결과가 아직 반영되지 않은 상태
- `CALLING`: 전화 시도 중 또는 진행 중 상태
- `CONFIRMED`: 예약 가능으로 확인된 상태
- `UNAVAILABLE`: 해당 조건 예약이 불가한 상태
- `NEEDS_CONFIRMATION`: 추가 확인이 필요한 상태
- `FAILED`: provider 오류 또는 처리 실패 상태
- `CANCELED`: 사용자가 취소한 상태

현재 허용 전이:
- `REQUESTED -> CALLING`
- `REQUESTED -> CONFIRMED`
- `REQUESTED -> UNAVAILABLE`
- `REQUESTED -> NEEDS_CONFIRMATION`
- `REQUESTED -> FAILED`
- `REQUESTED -> CANCELED`
- `CALLING -> CONFIRMED`
- `CALLING -> UNAVAILABLE`
- `CALLING -> NEEDS_CONFIRMATION`
- `CALLING -> FAILED`
- `NEEDS_CONFIRMATION -> CANCELED`

## 5. 중복 예약 기준
동일 사용자 / 동일 식당 / 동일 예약 시각 기준으로 아래 상태의 예약이 있으면 새 예약을 거부한다.

- `REQUESTED`
- `CALLING`
- `CONFIRMED`
- `NEEDS_CONFIRMATION`

아래 상태는 같은 조건의 새 요청을 막지 않는다.

- `UNAVAILABLE`
- `FAILED`
- `CANCELED`

현재 코드는 서비스 로직에서 중복을 검증한다. 운영 DB에서는 동시 요청 race condition을 줄이기 위해 같은 기준의 partial unique index 후보를 둔다.

## 6. Mock provider 기준
- 현재 provider 구현은 `MockReservationCallProvider`다.
- Mock provider는 실제 전화를 걸지 않는다.
- 생성 시 provider 메타데이터만 저장하고, 결과는 Mock 결과 반영 API로 저장한다.
- 추후 실제 전화 provider는 `ReservationCallProvider` 인터페이스를 구현하는 adapter로 교체한다.

## 6-0. Provider client 설정 / 안전장치
`ReservationService`는 계속 `ReservationCallProvider` 인터페이스만 의존한다. 실제 선택 지점은 `SafeReservationCallProvider`가 담당한다.

현재 기본값:
- `reservation.provider.mode=mock`
- `reservation.provider.runtime=direct-rest`
- `reservation.provider.calling-enabled=false`
- `reservation.provider.real-call-enabled=false`
- `reservation.provider.call-allowlist-enabled=true`
- `reservation.provider.require-allowlist-for-external-call=true`
- `reservation.provider.prod-external-call-blocked=true`
- `reservation.provider.external-call-allowed-profiles=dev`

현재 동작:
- `MOCK` 모드에서는 기존 `MockReservationCallProvider`를 사용한다.
- `reservation.provider.mode`는 provider 종류를 고르고, `reservation.provider.runtime`은 `CLAWOPS` mode 안에서 Spring direct REST와 Python sidecar 경로를 나누는 실행 방식을 고른다.
- `NOOP` 모드 또는 외부 provider 모드에서 `calling-enabled=false`이면 실제 client를 호출하지 않고 `provider=NOOP` 결과를 반환한다.
- 외부 provider 모드에서 `calling-enabled=true`여도 dev profile이 아니면 `NOOP_EXTERNAL_CALL_PROFILE_BLOCKED`로 막는다.
- prod profile에서는 `NOOP_PROD_PROFILE_BLOCKED`로 막는다.
- 외부 provider 모드에서 allowlist가 비어 있거나 꺼져 있으면 `NOOP_CALL_ALLOWLIST_REQUIRED`로 막는다.
- 외부 provider 모드에서 allowlist에 없는 번호는 `NOOP_CALL_NOT_ALLOWLISTED`로 막는다.
- ClawOps dev PoC에서 `reservation.provider.dev-target-phone-override-enabled=true`이고 `reservation.provider.dev-target-phone-override-number`가 allowlist에 포함되어 있으면, DB snapshot은 유지한 채 provider 직전 대상 번호만 override 번호로 바꾼다.
- dev target override는 ClawOps mode 전용이며, 기본값은 비활성화다. prod profile, allowlist 미충족, override 번호 누락, ClawOps 필수 설정 누락 중 하나라도 있으면 기존 no-op / preflight 차단 흐름을 따른다.
- OpenAI/Twilio 등 provider 설정이 부족하면 `NOOP_PROVIDER_CONFIGURATION_MISSING`으로 막는다.
- OpenAI Realtime client와 phone provider client는 현재 no-op 구현만 등록되어 있다.
- ClawOps phone adapter는 `clawops.base-url`이 `localhost`, `127.0.0.1`, `::1`인 fake server이면 REST client 계약 테스트 경로로 진행할 수 있다.
- ClawOps non-local 실제 운영 endpoint 후보는 dev profile, prod profile 미포함, `calling-enabled=true`, `real-call-enabled=true`, allowlist 일치, ClawOps 필수 설정이 모두 만족될 때만 접근 후보가 된다.
- `CLAWOPS_SIDECAR` runtime에서는 Spring이 ClawOps / OpenAI API key를 요구하지 않고 `clawops.sidecar-base-url`, `clawops.sidecar-internal-signing-key`만 Spring-side 필수 설정으로 본다.
- 현재 `ClawOpsSidecarPhoneProviderClient`는 local fake/dry-run sidecar에만 HTTP 계약 요청을 보낼 수 있다. 성공 응답도 실제 call start가 아니므로 예약 상태는 `REQUESTED`로 유지하고 `NOOP_CLAWOPS_SIDECAR_DRY_RUN_ACCEPTED`를 반환한다.
- sidecar HTTP 오류, network 오류, timeout은 각각 `NOOP_CLAWOPS_SIDECAR_HTTP_ERROR_{status}`, `NOOP_CLAWOPS_SIDECAR_NETWORK_ERROR`, `NOOP_CLAWOPS_SIDECAR_TIMEOUT`으로 구분하고 예약 상태를 오염시키지 않는다.
- sidecar runtime에서도 dev profile, prod 차단, `calling-enabled`, `real-call-enabled`, allowlist, dev target override 검증은 기존 `SafeReservationCallProvider` 경로를 그대로 따른다.
- `real-call-enabled=false`이면 `NOOP_CLAWOPS_REAL_CALL_DISABLED`로 막는다.
- ClawOps 실제 endpoint 후보가 활성화될 때는 `clawops.real-call.candidate` structured log를 남긴다. 로그에는 예약 id, 식당 id, 마스킹된 전화번호, provider mode만 기록하고 secret은 기록하지 않는다.
- ClawOps HTTP 오류는 `NOOP_CLAWOPS_HTTP_ERROR_{status}`로 변환하고 예약 상태를 `REQUESTED`로 유지한다.
- ClawOps network 오류와 timeout은 각각 `NOOP_CLAWOPS_NETWORK_ERROR`, `NOOP_CLAWOPS_TIMEOUT`으로 구분한다.
- ClawOps 오류 로그 / 결과 메시지에는 method, account id를 노출하지 않는 path template, provider mode, 마스킹된 대상 번호, 마스킹된 발신 번호, HTTP status, sanitizing된 response body summary만 남긴다. Authorization header, API key, signing key, JWT, 원본 전화번호, 원본 request body는 남기지 않는다.
- 이번 단계에서는 실제 OpenAI API, Twilio/SIP/ClawOps/전화 provider를 호출하지 않는다.

환경변수 / 설정 후보:
- `RESERVATION_PROVIDER_MODE` -> `reservation.provider.mode`
- `RESERVATION_PROVIDER_RUNTIME` -> `reservation.provider.runtime`
- `RESERVATION_PROVIDER_CALLING_ENABLED` -> `reservation.provider.calling-enabled`
- `RESERVATION_PROVIDER_REAL_CALL_ENABLED` -> `reservation.provider.real-call-enabled`
- `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED` -> `reservation.provider.call-allowlist-enabled`
- `RESERVATION_PROVIDER_REQUIRE_ALLOWLIST_FOR_EXTERNAL_CALL` -> `reservation.provider.require-allowlist-for-external-call`
- `RESERVATION_PROVIDER_PROD_EXTERNAL_CALL_BLOCKED` -> `reservation.provider.prod-external-call-blocked`
- `RESERVATION_PROVIDER_EXTERNAL_CALL_ALLOWED_PROFILES` -> `reservation.provider.external-call-allowed-profiles`
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS` -> `reservation.provider.call-allowed-numbers`
- `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED` -> `reservation.provider.dev-target-phone-override-enabled`
- `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER` -> `reservation.provider.dev-target-phone-override-number`
- `RESERVATION_PROVIDER_WEBHOOK_BASE_URL` -> `reservation.provider.webhook-base-url`
- `RESERVATION_PROVIDER_WEBHOOK_MAX_CLOCK_SKEW_SECONDS` -> `reservation.provider.webhook-max-clock-skew-seconds`
- `RESERVATION_PROVIDER_WEBHOOK_RAW_PAYLOAD_STORE_ENABLED` -> `reservation.provider.webhook-raw-payload-store-enabled`
- `RESERVATION_PROVIDER_TRANSCRIPT_STORE_ENABLED` -> `reservation.provider.transcript-store-enabled`
- `RESERVATION_PROVIDER_RECORDING_STORE_ENABLED` -> `reservation.provider.recording-store-enabled` (현재 Voice Agent 설계에서는 사용하지 않음)
- `RESERVATION_PROVIDER_MOCK_RESULT_API_ENABLED` -> `reservation.provider.mock-result-api-enabled`
- `OPENAI_API_KEY` -> `openai.api-key`
- `OPENAI_PROJECT_ID` -> `openai.project-id`
- `OPENAI_WEBHOOK_SECRET` -> `openai.webhook-secret`
- `OPENAI_REALTIME_MODEL` -> `openai.realtime.model`
- `OPENAI_REALTIME_VOICE` -> `openai.realtime.voice`
- `OPENAI_REALTIME_INSTRUCTIONS_VERSION` -> `openai.realtime.instructions-version`
- `OPENAI_REALTIME_SESSION_TIMEOUT_SECONDS` -> `openai.realtime.session-timeout-seconds`
- `OPENAI_REALTIME_WS_URL` -> `openai.realtime.ws-url`
- `OPENAI_REALTIME_SIP_ENDPOINT` -> `openai.realtime.sip-endpoint`
- `TWILIO_ACCOUNT_SID` -> `twilio.account-sid`
- `TWILIO_AUTH_TOKEN` -> `twilio.auth-token`
- `TWILIO_FROM_NUMBER` -> `twilio.from-number`
- `TWILIO_STATUS_CALLBACK_URL` -> `twilio.status-callback-url`
- `TWILIO_VOICE_WEBHOOK_URL` -> `twilio.voice-webhook-url`
- `TWILIO_MEDIA_STREAM_URL` -> `twilio.media-stream-url`
- `TWILIO_WEBHOOK_SIGNATURE_ENABLED` -> `twilio.webhook-signature-enabled`
- `TWILIO_DIALING_COUNTRY_ALLOWLIST` -> `twilio.dialing-country-allowlist`
- `CLAWOPS_API_KEY` -> `clawops.api-key`
- `CLAWOPS_ACCOUNT_ID` -> `clawops.account-id`
- `CLAWOPS_BASE_URL` -> `clawops.base-url`
- `CLAWOPS_FROM_NUMBER` -> `clawops.from-number`
- `CLAWOPS_STATUS_CALLBACK_URL` -> `clawops.status-callback-url`
- `CLAWOPS_WEBHOOK_SIGNING_KEY` -> `clawops.webhook-signing-key`
- `CLAWOPS_WEBHOOK_SIGNATURE_HEADER` -> `clawops.webhook-signature-header`
- `CLAWOPS_CALL_TIMEOUT_SECONDS` -> `clawops.call-timeout-seconds`
- `CLAWOPS_RECORDING_ENABLED` -> `clawops.recording-enabled` (현재 Voice Agent 설계에서는 사용하지 않음)
- `CLAWOPS_TRANSCRIPT_ENABLED` -> `clawops.transcript-enabled`
- `CLAWOPS_SUMMARY_ENABLED` -> `clawops.summary-enabled`
- `CLAWOPS_AGENT_RUNTIME_MODE` -> `clawops.agent-runtime-mode`
- `CLAWOPS_SIDECAR_BASE_URL` -> `clawops.sidecar-base-url`
- `CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY` -> `clawops.sidecar-internal-signing-key`
- `CLAWOPS_SIDECAR_CONNECT_TIMEOUT_SECONDS` -> `clawops.sidecar-connect-timeout-seconds`
- `CLAWOPS_SIDECAR_READ_TIMEOUT_SECONDS` -> `clawops.sidecar-read-timeout-seconds`
- `CLAWOPS_SIDECAR_READINESS_REQUIRED` -> `clawops.sidecar-readiness-required`

현재 제한:
- 실제 OpenAI/Twilio/SIP client 구현은 아직 없다.
- no-op client는 실제 외부 API 호출을 하지 않는다.
- ClawOps direct REST adapter는 dev-only gate 통과 후 call create를 1회 시도했으나 `NOOP_CLAWOPS_HTTP_ERROR`로 종료되었다.
- ClawOps sidecar real-agent 경로는 local dev 승인 조건에서 1회 발신 / 통화 완료까지 확인했다. 다만 AI 결과 tool 제출과 예약 확정 상태 전이는 아직 완료되지 않았다.
- Python sidecar는 dry-run endpoint와 approved real-agent 후보 경로를 제공한다. sidecar는 DB에 접근하지 않는다.
- 실제 provider adapter를 추가하거나 ClawOps PoC를 승인하기 전에는 `calling-enabled=true`도 실제 발신을 보장하지 않는다.

dev-only PoC에서 실제 provider 후보로 넘어가기 위한 조건:
- active profile에 `dev`가 포함되어야 한다.
- active profile에 `prod`가 포함되면 안 된다.
- `reservation.provider.mode`가 `twilio`, `openai_sip`, `clawops`, `generic` 중 하나여야 한다.
- `reservation.provider.calling-enabled=true`여야 한다.
- `reservation.provider.call-allowlist-enabled=true`여야 한다.
- `reservation.provider.call-allowed-numbers`가 비어 있지 않아야 한다.
- 예약 대상 전화번호가 allowlist에 정확히 포함되어야 한다.
- Twilio / OpenAI SIP 방식은 OpenAI Realtime 설정과 선택한 전화 provider 설정이 모두 채워져야 한다.

ClawOps 추가 조건:
- `DIRECT_REST` runtime에서는 `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, `CLAWOPS_FROM_NUMBER`, `CLAWOPS_STATUS_CALLBACK_URL`, `CLAWOPS_WEBHOOK_SIGNING_KEY`가 모두 있어야 한다.
- `CLAWOPS_SIDECAR` runtime에서는 Spring 기준으로 `CLAWOPS_SIDECAR_BASE_URL`, `CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY`가 있어야 한다. ClawOps / OpenAI API key는 sidecar secret으로 둔다.
- localhost fake server 계약 테스트가 아니고 실제 endpoint 후보를 사용하려면 `reservation.provider.real-call-enabled=true`가 추가로 필요하다.
- prod profile에서는 어떤 설정 조합이더라도 실제 endpoint 후보를 허용하지 않는다.
- ClawOps Voice Agent를 Python/Node sidecar로 실행해야 하는 경우 Spring backend의 역할은 예약 요청 저장, ClawOps call 요청, webhook 수신, 상태 전이에 한정한다.
- ClawOps REST API만으로 AI agent 발신과 결과 수신이 충분하면 Spring `PhoneProviderClient` adapter로 단순화한다.

ClawOps REST client 계약:
- method: `POST`
- path: `/v1/accounts/{accountId}/calls`
- header: `Authorization: Bearer {CLAWOPS_API_KEY}`
- header: `Accept: application/json`
- content type: `application/json`
- request body 후보: `To`, `From`, `Url`, `AI`, `StatusCallback`, `StatusCallbackEvent`, `Timeout`
- 현재 Spring adapter는 실제 OpenAI 호출을 막기 위해 `AI`를 전송하지 않는다. `Url`도 아직 전송하지 않으므로 ClawOps 문서 기준으로는 Agent SDK mode 후보에 해당하며, 재발신 전 Python / Node Voice Agent sidecar 연결 또는 `Url` / `AI` mode 선택을 확정해야 한다.
- `StatusCallback`과 `StatusCallbackEvent`는 ClawOps SDK 기준 선택값이지만, 예약 도메인에서는 call attempt / event ledger 확인을 위해 실제 PoC 설정 완비 조건으로 유지한다.
- response body 후보: 공식 SDK 모델 기준 `call_id`, `status`를 우선으로 보고, Agent SDK / webhook 표기 차이를 흡수하기 위해 `callId` / `CallId`, `call_status` / `callStatus` / `CallStatus` alias도 허용한다.
- 이 계약은 fake HTTP server 테스트에서만 검증한다.
- HTTP 오류 response body가 JSON이면 token / key / secret / authorization / phone / to / from / number 계열 필드를 `[REDACTED]`로 마스킹한다.
- HTTP 오류 response body가 JSON이 아니면 token / key / secret / authorization / 전화번호 패턴을 마스킹하고 길이를 제한한다.

ClawOps Python Voice Agent sidecar 설계 후보:
- 상세 설계는 `plans/clawops-voice-agent-sidecar-design-2026-05-26.md`에 둔다.
- 권장 구조는 Spring을 예약 source of truth로 유지하고, Python sidecar는 ClawOps Voice Agent 실행 전용 process로 둔다.
- sidecar는 DB에 직접 접근하지 않는다.
- Spring은 예약 생성, preflight, allowlist, dev target override, call attempt, provider event ledger, retry / timeout, terminal 상태 보호를 계속 담당한다.
- 현재 sidecar는 Spring preflight가 통과한 요청을 dry-run HTTP 계약으로 받을 수 있고, 별도 승인된 local dev real-agent 요청에서만 SDK-backed 후보 경로를 실행한다.
- sidecar가 통화 상태나 예약 결과 후보를 받으면 Spring 내부 provider event endpoint로 전달하고, Spring이 기존 idempotency / 상태 전이 규칙으로 최종 반영한다.
- ClawOps API key와 OpenAI API key는 sidecar runtime secret으로 두는 방향을 우선 검토한다. Spring direct REST adapter를 유지하는 동안 필요한 secret과 sidecar mode secret은 분리한다.
- sidecar mode에서도 prod profile, allowlist, real-call flag, internal signature 검증을 모두 통과해야 하며, sidecar 단독 발신 admin API는 만들지 않는다.
- Spring sidecar runtime skeleton, local fake/dry-run dispatch, sidecar readiness 호출, sidecar -> Spring internal event endpoint, Python sidecar dry-run scaffold, approved real-agent 후보 경로는 준비되었다. 성공 기준 실제 통화 완료는 1회 확인했지만, AI 결과 tool 제출과 예약 상태 전이는 아직 남아 있다.

Computer Use로 외부 콘솔에서 확인 / 설정할 항목:
- OpenAI: API key 발급, 프로젝트 범위, Realtime 사용 가능 모델, webhook secret, Realtime instruction/tool schema 관리 방식
- Twilio: Account SID/Auth Token, 발신 번호, geographic permission, voice status callback URL, voice webhook URL, Media Stream WebSocket URL, webhook signature 검증 기준
- ClawOps: 070 번호 발급, Account ID/API key, webhook signing key, status callback URL, outbound REST/Voice Agent 경로, event id 제공 여부, Beta/Trial 제한, transcript/summary 부가서비스 조건
- dev 터널: provider가 접근 가능한 HTTPS webhook base URL
- 전화 allowlist: 팀 소유 테스트 번호만 등록하고 실제 식당 번호는 등록하지 않는다.
- 비용 / 사용량 제한: dev 테스트 중 과금 한도와 알림을 설정한다.

## 6-1. Provider event 내부 처리 기준
- provider raw payload와 예약 상태 변경을 직접 섞지 않는다.
- 실제 webhook endpoint를 열기 전에 내부 표준 이벤트 command와 event ledger를 먼저 둔다.
- `ReservationProviderEventService`는 검증된 내부 이벤트만 처리한다.
- 보안 검증 실패 이벤트는 예약 상태와 event ledger를 변경하지 않는다.
- idempotency key가 이미 처리된 이벤트는 중복으로 보고 예약 상태를 다시 변경하지 않는다.
- terminal 상태(`CONFIRMED`, `UNAVAILABLE`, `FAILED`, `CANCELED`) 예약에 늦게 도착한 provider event는 `IGNORED_STALE`로 기록하고 상태를 덮어쓰지 않는다.
- provider 식별자(`provider`, `providerCallId`)가 기존 예약과 맞지 않으면 `REJECTED_INVALID`로 기록하고 상태를 변경하지 않는다.
- retry/timeout scheduler 골격은 `ReservationRetryTimeoutSchedulerService`가 담당하며, 실제 외부 provider 호출 없이 Mock/no-op 흐름으로만 동작한다.

현재 내부 event type 후보:
- `CALL_QUEUED`
- `CALL_STARTED`
- `CALL_CONNECTED`
- `CALL_CONNECTION_FAILED`
- `CALL_NO_ANSWER`
- `CALL_BUSY`
- `CALL_ENDED`
- `RESERVATION_CONFIRMED`
- `RESERVATION_UNAVAILABLE`
- `RESERVATION_NEEDS_CONFIRMATION`
- `AI_PARSE_FAILED`
- `PROVIDER_TRANSIENT_ERROR`
- `PROVIDER_FATAL_ERROR`

현재 processing status:
- `RECEIVED`
- `PROCESSED`
- `IGNORED_DUPLICATE`
- `IGNORED_STALE`
- `REJECTED_SECURITY`
- `REJECTED_INVALID`

Mock/test 보안 검증:
- `MockReservationProviderWebhookSecurityVerifier`는 `MOCK` provider용 HMAC signature와 timestamp skew를 검증한다.
- 이는 실제 provider 인증이 아니라 mock/test webhook endpoint의 보안 검증 기반이다.

ClawOps skeleton 보안 검증:
- `ClawOpsWebhookSecurityVerifier`는 `X-Signature` 기본 헤더, callback URL, form parameter 정렬, HMAC-SHA256, timestamp skew 검증 골격을 제공한다.
- 현재 ClawOps webhook endpoint는 공개하지 않는다.
- reverse proxy / dev tunnel 환경에서 실제 ClawOps signature base string이 일치하는지는 추가 PoC가 필요하다.

## 6-2. Call attempt / retry / timeout 기준
`ReservationCallAttemptService`는 실제 전화 발신 없이 provider event를 기준으로 전화 시도 기록을 남긴다.

현재 기록 기준:
- `CALL_STARTED`: 새 전화 시도를 생성하고 예약의 `attemptCount`, `lastAttemptedAt`을 갱신한다.
- `CALL_CONNECTED`: 해당 전화 시도를 `CONNECTED`로 갱신한다.
- `CALL_NO_ANSWER`, `CALL_BUSY`, `CALL_CONNECTION_FAILED`, `PROVIDER_TRANSIENT_ERROR`: retry 가능 여부와 시도 횟수에 따라 `RETRY_SCHEDULED` 또는 `FAILED_FINAL`로 갱신한다.
- `PROVIDER_FATAL_ERROR`: retry 없이 `FAILED_FINAL`로 갱신하고 예약 상태 `FAILED` 전이 후보를 반환한다.
- `RESERVATION_CONFIRMED`, `RESERVATION_UNAVAILABLE`, `RESERVATION_NEEDS_CONFIRMATION`, `AI_PARSE_FAILED`, `CALL_ENDED`: 해당 전화 시도를 `COMPLETED`로 갱신한다.

현재 retry 정책 기본값:
- 최대 시도 횟수: 3회
- 1차 실패 후 retry delay: 60초
- 2차 이후 실패 후 retry delay: 300초
- call timeout 기준: 600초

현재 구현 범위:
- retry 가능한 실패와 retry 불가능한 실패를 구분한다.
- 최대 시도 횟수를 넘으면 `FAILED_FINAL` 시도 상태와 예약 `FAILED` 전이 후보를 만든다.
- `RETRY_SCHEDULED`와 `nextRetryAt`을 기준으로 재시도 가능 시도를 조회할 수 있다.
- `STARTED`, `CONNECTED` 상태에서 timeout 기준보다 오래된 시도를 timeout 후보로 조회할 수 있다.
- `RETRY_SCHEDULED` 대상이 scheduler에 의해 선택되면 기존 시도는 `RETRY_DISPATCHED`로 바꾸고 새 Mock 시도(`STARTED`)를 만든다.
- timeout 대상이 retry 가능하면 `RETRY_SCHEDULED`로 바꾸고, 최대 시도 횟수를 넘으면 해당 예약을 `FAILED`로 전이한다.

## 6-3. Retry / timeout scheduler 골격
`ReservationRetryTimeoutScheduler`는 `reservation.scheduler.enabled=true`일 때만 bean으로 등록된다. 기본값과 테스트 설정은 비활성화이며, 실제 전화 provider 호출은 수행하지 않는다.

현재 scheduler 흐름:
- `processRetryReadyAttempts`: `RETRY_SCHEDULED` + `nextRetryAt <= now` 시도를 조회한다.
- terminal 상태 예약과 `NEEDS_CONFIRMATION` 예약은 제외한다.
- 중복 실행 방지는 `ReservationSchedulerExecutionGuard`의 in-memory key(`reservation-call-attempt:{retry|timeout}:{attemptId}`)로 수행한다.
- retry 대상은 실제 전화 발신 대신 기존 시도를 `RETRY_DISPATCHED`로 바꾸고 새 `MOCK` 재시도 시도를 `STARTED`로 생성한다.
- `processTimeoutCandidates`: `STARTED`, `CONNECTED` 상태에서 timeout 기준보다 오래된 시도를 조회한다.
- timeout 대상은 retry 가능하면 `RETRY_SCHEDULED`, 최대 시도 횟수에 도달하면 `FAILED_FINAL` + 예약 `FAILED`로 처리한다.

현재 제한:
- in-memory guard는 단일 애플리케이션 프로세스의 중복 실행만 막는다.
- 다중 인스턴스 운영 lock, DB row lock, provider 호출 job idempotency, 운영 알림은 아직 확정되지 않았다.
- 실제 provider adapter가 붙기 전까지 scheduler retry는 no-op/mock dispatch만 수행한다.

## 7. 추천 / 랭킹과의 관계
- 예약 요청과 결과는 추천 / 랭킹 계산 입력이 아니다.
- `UserList`, `ListRestaurant`, `autoScore` 계산식은 변경하지 않는다.
- 예약 성공 여부가 식당 점수나 추천 후보 가중치에 영향을 주지 않는다.

## 8. 추가 확인 필요
- 실제 전화 provider webhook 구현 방식
- 실제 전화 provider 호출 scheduler 구현 방식
- 다중 인스턴스 scheduler lock / lease 구현 방식
- 실제 provider별 webhook signature base string과 event id 제공 방식
- ClawOps status callback에 별도 event id 또는 delivery id가 있는지
- ClawOps를 Spring REST adapter로 붙일지, Python/Node Voice Agent sidecar를 둘지
- ClawOps 실제 운영 endpoint REST `calls.create` 호출 허용 조건
- ClawOps 실제 webhook endpoint 공개 범위
- AI 생성 요약 후보와 raw transcript를 저장할 경우 저장 범위, 고지, 동의, 보관 / 삭제 정책. 통화 녹음 저장은 현재 설계 범위에서 제외한다.
- ClawOps Beta/Trial 제한, SLA, 장애 시 Twilio 등 대체 provider 전환 기준
- mock webhook endpoint를 local/test/dev에서만 열지, dev 운영에서도 feature flag로 열지
- 예약 개인정보 보관 / 삭제 정책
- 확정된 예약 취소 시 실제 식당 취소 전화까지 수행할지
- 식당 단위 전역 중복 예약을 막을지
- 운영 DB migration 적용 방식을 무엇으로 둘지

## 9. 후속 수정 후보
- 실제 provider adapter 추가 전 별도 provider event 정책 문서 작성
- 개인정보 저장 정책 확정 후 응답 마스킹 범위 조정

## 10. 실제 provider 연동 전 설계 후보
2026-05-20 기준 실제 provider webhook / retry / idempotency / security 설계 후보는 `plans/ai-call-reservation-provider-webhook-plan-2026-05-20.md`에 정리되어 있다.
2026-05-21 기준 실제 provider 선택안, 환경변수 후보, 연동 전 결정사항은 `plans/ai-call-reservation-provider-selection-readiness-2026-05-21.md`에 정리되어 있다.
2026-05-24 기준 ClawOps 공식 문서 검토와 Twilio 대비 비교도 같은 provider 선택안 문서에 보강되어 있다.

현재 문서 기준:
- mock/test provider webhook endpoint skeleton은 구현되어 있다.
- 실제 전화 provider webhook endpoint는 아직 구현하지 않는다.
- 실제 OpenAI API, Twilio/SIP/ClawOps/전화 provider는 아직 연동하지 않는다.
- provider 설정 구조, OpenAI/phone provider client interface, no-op adapter는 준비되어 있다.
- ClawOps provider mode, 설정 properties, REST 계약 DTO/client, fake-server 전용 call adapter, status callback mapper, HMAC verifier skeleton은 준비되어 있다.
- ClawOps dev 실제 발신 PoC 런북과 수동 승인 게이트는 provider 선택안 문서에 정리되어 있다.
- provider raw payload는 곧바로 예약 Entity에 반영하지 않고 내부 표준 이벤트로 변환하는 구조를 우선 후보로 둔다.
- idempotency는 provider event id를 우선 기준으로 보고, 없으면 provider call id / event type / occurredAt / payload hash 조합을 fallback 후보로 둔다.
- retry는 최대 3회, 1분/5분 backoff를 기본 후보로 둔다.
- call attempt 기록, retry/timeout 후보 조회, mock/no-op scheduler 골격은 구현되어 있다.
- webhook 보안은 HMAC signature, timestamp window, idempotency replay 방어 조합을 우선 후보로 둔다.
- 관리자 Mock 결과 API는 local/test/dev에서는 관리자 전용으로 유지하고, prod에서는 기본 비활성화하는 정책을 우선 후보로 둔다.
- 실제 provider 추천은 `C안: provider abstraction은 유지하되 ClawOps adapter를 먼저 PoC한다`로 갱신한다.
- Twilio Programmable Voice + OpenAI Realtime WebSocket bridge는 삭제하지 않고 비교 / 대체 후보로 유지한다.

## 11. ClawOps dev 실제 발신 전 승인 조건
실제 ClawOps 발신은 기본값에서 닫혀 있다. 후속 단계에서 실제 발신 PoC를 수행하려면 아래 조건을 모두 만족해야 한다.

- 사용자에게 실제 발신 승인 문구를 제시하고 명시 승인을 받는다.
- active profile에는 `dev`가 포함되어야 하고 `prod`는 포함되면 안 된다.
- `RESERVATION_PROVIDER_MODE=CLAWOPS`여야 한다.
- `RESERVATION_PROVIDER_CALLING_ENABLED=true`는 승인 직전에만 켠다.
- `RESERVATION_PROVIDER_REAL_CALL_ENABLED=true`는 승인 직전에만 켠다.
- `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED=true`여야 한다.
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`에는 팀 소유 테스트 번호 1개만 둔다.
- 테스트 번호 소유자가 수신에 동의해야 한다.
- 대상 예약의 식당 전화번호 snapshot이 allowlist 테스트 번호와 정확히 일치해야 한다. 서버 DB를 수정하지 않는 dev PoC에서는 예외적으로 dev target override를 켜고, override 번호가 allowlist 테스트 번호와 정확히 일치해야 한다.
- `/admin/reservations/clawops-real-call/preflight` 결과가 `realCallCandidate=true`여야 한다.
- 실제 식당 번호가 seed, DB, allowlist, 요청 데이터에 섞여 있지 않아야 한다.
- 최초 실제 발신 PoC에서는 scheduler를 비활성화해 자동 재시도 발신을 막는다.
- 실패 또는 의심 상황에서는 즉시 `RESERVATION_PROVIDER_CALLING_ENABLED=false`, `RESERVATION_PROVIDER_MODE=mock`, allowlist 비우기 순서로 롤백한다.
