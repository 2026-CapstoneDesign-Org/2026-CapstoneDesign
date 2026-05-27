# ClawOps Voice Agent Sidecar 설계 계획

기준 날짜 및 시간: 2026-05-26 (Asia/Seoul)

## 작업명
ClawOps Python SDK 기반 Voice Agent sidecar 설계

## 목적
Spring Boot가 예약 생성, preflight, allowlist, 상태 저장, call attempt, event ledger를 계속 담당하고, Python sidecar가 ClawOps Voice Agent 실행만 담당하는 구조가 현재 프로젝트에 적합한지 설계한다.

이번 단계는 실제 구현 전 설계 문서화 단계다. 실제 ClawOps API 호출, 실제 OpenAI API 호출, 실제 전화 발신은 하지 않는다.

## 사용자 관점 결과
운영자 / 개발자는 실제 발신 PoC 전에 아래를 명확히 알 수 있다.

- Spring 서버가 어떤 검증을 끝낸 뒤 sidecar에 발신을 위임하는지
- sidecar가 어떤 최소 정보만 받아 ClawOps Voice Agent를 실행하는지
- sidecar가 통화 결과를 어떤 내부 이벤트로 Spring에 돌려주는지
- API key, OpenAI key, 원본 전화번호가 어디에 머물러야 하는지
- 실패, timeout, retry, 중복 처리를 어느 시스템이 최종 판단하는지

## 범위
- sidecar 채택 여부 판단
- Spring / sidecar 책임 분리
- sidecar 배치 방식 후보
- Spring -> sidecar 호출 방식 후보
- sidecar -> Spring 결과 전달 방식 후보
- 내부 endpoint 후보
- 환경변수 후보
- secret 소유 경계
- allowlist / preflight / retry / timeout 책임 유지 방향
- 기존 Spring 코드 변경 후보
- 실제 구현 전 위험 요소와 단계 제안

## 비범위
- 실제 ClawOps Voice Agent 실행
- 실제 ClawOps call create API 호출
- 실제 OpenAI Realtime API 호출
- 실제 식당 / 테스트 번호 발신
- API key / secret / 원본 전화번호 문서화
- 운영 DB migration 적용
- Twilio / OpenAI 기존 골격 삭제

## 관련 문서/코드
- `AGENTS.md`
- `GUIDE.md`
- `DB.md`
- `LOGIC.md`
- `docs/logic/reservation-policy.md`
- `docs/db/reservations.md`
- `docs/current-gaps.md`
- `plans/ai-call-reservation-provider-selection-readiness-2026-05-21.md`
- `Capstone/src/main/java/com/example/Capstone/client/reservation/*`
- `Capstone/src/main/java/com/example/Capstone/service/ReservationProviderEventService.java`
- `Capstone/src/main/java/com/example/Capstone/service/ReservationCallAttemptService.java`
- `Capstone/src/main/java/com/example/Capstone/service/ReservationRetryTimeoutSchedulerService.java`

외부 공식 문서:
- ClawOps Getting Started: Python / Node Voice Agent, OpenAI Realtime, outbound `agent.call(...)`
- ClawOps Python SDK: `calls.create(...)`, AI Completion mode, webhook signature verify
- ClawOps VoiceML / build guide: Voice Agent와 VoiceML 경로 차이
- ClawOps Webhook signature verification: `X-Signature`, HMAC-SHA256

## 현재 Spring 구현 상태 요약
- 예약 생성 / 조회 / 취소 API가 있다.
- 관리자 Mock 결과 API가 있다.
- ClawOps provider mode, REST client 계약 DTO, real-call gate, preflight API가 있다.
- call attempt / event ledger / retry-timeout scheduler 골격이 있다.
- dev-only target phone override가 있다.
- ClawOps call create는 dev allowlist 조건에서 1회 시도했으나 `NOOP_CLAWOPS_HTTP_ERROR`로 종료되었다.
- ClawOps Voice Agent 실행은 아직 없다.

## sidecar 방식 채택 여부
권장 결론: **채택한다. 단, provider abstraction은 유지하고 ClawOps sidecar를 첫 실제 PoC 경로로 둔다.**

근거:
- ClawOps 공식 문서는 Voice Agent를 Python / Node SDK 중심으로 설명한다.
- `agent.serve()`는 ClawOps 서버로 역접속하므로 Spring이 직접 OpenAI Realtime audio bridge를 구현하지 않아도 된다.
- Spring에는 이미 예약 검증, 상태 저장, retry, idempotency 기반이 있으므로 sidecar가 DB를 직접 만질 필요가 없다.
- sidecar를 별도 process로 두면 Python SDK / OpenAI Realtime 의존성이 Spring Boot classpath와 섞이지 않는다.
- Twilio / OpenAI bridge 후보는 삭제하지 않고 대체 provider로 유지할 수 있다.

보류 조건:
- ClawOps sidecar가 dev allowlist 테스트에서도 안정적으로 통화 시작 / 이벤트 반환을 못 하면 Spring 단독 REST `AI` mode 또는 Twilio / OpenAI bridge 후보로 돌아간다.

## sidecar 프로젝트 배치 후보
추천: 저장소 루트에 별도 Python 프로젝트를 둔다.

후보 경로:
- `sidecars/clawops-voice-agent/`

이유:
- `Capstone/` Spring Boot 프로젝트와 의존성 / 빌드 도구를 분리할 수 있다.
- 같은 저장소 안에 두면 캡스톤 시연 문서, Docker Compose, dev runbook과 함께 관리하기 쉽다.
- 추후 운영 분리 시 별도 repo / container image로 옮기기 쉽다.

