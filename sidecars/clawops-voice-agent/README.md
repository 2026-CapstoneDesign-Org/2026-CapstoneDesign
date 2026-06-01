# ClawOps Voice Agent Sidecar

This sidecar keeps the default path dry-run and stdlib-only, while also containing an opt-in real-agent path for local dev PoC work. The real-agent path is disabled by default and must pass runtime flags, Spring preflight, allowlist, approval, secret presence, and SDK readiness before it can create SDK objects or call a provider.

## Endpoints
- `GET /internal/clawops-agent/readiness`
- `POST /internal/clawops-agent/calls`

Both endpoints use `X-Request-Timestamp` and `X-Internal-Signature`. The signature is HMAC-SHA256 Base64 over `timestamp + "\n" + rawBody`.

## Environment
- `SIDECAR_PROFILE`
- `SIDECAR_RUNTIME_MODE=dry-run`
- `SIDECAR_REAL_CALL_ENABLED=false`
- `SIDECAR_REAL_AGENT_ENABLED=false`
- `SIDECAR_REAL_AGENT_APPROVAL_REQUIRED=true`
- `SIDECAR_REQUIRE_SPRING_PREFLIGHT=true`
- `SIDECAR_ALLOWED_TARGET_NUMBERS`
- `SIDECAR_INTERNAL_SIGNING_KEY`
- `SIDECAR_BIND_HOST`
- `SIDECAR_PORT`
- `SPRING_INTERNAL_BASE_URL`
- `SPRING_INTERNAL_SIGNING_KEY`

No DB configuration is used. Real ClawOps/OpenAI credentials remain out of scope until a separately approved phase.

## Optional Real-Agent Dependencies
`requirements-real-agent.txt` is an opt-in dependency file for a later approved real-agent phase. The default dry-run sidecar path remains stdlib-only and must pass tests without installing this file.

Current dependency declaration:
- `clawops[agent,openai]`
- `websockets>=13,<16`

Do not install this file during dry-run-only work. `websockets>=13,<16` is declared directly because the installed OpenAI 2.x package metadata uses that bound for its realtime extra, and ClawOps already installs `openai>=2.0.0`.

Local surface check notes:
- Python 3.9.6 can import REST surfaces, but not `clawops.agent` because the installed package uses `enum.StrEnum`.
- Python 3.12.13 in `.codex/venvs/clawops-voice-agent-real-agent-py312` imports `clawops.agent.ClawOpsAgent`, `clawops.agent.OpenAIRealtime`, and OpenAI Realtime resource modules.
- After adding the explicit `websockets` bound, Python 3.12.13 imports `websockets` and `real_agent_sdk_runner.check_real_agent_sdk_surface()` reports ready.

## Real-Agent Wiring Boundary
The sidecar now has an explicit runtime boundary:
- `SIDECAR_RUNTIME_MODE=dry-run`: default and only implemented mode
- `SIDECAR_RUNTIME_MODE=real-agent`: candidate mode for a later phase
- `SIDECAR_REAL_AGENT_ENABLED=false`: default hard stop

The default dry-run path does not import ClawOps SDK, OpenAI SDK, or start a Voice Agent. In `real-agent` mode, the call endpoint still evaluates the same gate and only continues when the request is explicitly non-dry-run and every local dev safety condition is satisfied. This keeps dry-run behavior available in SDK-free environments.

Readiness is process/dry-run readiness only. Real-agent execution blocks are reported by the call endpoint gate preview, not by breaking readiness.

Future SDK wiring should stay behind this boundary:
- `clawops_agent_sidecar.py` remains the HTTP/auth/allowlist/runtime gate.
- `real_agent_interface.py` defines the future runner request/result shape and an SDK-free fake implementation for tests.
- `real_agent_adapter.py` defines the lazy-import adapter skeleton and approval/preflight dependency gate.
- `reservation_result_mapper.py` remains the pure Voice Agent result -> Spring event mapper.
- `spring_event_dispatch_candidate.py` remains the Spring internal event payload/header builder.
- `spring_event_delivery_policy.py` classifies local/fake Spring event delivery outcomes without sending HTTP.
- `real_agent_sdk_runner.py` defines the SDK-backed runner boundary, lazy import helper, SDK surface readiness check, mocked runner tests, and the approved local real-agent execution path. SDK object creation is inside the runner function only and is reached only after runtime flags, local-only runbook checks, Spring preflight, readiness, allowlist, and explicit approval have passed.

