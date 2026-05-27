# AI 전화 예약 실제 Provider 연동 전 최종 점검

기준 날짜 및 시간: 2026-05-21 (Asia/Seoul)

## Status
provider-selection-readiness-documented

## 작업명
AI 전화 예약 실제 외부 provider 연동 전 최종 점검 및 provider 선택안 정리

## 목적
Mock 기반 AI 전화 예약 기능에 실제 AI/전화 provider를 붙이기 전에 현재 구현 준비 상태, provider 후보, 환경변수 후보, 결정 필요 항목, 다음 구현 순서를 정리한다.

이번 문서는 점검 / 연동 계획 문서다. 실제 OpenAI API, Twilio/SIP/전화 provider를 호출하지 않는다.

## 관련 문서 / 코드
문서:
- `AGENTS.md`
- `GUIDE.md`
- `DB.md`
- `LOGIC.md`
- `docs/db/reservations.md`
- `docs/logic/reservation-policy.md`
- `docs/logic/seed-import.md`
- `docs/current-gaps.md`
- `plans/ai-call-reservation-provider-webhook-plan-2026-05-20.md`

현재 예약 관련 코드:
- `ReservationController`
- `AdminController`
- `ReservationProviderWebhookController`
- `ReservationService`
- `ReservationProviderEventService`
- `ReservationCallAttemptService`
- `ReservationRetryTimeoutScheduler`
- `ReservationRetryTimeoutSchedulerService`
- `ReservationSchedulerExecutionGuard`
- `RestaurantReservation`
- `ReservationProviderEvent`
- `ReservationCallAttempt`
- `ReservationCallProvider`
- `MockReservationCallProvider`