비권장:
- `Capstone/src/main` 아래에 Python 코드를 넣는 방식: Spring 구조와 책임 경계가 흐려진다.
- sidecar가 직접 DB repository처럼 행동하는 방식: 예약 도메인 source of truth가 둘로 갈라진다.

## 책임 분리
### Spring Boot 책임
- 사용자 인증 / 관리자 인증
- 예약 생성 / 조회 / 취소
- 식당 visible / 전화번호 / 과거 시간 / 인원 수 검증
- dev-only preflight
- allowlist / dev target phone override 판단
- call attempt 생성 / 상태 저장
- provider event ledger 저장
- provider event idempotency
- retry / timeout 정책 판단
- terminal 상태 보호
- DB 접근 전체
- sidecar 호출 전후 audit log

### Python sidecar 책임
- ClawOps Voice Agent process 실행
- OpenAI Realtime session 구성
- allowlist를 통과한 단일 발신 요청 수행
- ClawOps SDK 이벤트를 Spring 내부 표준 이벤트로 변환할 최소 metadata 생성
- Spring 내부 API로 call status / reservation result 후보 전달
- 통화 중 tool callback이 필요하면 Spring 내부 read-only endpoint를 호출
- Voice Agent prompt와 결과 schema를 기준으로 예약 결과 후보를 구조화
- DB 직접 접근 금지
- 예약 상태 최종 판단 금지

### ClawOps 책임
- 070 번호 / PSTN 발신
- Voice Agent transport
- call id / call status event 제공
- transcript / summary 부가 기능 제공 가능성
- 통화 녹음은 현재 설계에서 사용하지 않는다.

## 권장 실행 흐름
1. 사용자가 Spring 예약 API로 예약을 생성한다.
2. Spring은 예약을 `REQUESTED`로 저장한다.
3. 운영자 또는 dev flow가 Spring preflight API를 호출한다.
4. Spring preflight가 dev profile, prod 차단, ClawOps mode, real-call flag, allowlist, override, scheduler disabled, sidecar 설정을 검증한다.
5. 실제 발신 승인 후 Spring이 call attempt를 생성하고 `attemptId` / `reservationId` / `targetPhone` / `fromNumber` / 예약 문맥을 sidecar에 전달한다.
6. sidecar는 자체 allowlist와 internal auth를 다시 확인한다.
7. sidecar가 ClawOps Voice Agent를 통해 1회 발신한다.
8. sidecar는 call id를 받으면 Spring 내부 endpoint에 `CALL_STARTED` 또는 `CALLING` 후보 이벤트를 보낸다.
9. 통화 중 / 종료 후 sidecar는 예약 가능 / 불가 / 확인 필요 / 실패 후보 이벤트를 Spring에 보낸다.
10. Spring은 기존 `ReservationProviderEventService`와 `ReservationCallAttemptService`로 idempotency, terminal 상태 보호, 상태 전이를 처리한다.
11. retry / timeout은 Spring scheduler가 기존 정책대로 판단한다.

## Spring -> sidecar endpoint 후보
sidecar endpoint는 외부 공개 API가 아니라 Spring 전용 내부 API로 둔다.

### 1. 발신 요청
```http
POST {SIDECAR_BASE_URL}/internal/clawops-agent/calls
```

인증:
- `X-Internal-Signature`
- `X-Request-Timestamp`
- 선택: mTLS 또는 private network security group

request body 후보:
- `reservationId`
- `callAttemptId`
- `idempotencyKey`
- `targetPhoneNumber`
- `fromNumber`
- `restaurantName`
- `reservationDateTime`
- `partySize`
- `requestNote`
- `systemPromptVersion`
- `toolSchemaVersion`
- `statusCallbackUrl` 또는 Spring internal event callback URL

response body 후보:
- `accepted`
- `provider`
- `providerCallId`
- `providerStatus`
- `sidecarCallId`
- `idempotencyKey`
- `message`

정책:
- 같은 `callAttemptId` / `idempotencyKey`가 두 번 들어오면 sidecar는 중복 발신하지 않고 기존 처리 결과 또는 `DUPLICATE_IGNORED`를 반환한다.
- sidecar는 request / response 로그에 원본 전화번호와 secret을 남기지 않는다.

### 2. sidecar readiness
```http
GET {SIDECAR_BASE_URL}/internal/clawops-agent/readiness
```

response body 후보:
- `ready`
- `profile`
- `realCallEnabled`
- `allowlistCount`
- `clawOpsConfigured`
- `openAiConfigured`
- `agentRuntime`
- `warnings`

정책:
- Spring preflight에서 선택적으로 호출한다.
- secret 존재 여부만 boolean으로 반환하고 원문은 반환하지 않는다.

### 3. 발신 취소 / 종료 후보
```http
POST {SIDECAR_BASE_URL}/internal/clawops-agent/calls/{sidecarCallId}/cancel
```

MVP에서는 보류한다. 실제 예약 취소와 실제 식당 취소 전화 정책이 확정된 뒤 검토한다.

## sidecar -> Spring endpoint 후보
Spring endpoint는 사용자 JWT API와 분리한다.

### 1. 내부 provider event 수신
```http
POST /internal/reservations/provider-events/clawops-agent
```

인증:
- `X-Internal-Signature`
- `X-Request-Timestamp`
- 선택: source IP allowlist / private network

request body 후보:
- `provider`
- `reservationId`
- `callAttemptId`
- `providerCallId`
- `sidecarCallId`
- `eventType`
- `providerStatus`
- `occurredAt`
- `retryable`
- `failureReason`
- `reservationResultCandidate`
- `aiSummary`
- `idempotencyKey`
- `rawPayloadHash`