Do not add SDK imports at module import time. Dry-run tests must pass without ClawOps or OpenAI packages installed.

`FakeRealAgentRunner` can return `CONFIRMED`, `UNAVAILABLE`, `NEEDS_CONFIRMATION`, or `FAILED` sample results. Those fake results are verified through the same mapper and dispatch candidate path used by dry-run previews. It does not call SDKs, read secrets, access a database, or place calls.

`real_agent_adapter.py` evaluates dependency candidates and blocks execution unless every safety condition is satisfied:
- `REAL_AGENT_DISABLED`
- `APPROVAL_REQUIRED`
- `SPRING_PREFLIGHT_REQUIRED`
- `SDK_NOT_INSTALLED`
- `SECRET_MISSING`
- `TARGET_NOT_ALLOWLISTED`
- `REAL_AGENT_NOT_IMPLEMENTED`

`REAL_AGENT_NOT_IMPLEMENTED` is still used when code is evaluating readiness without enabling the implementation path. The local dev implementation path passes `implementation_ready=true` only from the call endpoint after the approved real-agent request gate has passed.

Required real-agent environment names are `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, `CLAWOPS_FROM_NUMBER`, and `OPENAI_API_KEY`. The gate reports names only; it does not log environment secret values.

The dry-run call response includes `realAgentGatePreview` as shadow diagnostics. This preview is evaluation-only and does not execute the adapter:
- `runtimeMode`
- `realAgentEnabled`
- `approvalRequired`
- `springPreflightRequired`
- `allowed` / `blocked`
- `blockReasons`
- `missingRequiredEnvNames`
- `sdkInstalled`

The preview must never include secret values, signing keys, JWTs, or raw phone numbers. `missingRequiredEnvNames` contains environment variable names only.

Spring treats `realAgentGatePreview` as an optional diagnostic field. It is not a business input and the current Spring DTO intentionally ignores it as an unknown field.

## Voice Agent Contract
- Prompt: `prompts/reservation_agent_prompt.md`
- Result schema: `contracts/reservation_result_schema.json`
- Sample outcomes: `contracts/samples/*.json`
- Spring event payload fixtures: `contracts/spring-event-payloads/*.json`
- Dry-run mapper: `reservation_result_mapper.py`
- Dispatch candidate builder: `spring_event_dispatch_candidate.py`
- Real-agent interface boundary: `real_agent_interface.py`
- Real-agent adapter gate skeleton: `real_agent_adapter.py`
- Local/fake event delivery policy: `spring_event_delivery_policy.py`

The dry-run contract does not execute a Python Voice Agent and does not call OpenAI or ClawOps. The approved local real-agent path uses the same result schema and mapper after SDK execution.

`reservation_result_mapper.py` converts a validated Voice Agent result payload into Spring's internal event request shape. It is a pure mapper: it does not send HTTP requests and does not access a database.

`spring_event_dispatch_candidate.py` wraps that mapped payload with the Spring internal event path, raw JSON body, timestamp, and HMAC header candidate. It is still dry-run only and does not send the HTTP request.

`spring_event_delivery_policy.py` is not an HTTP client. It only classifies fake outcomes such as success, duplicate ack, network/timeout retry candidates, 4xx non-retry candidates, and 5xx retry candidates.

The dry-run `POST /internal/clawops-agent/calls` response includes a `springEventPreview` generated from sample result JSON. The preview omits signature values and is only for contract verification.

`contracts/spring-event-payloads/*.json` are shared fixtures for Python mapper tests and Spring internal event endpoint tests.

## SDK Surface Check
Checked surface, without SDK client creation or network calls:
- REST client: `clawops.ClawOps`, `clawops.AsyncClawOps`
- REST outbound call: `client.calls.create(to, from_, url=None, ai=None, status_callback=None, status_callback_event=None, timeout=None)`
- Webhook helper: `client.webhooks.verify(url, params, signature, signing_key)`
- Agent source surface: `ClawOpsAgent(from_, session, ...)`, `agent.connect()`, `agent.call(to, timeout=60)`, `agent.serve()`, `agent.disconnect()`
- OpenAI session source surface: `OpenAIRealtime(system_prompt, api_key=None, model="gpt-realtime-2", voice="marin", language="ko", greeting=False)`. `greeting=False` is intentional for phone calls because `greeting=True` creates the first model response as soon as the Realtime session starts, which can speak before the callee answers.
- Tool surface: `@agent.tool` registers async Python functions and maps primitive parameters to OpenAI tool schemas.

`RealAgentSdkRunner` implementation boundary:
- Run only after `real_agent_adapter.py` gate passes from the sidecar call endpoint.
- Lazy import `ClawOpsAgent`, `OpenAIRealtime`, and `BuiltinTool` inside the SDK runner function, not at module import time.
- Build the flow as prompt load, OpenAI Realtime session config, ClawOpsAgent config, result tool registration, outbound call candidate, result wait, and disconnect-finally.
- Build the system prompt from `prompts/reservation_agent_prompt.md` and inject reservation request values without mutating date, time, party size, name, or contact assumptions.
- Prefer ClawOps Agent SDK mode: create an `OpenAIRealtime` session, create a `ClawOpsAgent`, register `submit_reservation_call_result`, call the allowlisted target, race result-tool submission against call end, disconnect, and then map the captured result through `reservation_result_mapper.py`.
- The real-agent path enables only the ClawOps `send_dtmf` builtin tool for clear ARS / staff-routing menus. The AI must submit `submit_reservation_call_result` before ending the conversation; once an accepted result tool is received, the sidecar hangs up the call.
- To avoid calls ending during the opening exchange, the runner rejects early `FAILED` results for unclear opening responses, but it does not force a second confirmation when the restaurant clearly says the requested reservation is available. A hearing check such as "들리나요?" / "네 들립니다." is not a reservation outcome; the AI must give the restaurant time to answer after asking availability and must not immediately fill silence with another question. The runner rejects terminal result submissions whose summary does not mention the requested date/time and party size; `CONFIRMED` is accepted only when `confirmedDateTime` and `partySize` match the original request. After an accepted result tool call, the sidecar leaves a short closing grace period so the AI can say a final confirmation sentence before hangup.
- If the call ends before the result tool is submitted, the runner now collects ClawOps/OpenAI transcript events and applies a narrow demo fallback. A transcript with the original requested date/time and party size plus a clear restaurant availability answer can become a `CONFIRMED` candidate; unavailable / alternative / ambiguous transcript patterns map to `UNAVAILABLE`, `NEEDS_CONFIRMATION`, or `AI_PARSE_FAILED` instead of retrying forever.
- Do not use AI Completion mode unless explicitly re-approved, because it would pass OpenAI config through `calls.create(ai=...)` and would bypass the existing sidecar result tool boundary.
- If the result conflicts with the original reservation request, map to `NEEDS_CONFIRMATION` or `AI_PARSE_FAILED`, never directly to confirmed.
- Current local tests cover mocked SDK runner outcomes for confirmed, unavailable, needs-confirmation, failed, missing result tool output, transcript fallback, confirmed-result conflicts, result-tool-vs-call-end wait behavior, and the actual SDK runner boundary with fake ClawOps/OpenAI classes. These tests do not create real SDK clients, call external APIs, or send HTTP.

## Contract Drift Guard
Run the local guard before changing prompt/schema/mapper/Spring event fixtures:

```bash
python3 sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py
```

The guard runs:
- Python sidecar contract tests
- Spring `ClawOpsAgentProviderEventE2ETest`
- fixture/document leak scan for secret-like tokens and raw phone-like values

Spring event fixture names are fixed:
- `confirmed.json`
- `unavailable.json`
- `needs-confirmation.json`
- `failed.json`
- `ai-parse-failed.json`

If a fixture changes, update both the Python builder tests and Spring E2E fixture expectations in the same change. Fixtures must not contain API keys, JWTs, bearer tokens, or raw phone numbers.

## Local-Only Real Integration Runbook
This runbook is a safety checklist for a later ClawOps/OpenAI/Voice Agent integration phase. It does not grant approval to call external APIs or place calls.

This phase does not require setting real secrets. When a later approved real-integration phase begins, the user must provide real values directly in their local environment. Do not commit or document real ClawOps API keys, OpenAI API keys, signing keys, JWTs, or raw phone numbers. Spring should not receive ClawOps/OpenAI API keys in sidecar runtime; those secrets belong only to the local sidecar process.

The exact point where real secret values become necessary is the first separately approved real-agent run after dry-run, contract drift guard, Spring preflight, and sidecar readiness all pass. At that point the user must provide values only in their local environment; this repository should still contain only environment variable names.

Required state before any real-call candidate:
- Spring active profile includes `dev`
- Spring active profile does not include `prod`
- Spring retry/timeout scheduler is disabled
- Spring provider mode is `CLAWOPS`
- Spring provider runtime is `CLAWOPS_SIDECAR`
- Spring calling enabled and real-call enabled are explicitly enabled only for the local test window
- allowlist contains exactly one consented test number
- the test number owner has explicitly agreed to receive the call
- dev target override, if used, points to the same allowlisted test number
- sidecar base URL is local or private, never a public untrusted endpoint
- Spring internal signing key and sidecar signing key match through local environment only
- ClawOps/OpenAI secrets exist only in the sidecar local environment
- contract drift guard passes
- Spring preflight passes
- sidecar readiness passes
- dry-run call path succeeds before any real-call candidate

Run these checks in order:

```bash
cd Capstone
bash ./gradlew test
cd ..
python3 -m unittest discover sidecars/clawops-voice-agent/tests
python3 sidecars/clawops-voice-agent/scripts/verify_contract_drift_guard.py
```

After those pass, run Spring preflight, sidecar readiness, and dry-run call verification using only local/private endpoints and placeholder-safe logs. The next real-call step remains blocked until explicit user approval.

The only approval phrase that may unlock one actual test-number call in a future phase is:

```text
승인: ClawOps Voice Agent 테스트 번호 1회 발신
```

Stop immediately if any of these conditions occur:
- active profile includes `prod`
- scheduler is enabled
- allowlist has zero or more than one target
- dev target override does not match the allowlist
- sidecar endpoint is not local/private
- signing keys do not match
- preflight, readiness, dry-run, or contract drift guard fails
- logs or fixtures expose a secret, JWT, bearer token, or raw phone number
- external API call or phone call would happen without the exact approval phrase above

Rollback:
- Set `RESERVATION_PROVIDER_CALLING_ENABLED=false`
- Set `RESERVATION_PROVIDER_REAL_CALL_ENABLED=false`
- Stop the sidecar process
- Remove sidecar secret environment variables from the shell/session
- Empty the allowlist, or keep only the single consented test number for later dry-run checks
- Confirm scheduler remains disabled
- Re-run mock/no-op provider regression tests
- Re-run the contract drift guard

Actual Voice Agent execution, ClawOps/OpenAI SDK calls, and phone calls remain out of scope until a separately approved phase.

## Final Pre-Real-Integration Readiness
Current completed dry-run/fake pieces:
- Spring sidecar runtime/client sends local dry-run requests and ignores diagnostic-only unknown fields such as `realAgentGatePreview`
- Python sidecar readiness and dry-run call endpoints are implemented
- `realAgentGatePreview` reports gate diagnostics without executing the adapter
- mocked SDK runner checks cover the execution skeleton and fake result -> mapper -> dispatch candidate path
- `reservation_result_mapper.py` maps AI result schema samples to Spring internal event payloads
- `spring_event_dispatch_candidate.py` builds signed dispatch candidates without sending HTTP
- `spring_event_delivery_policy.py` classifies local/fake delivery, retry, and ack outcomes without an HTTP client or retry loop
- Spring internal event endpoint skeleton, event ledger, and idempotency tests are covered by shared fixtures
- contract drift guard covers Python tests, Spring fixture E2E, and sensitive-value scans
- `scripts/check_dev_real_agent_readiness.py` checks the public dev URL, optional Spring preflight, and optional sidecar readiness without placing calls or printing secret/raw phone values

- SDK wiring state: `requirements-real-agent.txt` declares opt-in `clawops[agent,openai]` and `websockets>=13,<16` dependencies. They are installed only in the sidecar Python 3.12 real-agent venv and are not imported by the default dry-run sidecar.
- Secret environment state: real values are local-only and must not be committed, documented, or logged.
- Voice Agent execution state: local approved execution path exists behind lazy imports and gates.
- Worker failure behavior: if the SDK runner raises before producing a result, the worker emits a generic `PROVIDER_FATAL_ERROR` Spring event candidate without logging exception detail values.
- Phone-call approval required: each allowlisted test-number call still requires explicit approval.
- Docs/tests only: readiness checklist maintenance, fake policy tests, contract drift guard improvements, and local-only runbook updates.

Environment variable names only:
- Spring: `RESERVATION_PROVIDER_RUNTIME`, `RESERVATION_PROVIDER_CALLING_ENABLED`, `RESERVATION_PROVIDER_REAL_CALL_ENABLED`, `CLAWOPS_SIDECAR_BASE_URL`, `CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY`
- Spring safety: `RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS`, `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED`, `RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER`
- Sidecar runtime: `SIDECAR_PROFILE`, `SIDECAR_RUNTIME_MODE`, `SIDECAR_REAL_CALL_ENABLED`, `SIDECAR_REAL_AGENT_ENABLED`, `SIDECAR_REAL_AGENT_APPROVAL_REQUIRED`, `SIDECAR_REQUIRE_SPRING_PREFLIGHT`
- Sidecar networking/auth: `SIDECAR_ALLOWED_TARGET_NUMBERS`, `SIDECAR_INTERNAL_SIGNING_KEY`, `SIDECAR_BIND_HOST`, `SIDECAR_PORT`, `SPRING_INTERNAL_BASE_URL`, `SPRING_INTERNAL_SIGNING_KEY`
- ClawOps/OpenAI future secrets: `CLAWOPS_API_KEY`, `CLAWOPS_ACCOUNT_ID`, `CLAWOPS_FROM_NUMBER`, `OPENAI_API_KEY`

SDK dependency state:
- `requirements-real-agent.txt` contains `clawops[agent,openai]` and explicit `websockets>=13,<16` for opt-in real-agent work.
- Python 3.12 install verification confirmed importable `clawops`, `openai`, `clawops.agent.ClawOpsAgent`, `clawops.agent.OpenAIRealtime`, OpenAI Realtime resource modules, and `websockets`.
- A direct `openai` dependency is not declared because ClawOps already installs `openai>=2.0.0`.

Do not call SDK APIs, create SDK clients, run a Voice Agent, or place calls unless the current run has explicit local dev approval and all safety checks pass. When real secret values are missing, stop and report: `이제 사용자가 로컬 환경변수에 직접 값을 넣어야 하는 타이밍입니다`.

Readiness check command:

```bash
python3 sidecars/clawops-voice-agent/scripts/check_dev_real_agent_readiness.py
```

Optional environment names for deeper checks are `DEV_BASE_URL`, `DEV_ADMIN_BEARER_TOKEN`, `DEV_PREFLIGHT_TARGET_PHONE_NUMBER`, `SIDECAR_BASE_URL`, and `SIDECAR_INTERNAL_SIGNING_KEY`. The script reports configured booleans and block reason names only.

Actual phone calls remain blocked unless the user provides exactly:

```text
승인: ClawOps Voice Agent 테스트 번호 1회 발신
```

## Local Dev PoC Status

2026-05-27~28 local dev execution reached the approved real-agent SDK path with Spring using dev profiles and `ddl-auto=validate`. Spring preflight and sidecar readiness passed, and Spring accepted sidecar real-agent call candidates. After ClawOps number onboarding and inbound fallback setup, approved allowlisted calls reached the AI result tool and Spring internal event endpoint.

2026-05-30~31 AWS dev execution uses the same Spring-sidecar path against the dev DB. Readiness and preflight passed, and allowlisted real calls were accepted by ClawOps/OpenAI. The latest AWS attempts still showed `AI_RESULT_TOOL_MISSING`, so the runner was updated to keep a short post-call result grace period, lower Realtime semantic VAD eagerness, map missing result tool output to `AI_PARSE_FAILED` / `NEEDS_CONFIRMATION` instead of retryable transient failure, and add transcript-based fallback for clear demo conversations. This fallback is intentionally narrow: clear availability can become confirmed, but ambiguous / hearing-check-only / conflicting transcripts do not become confirmed. No automatic retry was started.

For demo voice quality, the agent prompt now requires the first assistant turn to disclose that it is an AI reservation assistant and confirm the restaurant / branch name, then wait for the staff's answer before asking the date/time/party-size question. The Realtime session uses `greeting=False` so the model does not speak before the phone is answered. Turn detection keeps semantic VAD with low eagerness but disables automatic response interruption. Automatic VAD response creation is disabled, and the sidecar requests the first response one second after the SDK `call_start` event so the assistant can start after answer even if the staff stays silent. Later staff transcripts also trigger explicit response creation from the sidecar. The current request prompt includes a Korean spoken reservation phrase such as `6월 23일, 오후 8시 30분, 두 명` and instructs the model to use the commas as natural pauses.

Before another attempt:
- keep scheduler disabled
- keep allowlist to the single consented test number
- deploy or run the updated sidecar code next to the Spring process that will call it
- keep actual ClawOps provider call id persistence as a separate DB / DTO policy decision
- require a fresh one-call approval phrase