참고 공식 문서:
- [OpenAI Realtime API](https://platform.openai.com/docs/guides/realtime/function-calls)
- [OpenAI Realtime API with SIP](https://platform.openai.com/docs/guides/realtime-sip)
- [OpenAI Realtime server-side controls](https://platform.openai.com/docs/guides/realtime-server-controls)
- [OpenAI Webhooks](https://platform.openai.com/docs/webhooks)
- [Twilio Programmable Voice Call resource](https://www.twilio.com/docs/voice/api/call)
- [Twilio Media Streams](https://www.twilio.com/docs/voice/media-streams)
- [Twilio webhook security](https://www.twilio.com/docs/usage/security)
- [Twilio voice dialing permissions](https://www.twilio.com/docs/sip-trunking/voice-dialing-geographic-permissions)
- [Vonage Voice API](https://developer.vonage.com/en/api/voice)
- [Vonage Voice API webhooks](https://developer.vonage.com/en/voice/voice-api/webhook-reference)
- [Vonage NCCO WebSocket endpoint](https://developer.vonage.com/en/voice/voice-api/ncco-reference)
- [SOLAPI 음성 메시지](https://solapi.com/developers/api/messages-voice)
- [ClawOps product page](https://claw-ops.com/)
- [ClawOps getting started](https://platform.claw-ops.com/docs/getting-started)
- [ClawOps Voice Agent](https://platform.claw-ops.com/docs/voice-agent)
- [ClawOps provider compatibility](https://platform.claw-ops.com/docs/voice-agent/providers)
- [ClawOps Python SDK](https://platform.claw-ops.com/docs/sdk/python)
- [ClawOps Webhooks](https://platform.claw-ops.com/docs/webhooks)
- [ClawOps Status Callback](https://platform.claw-ops.com/docs/webhooks/status)
- [ClawOps Recording Webhook](https://platform.claw-ops.com/docs/webhooks/recording)
- [ClawOps Transcript Webhook](https://platform.claw-ops.com/docs/webhooks/transcript)
- [ClawOps Summary Webhook](https://platform.claw-ops.com/docs/webhooks/summary)
- [ClawOps signature verification](https://platform.claw-ops.com/docs/webhooks/signature-verification)

## 현재 구현 상태 점검
### 완료된 부분
- 예약 생성 / 목록 조회 / 상세 조회 / 취소 API가 구현되어 있다.
- 예약 생성은 인증 사용자만 가능하며, visible 식당, 전화번호 존재, 미래 시각, 인원 수, 동일 사용자/식당/시각 중복을 검증한다.
- 관리자 Mock 결과 API는 관리자 권한으로 제한되어 있다.
- mock/test provider webhook skeleton이 구현되어 있고, 사용자 JWT가 아니라 provider용 HMAC/timestamp 검증을 거친다.
- provider raw payload를 곧바로 예약 Entity에 반영하지 않고 내부 표준 이벤트 command로 변환한다.
- `reservation_provider_events` event ledger Entity/Repository가 있고, idempotency key 기반 중복 이벤트를 막는다.
- terminal 예약에 늦게 도착한 provider event는 상태를 덮어쓰지 않는다.
- provider call id 불일치, 잘못된 상태 전이는 `REJECTED_INVALID`로 처리한다.
- `reservation_call_attempts` Entity/Repository가 있고, 시도 번호, provider call id, 실패 사유, 다음 retry 시각을 기록한다.
- retry/timeout scheduler 골격은 `reservation.scheduler.enabled=true`일 때만 등록된다.
- scheduler는 현재 실제 provider 호출 없이 Mock/no-op retry dispatch와 timeout 처리만 수행한다.
- provider 설정 구조와 `SafeReservationCallProvider` 안전 wrapper가 준비되어 있다.
- OpenAI Realtime / phone provider client interface와 no-op adapter가 준비되어 있다.
- seed 식당 전화번호는 예약 테스트 편의를 위해 `01000000000`로 통일되어 있다.
- 2026-05-20 기준 전체 테스트는 `bash ./gradlew test`로 통과했다.

### 남은 부분
- 실제 OpenAI client 구현은 아직 없다. 현재는 no-op adapter만 있다.
- 실제 전화 provider client 구현은 아직 없다. 현재는 no-op adapter만 있다.
- 실제 provider webhook mapper / signature verifier가 없다.
- 실제 outbound call 발신 기능이 없다.
- 실제 provider call id와 현재 내부 provider call id 생성 규칙의 매핑 정책이 미확정이다.
- 운영 DB migration 적용 방식이 미확정이다.
- 통화 녹음 / transcript 저장 여부와 보관 정책이 미확정이다.
- 사용자 동의, 식당 측 안내 멘트, 자동 발신 법적/운영 정책이 미확정이다.
- prod에서 Mock 결과 API를 완전히 비활성화할지, feature flag + audit log로 유지할지 미확정이다.
- 다중 인스턴스 scheduler lock/lease와 운영 알림 정책이 미확정이다.

## 실제 Provider 연동 후보
### 후보 A. Twilio Programmable Voice + OpenAI Realtime WebSocket bridge
구조:
- 백엔드가 Twilio Voice API로 식당에 outbound call을 요청한다.
- Twilio status callback과 Media Streams 이벤트를 현재 provider webhook/event ledger로 받는다.
- 별도 media bridge가 Twilio bidirectional Media Stream과 OpenAI Realtime WebSocket을 연결한다.

장점:
- Twilio는 outbound call API, call status, status callback, signature 검증 문서가 충분하다.
- Media Streams는 WebSocket으로 raw audio를 주고받을 수 있어 AI agent bridge를 직접 제어할 수 있다.
- 현재 event ledger / call attempt / webhook skeleton과 가장 자연스럽게 맞는다.

단점:
- 실시간 음성 bridge 구현 난도가 높다.
- 한국 번호 발신/수신, 발신번호 표시, geo permission, 규제 제약을 실제 계정에서 검증해야 한다.
- OpenAI Realtime과 Twilio audio codec/streaming 처리를 직접 맞춰야 한다.

평가:
- 2026-05-21 기준 기술 MVP 1순위 후보였으나, 2026-05-24 ClawOps 검토 후에는 비교 / 대체 후보로 조정한다.
- 국내 식당 대상 실제 발신 성공 여부와 발신번호 정책은 사전 검증이 필요하다.

### 후보 B. OpenAI Realtime SIP + SIP trunk provider
구조:
- SIP trunk provider가 PSTN 전화를 SIP로 OpenAI Realtime SIP endpoint에 연결한다.
- OpenAI의 `realtime.call.incoming` webhook과 sideband WebSocket을 사용해 통화 세션을 제어한다.

장점:
- OpenAI Realtime이 SIP 연결을 공식 지원하므로 speech-to-speech agent 구성이 단순해질 수 있다.
- server-side controls로 tool call, 예약 결과 추출, guardrail을 서버에서 제어할 수 있다.

단점:
- OpenAI SIP 문서는 inbound SIP를 Realtime session으로 받는 흐름이 중심이다.
- 식당으로 outbound call을 시작하고 AI를 연결하는 전체 call orchestration은 SIP trunk/provider 쪽 설계가 필요하다.
- SIP trunk, TLS, 전화번호, caller id, 한국 PSTN 연결 품질 검증이 필요하다.

평가:
- SIP 경험이 있거나 SIP trunk provider가 확정되면 좋은 후보.
- 현재 팀/프로젝트 MVP에서는 Twilio/Vonage 같은 Voice API 방식보다 초기 난도가 높을 수 있다.

### 후보 C. Vonage Voice API + OpenAI Realtime bridge
구조:
- Vonage Voice API로 outbound call을 만들고, answer/event webhook을 현재 provider event 구조로 변환한다.
- NCCO WebSocket endpoint를 통해 통화 audio stream을 OpenAI Realtime bridge로 연결하는 방식을 검토한다.

장점:
- Voice API, event webhook, signed webhook, WebSocket endpoint 문서가 있다.
- Twilio와 유사하게 provider event를 내부 표준 이벤트로 매핑하기 쉽다.

단점:
- 현재 프로젝트에는 Twilio 기준 예시가 더 많이 맞아 있고, 국내 전화번호/발신 정책은 별도 검증이 필요하다.
- OpenAI Realtime bridge는 여전히 직접 구현해야 한다.

평가:
- Twilio 계정/국가 제약이 막힐 때의 대체 후보.

### 후보 D. SOLAPI 음성 메시지
구조:
- SOLAPI의 음성 메시지 기능을 사용해 전화를 발송한다.

장점:
- 국내 서비스이고 발신번호 사전 등록 같은 국내 메시징 운영 흐름에 익숙할 수 있다.
- 단순 알림, DTMF 기반 확인에는 적합할 수 있다.

단점:
- 문서상 중심 기능은 음성 메시지 발송이며, 식당 직원과 자유 대화를 나누는 AI 예약 전화에는 맞지 않는다.
- 실시간 양방향 AI 대화, provider call event, transcript 추출 구조가 제한적일 가능성이 크다.

평가:
- 이번 “AI가 식당과 대화해 예약 가능 여부 확인” MVP의 주 provider로는 비권장.
- 예약 확인 안내/알림 같은 후속 보조 기능 후보로 남긴다.

### 후보 E. ClawOps 또는 국내 AI 전화 API
구조:
- 국내 전화/문자 API 또는 AI voice agent provider가 제공하는 call API와 webhook을 사용한다.

장점:
- 한국 전화번호/070/국내 통화 환경을 제품 목표로 삼는 provider라면 실제 국내 식당 발신 검증이 빠를 수 있다.
- AI voice agent 기능을 provider가 이미 제공하면 OpenAI/Twilio bridge 구현 부담을 줄일 수 있다.

단점:
- provider 안정성, SLA, webhook signature, idempotency, raw event 제공 수준, 데이터 보관 정책을 꼼꼼히 검증해야 한다.
- 내부 표준 이벤트로 충분히 변환 가능한지 PoC가 필요하다.

평가:
- 국내 실제 발신 MVP 후보로 검토 가치가 있다.
- 단, 공식 API 스펙, 보안 서명, 통화 녹음/transcript 정책, 비용, 계약 조건 확인 전까지 확정하지 않는다.

## 2026-05-24 ClawOps 집중 검토 결과
ClawOps는 기존 문서의 "국내 AI 전화 API" 후보 중 가장 구체적인 공식 문서와 REST/API 예시가 확인된 후보다. 이번 검토는 실제 API 호출 없이 공개 공식 문서만 기준으로 정리한다.

### 기능 확인 결과
| 항목 | 공식 문서 기준 확인 결과 | 설계 반영 |
| --- | --- | --- |
| 한국 070 번호 발급 | 제품 페이지와 시작하기 문서에서 대시보드/API 기반 070 번호 발급을 설명한다. 발급된 070 번호는 수신/발신 번호로 사용된다. | 국내 식당 대상 dev PoC 후보로 적합하다. |
| 아웃바운드 전화 | 시작하기와 Python SDK 문서에서 `agent.call(...)`, `calls.create(...)`, REST `POST /v1/accounts/{accountId}/calls` 예시를 제공한다. | 기존 `ReservationCallProvider`의 outbound call 시작 adapter로 매핑 가능하다. |
| AI 음성 에이전트 | Voice Agent SDK가 OpenAI Realtime, Gemini Realtime, STT-LLM-TTS Pipeline을 지원한다. 현재 문서는 OpenAI Realtime을 검증 완료로 표시한다. | Twilio + 자체 OpenAI bridge보다 AI 음성 agent 구현 부담이 작다. |
| OpenAI Realtime 연동 | ClawOpsAgent + OpenAIRealtime 예시와 모델/voice/language 설정이 제공된다. | Spring 백엔드가 OpenAI WebSocket bridge를 직접 운영하지 않고 ClawOps agent 경로를 PoC할 수 있다. |
| webhook/status callback | Status Callback, Recording, Transcript, Summary Webhook 문서가 있다. Status Callback은 `initiated`, `ringing`, `answered`, `completed` 이벤트를 제공한다. | 현재 내부 event type(`CALL_STARTED`, `CALL_CONNECTED`, `CALL_ENDED` 등)으로 변환 가능하다. |
| call id/event id | Status/Recording/Transcript/Summary payload는 `CallId`, `AccountId`, `Timestamp`를 제공한다. 별도 `EventId` 제공 여부는 공식 문서에서 확인되지 않는다. | idempotency는 `provider=CLAWOPS + CallId + Event/CallStatus + Timestamp + payloadHash` fallback 후보가 필요하다. |
| 녹음 | Recording Webhook은 `recording.completed`, `recording.failed`, `RecordingUrl`, `DurationSec`를 제공한다. 녹음 부가서비스 활성화가 필요하다. | MVP에서는 저장하지 않고, 정책 확정 후 opt-in으로만 검토한다. |
| transcript | Transcript Webhook은 `transcript.completed`, `transcript.failed`, `TranscriptUrl`, `SegmentCount`, speaker segment 예시를 제공한다. 유료 부가서비스다. | 예약 결과 추출 보조 자료 후보지만 개인정보 정책 확정 전 저장하지 않는다. |
| summary | Summary Webhook은 `summary.completed`, `summary.failed`, `SummaryJson`을 제공한다. 전사와 요약 부가서비스가 필요하다. | 예약 결과 판단용으로 매력적이나, provider 요약을 곧바로 확정값으로 신뢰하지 않고 내부 검증 규칙이 필요하다. |
| signature 검증 | `X-Signature` HMAC-SHA256 방식과 signing key, URL + 정렬 파라미터 기반 base string이 문서화되어 있다. | 현재 mock HMAC verifier 구조를 ClawOps 전용 verifier로 확장 가능하다. |
| Java/Spring REST 연동 | 제품 페이지와 SDK 문서에 REST/cURL 호출 예시가 있다. 공식 Java SDK는 확인되지 않았다. | Spring에서는 REST client adapter로 PoC 가능성이 높지만, Voice Agent 실행은 Python/Node SDK 의존 가능성을 별도 검증해야 한다. |
| Python/Node SDK 필수 여부 | 번호/통화 REST API는 cURL 예시가 있으나, 고수준 Voice Agent는 Python/Node SDK 중심으로 설명된다. | Spring 단독 REST 호출과 별도 Python/Node agent sidecar 중 하나를 선택해야 한다. |

### 기존 구조와의 적합성
- `SafeReservationCallProvider`에는 `CLAWOPS` provider mode 후보를 추가하는 방식이 가장 자연스럽다.
- `ReservationCallProvider`는 유지한다. 예약 도메인은 provider가 Twilio인지 ClawOps인지 몰라야 한다.
- `PhoneProviderClient`는 ClawOps REST `calls.create` adapter 후보로 재사용할 수 있다.
- `OpenAiRealtimeClient`는 ClawOps Voice Agent를 선택하면 Spring 백엔드의 직접 OpenAI bridge 역할이 줄어든다. 다만 OpenAI session instruction/version 관리 후보로 보존한다.
- `ReservationProviderEvent` ledger는 ClawOps의 `CallId`, `CallStatus`/`Event`, `Timestamp`를 내부 표준 이벤트로 변환하는 데 사용할 수 있다.
- `CallId`는 `providerCallId`로 매핑한다. `EventId`가 없으면 현재 설계의 fallback idempotency key가 필요하다.
- call attempt / retry / timeout scheduler와 충돌하지 않는다. ClawOps status callback의 `initiated`, `ringing`, `answered`, `completed`를 시도 상태에 연결하면 된다.
- dev-only allowlist, prod 차단, `calling-enabled` feature flag는 그대로 적용한다. ClawOps adapter도 allowlist를 통과한 번호에만 실제 발신 후보 client로 진행해야 한다.
- 관리자 Mock API와 mock webhook은 유지한다. 실제 ClawOps adapter 도입 후에도 local/test/dev 검증 경로로 남길 수 있다.

### Twilio 대비 비교
| 비교 항목 | ClawOps | Twilio + OpenAI Realtime bridge |
| --- | --- | --- |
| 구현 난이도 | 070 번호, 발신, Voice Agent, status webhook이 같은 제품군에 묶여 있어 PoC 난도가 낮다. 단, Voice Agent는 Python/Node SDK 중심이다. | Voice API와 status callback은 성숙하지만, Media Stream과 OpenAI Realtime bridge를 직접 구현해야 한다. |
| 한국 전화번호/국내 발신 | 한국 070 번호와 국내 인프라를 제품 핵심으로 내세운다. | 한국 070 번호와 국내 발신번호 정책은 별도 검증 부담이 크다. |
| OpenAI Realtime 연결 | ClawOpsAgent + OpenAIRealtime 경로가 문서화되어 있다. | 별도 WebSocket bridge, audio codec 변환, session 제어 구현이 필요하다. |
| webhook/idempotency | Status/Recording/Transcript/Summary webhook과 HMAC 문서가 있다. 단, event id는 문서상 확인 필요다. | Twilio는 CallSid/status callback/signature 문서가 충분하다. 이벤트 중복/순서 처리는 직접 설계해야 한다. |
| 비용/무료 체험/제한 | 제품 페이지 기준 Beta/Trial, 070 번호 1개, 월 발신통화 10분 등 무료 체험 정보가 있다. 과금/제한은 가입 후 콘솔 확인 필요다. | Twilio는 글로벌 과금/크레딧 체계와 콘솔이 성숙하지만 국내 번호/통화 비용은 계정에서 확인해야 한다. |
| 문서 신뢰도/API 안정성 | 한국어 문서가 구체적이나 Beta 문구가 있고 운영 안정성/SLA 검증이 필요하다. | Twilio 문서는 성숙하고 API 안정성이 높다. 국내 전화 적합성은 별도 문제다. |
| 캡스톤 시연 적합성 | 국내 070 + AI agent 시연까지 빠르게 닿을 가능성이 높다. | 기술적으로 정석적이나 bridge 구현량이 커서 캡스톤 일정에는 부담이 크다. |

### 결론 제안
결론은 **C안: provider abstraction은 유지하되 ClawOps adapter를 먼저 PoC한다**로 둔다.

이유:
- ClawOps는 한국 070, outbound call, OpenAI Realtime 기반 Voice Agent, status callback, recording/transcript/summary webhook, HMAC signature까지 예약 전화 MVP에 필요한 구성요소가 가장 한 제품 안에 모여 있다.
- 하지만 Beta/Trial 성격, 공식 Java SDK 부재, Voice Agent의 Python/Node SDK 의존 가능성, event id 부재 가능성, REST API 문서 완성도, 개인정보/녹음 정책 미확정 때문에 바로 A안처럼 1순위 provider로 "확정"하기에는 이르다.
- 현재 코드가 이미 provider abstraction, no-op 안전장치, event ledger, retry/timeout scheduler를 갖고 있으므로 ClawOps를 먼저 PoC해도 Twilio 계획을 버릴 필요가 없다.

### ClawOps를 채택한다면 변경 후보
- `ReservationProviderMode`에 `CLAWOPS` 추가
- `ClawOpsProperties` 추가: account id, base url, from number, status callback url, webhook signing key, recording/transcript/summary flags
- `ClawOpsPhoneProviderClient` 또는 `ClawOpsReservationCallProviderAdapter` 추가
- `ClawOpsReservationProviderWebhookController` 또는 기존 provider webhook controller에 `clawops` path 추가
- `ClawOpsWebhookSecurityVerifier` 추가: `X-Signature`, HMAC-SHA256, URL + 정렬 파라미터 base string 검증
- `ClawOpsWebhookMapper` 추가: form payload를 내부 `ReservationProviderEventCommand`로 변환
- `ClawOpsEventIdempotencyKeyFactory` 후보 추가: event id가 없으면 `CallId + Event/CallStatus + Timestamp + payloadHash`
- `SafeReservationCallProvider`의 dev-only guard에 `CLAWOPS` 설정 완비 조건 추가
- 문서상 `PhoneProviderClient`는 유지하고, Spring 단독 REST adapter와 Python/Node Voice Agent sidecar 중 하나를 후속 PoC에서 결정

### 구현 전 반드시 확인할 항목
- ClawOps REST API만으로 "AI agent가 식당에 전화하고 대화 결과를 예약 가능/불가/확인 필요로 반환"하는 흐름이 가능한지
- Voice Agent가 Python/Node SDK 상주 프로세스를 요구하는지, Spring 백엔드에서 REST 호출만으로 시작/종료/결과 수신이 충분한지
- outbound call의 status callback에 `CallId`, `CallStatus`, `Timestamp` 외 event id 또는 delivery id가 있는지
- status callback의 실패 원인 세분화가 가능한지: busy, no-answer, failed, provider error 등
- webhook signature base string이 Spring 환경의 reverse proxy URL과 정확히 맞는지
- 070 번호 발급, 발신번호 표시, 실제 국내 휴대폰/유선 착신 정책
- Beta/Trial 제한, SLA, status page, 장애 시 대체 provider 정책
- 녹음/전사/요약 사용 시 개인정보 처리, 고지/동의, 저장 위치, 보관/삭제 정책
- 실제 식당 발신 전 사용자 동의와 식당 측 AI/자동 전화 고지 문구

### 2026-05-24 ClawOps adapter skeleton 구현 상태
이번 구현은 실제 ClawOps API 호출 없이 내부 연결 지점만 준비한 상태다. 이후 REST client 계약 구조도 추가했지만, HTTP 요청은 localhost fake server 계약 테스트에서만 허용한다.

구현된 부분:
- `ReservationProviderMode.CLAWOPS`를 외부 provider 후보로 추가했다.
- `ClawOpsProperties`를 추가해 `clawops.*` 설정을 환경변수 기반으로 바인딩할 수 있게 했다.
- `ReservationProviderActivationGuard`에 ClawOps 설정 완비 검사를 추가했다.
- `SafeReservationCallProvider`는 ClawOps mode에서 OpenAI WebSocket bridge 준비 단계를 거치지 않고 ClawOps phone provider로만 진행한다.
- `ClawOpsCreateCallRequest` / `ClawOpsCreateCallResponse`로 ClawOps call 생성 요청 / 응답 계약 후보를 표현한다.
- `ClawOpsRestClient`는 `POST /v1/accounts/{accountId}/calls` 요청 계약을 구현한다.
- `ClawOpsPhoneProviderClient`는 `clawops.base-url`이 `localhost`, `127.0.0.1`, `::1`인 fake server일 때만 `ClawOpsRestClient`를 호출한다.
- `clawops.base-url`이 실제 운영 endpoint이면 `NOOP_CLAWOPS_HTTP_CONTRACT_DISABLED`로 처리한다.
- `ClawOpsWebhookMapper`는 ClawOps status callback form payload를 내부 `ReservationProviderEventCommand` 후보로 변환한다.
- `ClawOpsWebhookSecurityVerifier`는 `X-Signature` HMAC-SHA256 검증 골격을 제공한다.
- `NoopPhoneProviderClient`는 ClawOps mode 요청을 ClawOps adapter로 라우팅할 수 있다.

아직 구현하지 않은 부분:
- 실제 ClawOps 운영 endpoint REST `calls.create` 호출
- 실제 ClawOps Voice Agent / Python 또는 Node sidecar 실행
- 실제 ClawOps webhook endpoint 공개
- recording / transcript / summary webhook의 도메인 반영
- event id 또는 delivery id 실측 검증
- 실제 allowlist 번호 발신 테스트

REST 요청 계약 후보:
- method: `POST`
- path: `/v1/accounts/{accountId}/calls`
- header: `Authorization: Bearer {CLAWOPS_API_KEY}`
- header: `Accept: application/json`
- content type: `application/json`
- body 후보: `To`, `From`, `Url`, `AI`, `StatusCallback`, `StatusCallbackEvent`, `Timeout`
- 현재 Spring adapter는 실제 OpenAI 호출을 막기 위해 `AI`를 보내지 않고, VoiceML `Url`도 아직 보내지 않는다. ClawOps 공식 SDK 기준 `url`과 `ai`를 모두 생략하면 Agent SDK mode 후보가 되므로, 다음 실제 재시도 전 Python / Node Voice Agent sidecar를 실행할지, VoiceML `Url` mode 또는 AI Completion `AI` mode로 전환할지 결정해야 한다.
- `StatusCallback` / `StatusCallbackEvent`는 SDK 계약상 선택값으로 보이지만, 예약 상태 추적과 event ledger 검증을 위해 dev PoC 필수 설정으로 유지한다.

REST 응답 계약 후보:
- call id: 공식 SDK 모델의 `call_id`를 우선으로 보고, Agent SDK / webhook 표기 차이를 흡수하기 위해 `callId`, `CallId` alias도 허용한다.
- status: 공식 SDK 모델의 `status`를 우선으로 보고, `call_status`, `callStatus`, `CallStatus` alias도 허용한다.
- fake server 성공 응답은 내부 `PhoneProviderCallStartResult(started=true, status=CALLING, provider=CLAWOPS)`로 변환한다.
- fake server 오류 응답은 `NOOP_CLAWOPS_HTTP_ERROR_{status}`로 변환하고 예약 상태는 `REQUESTED`로 유지한다.
- HTTP 오류 body는 JSON이면 민감 필드와 전화번호를 마스킹하고, JSON이 아니면 민감 패턴을 마스킹한 뒤 길이를 제한한다.
- timeout과 network error는 각각 `NOOP_CLAWOPS_TIMEOUT`, `NOOP_CLAWOPS_NETWORK_ERROR`로 구분한다.

### 2026-05-26 ClawOps Python Voice Agent sidecar 설계
상세 설계는 `plans/clawops-voice-agent-sidecar-design-2026-05-26.md`에 분리한다.

결론:
- provider abstraction은 유지한다.
- ClawOps 실제 AI 전화 PoC의 우선 경로는 Spring direct REST adapter가 아니라 Python SDK 기반 Voice Agent sidecar로 둔다.
- Spring은 예약 / preflight / allowlist / call attempt / event ledger / retry-timeout의 source of truth로 남는다.
- sidecar는 ClawOps Voice Agent와 OpenAI Realtime 실행만 맡고 DB에 직접 접근하지 않는다.
- ClawOps API key와 OpenAI API key는 sidecar secret으로 두는 방향을 우선 검토한다.
- sidecar 결과는 Spring 내부 provider event endpoint로 돌려주고, Spring이 idempotency와 상태 전이를 최종 결정한다.

남은 구현 전 결정:
- `reservation.provider.runtime=direct-rest|sidecar` 같은 runtime property 이름
- Spring -> sidecar internal endpoint와 signature 방식
- sidecar -> Spring provider event endpoint와 idempotency key
- sidecar process lifecycle / Docker Compose / dev 서버 배포 방식
- Voice Agent tool schema와 예약 성공 / 불가 / 확인 필요 구조화 기준

## 추천 Provider 조합
### 1순위 PoC 후보
`ClawOps adapter + 기존 ReservationCallProvider abstraction`

이유:
- 국내 070 번호, outbound call, AI voice agent, status callback, HMAC webhook이 한 provider 안에 문서화되어 있어 캡스톤 PoC에 가장 빠르게 닿을 가능성이 높다.
- 현재 코드의 provider event ledger, call attempt, retry/timeout scheduler와 잘 맞는다.
- provider call id는 ClawOps `CallId`를 `providerCallId`로 매핑하면 된다.
- call status는 `initiated`, `ringing`, `answered`, `completed`를 내부 event type으로 변환한다.
- event id가 문서상 확인되지 않으므로 idempotency fallback 기준을 먼저 테스트로 고정해야 한다.

단, 실제 식당 발신 전에는 반드시 allowlist 전화번호로만 dev 테스트를 수행해야 한다.

### 비교 / 대체 기술 MVP 후보
`Twilio Programmable Voice + OpenAI Realtime WebSocket bridge`

이유:
- outbound call, call status callback, webhook signature, media streaming 문서가 비교적 명확하다.
- 공식 Java SDK와 글로벌 운영 안정성이 강점이다.
- ClawOps PoC에서 REST/SDK 제약, webhook 품질, 국내 발신 품질, Beta 안정성 문제가 나오면 돌아갈 수 있는 대체 후보로 유지한다.

보류 이유:
- OpenAI Realtime과 Twilio audio codec/streaming을 직접 연결하는 bridge 구현 부담이 크다.
- 한국 070 번호와 국내 발신번호 정책은 별도 검증이 필요하다.

### 국내 실사용 검증 후보
`국내 전화 API / AI 전화 API provider + OpenAI Realtime 또는 provider 내장 agent`

ClawOps 외에도 국내 provider는 비교 후보로 남긴다. 국내 발신번호, 070/대표번호, 통화 품질, 이용약관, 자동 발신 정책은 글로벌 provider보다 국내 provider가 유리할 수 있다.

단, webhook signature, idempotency event id, call status granularity, transcript export, 녹음 저장 옵션이 현재 내부 구조와 맞는지 먼저 확인해야 한다.

### 보류 후보
`OpenAI Realtime SIP + SIP trunk`

이유:
- 기술적으로 매력적이지만, outbound call orchestration과 SIP trunk 운영 난도가 있다.
- SIP provider가 확정된 뒤 재검토한다.

## 필요한 환경변수 후보
실제 secret 값은 작성하지 않는다. 이름만 정의한다.

### 공통
- `RESERVATION_PROVIDER_MODE`
- `RESERVATION_PROVIDER_CALLING_ENABLED`
- `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED`
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`
- `RESERVATION_PROVIDER_WEBHOOK_BASE_URL`
- `RESERVATION_PROVIDER_WEBHOOK_MAX_CLOCK_SKEW_SECONDS`
- `RESERVATION_PROVIDER_WEBHOOK_RAW_PAYLOAD_STORE_ENABLED`
- `RESERVATION_PROVIDER_TRANSCRIPT_STORE_ENABLED`
- `RESERVATION_PROVIDER_RECORDING_STORE_ENABLED`
- `RESERVATION_PROVIDER_MOCK_RESULT_API_ENABLED`
- `RESERVATION_SCHEDULER_ENABLED`
- `RESERVATION_SCHEDULER_FIXED_DELAY_MS`

### OpenAI
- `OPENAI_API_KEY`
- `OPENAI_PROJECT_ID`
- `OPENAI_REALTIME_MODEL`
- `OPENAI_REALTIME_VOICE`
- `OPENAI_REALTIME_INSTRUCTIONS_VERSION`
- `OPENAI_REALTIME_SESSION_TIMEOUT_SECONDS`
- `OPENAI_REALTIME_WS_URL`
- `OPENAI_REALTIME_SIP_ENDPOINT`
- `OPENAI_WEBHOOK_SECRET`

### Twilio
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_FROM_NUMBER`
- `TWILIO_STATUS_CALLBACK_URL`
- `TWILIO_VOICE_WEBHOOK_URL`
- `TWILIO_MEDIA_STREAM_URL`
- `TWILIO_DIALING_COUNTRY_ALLOWLIST`
- `TWILIO_WEBHOOK_SIGNATURE_ENABLED`

### ClawOps
- `CLAWOPS_API_KEY`
- `CLAWOPS_ACCOUNT_ID`
- `CLAWOPS_BASE_URL`
- `CLAWOPS_FROM_NUMBER`
- `CLAWOPS_STATUS_CALLBACK_URL`
- `CLAWOPS_WEBHOOK_SIGNING_KEY`
- `CLAWOPS_WEBHOOK_SIGNATURE_HEADER`
- `CLAWOPS_CALL_TIMEOUT_SECONDS`
- `CLAWOPS_RECORDING_ENABLED`
- `CLAWOPS_TRANSCRIPT_ENABLED`
- `CLAWOPS_SUMMARY_ENABLED`
- `CLAWOPS_AGENT_RUNTIME_MODE`

### Vonage
- `VONAGE_APPLICATION_ID`
- `VONAGE_PRIVATE_KEY`
- `VONAGE_FROM_NUMBER`
- `VONAGE_ANSWER_URL`
- `VONAGE_EVENT_URL`
- `VONAGE_WEBHOOK_SIGNATURE_SECRET`
- `VONAGE_WEBSOCKET_AUDIO_URL`

### 국내 / Generic provider
- `CALL_PROVIDER_BASE_URL`
- `CALL_PROVIDER_API_KEY`
- `CALL_PROVIDER_API_SECRET`
- `CALL_PROVIDER_FROM_NUMBER`
- `CALL_PROVIDER_WEBHOOK_SECRET`
- `CALL_PROVIDER_WEBHOOK_SIGNATURE_HEADER`
- `CALL_PROVIDER_EVENT_ID_HEADER`
- `CALL_PROVIDER_TIMEOUT_SECONDS`

## 환경 분리 기준
### local
- `RESERVATION_PROVIDER_MODE=mock`
- 실제 발신 비활성화
- mock webhook / 관리자 Mock API 허용 가능
- 외부 webhook 공개 URL 필요 시 ngrok 등 임시 터널만 사용

### test
- `RESERVATION_PROVIDER_MODE=mock`
- 실제 발신 비활성화
- Mock provider와 fake verifier만 사용
- 외부 API key 없음

### dev
- 기본은 mock
- 실제 provider 테스트는 별도 profile 또는 명시 flag 필요
- `RESERVATION_CALL_ALLOWLIST_ENABLED=true`
- allowlist 번호 외 발신 금지
- 관리자 Mock API는 관리자 권한 + audit log 후보
- ClawOps mode를 켜도 현재 구현은 `NOOP_CLAWOPS_CLIENT_STUB`까지만 진행하며 실제 발신하지 않음

### prod
- mock webhook 비활성화
- 관리자 Mock API 기본 비활성화
- 실제 provider webhook만 허용
- secret rotation, audit log, call allowlist/denylist, rate limit 필요
- 통화 녹음/transcript 저장 여부가 정책으로 확정되기 전에는 저장하지 않는다.

## 실제 연동 전 결정사항
- 실제 전화 provider를 ClawOps, Twilio, Vonage, OpenAI SIP + SIP trunk, 국내 provider 중 무엇으로 할지
- ClawOps를 쓴다면 Spring REST adapter만으로 충분한지, Python/Node Voice Agent sidecar가 필요한지
- 한국 번호로 식당에 안정적으로 발신 가능한지
- 발신번호를 어떤 번호로 표시할지
- 실제 식당에 자동 전화하기 전 사용자 동의를 어떻게 받을지
- 식당 측 첫 멘트에서 AI/자동 전화임을 고지할지
- 통화 녹음을 저장할지
- transcript 또는 AI 요약만 저장할지
- raw provider payload 저장 범위와 보관 기간
- OpenAI Realtime의 tool call 결과를 예약 결과로 확정하는 기준
- provider webhook signature 검증 방식
- provider event id가 없을 때 idempotency fallback 기준
- 운영 DB migration 도구
- prod에서 관리자 Mock API를 완전히 제거할지, feature flag로 남길지
- retry가 식당에 중복 전화로 보이지 않도록 최대 시도 횟수와 간격을 어떻게 둘지
- 다중 인스턴스 scheduler lock/lease 방식
- 실패/불명확 결과를 운영자가 확인할 화면 또는 로그

## 다음 구현 단계 제안
1. ClawOps adapter PoC 설계 확정
   - 기존 provider abstraction은 유지한다.
   - `CLAWOPS` mode, 설정값, REST adapter, webhook mapper/verifier 범위를 별도 구현 계획으로 확정한다.
   - dev allowlist 번호로만 발신 가능한 테스트 계획을 작성한다.
2. ClawOps 설정 / client 골격
   - 실제 호출 없이 `ClawOpsProperties`, no-op/fake client, config validation test를 먼저 둔다.
   - `calling-enabled=false`, prod profile, allowlist 미충족 시 반드시 no-op으로 떨어지게 한다.
3. ClawOps webhook mapper / signature verifier
   - `CallId`, `CallStatus`/`Event`, `Timestamp`를 내부 `ReservationProviderEventCommand`로 변환한다.
   - HMAC-SHA256 signature base string을 공식 문서 기준으로 구현하고 테스트로 고정한다.
4. ClawOps idempotency fallback
   - event id가 없을 때 `CallId + Event/CallStatus + Timestamp + payloadHash` 조합을 검증한다.
   - 같은 status callback이 재전송되어도 상태가 다시 바뀌지 않게 한다.
5. AI agent 실행 방식 PoC
   - Spring 단독 REST 호출로 가능한 범위와 Python/Node Voice Agent sidecar가 필요한 범위를 나눈다.
   - OpenAI Realtime instruction/tool schema는 ClawOps agent 설정 또는 별도 sidecar 설정으로 관리한다.
6. dev 환경 실제 발신 테스트
   - 식당 번호가 아니라 팀 소유 allowlist 번호로만 테스트한다.
   - call attempt, event ledger, retry/timeout, terminal 보호를 함께 검증한다.
7. 운영 전 안전장치
   - prod mock 비활성화
   - call rate limit
   - allowlist/denylist
   - audit log
   - scheduler distributed lock
   - provider 비용 모니터링

## Computer Use 외부 콘솔 설정 체크리스트
실제 provider PoC 전 Computer Use로 브라우저 / 콘솔 확인이 필요한 항목이다. 이번 단계에서는 실제 secret 값을 문서에 쓰지 않는다.

OpenAI 콘솔:
- API key 발급과 프로젝트 범위 확인
- Realtime 사용 가능 모델 확인
- webhook secret 발급 여부 확인
- Realtime instruction / tool schema를 어디에서 관리할지 결정

Twilio 콘솔:
- Account SID / Auth Token 확인
- dev 테스트용 발신 번호 준비
- 한국 또는 테스트 대상 국가 발신 권한 확인
- voice status callback URL 설정
- voice webhook URL 설정
- Media Stream WebSocket URL 설정
- webhook signature 검증 방식 확인

ClawOps 콘솔:
- 070 번호 발급 가능 여부와 dev 테스트용 발신 번호 확인
- `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, webhook signing key 발급 위치 확인
- outbound call REST API와 Voice Agent SDK 중 어떤 경로가 예약 MVP에 맞는지 확인
- status callback URL 등록 방식과 payload 샘플 확인
- event id 또는 webhook delivery id 제공 여부 확인
- recording/transcript/summary 부가서비스 활성화 여부와 비용 확인
- Beta/Trial 제한, 동시통화 수, 발신 가능 분수, status page/SLA 확인

dev 인프라:
- provider가 접근 가능한 HTTPS webhook base URL 준비
- allowlist에는 팀 소유 테스트 번호만 등록
- 실제 식당 번호는 allowlist에 넣지 않음
- 비용 한도와 사용량 알림 설정

## ClawOps dev 실제 발신 PoC 실행 준비 런북
이번 런북은 다음 단계에서 allowlist 테스트 번호로만 실제 ClawOps 발신 PoC를 수행하기 위한 준비 문서다. 이 문서 작성 단계에서는 실제 ClawOps API, OpenAI API, Twilio/SIP API를 호출하지 않고 실제 전화 발신도 하지 않는다.

### 현재 코드 기준 출발점
- `CLAWOPS` provider mode와 `ClawOpsProperties`는 준비되어 있다.
- `ReservationProviderActivationGuard`는 dev profile, prod 차단, calling enabled, real-call enabled, allowlist, ClawOps 필수 설정을 검사한다.
- `ClawOpsRestClient`는 현재 `localhost`, `127.0.0.1`, `::1` fake server base-url에서만 HTTP 요청을 허용한다.
- `ClawOpsPhoneProviderClient`는 실제 운영 endpoint 후보에 대해 `dev profile + prod profile 미포함 + CLAWOPS mode + calling-enabled + real-call-enabled + allowlist 일치 + ClawOps 필수 설정`을 모두 요구한다.
- 조건이 하나라도 빠지면 no-op으로 멈춘다.
- 실제 endpoint 후보가 활성화될 때는 `clawops.real-call.candidate` structured log를 남긴다. secret은 기록하지 않는다.
- 이번 단계에서는 실제 ClawOps API 호출과 전화 발신을 수행하지 않는다.

### ClawOps 콘솔 준비 항목
- 계정 생성 또는 프로젝트 생성
- dev PoC 전용 070 발신 번호 준비
- API key 발급 위치 확인
- account id 확인
- status callback URL 설정 방식 확인
- webhook signing key 확인
- 비용 한도, 사용량 알림, Trial 제한 확인
- 동시통화 수와 월 발신 가능 분수 확인
- 테스트 번호 allowlist 확인
- 실제 식당 번호가 테스트 allowlist에 들어가지 않았는지 확인
- Voice Agent 사용 시 OpenAI Realtime 연결 경로와 Python/Node SDK sidecar 필요 여부 확인

### 서버 환경변수 후보
실제 secret 값은 문서에 쓰지 않는다. 이름과 의미만 둔다.

| 환경변수 | 의미 | dev PoC 조건 |
| --- | --- | --- |
| `CLAWOPS_API_KEY` | ClawOps API 인증 키 | 실제 값은 로컬/배포 secret store에만 저장 |
| `CLAWOPS_ACCOUNT_ID` | ClawOps account id | 콘솔에서 확인 |
| `CLAWOPS_FROM_NUMBER` | dev PoC용 070 발신 번호 | 팀이 소유한 번호만 사용 |
| `CLAWOPS_BASE_URL` | ClawOps API base URL | 실제 endpoint 후보는 dev-only real-call gate 통과 시에만 허용 |
| `CLAWOPS_STATUS_CALLBACK_URL` | ClawOps status callback 수신 URL | HTTPS dev tunnel 또는 dev 서버 URL 필요 |
| `CLAWOPS_WEBHOOK_SIGNING_KEY` | ClawOps webhook 서명 검증 키 | 실제 값 문서화 금지 |
| `RESERVATION_PROVIDER_MODE` | 예약 provider mode | `CLAWOPS` |
| `RESERVATION_PROVIDER_CALLING_ENABLED` | 외부 provider 호출 활성화 | `true`, 수동 승인 후에만 |
| `RESERVATION_PROVIDER_REAL_CALL_ENABLED` | 실제 endpoint 후보 활성화 | `true`, 수동 승인 후에만 |
| `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED` | 발신 allowlist 활성화 | `true` 고정 |
| `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS` | 실제 발신 허용 번호 목록 | 테스트 번호 1개만 |
| `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED` | dev PoC 대상 번호 override 활성화 | 서버 DB snapshot을 수정할 수 없을 때만 `true` |
| `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER` | provider 직전 override 대상 번호 | allowlist 테스트 번호와 동일해야 함 |
| `RESERVATION_PROVIDER_EXTERNAL_CALL_ALLOWED_PROFILES` | 외부 호출 허용 profile | `dev` |
| `RESERVATION_PROVIDER_PROD_EXTERNAL_CALL_BLOCKED` | prod profile 차단 | `true` 고정 |
| `RESERVATION_SCHEDULER_ENABLED` | retry/timeout scheduler | 최초 실제 발신 PoC에서는 `false` 권장 |

### 실제 PoC 실행 전 체크리스트
- active profile에 `dev`가 포함되어 있다.
- active profile에 `prod`가 포함되어 있지 않다.
- `RESERVATION_PROVIDER_MODE=CLAWOPS`다.
- `RESERVATION_PROVIDER_CALLING_ENABLED=true`는 최종 승인 직전에만 설정한다.
- `RESERVATION_PROVIDER_REAL_CALL_ENABLED=true`는 최종 승인 직전에만 설정한다.
- `RESERVATION_PROVIDER_CALL_ALLOWLIST_ENABLED=true`다.
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`에는 테스트 번호 1개만 있다.
- 테스트 번호 소유자가 실제 발신 수신에 동의했다.
- seed 식당 번호 또는 테스트용 식당 번호가 실제 식당 번호가 아니라 테스트 번호다.
- 실제 식당 전화번호가 allowlist / seed / 요청 body / DB row 어디에도 섞이지 않았는지 확인했다.
- 호출 대상 예약이 테스트 사용자 / 테스트 식당 / 테스트 시간의 예약이다.
- `/admin/reservations/clawops-real-call/preflight`를 호출해 `realCallCandidate=true`를 확인했다.
- `reservation.scheduler.enabled=false`로 두어 retry가 자동으로 반복 발신하지 않는다.
- 호출 후 즉시 `restaurant_reservations.provider`, `provider_call_id`, `provider_status`, `status`를 확인할 준비가 되어 있다.
- 호출 후 즉시 `reservation_call_attempts`와 `reservation_provider_events` 기록을 확인할 준비가 되어 있다.
- 실패 또는 의심 상황에서 `RESERVATION_PROVIDER_CALLING_ENABLED=false`로 즉시 차단할 수 있다.
- 비용 한도와 사용량 알림이 켜져 있다.

### 수동 승인 게이트
실제 발신 단계는 자동화하지 않는다. 실행 직전에 아래 확인 문구를 사용자에게 그대로 제시하고 명시 승인을 받아야 한다.

승인 문구 후보:
```text
ClawOps dev 실제 발신 PoC를 시작합니다.
이번 호출은 allowlist에 등록된 테스트 번호 1개로만 발신되어야 하며, 실제 식당 번호로 발신되면 안 됩니다.
비용이 발생할 수 있고, 테스트 번호 소유자의 수신 동의가 필요합니다.
현재 profile, provider mode, calling-enabled, allowlist, 대상 예약, 대상 전화번호를 확인했습니다.
실제 발신을 1회 진행해도 되면 "승인: ClawOps 테스트 번호 1회 발신"이라고 답해주세요.
```

실행 명령 전 최종 확인 항목:
- 현재 profile 출력
- `RESERVATION_PROVIDER_MODE`
- `RESERVATION_PROVIDER_CALLING_ENABLED`
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`
- 대상 예약 id
- 대상 식당 id / 전화번호 snapshot
- 대상 번호가 테스트 번호와 정확히 일치하는지
- scheduler 비활성화 여부

실제 발신 후 확인 항목:
- HTTP 응답 status와 ClawOps call id
- 예약 상태가 의도한 상태로만 변경되었는지
- `provider=CLAWOPS`, `providerCallId`, `providerStatus` 저장 여부
- call attempt 생성 여부
- status callback 수신 여부
- 중복 이벤트가 idempotency로 처리되는지
- 실패 시 `calling-enabled=false`로 되돌렸는지

### 롤백 절차
문제가 생기면 아래 순서로 즉시 롤백한다.

1. `RESERVATION_PROVIDER_CALLING_ENABLED=false`
2. `RESERVATION_PROVIDER_REAL_CALL_ENABLED=false`
3. `RESERVATION_PROVIDER_MODE=mock`
4. `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS` 비우기
5. `CLAWOPS_API_KEY` 제거 또는 secret store 비활성화
6. `RESERVATION_SCHEDULER_ENABLED=false`
7. 실행 중인 dev 서버 재시작 또는 환경변수 reload
8. 새 예약 생성이 `provider=MOCK` 또는 `NOOP`으로 돌아오는지 확인
9. Mock provider 예약 생성 / 관리자 Mock 결과 API / mock webhook 회귀 테스트 실행

### dev-only real-call gate 구현 상태
- `reservation.provider.real-call-enabled`를 추가했다.
- 기본값은 `false`다.
- `ReservationProviderActivationGuard`는 ClawOps 실제 endpoint 후보에 대해 `real-call-enabled=true`를 요구한다.
- `ClawOpsEndpointAccessPolicy`는 `ClawOpsPhoneProviderClient`를 직접 호출하는 경우에도 mode, calling flag, real-call flag, profile, allowlist, ClawOps 설정을 다시 확인한다.
- `ClawOpsRestClient`는 localhost fake server가 아니면 `ClawOpsEndpointAccessPolicy`가 통과한 real endpoint 후보에서만 요청을 허용한다.
- prod profile에서는 `real-call-enabled=true`여도 `NOOP_PROD_PROFILE_BLOCKED`로 차단한다.
- 실제 endpoint 후보가 활성화되는 순간 `clawops.real-call.candidate` structured log를 남긴다.
- ClawOps call create 실패 시 structured log는 method, path template, provider mode, 마스킹된 to/from 번호, error type, HTTP status, 마스킹된 response summary만 남긴다. Authorization/API key/JWT/signing key/원본 번호는 남기지 않는다.

### 실제 발신 전 preflight 구현 상태
- `/admin/reservations/clawops-real-call/preflight` 관리자 API를 추가했다.
- 요청은 특정 `reservationId` 또는 `targetPhoneNumber` 기준으로 검증할 수 있다.
- 이 API는 ClawOps / OpenAI / Twilio / 전화 provider를 호출하지 않는다.
- 응답에는 실제 발신 가능 후보 여부, 차단 사유, 경고, provider mode, active profile, scheduler 활성 여부, 마스킹된 실제 대상 번호, 마스킹된 DB phone snapshot, dev target override 적용 여부, 예약 id / 식당 id / 예약 상태, ClawOps 설정 누락 항목 이름만 포함한다.
- secret 값과 API key 원문은 응답과 로그에 남기지 않는다.
- `realCallCandidate=true`가 아니면 수동 승인 단계로 넘어가지 않는다.

### dev-only target phone override 구현 상태
- 서버 DB / seed / 예약 phone snapshot은 수정하지 않는다.
- `reservation.provider.dev-target-phone-override-enabled=false`가 기본값이다.
- `CLAWOPS` mode, dev profile, prod profile 미포함, `calling-enabled=true`, `real-call-enabled=true`, allowlist 활성화 / 비어 있지 않음, override number 존재, override number allowlist 포함, ClawOps 필수 설정이 모두 맞을 때만 provider 직전 대상 번호를 override한다.
- preflight는 DB snapshot이 allowlist 밖인 경우에도 override target이 allowlist 테스트 번호이면 `realCallCandidate=true` 후보로 표시할 수 있다.
- 응답과 로그에는 DB snapshot과 override target의 원문 번호를 남기지 않고 마스킹 값만 남긴다.

### 실제 발신 전 남은 코드 / 정책 후보
- 실제 endpoint 호출 전 사용자 수동 승인 UI 또는 운영자 run command 분리
- `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`가 테스트 번호 1개인지 더 강하게 검증할지 결정
- `ClawOpsPhoneProviderClient`에서 4xx/5xx 오류를 transient / fatal로 구분
- 실제 ClawOps response field 샘플에 맞춰 response mapping 보강
- ClawOps status callback endpoint 추가
- event id가 없으면 `CallId + Event/CallStatus + Timestamp + payloadHash` idempotency fallback을 테스트로 고정

### 다음 구현 후보
- dev-only ClawOps 실제 발신 1회 PoC 수동 승인 실행
- ClawOps 실제 REST response mapping 보강
- ClawOps status callback endpoint skeleton 추가
- ClawOps webhook signature base string dev tunnel 검증
- Voice Agent를 Spring REST adapter로 처리할지 Python/Node sidecar로 둘지 결정
- 실제 발신 PoC 전용 테스트 식당 fixture 생성 방식 결정
- 실제 발신 후 call attempt / event ledger 검증 쿼리 문서화

## Progress
- [x] 현재 구현 상태 점검
- [x] provider 후보 정리
- [x] 환경변수 후보 정리
- [x] 실제 연동 전 결정사항 정리
- [x] 다음 구현 단계 제안
- [x] ClawOps 집중 검토 및 Twilio 대비 비교
- [x] ClawOps provider mode / properties / stub adapter / mapper / verifier skeleton 구현
- [x] ClawOps REST client 계약 DTO / fake server 검증 구조 구현
- [x] ClawOps dev 실제 발신 PoC 실행 준비 런북 작성
- [x] ClawOps dev-only real-call gate 구현
- [x] ClawOps 실제 발신 전 preflight 검증 API 구현
- [x] ClawOps call create 계약 공식 SDK / 문서 기준 재확인
- [x] ClawOps Python Voice Agent sidecar 설계 분리
- [ ] 사용자 검토 / provider 선택
- [ ] 후속 구현 계획 작성

## 결정 사항 / 변경 로그
- 2026-05-21: 실제 provider 호출 없이 provider 선택안과 연동 전 점검 문서를 작성했다.
- 2026-05-21: 기술 MVP 1순위는 `Twilio Programmable Voice + OpenAI Realtime WebSocket bridge` 후보로 정리했다.
- 2026-05-21: 국내 실제 발신 검증은 국내 전화 API / AI 전화 API provider를 별도 후보로 남겼다.
- 2026-05-21: SOLAPI 음성 메시지는 자유 대화형 예약 전화의 주 provider로는 비권장으로 정리했다.
- 2026-05-21: 실제 호출 없이 provider 설정 구조, OpenAI/phone provider client interface, no-op adapter, `SafeReservationCallProvider` 안전 wrapper를 추가했다.
- 2026-05-21: dev profile, prod 차단, allowlist 필수, provider 설정 완비 조건을 모두 만족해야 외부 provider 후보 client까지 진행하도록 dev-only PoC guard를 보강했다.
- 2026-05-24: ClawOps 공식 문서를 기준으로 070 번호, outbound call, Voice Agent, OpenAI Realtime, status/recording/transcript/summary webhook, HMAC signature 검증 가능성을 확인했다.
- 2026-05-24: 결론은 `C안: provider abstraction은 유지하되 ClawOps adapter를 먼저 PoC한다`로 정리했다. Twilio 계획은 삭제하지 않고 비교 / 대체 후보로 남긴다.
- 2026-05-24: 실제 ClawOps API 호출 없이 `CLAWOPS` mode, `ClawOpsProperties`, no-op/stub call adapter, status callback mapper, HMAC signature verifier skeleton을 추가했다.
- 2026-05-24: 실제 ClawOps 운영 endpoint 호출 없이 `ClawOpsCreateCallRequest` / `ClawOpsCreateCallResponse` / `ClawOpsRestClient` 계약 구조를 추가하고, localhost fake server에서만 HTTP 요청 mapping을 검증하도록 제한했다.
- 2026-05-24: 실제 발신 없이 ClawOps dev 실제 발신 PoC 런북, 수동 승인 게이트, 롤백 절차, 후속 코드 변경 후보를 정리했다.
- 2026-05-24: `reservation.provider.real-call-enabled` 기본값 `false`를 추가하고, ClawOps non-local 실제 endpoint 후보는 dev profile, prod 차단, CLAWOPS mode, calling flag, real-call flag, allowlist, 필수 설정을 모두 통과해야만 접근 후보가 되도록 게이트를 구현했다. 실제 API 호출과 전화 발신은 수행하지 않았다.
- 2026-05-24: 실제 provider 호출 없이 ClawOps 실제 발신 전 preflight API를 추가해 특정 예약 또는 테스트 번호 기준으로 발신 가능 후보 여부, 차단 사유, scheduler 상태, 마스킹된 대상 번호를 확인할 수 있게 했다.
- 2026-05-26: dev allowlist 조건에서 ClawOps call create를 1회 시도했으나 `NOOP_CLAWOPS_HTTP_ERROR`로 종료되었다. 성공 call id 저장, 추가 재시도, OpenAI 호출은 없었다.
- 2026-05-26: 실제 재발신 없이 ClawOps 공식 문서와 Python SDK 0.26.5 기준으로 `calls.create` 계약을 재확인했다. 현재 endpoint, account id path, Bearer 인증, JSON PascalCase body는 일치하며, Spring adapter에 `Accept: application/json` header 검증을 추가했다. `Url` / `AI` mode 선택과 Voice Agent sidecar 필요 여부는 다음 재시도 전 결정사항으로 남긴다.
- 2026-05-26: Python SDK 기반 Voice Agent sidecar 방식을 별도 설계 문서로 분리했다. Spring은 예약 source of truth, sidecar는 ClawOps / OpenAI 실행 전용으로 두는 안을 우선 권장한다.

## 완료 조건
- 실제 provider 후보와 추천 조합이 문서화되어 있다.
- 실제 secret 값 없이 환경변수 이름만 정리되어 있다.
- 실제 연동 전 결정사항이 `docs/current-gaps.md`와 연결되어 있다.
- 실제 API 호출, seed 수정, 추천/랭킹 반영이 없다.
