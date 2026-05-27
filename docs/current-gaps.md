# current-gaps.md

기준 날짜 및 시간: 2026-05-26 (Asia/Seoul)

## 1. 목적
이 문서는 아직 확정되지 않았거나, 코드와 문서가 어긋나는 정책 항목만 남긴다.

확정된 구조와 정책은 `DB.md`, `LOGIC.md`, `docs/db/*`, `docs/logic/*`에 기록한다.

## 2. 미확정 정책
### 2-1. 리스트 상세 조회 공개 범위
- `GET /lists/{id}`는 현재 `isPublic` 또는 owner 여부를 검증하지 않는다.
- 외부 노출 정책을 API 계약으로 더 명확히 정리할 필요가 있다.

### 2-2. 점수 수정 API path variable 의미
- 경로는 `/lists/{id}/restaurants/{restaurantId}` 형태지만 실제 수정 대상 조회는 `list_restaurants.id` 기준이다.
- 외부 계약과 구현 의미가 다르다.

### 2-3. refresh token 재발급 시 role 복원 경로
- refresh token 자체에는 role claim이 없다.
- `AuthService.refresh()`는 `jwtProvider.getRole(refreshToken)`에 의존한다.

### 2-4. 대표 리스트와 공개 상태 관계
- 대표 리스트를 비공개로 바꾸지 못하게 막는 로직은 있다.
- 대표 리스트 삭제 후 후속 승격, 사용자당 대표 1개 보장의 운영 정책은 더 명확히 정리할 여지가 있다.

### 2-5. cold start 추천
- 식당 추천과 리스트 추천 모두 사용자 상호작용이 없으면 빈 결과를 반환한다.
- 기본 추천 정책은 아직 확정되지 않았다.

### 2-6. follow와 hidden 사용자 관계
- follow 생성은 `isDeleted = false`만 확인한다.
- hidden 사용자를 follow 대상 / 응답에서 어떻게 다룰지는 정리가 필요하다.

### 2-7. placeholder OAuth 엔드포인트
- 실제 로그인 진입점은 `/oauth2/authorization/{provider}`다.
- `/auth/oauth/{provider}`는 현재 `200 OK` placeholder 성격이다.

### 2-8. Google OAuth2 실로그인 검증
- OAuth2 provider 설정에는 Google이 포함되어 있다.
- 로컬 / 개발 서버에서 Google 실로그인이 정상 동작하는지는 추가 검증이 필요하다.

### 2-9. 외부 fallback으로 생성된 식당의 후속 정제 정책
- `POST /lists/{id}/restaurants/external-fallback`는 Pcmap 후보가 기존 DB에 없으면 `Restaurant` row를 생성한다.
- 현재 생성 시점에는 메뉴 / 태그 상세 데이터가 함께 생성되지 않는다.
- 이 식당을 seed preview, 관리자 검수, 별도 enrichment 흐름 중 어디로 편입할지 정책 결정이 필요하다.

### 2-10. Pcmap HTML fallback 운영 안정성
- `PcmapSearchClientImpl`은 NAVER Pcmap HTML의 Apollo state 구조를 파싱한다.
- 외부 HTML 구조 변경이나 요청 제한이 발생하면 현재 코드는 빈 결과를 반환한다.
- 장애 감지, 알림, 대체 provider, retry 여부는 아직 운영 정책으로 확정되지 않았다.