정책:
- 기존 `ReservationProviderEventService`로 연결한다.
- raw transcript는 MVP에서 받지 않는다. recording URL은 현재 설계에서 사용하지 않는다.
- `reservationResultCandidate`는 후보일 뿐이며 Spring 상태 전이 규칙이 최종 판단한다.

### 2. sidecar callback health / ack
```http
POST /internal/reservations/provider-events/clawops-agent/ack
```

MVP에서는 보류한다. event delivery retry가 필요하면 후속 단계에서 도입한다.

## Voice Agent prompt / tool schema
Phase 7에서는 실제 Voice Agent를 실행하지 않고, Python sidecar가 이후 사용할 prompt와 결과 tool schema만 설계한다.

설계 파일:
- `sidecars/clawops-voice-agent/prompts/reservation_agent_prompt.md`
- `sidecars/clawops-voice-agent/contracts/reservation_result_schema.json`
- `sidecars/clawops-voice-agent/contracts/samples/*.json`
- `sidecars/clawops-voice-agent/contracts/spring-event-payloads/*.json`
- `sidecars/clawops-voice-agent/reservation_result_mapper.py`
- `sidecars/clawops-voice-agent/spring_event_dispatch_candidate.py`
- `sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`

### 상담원 prompt 정책
- AI는 식당 예약 문의를 대행하는 상담원이다.
- 통화 초반에 AI 예약 도우미임을 짧고 자연스럽게 밝힌다.
- 요청 날짜, 시간, 인원 수는 Spring에서 받은 값을 그대로 전달한다.
- 예약자명과 연락처는 항상 시스템에서 제공된다고 전제한다.
- 예약자명 / 연락처 관련 결과 필드는 값의 존재 여부가 아니라 식당 요청 여부와 전달 여부로 해석한다.
- 요청 조건을 임의로 변경하지 않는다.
- 요청 조건이 불가능하면 가능한 대체 시간이 있는지 질문한다.
- 대체 시간이 제안되면 사용자 확인이 필요하므로 `NEEDS_CONFIRMATION` 후보로 분류한다.
- 예약금, 추가 개인정보, 특수 조건, 모호한 응답은 `NEEDS_CONFIRMATION` 후보로 분류한다.
- 명확한 확정 응답만 `CONFIRMED`로 분류한다.
- 판단이 애매하면 `FAILED`보다 `NEEDS_CONFIRMATION`을 우선 검토한다.

### 결과 schema
상태 후보:
- `CONFIRMED`
- `UNAVAILABLE`
- `NEEDS_CONFIRMATION`
- `FAILED`

필드:
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

정책:
- `confirmedDateTime`은 요청 조건이 명확히 확정된 경우에만 채운다.
- `alternativeDateTime`이 있으면 `resultStatus=NEEDS_CONFIRMATION`이어야 한다.
- `FAILED`는 전화 연결 실패, provider / sidecar 기술 실패처럼 통화 자체를 완료하지 못한 경우에 사용한다.
- 직원이 이해하지 못했거나 식당 응답이 모호한 경우는 `FAILED`가 아니라 `NEEDS_CONFIRMATION`으로 둔다.
- `transcriptSummary`는 AI 생성 요약 후보이며 녹음 기반 transcript가 아니다.
- recording 저장, recording webhook, 녹음 파일 보관 정책은 범위에서 제외한다.

### Spring event mapping
- `CONFIRMED` -> `RESERVATION_CONFIRMED`
- `UNAVAILABLE` -> `RESERVATION_UNAVAILABLE`
- `NEEDS_CONFIRMATION` -> `RESERVATION_NEEDS_CONFIRMATION`
- `FAILED` -> 연결 / provider 오류 종류에 따라 `CALL_CONNECTION_FAILED`, `CALL_NO_ANSWER`, `CALL_BUSY`, `PROVIDER_TRANSIENT_ERROR`, `PROVIDER_FATAL_ERROR` 중 하나로 변환한다.
- schema validation 실패 또는 AI 판단 불명확은 `AI_PARSE_FAILED` 또는 `RESERVATION_NEEDS_CONFIRMATION` 후보로 두며, 바로 `CONFIRMED`로 전이하지 않는다.
- Phase 8 dry-run mapper는 schema 결과를 Spring `ClawOpsAgentProviderEventRequest` shape로 변환한다. 이 mapper는 HTTP 요청, DB 접근, Voice Agent 실행을 하지 않는다.
- Phase 9 dry-run dispatch candidate builder는 mapper 결과를 Spring internal event path, raw JSON body, timestamp, HMAC header 후보로 감싼다. 실제 Spring endpoint로 전송하지 않는다.
- dry-run `/internal/clawops-agent/calls`는 샘플 AI 결과를 읽어 `springEventPreview`를 생성한다. preview는 payload와 header 이름만 노출하고 signature 값은 응답에 넣지 않는다.
- Phase 10 상호 계약 테스트는 `contracts/spring-event-payloads/*.json`을 공유 fixture로 사용한다. Python 테스트는 builder 출력과 fixture 일치를 확인하고, Spring E2E 테스트는 같은 fixture가 `ClawOpsAgentProviderEventRequest`로 들어와 event ledger/idempotency 흐름에 들어가는지 확인한다.
- Phase 11 contract drift guard는 `python3 sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`로 실행한다. Python sidecar contract tests, Spring `ClawOpsAgentProviderEventE2ETest`, fixture/document 민감값 스캔을 묶어 실행한다.
- Spring event fixture를 수정할 때는 Python builder fixture 일치 테스트와 Spring E2E fixture 기대값을 같은 변경에서 갱신해야 한다.
- `CONFIRMED`라도 `confirmedDateTime` 또는 `partySize`가 Spring 요청 원본과 충돌하면 `RESERVATION_NEEDS_CONFIRMATION` + `AI_RESULT_CONFLICT`로 보낸다.
- 필수 필드 누락이나 schema 위반은 `AI_PARSE_FAILED`로 보낸다.
- 최종 상태 전이는 Spring `ReservationProviderEventService`와 기존 `ReservationStatus` 전이 규칙이 판단한다.