### 2-11. AI 전화 예약 후속 운영 정책
- 현재 AI 전화 예약의 기본 경로는 Mock / no-op provider 기반이며, 실제 발신은 dev-only 수동 승인 게이트 밖에서는 실행하지 않는다.
- 실제 전화 provider webhook / retry / timeout / idempotency / 보안 설계 후보는 `plans/ai-call-reservation-provider-webhook-plan-2026-05-20.md`에 정리되어 있다.
- 실제 provider 선택안, 환경변수 후보, OpenAI/전화 provider 연동 전 결정사항, ClawOps 공식 문서 검토 결과는 `plans/ai-call-reservation-provider-selection-readiness-2026-05-21.md`에 정리되어 있다.
- 내부 표준 provider event 처리, event ledger Entity/Repository, mock/test용 signature verifier, mock/test provider webhook endpoint skeleton, provider 설정 구조, no-op client adapter, dev-only provider 활성화 guard는 준비되었다.
- ClawOps provider mode, 설정 properties, REST 요청/응답 계약 DTO, fake-server 전용 REST client, call adapter, status callback mapper, HMAC signature verifier skeleton은 준비되었다.
- 실제 전화 provider 운영 endpoint와 provider별 실제 호출 세부값은 아직 구현/확정되지 않았다.
- mock/test provider webhook endpoint는 `reservation.webhook.mock.enabled=true`일 때만 등록된다. local/test/dev/prod별 최종 운영 허용 정책과 audit log 필요 여부는 실제 provider 전환 전에 추가 확정이 필요하다.
- ClawOps 전화번호 인바운드 라우팅 / 온보딩 fallback용 endpoint는 `GET|POST /webhooks/reservations/call-providers/clawops/inbound`로 추가했다. 이 endpoint는 정적 Voice XML만 반환하고 예약 DB, event ledger, call attempt를 수정하지 않는다. ClawOps 콘솔에는 Swagger UI 주소가 아니라 이 공개 HTTPS endpoint를 Webhook URL로 등록해야 한다.
- provider webhook event ledger와 call attempt Entity/Repository는 준비되었지만, 운영 DB migration 적용 방식은 아직 확정되지 않았다.
- retry/timeout scheduler 골격은 준비되었지만, 실제 provider 호출 retry, 다중 인스턴스 lock/lease, 운영 실행 주기, 운영 알림 정책은 아직 확정되지 않았다.
- 실제 전화 provider는 아직 최종 확정되지 않았다. 2026-05-24 검토 기준 추천은 `C안: provider abstraction은 유지하되 ClawOps adapter를 먼저 PoC`이며, Twilio는 비교 / 대체 후보로 유지한다.
- 실제 OpenAI Realtime 연결 방식은 ClawOps Voice Agent, Spring 직접 WebSocket bridge, OpenAI SIP 방식 중 아직 확정되지 않았다.
- 실제 OpenAI/Twilio/SIP client는 interface와 no-op 골격만 있으며 실제 운영 API 호출 구현은 아직 없다.
- ClawOps REST client는 fake-server 계약 테스트, dev-only real-call gate, 실제 발신 전 preflight 검증 API까지 준비되었다. dev allowlist 조건에서 실제 ClawOps call create를 1회 시도했으나 `NOOP_CLAWOPS_HTTP_ERROR`로 종료되었고, 성공 call id 저장이나 추가 재시도는 없었다.
- dev-only PoC 조건은 코드와 문서에 정리되었지만, OpenAI/Twilio/ClawOps 콘솔 설정, dev 터널 URL, allowlist 테스트 번호, 실제 webhook signature 검증값은 아직 확정되지 않았다.
- ClawOps dev 실제 발신 PoC 실행 준비 런북, 수동 승인 게이트, 롤백 절차는 `plans/ai-call-reservation-provider-selection-readiness-2026-05-21.md`에 정리되었다.
- ClawOps는 공식 문서상 한국 070 번호, outbound call, Voice Agent, OpenAI Realtime, status/transcript/summary webhook, HMAC signature를 제공하지만 실제 가입 콘솔에서 070 발급, 발신번호 표시, Trial 제한, 비용, status page/SLA를 확인해야 한다. 통화 녹음 기능은 현재 설계 범위에서 사용하지 않는다.
- ClawOps 공식 SDK 기준 `calls.create`에서 `Url`과 `AI`를 생략하면 Agent SDK mode 후보가 된다. Spring 백엔드 REST adapter만으로 재발신하기 전 Python/Node Voice Agent sidecar를 실행할지, VoiceML `Url` mode 또는 AI Completion `AI` mode를 선택할지 결정해야 한다.
- Python SDK 기반 ClawOps Voice Agent sidecar 설계안은 `plans/clawops-voice-agent-sidecar-design-2026-05-26.md`에 정리했다. Spring 쪽 `CLAWOPS_SIDECAR` runtime property, sidecar Spring-side 필수 설정 검증, local fake/dry-run HTTP dispatch, readiness 호출, sidecar -> Spring internal event endpoint skeleton, fake sidecar 계약 테스트, Python stdlib dry-run sidecar scaffold는 준비되었다.
- Voice Agent prompt / tool schema 설계는 `sidecars/clawops-voice-agent/prompts/reservation_agent_prompt.md`, `sidecars/clawops-voice-agent/contracts/reservation_result_schema.json`, `sidecars/clawops-voice-agent/contracts/samples/*.json`에 추가했다. 예약자명과 연락처는 항상 제공되는 값으로 전제하고, 결과 필드는 식당 요청 여부 / 전달 여부로만 해석한다.
- Voice Agent schema 결과를 Spring internal event request shape로 바꾸는 dry-run mapper는 `sidecars/clawops-voice-agent/reservation_result_mapper.py`에 추가했다. 아직 실제 sidecar HTTP 전송이나 Voice Agent 실행에는 연결하지 않았다.
- mapper 결과를 Spring internal event path, raw JSON body, timestamp, HMAC header 후보로 감싸는 dry-run dispatch candidate builder는 `sidecars/clawops-voice-agent/spring_event_dispatch_candidate.py`에 추가했다. dry-run sidecar call 응답은 `springEventPreview`만 만들며 실제 Spring endpoint로 HTTP 전송하지 않는다.
- sidecar가 만들 Spring event payload와 Spring `ClawOpsAgentProviderEventRequest` DTO의 상호 계약 fixture는 `sidecars/clawops-voice-agent/contracts/spring-event-payloads/*.json`에 추가했다. Spring E2E 테스트는 이 fixture가 event ledger/idempotency 흐름에 안전하게 들어가는지 확인한다.
- sidecar-Spring contract drift guard는 `sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py`에 추가했다. fixture 변경 시 Python builder test, Spring E2E fixture expectation, 민감값 스캔을 함께 통과해야 한다.
- 실제 연동 전 local-only runbook, 승인 문구, 즉시 중단 조건, 롤백 절차는 `sidecars/clawops-voice-agent/README.md`, `docs/logic/reservation-policy.md`, `plans/clawops-voice-agent-sidecar-design-2026-05-26.md`에 정리했다. 실제 발신은 `승인: ClawOps Voice Agent 테스트 번호 1회 발신` 문구가 사용자에게서 직접 주어지기 전까지 금지한다.
- Voice Agent wiring 후보 경계는 sidecar runtime flag로 추가했다. `dry-run`은 기본 구현 모드이고 `real-agent`는 call endpoint에서 gate preview와 함께 차단되는 후보 모드다. readiness는 process / dry-run 준비 상태로 분리했다. 실제 SDK import, Voice Agent 실행, secret 주입, 외부 API 호출은 아직 구현하지 않았다.
- real-agent module interface와 SDK-free fake implementation은 `sidecars/clawops-voice-agent/real_agent_interface.py`에 추가했다. fake 결과는 기존 mapper / dispatch candidate 경로로 검증하지만 실제 SDK wiring과 secret 주입은 아직 하지 않았다.
- real-agent adapter lazy-import skeleton과 승인 / preflight dependency contract는 `sidecars/clawops-voice-agent/real_agent_adapter.py`에 추가했다. 현재는 조건 평가와 차단 사유만 반환하며 모든 실행은 `REAL_AGENT_NOT_IMPLEMENTED` 또는 승인 / preflight / secret / allowlist 차단 사유로 멈춘다.
- sidecar dry-run call 응답에는 `realAgentGatePreview` shadow diagnostics를 추가했다. 이 preview는 adapter gate 평가 결과만 보여주며 실제 adapter 실행, SDK import, secret 값 노출, 전화 발신은 하지 않는다.
- Spring fake sidecar response에 `realAgentGatePreview`가 포함되어도 Spring DTO는 unknown field ignore로 처리하며 business logic 입력으로 사용하지 않는다.
- sidecar -> Spring event delivery / retry / ack 후보는 `sidecars/clawops-voice-agent/spring_event_delivery_policy.py`에 local/fake 순수 정책 테스트로만 추가했다. 실제 HTTP delivery client, retry loop, background worker, scheduler, queue는 아직 구현하지 않았다.
- 실제 SDK / secret / 발신 단계 진입 전 최종 readiness 기준은 README, `docs/logic/reservation-policy.md`, sidecar design plan에 정리했다. `sidecars/clawops-voice-agent/requirements-real-agent.txt`에는 opt-in `clawops[agent,openai]`와 `websockets>=13,<16` dependency를 명시했다. sidecar 전용 Python 3.12 real-agent venv에서 설치와 import availability는 확인했고, `real_agent_sdk_runner.py`에는 lazy import boundary, surface readiness check, real-agent execution boundary, SDK-object-free mocked runner 검증을 추가했다. 기본 dry-run 경로는 SDK 미설치 환경에서도 유지한다.
- Python 3.9.6 venv에서는 `clawops.agent`가 `enum.StrEnum` 부재로 import되지 않았지만, Python 3.12.13 sidecar venv에서는 `ClawOpsAgent` / `OpenAIRealtime` import compatibility를 확인했다. 실제 real-agent 개발은 Python 3.11+ 기준으로 유지해야 한다.
- OpenAI Realtime WebSocket 준비용 `websockets` import도 Python 3.12 venv에서 확인했다. 남은 gap은 dependency가 아니라 실제 SDK object 생성 / session start / call 실행 승인이다.
- secret 값 주입 전까지 가능한 runner 경계 검증은 완료했다. mocked runner는 confirmed / unavailable / needs-confirmation / failed / tool result missing / 예약 조건 충돌 결과를 mapper와 Spring dispatch candidate까지 연결하지만, 실제 SDK object 생성과 네트워크 호출은 하지 않는다.
- 2026-05-27 local dev PoC에서 Spring `dev,db,key` profile, `ddl-auto=validate`, Python 3.12 sidecar real-agent 설정으로 승인된 allowlist 1회 발신을 수행했다. Spring preflight와 sidecar readiness는 통과했고 ClawOps 통화 기록상 발신 완료까지 확인했다. 다만 AI가 결과 tool을 제출하지 않아 Spring에는 `AI_FAILED` 후보 이벤트가 기록되었고, 예약 확정 상태 전이와 AI 결과 schema 성공 결과는 아직 확인되지 않았다.
- Voice Agent 결과 tool 호출 누락을 줄이기 위해 prompt에 `submit_reservation_call_result` 필수 제출 규칙을 명시하고, SDK runner는 builtin tool을 비활성화한 뒤 결과 tool 제출과 call end를 race로 기다리도록 보강했다. 실제 SDK runner boundary는 fake ClawOps/OpenAI class로 tool 제출 / hangup / missing tool 결과를 검증한다. 실제 통화에서 AI 결과 tool 제출과 예약 상태 전이 성공은 아직 재검증이 필요하다.
- 남은 sidecar gap은 실제 OpenAI Realtime 통화 결과 확인, Python process lifecycle / 배포 방식, 실제 sidecar event delivery / retry / ack 운영 구현, 실제 provider call id 수신 후 상태 전이 기준이다.
- ClawOps status callback에 별도 event id 또는 delivery id가 제공되는지 확인이 필요하다. 없으면 `CallId + Event/CallStatus + Timestamp + payloadHash` fallback idempotency 기준을 적용해야 한다.
- ClawOps webhook signature 검증은 공식 문서상 `X-Signature` HMAC-SHA256이지만, reverse proxy / dev tunnel URL에서 base string이 일치하는지 dev PoC로 검증해야 한다.
- ClawOps fake server 기반 REST `calls.create` 요청/응답 mapping은 공식 SDK / 문서 기준으로 재확인했다. `POST /v1/accounts/{accountId}/calls`, Bearer 인증, JSON body, `Accept: application/json`, `To` / `From` / `StatusCallback` / `StatusCallbackEvent` / `Timeout` mapping을 검증했고, non-local 실제 endpoint 후보는 `dev + CLAWOPS + calling-enabled + real-call-enabled + allowlist + 필수 설정`을 모두 만족할 때만 통과하도록 코드 게이트를 두었다.
- ClawOps HTTP 오류는 fake server 기준으로 4xx/5xx status와 마스킹된 response body summary를 확인할 수 있게 보강했다. 다만 실제 운영 endpoint에서 어떤 status/body가 반환되는지는 다음 수동 승인 PoC 전까지 추가 확인이 필요하다.
- ClawOps 실제 발신 preflight는 `/admin/reservations/clawops-real-call/preflight`에서 관리자 전용으로 제공하며, 차단 사유와 마스킹된 대상 번호만 반환하고 실제 provider를 호출하지 않는다.
- dev-only target phone override는 DB / seed / 예약 phone snapshot을 수정하지 않고 provider 직전 대상 번호만 allowlist 테스트 번호로 바꾸는 안전장치로 구현되었다. prod profile, allowlist 불일치, override 번호 누락, ClawOps 필수 설정 누락 시에는 기존 no-op / preflight 차단 흐름을 따른다.
- 실제 ClawOps Voice Agent 연동, 실제 provider webhook endpoint 공개, 성공 기준 allowlist 번호 발신 테스트는 아직 구현 / 수행하지 않는다. 다음 실제 call create 재시도 전에는 `Url` / `AI` / Agent SDK mode 중 하나를 확정해야 한다.
- 현재 audit는 `clawops.real-call.candidate` structured log 후보만 있으며, 별도 DB audit table 또는 장기 보관 정책은 아직 확정되지 않았다.
- AI 생성 통화 요약 후보를 어디까지 저장할지와 raw transcript 저장 여부는 아직 확정되지 않았다. recording 저장, recording webhook, 녹음 파일 보관 정책은 현재 설계 범위에서 제외한다.
- 관리자 Mock 결과 API는 현재 관리자 전용으로 제한되어 있지만, prod 기본 비활성화 feature flag와 audit log 적용 여부는 실제 provider 전환 전에 추가 확정이 필요하다.
- 예약 요청의 개인정보 저장 범위와 보관 / 삭제 정책이 필요하다.
- 현재 중복 예약 차단은 동일 사용자 / 동일 식당 / 동일 예약 시각 기준이다. 식당 단위 전역 중복 예약을 막을지는 추가 확인이 필요하다.
- 확정된 예약을 앱에서 취소할 때 실제 식당 취소 전화까지 수행할지는 추가 확인이 필요하다.
- 예약 테이블 DDL/index 후보는 문서화했지만, 운영 DB migration 적용 방식은 아직 확정되지 않았다.
- 기본 seed 전화번호는 예약 테스트 편의를 위해 allowlist 테스트 번호로 통일했지만, 실제 운영 전화번호 품질 / 정규화 / 검수 정책은 아직 확정되지 않았다.

## 3. 현재 gap에서 제외한 항목
- 리스트 최소 5개 규칙
- 동일 리스트 내 동일 식당 중복 금지
- 리스트와 식당 지역 exact match
- 식당 추천 API 존재
- 리스트 추천 API 존재
- recommendation scorer / model 패키지 분리
- seed import runner 패키지 분리
- `PcmapSearchClient` 사용처 부재
- 검색 fallback 기준: 현재 코드는 내부 식당 결과가 0개일 때만 외부 fallback을 사용한다
- 별도 `RestaurantCategory` 엔티티 / `restaurant_categories` 테이블 의존: 현재 코드 기준 제거됨
- Mock 결과 반영 API 운영 노출 범위: 현재 코드 기준 관리자 전용 `/admin/reservations/{reservationId}/mock-result`로 제한한다
- 통화 녹음 기능 / recording 저장 정책: 현재 Voice Agent 설계 범위에서 사용하지 않음으로 확정한다