### 샘플 시나리오
- 예약 성공: `confirmed.json`
- 예약 불가: `unavailable.json`
- 대체 시간 제안: `alternative-time.json`
- 식당이 예약자명 / 전화번호 요구: `name-phone-requested.json`
- 전화 연결 실패: `connection-failed.json`
- 직원이 내용을 이해하지 못함: `staff-did-not-understand.json`
- AI가 판단하기 애매한 경우: `ambiguous.json`

## 환경변수 후보
### Spring Boot
- `RESERVATION_PROVIDER_MODE=CLAWOPS`
- `RESERVATION_PROVIDER_CALLING_ENABLED`
- `RESERVATION_PROVIDER_REAL_CALL_ENABLED`
- `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED`
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`
- `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED`
- `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER`
- `RESERVATION_SCHEDULER_ENABLED`
- `RESERVATION_PROVIDER_RUNTIME=CLAWOPS_SIDECAR`
- `CLAWOPS_FROM_NUMBER`
- `CLAWOPS_STATUS_CALLBACK_URL`
- `CLAWOPS_WEBHOOK_SIGNING_KEY`
- `CLAWOPS_SIDECAR_BASE_URL`
- `CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY`
- `CLAWOPS_SIDECAR_CONNECT_TIMEOUT_SECONDS`
- `CLAWOPS_SIDECAR_READ_TIMEOUT_SECONDS`
- `CLAWOPS_SIDECAR_READINESS_REQUIRED`

Spring에 두지 않는 것을 권장:
- `CLAWOPS_API_KEY`
- `OPENAI_API_KEY`

단, 기존 Spring direct REST adapter를 유지하는 동안에는 `CLAWOPS_API_KEY`가 필요할 수 있다. sidecar runtime을 선택하면 Spring direct REST adapter의 실제 endpoint 접근은 비활성화하는 방향이 안전하다.

### Python sidecar dry-run 단계
- `SIDECAR_PROFILE`
- `SIDECAR_REAL_CALL_ENABLED`
- `SIDECAR_ALLOWED_TARGET_NUMBERS`
- `SIDECAR_BIND_HOST`
- `SIDECAR_PORT`
- `SIDECAR_INTERNAL_SIGNING_KEY`
- `SPRING_INTERNAL_BASE_URL`
- `SPRING_INTERNAL_SIGNING_KEY`

Phase 7 이후 실제 Voice Agent 단계 전까지 사용하지 않는다:
- `CLAWOPS_API_KEY`
- `CLAWOPS_ACCOUNT_ID`
- `CLAWOPS_FROM_NUMBER`
- `CLAWOPS_BASE_URL`
- `OPENAI_API_KEY`
- `OPENAI_REALTIME_MODEL`
- `OPENAI_REALTIME_VOICE`
- `OPENAI_REALTIME_LANGUAGE`
- `OPENAI_REALTIME_INSTRUCTIONS_VERSION`
- `CLAWOPS_AGENT_TIMEOUT_SECONDS`
- `CLAWOPS_AGENT_MAX_ACTIVE_CALLS`
- `CLAWOPS_AGENT_RECORDING_ENABLED`
- `CLAWOPS_AGENT_TRANSCRIPT_ENABLED`

## secret 소유 경계
- ClawOps API key: sidecar가 소유한다.
- OpenAI API key: sidecar가 소유한다.
- Spring DB credential / JWT secret: Spring이 소유한다.
- Spring -> sidecar internal signing key: Spring과 sidecar에만 주입한다.
- sidecar -> Spring internal signing key: Spring과 sidecar에만 주입한다.
- ClawOps webhook signing key: 실제 ClawOps가 Spring webhook으로 직접 callback하는 경로를 열 때 Spring이 소유한다.

모든 secret은 코드, 문서, 테스트 fixture, 로그에 원문을 남기지 않는다.

## allowlist / preflight 정책
- allowlist와 dev-only target phone override의 최종 판단은 Spring preflight가 담당한다.
- sidecar도 defense-in-depth로 동일 allowlist 또는 Spring이 전달한 signed target을 검증한다.
- Spring preflight가 실패하면 sidecar 호출 자체를 하지 않는다.
- sidecar가 직접 임의 번호로 발신할 수 있는 standalone admin API는 만들지 않는다.
- prod profile에서는 Spring과 sidecar 모두 실제 발신을 차단한다.

## DB 접근 정책
- sidecar는 DB에 직접 접근하지 않는다.
- sidecar는 reservation id / call attempt id를 opaque id처럼 다룬다.
- 예약 상태, call attempt 상태, provider event ledger는 모두 Spring에서만 저장한다.
- sidecar 장애 후 재처리는 Spring retry / timeout scheduler 기준으로 판단한다.

## 실패 / timeout / retry 정책
- sidecar 호출 실패: Spring은 call attempt를 retry 가능 실패 후보로 기록한다.
- sidecar가 provider call id를 받기 전 실패: Spring은 `PROVIDER_TRANSIENT_ERROR` 또는 `PROVIDER_FATAL_ERROR` 후보로 분류한다.
- sidecar가 provider call id를 받은 뒤 통화 실패: sidecar event를 Spring에 전달하고 Spring이 retry 가능 여부를 결정한다.
- timeout 대상 식별과 최종 실패 전이는 Spring scheduler가 담당한다.
- sidecar는 같은 call attempt를 중복 발신하지 않는 idempotency guard만 가진다.

## 기존 Spring 코드 변경 후보
구현 단계에서 검토할 후보다. 2026-05-26 기준 Phase 0~6 dry-run skeleton이 반영되었다.

- `ReservationProviderMode`는 `CLAWOPS`를 유지하고 `ReservationProviderRuntime`으로 runtime sub-mode를 분리했다.
- `ReservationProviderProperties`에 `runtime=direct-rest|clawops-sidecar`를 추가했다.
- `ClawOpsProperties.isConfigured()`는 기존 direct REST 호환을 유지하고, sidecar 필수값은 `isSidecarConfigured()`로 분리했다.
- `ClawOpsRealCallPreflightService`는 sidecar runtime에서 sidecar base URL / internal signing key를 검사한다. readiness required가 켜져 있으면 local fake/dry-run sidecar readiness를 호출하고 실패 시 preflight를 차단한다.
- `ClawOpsSidecarPhoneProviderClient`는 local fake/dry-run sidecar에만 HTTP 계약 요청을 보내며, 성공 응답도 `REQUESTED` 상태를 유지하는 no-op 결과로 변환한다.
- sidecar internal client DTO와 HMAC/timestamp signing utility를 추가했다.
- sidecar -> Spring internal event controller를 사용자 JWT API와 분리해 추가했다.
- `CALL_QUEUED` dry-run event는 event ledger/idempotency만 확인하고 예약 상태와 provider field를 오염시키지 않는다.
- `ReservationCallAttemptService`가 sidecar dispatch id / sidecar call id를 저장할 필드가 필요한지 검토한다.

## 위험 요소
- Python sidecar lifecycle: Spring 서버와 sidecar가 따로 떠 있어야 한다.
- active call memory: sidecar 재시작 시 진행 중 통화 세션 추적이 끊길 수 있다.
- duplicate call risk: Spring retry와 sidecar 재시도가 겹치면 중복 발신 위험이 있다.
- secret surface 증가: ClawOps / OpenAI key가 sidecar 운영 환경에 추가된다.
- event delivery reliability: sidecar -> Spring 내부 event 전달 실패 시 보상 로직이 필요하다.
- Voice Agent execution: prompt / tool schema는 준비되었지만 실제 ClawOps / OpenAI Voice Agent에는 아직 연결하지 않았다.
- 개인정보: AI 생성 요약 후보를 어디까지 저장할지와 raw transcript 저장 여부가 미정이다. 통화 녹음은 현재 설계 범위에서 사용하지 않는다.
- prod safety: sidecar에 standalone 발신 명령이 생기면 Spring preflight를 우회할 위험이 있다.

## 구현 단계 제안
1. sidecar runtime 설계 확정
   - `direct-rest`와 `sidecar` runtime property를 문서 / 코드에 반영한다.
2. sidecar 내부 API 계약 추가
   - Spring -> sidecar request / response DTO와 fake sidecar test server를 먼저 만든다.
3. Spring preflight sidecar 모드 보강
   - sidecar base URL / internal signing key / readiness를 검증한다.
4. `ClawOpsSidecarPhoneProviderClient` skeleton 구현
   - 실제 ClawOps / OpenAI 호출 없이 fake sidecar에만 요청한다.
5. sidecar -> Spring internal event endpoint skeleton 구현
   - 기존 event ledger와 idempotency로 연결한다.
6. Python sidecar scaffold
   - 실제 호출 없이 health/readiness와 dry-run call endpoint만 만든다.
7. Voice Agent prompt / tool schema 설계
   - 실제 OpenAI / ClawOps 호출 없이 prompt, 결과 schema, 샘플, 순수 validation test를 만든다.
8. Voice Agent result -> Spring event dry-run mapper
   - 실제 HTTP 전송 없이 schema 결과를 Spring internal event request shape로 변환하고 샘플 테스트로 검증한다.
9. Spring internal event dispatch candidate dry-run builder
   - 실제 HTTP 전송 없이 mapper 결과를 raw body와 HMAC header 후보까지 생성한다.
10. sidecar payload와 Spring DTO 상호 계약 테스트
   - 공유 fixture로 Python mapper 출력과 Spring internal endpoint DTO/event ledger 처리를 함께 검증한다.
11. sidecar-Spring contract drift guard
   - Python/Spring fixture 계약 테스트와 민감값 스캔을 한 번에 실행하는 로컬/CI용 guard를 둔다.
12. 실제 연동 승인 게이트와 local-only runbook
   - 실제 secret 주입 전제, preflight, readiness, dry-run, 승인 문구, 중단 조건, 롤백 절차를 문서화한다.
   - Phase 12 자체에서는 실제 secret을 주입하지 않고, 후속 승인 단계에서 사용자가 로컬 환경변수에 직접 넣는 기준만 정리한다.
13. Voice Agent wiring 후보 코드 경계 / feature flag 설계
   - `dry-run` 기본 runtime과 `real-agent` 후보 runtime을 구분하고, 현재 단계에서는 real-agent를 call endpoint gate로 막는다.
   - 실제 SDK import 위치는 후속 module lazy import 후보로만 문서화한다.
14. real-agent module interface / fake implementation
   - 실제 SDK import 없이 runner request / result interface와 fake runner를 추가한다.
   - fake 결과가 기존 mapper / dispatch candidate 경로로 변환되는지 테스트한다.
15. real-agent adapter lazy-import skeleton / approval-preflight contract
   - 실제 SDK import 없이 adapter dependency gate와 차단 사유를 추가한다.
   - 모든 조건이 후보상 충족되어도 현재는 `REAL_AGENT_NOT_IMPLEMENTED`로 차단한다.
16. real-agent adapter shadow wiring
   - dry-run call response에 adapter gate preview를 추가한다.
   - preview는 실행 없이 block reasons, missing env names, sdk installed 후보만 보여준다.
17. Spring sidecar response preview compatibility
   - Spring fake sidecar response에 `realAgentGatePreview`가 있어도 unknown field ignore로 기존 동작을 유지한다.
18. readiness / call gate separation
   - readiness는 process / dry-run 준비 상태로 유지하고, real-agent 실행 차단은 call endpoint preview로만 표현한다.
19. local fake Spring event delivery policy
   - 실제 HTTP client 없이 fake transport result를 delivery preview로 분류한다.
20. local fake retry / ack policy
   - retry loop 없이 network / timeout / 4xx / 5xx / duplicate ack 후보만 순수 테스트로 검증한다.
21. guard / docs refresh
   - contract drift guard scan 범위와 문서를 최신 dry-run 계약에 맞춘다.
22. 실제 SDK / secret / 발신 전 최종 readiness 점검
   - 현재 dry-run / fake 완성 상태, 남은 실제 연동 작업, 환경변수 이름, 승인 절차, 중단 조건을 정리한다.
   - SDK dependency 추가, secret 주입, Voice Agent 실행, 전화 발신은 하지 않는다.
23. real-agent SDK dependency 선언
   - `requirements-real-agent.txt`에 opt-in `clawops[agent,openai]` dependency만 명시한다.
   - SDK 설치, SDK import / call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않는다.
24. real-agent SDK 설치 확인 / runner skeleton
   - sidecar 전용 venv에 opt-in dependency를 설치하고 `clawops` / `openai` import availability만 확인한다.
   - `real_agent_sdk_runner.py`는 lazy import boundary와 hard-stop runner skeleton만 제공한다.
   - SDK object 생성, API 호출, Voice Agent 실행, secret 주입, 전화 발신은 하지 않는다.
25. SDK API surface 확인 / runner 구현 계획
   - 설치된 package source / signature와 공식 문서를 대조한다.
   - 실제 SDK object 생성, API 호출, Voice Agent 실행, secret 주입, 전화 발신은 하지 않는다.
26. Python 3.11+ real-agent venv compatibility
   - Python 3.11+ sidecar venv에서 `ClawOpsAgent` / `OpenAIRealtime` import compatibility만 확인한다.
   - 부족한 dependency가 있으면 후보와 이유만 정리하고 바로 추가하지 않는다.
27. Realtime dependency / runner pre-execution skeleton
   - OpenAI Realtime WebSocket 준비용 최소 dependency를 보강한다.
   - runner는 prompt / session / agent / tool / call / wait / disconnect 후보 경계까지만 나누고 실제 SDK object 생성은 하지 않는다.
28. 실제 Voice Agent sidecar wiring
   - 사용자 수동 승인 후에만 ClawOps / OpenAI SDK 연결을 실행한다.
29. dev allowlist 1회 PoC
   - 사용자 수동 승인 후에만 ClawOps / OpenAI 실제 호출을 켠다.
30. ClawOps 번호 inbound fallback endpoint
   - ClawOps 전화번호 온보딩 / 인바운드 라우팅에 사용할 정적 Voice XML endpoint를 Spring에 둔다.
31. AI 결과 tool 제출 안정화
   - 발신 완료 후 AI가 `submit_reservation_call_result` tool을 제출하지 않는 경우를 분석한다.

2026-05-27 현재 1~30 중 approved local real-agent 실행 후보와 ClawOps 번호 inbound fallback endpoint까지 구현 / 시도했다. Spring preflight와 sidecar readiness는 통과했고 ClawOps 통화 기록상 발신 완료도 확인했다. 다음 작업은 발신 완료 후 AI 결과 tool 제출이 누락되는 원인을 분석하는 것이다.

## 검증 방법
현재 구현 단계:
- Spring preflight sidecar mode test
- fake sidecar success / failure / timeout test
- internal signature failure test
- sidecar -> Spring event idempotency test
- full `bash ./gradlew test`
- Python sidecar unit test
- sidecar dry-run integration test
- Voice Agent result schema sample validation test
- Voice Agent result -> Spring internal event dry-run mapper test
- Spring internal event dispatch candidate dry-run builder test
- sidecar Spring event payload fixture -> Spring DTO / event ledger E2E test
- sidecar-Spring contract drift guard script
- 실제 연동 전 local-only runbook checklist
- sidecar runtime mode / real-agent hard-stop test
- real-agent fake runner -> mapper / dispatch candidate test
- real-agent adapter dependency gate block reason test
- dry-run response real-agent gate preview test
- Spring sidecar response unknown field compatibility test
- local fake Spring event delivery policy test
- local fake retry / ack policy test
- 실제 SDK / secret / 발신 전 readiness checklist
- real-agent opt-in dependency file check
- real-agent SDK runner skeleton lazy-import test
- SDK surface / Python runtime compatibility check
- Python 3.12 real-agent venv import compatibility check
- Realtime dependency import compatibility check
- runner pre-execution skeleton unit test
- `git diff --check`

## Progress
- [x] 계획 작성 완료
- [x] 사용자 검토 / 승인 완료
- [x] Spring sidecar runtime 설계 코드 반영
- [x] fake sidecar 계약 테스트 구현
- [x] Python sidecar scaffold 구현
- [x] dev dry-run 검증 완료
- [x] Voice Agent prompt / tool schema 설계
- [x] Voice Agent result -> Spring event dry-run mapper
- [x] Spring event dispatch candidate dry-run builder
- [x] sidecar payload와 Spring DTO 상호 계약 테스트
- [x] sidecar-Spring contract drift guard
- [x] 실제 연동 승인 게이트와 local-only runbook
- [x] Voice Agent wiring 후보 코드 경계 / feature flag 설계
- [x] real-agent module interface / fake implementation
- [x] real-agent adapter lazy-import skeleton / approval-preflight contract
- [x] real-agent adapter shadow wiring
- [x] Spring sidecar response preview compatibility
- [x] readiness / call gate separation
- [x] local fake Spring event delivery policy
- [x] local fake retry / ack policy
- [x] guard / docs refresh
- [x] 실제 SDK / secret / 발신 전 최종 readiness 점검
- [x] real-agent SDK dependency 선언
- [x] real-agent SDK 설치 확인 / runner skeleton
- [x] SDK API surface 확인 / runner 구현 계획
- [x] Python 3.11+ real-agent venv compatibility
- [x] Realtime dependency / runner pre-execution skeleton
- [x] secret 주입 전 mocked SDK runner / mapper / dispatch candidate 검증
- [x] 실제 allowlist 1회 PoC 수동 승인 후 실행 후보 시도
- [x] ClawOps allowlist 1회 발신 / 통화 완료 확인
- [x] ClawOps 번호 인바운드 fallback webhook endpoint
- [ ] AI 결과 tool 제출 성공 / 예약 상태 전이 확인
- [x] 문서 반영 완료

## 결정 사항 / 변경 로그
- 2026-05-26: ClawOps 공식 문서와 현재 Spring 구조 기준으로 Python Voice Agent sidecar 설계안을 작성했다.
- 2026-05-26: 권장안은 `Spring = 예약 source of truth`, `Python sidecar = ClawOps Voice Agent 실행 전용`이다.
- 2026-05-26: ClawOps / OpenAI API key는 sidecar에 두고, Spring은 preflight / allowlist / DB / event ledger를 담당하는 방향을 추천한다.
- 2026-05-26: Spring 쪽 `ReservationProviderRuntime.CLAWOPS_SIDECAR`, sidecar Spring-side 설정, no-op `ClawOpsSidecarPhoneProviderClient` skeleton, sidecar mode preflight 설정 검증을 추가했다. 실제 sidecar HTTP dispatch, readiness 호출, Python sidecar 구현, ClawOps / OpenAI 호출, 전화 발신은 하지 않았다.
- 2026-05-26: Phase 0~6 dry-run 범위로 Spring -> sidecar local HTTP 계약, sidecar readiness preflight, sidecar -> Spring internal event endpoint skeleton, Python stdlib dry-run sidecar scaffold를 추가했다. 실제 ClawOps / OpenAI 호출과 전화 발신은 하지 않았다.
- 2026-05-26: Phase 7 범위로 AI 예약 상담원 prompt, 결과 schema, 샘플 시나리오, schema validation test를 추가했다. 예약자명 / 연락처는 항상 제공되는 값으로 전제하고, 녹음 기능은 범위에서 제외했다.
- 2026-05-26: Phase 8 범위로 Voice Agent result schema를 Spring internal event request shape로 변환하는 dry-run mapper와 샘플 기반 테스트를 추가했다. 확정 결과의 날짜 / 인원 충돌과 schema 오류는 확정 이벤트로 보내지 않는다.
- 2026-05-26: Phase 9 범위로 mapper 결과를 Spring internal event raw payload와 HMAC header 후보로 감싸는 dry-run dispatch candidate builder를 추가했다. dry-run sidecar call 응답은 실제 전송 없이 `springEventPreview`만 생성한다.
- 2026-05-26: Phase 10 범위로 sidecar Spring event payload fixture와 Spring internal event DTO/E2E 처리 흐름의 상호 계약 테스트를 추가했다. 실제 운영 Spring 서버나 실제 sidecar로 HTTP 전송하지 않았다.
- 2026-05-26: Phase 11 범위로 Python/Spring contract tests와 fixture/document 민감값 스캔을 묶은 contract drift guard script를 추가했다. 실제 외부 API 호출, 전화 발신, Voice Agent 실행은 없다.
- 2026-05-26: Phase 12 범위로 실제 연동 전 local-only runbook, 필수 검증 순서, 승인 문구, 즉시 중단 조건, 롤백 절차를 문서화했다. 실제 외부 API 호출, 전화 발신, Voice Agent 실행은 없다.
- 2026-05-26: Phase 13 범위로 sidecar runtime mode와 real-agent feature flag hard stop을 추가했다. `real-agent` 후보 모드는 call endpoint gate로 막히며, 실제 SDK import, secret 요구, 외부 API 호출, 전화 발신은 없다.
- 2026-05-26: Phase 14 범위로 real-agent runner interface와 SDK-free fake implementation을 추가했다. fake 결과는 기존 mapper / dispatch candidate 경로로 검증하며, 실제 SDK import, secret 요구, 외부 API 호출, 전화 발신은 없다.
- 2026-05-26: Phase 15 범위로 real-agent adapter lazy-import skeleton과 approval / preflight dependency gate를 추가했다. 현재 adapter는 모든 실행을 차단하며, 실제 SDK import, secret 요구, 외부 API 호출, 전화 발신은 없다.
- 2026-05-26: Phase 16 범위로 dry-run call response에 `realAgentGatePreview` shadow diagnostics를 추가했다. adapter gate는 평가만 하고 실행하지 않으며, 실제 SDK import, secret 요구, 외부 API 호출, 전화 발신은 없다.
- 2026-05-26: Phase 17 범위로 Spring fake sidecar response가 `realAgentGatePreview` unknown field를 받아도 기존 dry-run no-op 동작을 유지하는 회귀 테스트를 보강했다.
- 2026-05-26: Phase 18 범위로 readiness와 call endpoint gate를 분리했다. readiness는 dry-run 준비 상태로 유지하고 real-agent block은 call endpoint preview로만 표현한다.
- 2026-05-26: Phase 19~20 범위로 sidecar -> Spring event delivery / retry / ack 후보를 local fake policy와 순수 테스트로만 추가했다. 실제 HTTP client, retry loop, worker, scheduler, queue는 없다.
- 2026-05-26: Phase 21 범위로 contract drift guard scan 범위를 sidecar `.py`까지 확장하고 관련 문서를 갱신했다.
- 2026-05-26: 실제 SDK / secret / 발신 단계 진입 전 최종 readiness 기준을 정리했다. SDK dependency 추가, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-26: real-agent SDK dependency 추가 승인 전 조사 결과에 따라 `requirements-real-agent.txt`를 추가하고 `clawops[agent,openai]`만 opt-in dependency로 명시했다. SDK 설치, SDK import / call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-26: sidecar 전용 real-agent venv에 `requirements-real-agent.txt`를 설치해 `clawops` / `openai` import availability를 확인했다. `real_agent_sdk_runner.py`에는 lazy import helper와 hard-stop runner skeleton만 추가했다. SDK object 생성, SDK API call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-26: 설치된 SDK source / signature와 공식 문서를 대조했다. Python 3.9.6 venv에서는 `clawops.agent`가 `enum.StrEnum` 부재로 import되지 않고, `clawops[agent,openai]`만으로는 OpenAI Realtime WebSocket dependency가 설치되지 않음을 확인했다. 실제 SDK object 생성, SDK API call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-26: Python 3.12.13 sidecar venv를 구성하고 `clawops.agent.ClawOpsAgent`, `clawops.agent.OpenAIRealtime`, OpenAI Realtime resource import compatibility를 확인했다. `websockets`는 설치되지 않아 후속 `openai[realtime]` 또는 explicit `websockets` dependency 추가 여부가 남았다. 실제 SDK object 생성, SDK API call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-26: OpenAI 2.x realtime extra metadata와 같은 `websockets>=13,<16`을 opt-in real-agent dependency에 추가했다. Python 3.12.13 venv에서 `websockets` import와 SDK surface ready 상태를 확인했다. `RealAgentSdkRunner`는 prompt / session / agent / tool / call 후보 skeleton을 만들 수 있지만 여전히 `REAL_AGENT_NOT_IMPLEMENTED` 또는 gate 차단으로 멈춘다.
- 2026-05-26: API key / secret 주입 직전까지 가능한 범위로 `RealAgentSdkRunner` pre-execution skeleton을 result wait / disconnect 후보까지 확장하고, `MockedRealAgentSdkRunner`로 confirmed / unavailable / needs-confirmation / failed / missing tool result / 예약 조건 충돌이 mapper와 Spring dispatch candidate까지 이어지는지 검증했다. 실제 SDK object 생성, SDK API call, secret 주입, Voice Agent 실행, 전화 발신은 하지 않았다.
- 2026-05-27: local dev 승인 조건에서 Spring `dev,db,key`, `ddl-auto=validate`, Python 3.12 sidecar real-agent 경로로 1회 연결 후보를 시도했다. Spring preflight와 sidecar readiness는 통과했고 Spring은 sidecar real-agent call 후보를 수락했다. 초기에는 ClawOps control websocket HTTP 403으로 실패했지만, 이후 번호 온보딩 / 인바운드 fallback 설정 후 승인된 재시도에서 ClawOps 통화 기록상 발신 완료까지 확인했다. AI 결과 tool 제출은 아직 실패 후보로 남아 있다.
- 2026-05-27: ClawOps 전화번호 온보딩 / 인바운드 fallback 확인을 위해 임시 tunnel Voice XML webhook을 설정했고, 이후 승인된 local dev 재시도에서 ClawOps 통화 기록상 발신 완료까지 확인했다. 다만 AI가 결과 tool을 제출하지 않아 Spring에는 `AI_FAILED` 후보 이벤트가 기록되었고, 예약 확정 상태 전이는 아직 확인되지 않았다.
- 2026-05-27: 임시 tunnel URL을 장기 설정으로 남기지 않기 위해 Spring에 `GET|POST /webhooks/reservations/call-providers/clawops/inbound` endpoint를 추가했다. 이 endpoint는 정적 Voice XML만 반환하며 예약 DB, event ledger, call attempt를 수정하지 않는다. 배포 후 ClawOps 전화번호 Webhook URL에는 Swagger UI 주소가 아니라 `https://wagu.uk/webhooks/reservations/call-providers/clawops/inbound`를 등록한다.

## 완료 조건
- sidecar 채택 여부가 문서화되어 있다.
- Spring / sidecar 책임 경계가 문서화되어 있다.
- 내부 endpoint 후보와 환경변수 후보가 문서화되어 있다.
- 실제 구현 전 위험 요소와 구현 단계가 정리되어 있다.
- 실제 allowlist 테스트 번호 1회 발신 / 통화 완료는 확인했다. 남은 완료 기준은 AI 결과 tool 제출과 Spring 예약 상태 전이 확인이다.
