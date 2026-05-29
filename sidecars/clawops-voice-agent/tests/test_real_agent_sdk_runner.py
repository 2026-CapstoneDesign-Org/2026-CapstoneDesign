import pathlib
import sys
import types
import unittest
import asyncio
from unittest.mock import patch


PRE_IMPORT_MODULES = set(sys.modules)
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from real_agent_adapter import APPROVAL_REQUIRED, REAL_AGENT_NOT_IMPLEMENTED, RealAgentBlockedError, RealAgentGateResult  # noqa: E402
from real_agent_interface import RealAgentCallRequest  # noqa: E402
from reservation_result_mapper import ReservationResultMappingContext  # noqa: E402
from real_agent_sdk_runner import (  # noqa: E402
    MockedRealAgentSdkRunner,
    SDK_NOT_READY,
    WAIT_CALL_ENDED,
    WAIT_RESULT_TOOL_SUBMITTED,
    RealAgentSdkRunner,
    RealAgentSdkSurfaceStatus,
    accepted_result_tool_response,
    build_real_agent_execution_skeleton,
    check_real_agent_sdk_surface,
    coerce_tool_result_payload,
    final_result_rejection_reason,
    load_reservation_agent_prompt,
    load_real_agent_sdk_modules,
    provider_fatal_failure_reason,
    safe_exception_failure_reason,
    run_real_agent_sdk_call,
    should_reject_early_ai_failed_result,
    should_reject_early_final_result,
    wait_for_result_tool_or_call_end,
)
from spring_event_dispatch_candidate import build_spring_event_dispatch_candidate, load_sample_result  # noqa: E402


SIGNING_KEY = "fake-spring-internal-signing-key"
TIMESTAMP = "2026-06-01T10:00:00Z"


class RealAgentSdkRunnerTest(unittest.TestCase):
    def test_module_import_does_not_import_sdk_modules(self):
        for module_name in ("clawops", "openai"):
            if module_name not in PRE_IMPORT_MODULES:
                self.assertNotIn(module_name, sys.modules)

    def test_lazy_loader_uses_injected_importer(self):
        imported = []

        def fake_importer(module_name):
            imported.append(module_name)
            return {"module": module_name}

        modules = load_real_agent_sdk_modules(importer=fake_importer)

        self.assertEqual(imported, ["clawops", "openai"])
        self.assertEqual(modules.clawops, {"module": "clawops"})
        self.assertEqual(modules.openai, {"module": "openai"})

    def test_surface_checker_reports_ready_with_required_symbols(self):
        status = check_real_agent_sdk_surface(
            importer=fake_surface_importer,
            module_finder=lambda name: object() if name == "websockets" else None,
        )

        self.assertTrue(status.ready)
        self.assertEqual(status.missing, ())

    def test_surface_checker_reports_missing_websockets(self):
        status = check_real_agent_sdk_surface(
            importer=fake_surface_importer,
            module_finder=lambda name: None,
        )

        self.assertFalse(status.ready)
        self.assertEqual(status.missing, ("websockets",))

    def test_runner_blocks_existing_gate_result_without_loading_sdk(self):
        runner = RealAgentSdkRunner(
            RealAgentGateResult(allowed=False, block_reasons=(APPROVAL_REQUIRED,), sdk_installed=True),
            sdk_loader=fail_if_loaded,
            surface_checker=fail_if_checked,
        )

        with self.assertRaises(RealAgentBlockedError) as captured:
            runner.run_reservation_call(call_request())

        self.assertEqual(captured.exception.gate_result.block_reasons, (APPROVAL_REQUIRED,))

    def test_runner_still_blocks_when_gate_would_allow(self):
        built = []
        runner = RealAgentSdkRunner(
            RealAgentGateResult(allowed=True, block_reasons=(), sdk_installed=True),
            sdk_loader=fail_if_loaded,
            surface_checker=ready_surface,
            execution_skeleton_builder=lambda request: built.append(request) or fake_execution_skeleton(),
        )

        with self.assertRaises(RealAgentBlockedError) as captured:
            runner.run_reservation_call(call_request())

        self.assertEqual(len(built), 1)
        self.assertEqual(captured.exception.gate_result.block_reasons, (REAL_AGENT_NOT_IMPLEMENTED,))

    def test_runner_blocks_when_sdk_surface_not_ready(self):
        runner = RealAgentSdkRunner(
            RealAgentGateResult(allowed=True, block_reasons=(), sdk_installed=True),
            sdk_loader=fail_if_loaded,
            surface_checker=missing_websockets_surface,
            execution_skeleton_builder=fail_if_built,
        )

        with self.assertRaises(RealAgentBlockedError) as captured:
            runner.run_reservation_call(call_request())

        self.assertEqual(captured.exception.gate_result.block_reasons, (SDK_NOT_READY,))

    def test_execution_skeleton_uses_masked_call_config_without_secret_values(self):
        skeleton = build_real_agent_execution_skeleton(
            call_request(),
            prompt_loader=lambda: "system prompt",
        )

        self.assertEqual(skeleton.session_config["className"], "OpenAIRealtime")
        self.assertEqual(skeleton.agent_config["className"], "ClawOpsAgent")
        self.assertEqual(skeleton.agent_config["apiKeyEnvName"], "CLAWOPS_API_KEY")
        self.assertEqual(skeleton.agent_config["builtinTools"], "SEND_DTMF")
        self.assertEqual(skeleton.result_tool["name"], "submit_reservation_call_result")
        self.assertIn("Mandatory final result tool", skeleton.result_tool["purpose"])
        self.assertEqual(skeleton.call_config["targetPhoneMask"], "****0000")
        self.assertEqual(skeleton.result_wait_config["missingResultPolicy"], "AI_PARSE_FAILED")
        self.assertEqual(skeleton.result_wait_config["source"], "submit_reservation_call_result_tool")
        self.assertTrue(skeleton.disconnect_config["finally"])
        self.assertEqual(skeleton.call_config["postResultClosingGraceSeconds"], 6.0)
        self.assertNotIn("target_phone_number", repr(skeleton))

    def test_execution_skeleton_prompt_requires_closing_after_result_tool(self):
        skeleton = build_real_agent_execution_skeleton(
            call_request(),
            prompt_loader=lambda: "system prompt",
        )

        self.assertIn("submit_reservation_call_result", skeleton.system_prompt)
        self.assertIn("After the result tool is accepted", skeleton.system_prompt)

    def test_prompt_instructs_wait_before_hearing_check_and_avoid_reconfirmation(self):
        prompt = load_reservation_agent_prompt()

        self.assertIn("Do not immediately fill silence with another question", prompt)
        self.assertIn('Do not ask "혹시 들리시나요?"', prompt)
        self.assertIn("Do not ask the same availability question again", prompt)
        self.assertIn("그 시간에 방문하겠습니다", prompt)

    def test_wait_helper_prefers_result_tool_before_call_end(self):
        async def scenario():
            result_event = asyncio.Event()
            call_session = FakeCallSession()

            async def submit_result():
                await asyncio.sleep(0.01)
                result_event.set()

            asyncio.create_task(submit_result())
            return await wait_for_result_tool_or_call_end(call_session, result_event, timeout_seconds=1)

        self.assertEqual(asyncio.run(scenario()), WAIT_RESULT_TOOL_SUBMITTED)

    def test_wait_helper_reports_call_end_without_result(self):
        async def scenario():
            result_event = asyncio.Event()
            call_session = FakeCallSession(end_after_seconds=0.01)
            return await wait_for_result_tool_or_call_end(call_session, result_event, timeout_seconds=1)

        self.assertEqual(asyncio.run(scenario()), WAIT_CALL_ENDED)

    def test_wait_helper_times_out_when_neither_result_nor_call_end_happens(self):
        async def scenario():
            result_event = asyncio.Event()
            call_session = FakeCallSession()
            return await wait_for_result_tool_or_call_end(call_session, result_event, timeout_seconds=0.01)

        with self.assertRaises(asyncio.TimeoutError):
            asyncio.run(scenario())

    def test_early_ambiguous_failed_result_is_rejected(self):
        async def scenario():
            started_at = asyncio.get_running_loop().time()
            return should_reject_early_ai_failed_result(
                "FAILED",
                "응답이 불명확하여 예약 가능 여부를 확인하지 못했습니다.",
                "응답 없음",
                started_at,
            )

        self.assertTrue(asyncio.run(scenario()))

    def test_early_confirmed_result_is_not_rejected_when_quality_gate_passes(self):
        async def scenario():
            started_at = asyncio.get_running_loop().time()
            return [
                should_reject_early_final_result(status, started_at)
                for status in ("CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION", "FAILED")
            ]

        self.assertEqual(asyncio.run(scenario()), [False, False, False, True])

    def test_late_or_non_failed_result_is_not_rejected(self):
        async def scenario():
            loop = asyncio.get_running_loop()
            late_started_at = loop.time() - 60
            return (
                should_reject_early_ai_failed_result(
                    "FAILED",
                    "응답이 불명확하여 예약 가능 여부를 확인하지 못했습니다.",
                    "응답 없음",
                    late_started_at,
                ),
                should_reject_early_final_result("CONFIRMED", late_started_at),
                should_reject_early_ai_failed_result(
                    "CONFIRMED",
                    "요청한 예약이 확정되었습니다.",
                    "",
                    loop.time(),
                ),
            )

        late_failed_rejected, late_confirmed_rejected, confirmed_rejected = asyncio.run(scenario())
        self.assertFalse(late_failed_rejected)
        self.assertFalse(late_confirmed_rejected)
        self.assertFalse(confirmed_rejected)

    def test_confirmed_result_rejects_conflicting_requested_details(self):
        request = call_request()

        self.assertIn(
            "reservationDateTime",
            final_result_rejection_reason(
                request=request,
                result_status="CONFIRMED",
                summary="6월 1일 19시 4명 예약이 가능하다고 확인했다.",
                confirmed_date_time="2026-06-01T20:00:00",
                party_size=4,
                alternative_time_suggested=False,
                alternative_date_time="",
                failure_reason="",
                transcript_summary="6월 1일 19시 4명 예약 가능.",
            ),
        )
        self.assertIn(
            "partySize",
            final_result_rejection_reason(
                request=request,
                result_status="CONFIRMED",
                summary="6월 1일 19시 5명 예약이 가능하다고 확인했다.",
                confirmed_date_time="2026-06-01T19:00:00",
                party_size=5,
                alternative_time_suggested=False,
                alternative_date_time="",
                failure_reason="",
                transcript_summary="6월 1일 19시 5명 예약 가능.",
            ),
        )

    def test_terminal_result_rejects_hearing_check_only_summary(self):
        request = call_request()

        rejection = final_result_rejection_reason(
            request=request,
            result_status="CONFIRMED",
            summary="들린다고 답변을 들었다.",
            confirmed_date_time="2026-06-01T19:00:00",
            party_size=4,
            alternative_time_suggested=False,
            alternative_date_time="",
            failure_reason="",
            transcript_summary="들리나요 확인 후 상대가 들린다고 했다.",
        )

        self.assertIn("hearing check", rejection)

    def test_terminal_result_requires_requested_time_and_party_in_summary(self):
        request = call_request()

        self.assertIn(
            "party size",
            final_result_rejection_reason(
                request=request,
                result_status="CONFIRMED",
                summary="6월 1일 19시 예약이 가능하다고 확인했다.",
                confirmed_date_time="2026-06-01T19:00:00",
                party_size=4,
                alternative_time_suggested=False,
                alternative_date_time="",
                failure_reason="",
                transcript_summary="6월 1일 19시 예약 가능.",
            ),
        )
        self.assertIn(
            "date/time",
            final_result_rejection_reason(
                request=request,
                result_status="CONFIRMED",
                summary="4명 예약이 가능하다고 확인했다.",
                confirmed_date_time="2026-06-01T19:00:00",
                party_size=4,
                alternative_time_suggested=False,
                alternative_date_time="",
                failure_reason="",
                transcript_summary="4명 예약 가능.",
            ),
        )

    def test_valid_confirmed_result_passes_quality_gate(self):
        self.assertIsNone(
            final_result_rejection_reason(
                request=call_request(),
                result_status="CONFIRMED",
                summary="6월 1일 19시 4명 예약이 가능하다고 확인했다.",
                confirmed_date_time="2026-06-01T19:00:00",
                party_size=4,
                alternative_time_suggested=False,
                alternative_date_time="",
                failure_reason="",
                transcript_summary="직원이 6월 1일 19시 4명 예약 가능하다고 답변했다.",
            )
        )

    def test_accepted_result_tool_response_instructs_closing_sentence(self):
        payload = accepted_result_tool_response()

        self.assertIn('"accepted":true', payload)
        self.assertIn("그 시간에 방문하겠습니다", payload)

    def test_real_sdk_call_boundary_with_fake_sdk_submits_result_and_hangs_up(self):
        fake_agent_class = build_fake_agent_class(submit_tool_result=True)

        with patch.dict(sys.modules, fake_clawops_agent_modules(fake_agent_class)), \
                patch("real_agent_sdk_runner.MIN_SECONDS_BEFORE_FINAL_RESULT", 0.0), \
                patch("real_agent_sdk_runner.POST_RESULT_CLOSING_GRACE_SECONDS", 0.0):
            result = asyncio.run(run_real_agent_sdk_call(call_request(), fake_execution_skeleton()))

        agent = fake_agent_class.instances[-1]
        self.assertEqual(agent.builtin_tools, [FakeBuiltinTool.SEND_DTMF])
        self.assertEqual(agent.call_to, call_request().target_phone_number)
        self.assertTrue(agent.call_session.hungup)
        self.assertTrue(agent.disconnected)
        self.assertEqual(result.provider_call_id, "fake-provider-call-sdk")
        self.assertEqual(result.ai_result["resultStatus"], "CONFIRMED")
        self.assertEqual(result.ai_result["confirmedDateTime"], "2026-06-01T19:00:00")
        self.assertEqual(result.ai_result["partySize"], 4)

    def test_real_sdk_call_boundary_with_fake_sdk_maps_missing_tool_result(self):
        fake_agent_class = build_fake_agent_class(submit_tool_result=False)

        with patch.dict(sys.modules, fake_clawops_agent_modules(fake_agent_class)):
            result = asyncio.run(run_real_agent_sdk_call(call_request(), fake_execution_skeleton()))

        agent = fake_agent_class.instances[-1]
        self.assertFalse(agent.call_session.hungup)
        self.assertTrue(agent.disconnected)
        self.assertEqual(result.ai_result["resultStatus"], "FAILED")
        self.assertEqual(result.ai_result["failureReason"], "AI_RESULT_TOOL_MISSING")
        self.assertTrue(result.retryable)

    def test_real_sdk_call_boundary_captures_fast_call_failed_event(self):
        fake_agent_class = build_fast_failure_agent_class("busy")

        with patch.dict(sys.modules, fake_clawops_agent_modules(fake_agent_class)):
            result = asyncio.run(run_real_agent_sdk_call(call_request(), fake_execution_skeleton()))

        self.assertEqual(result.ai_result["resultStatus"], "FAILED")
        self.assertEqual(result.ai_result["failureReason"], "CALL_BUSY")
        self.assertTrue(result.retryable)

    def test_safe_exception_failure_reason_uses_status_and_code_without_message(self):
        class ProviderException(Exception):
            pass

        exception = ProviderException("message could contain provider details")
        exception.status = 503
        exception.code = "temporary-outage"

        self.assertEqual(
            safe_exception_failure_reason(exception),
            "PROVIDEREXCEPTION_HTTP_503_TEMPORARY_OUTAGE_MESSAGE_COULD_CONTAIN_PROVIDER_DETAILS",
        )
        self.assertNotIn("temporary-outage", safe_exception_failure_reason(exception))

    def test_safe_exception_failure_reason_scrubs_phone_and_secret_like_values(self):
        class ProviderException(Exception):
            pass

        raw_phone = "010-" + "1234-" + "5678"
        raw_secret = "sk-" + "testsecretvalue0123456789"
        exception = ProviderException(
            f"failed target {raw_phone} token {raw_secret}"
        )
        exception.status = 500

        failure_reason = safe_exception_failure_reason(exception)

        self.assertIn("HTTP_500", failure_reason)
        self.assertNotIn(raw_phone.replace("-", "_"), failure_reason)
        self.assertNotIn(raw_secret.upper().replace("-", "_"), failure_reason)

    def test_provider_exception_failure_reason_is_marked_terminal(self):
        class ProviderException(Exception):
            pass

        exception = ProviderException("server failed")
        exception.status = 500

        self.assertTrue(provider_fatal_failure_reason(exception).startswith("PROVIDER_FATAL_ERROR__"))

    def test_mocked_sdk_runner_connects_fake_result_to_dispatch_candidate(self):
        expected_event_types = {
            "confirmed.json": "RESERVATION_CONFIRMED",
            "unavailable.json": "RESERVATION_UNAVAILABLE",
            "alternative-time.json": "RESERVATION_NEEDS_CONFIRMATION",
            "connection-failed.json": "CALL_CONNECTION_FAILED",
        }
        for sample_name, expected_event_type in expected_event_types.items():
            with self.subTest(sample=sample_name):
                runner = MockedRealAgentSdkRunner(lambda request: load_sample_result(sample_name))
                result = runner.run_reservation_call(call_request())
                candidate = build_spring_event_dispatch_candidate(
                    result.ai_result,
                    context(result),
                    SIGNING_KEY,
                    TIMESTAMP,
                )

                self.assertEqual(candidate.payload["eventType"], expected_event_type)
                self.assertEqual(candidate.payload["providerCallId"], result.provider_call_id)
                self.assertEqual(candidate.payload["sidecarCallId"], result.sidecar_call_id)
                self.assertNotIn(call_request().target_phone_number, repr(result))

    def test_mocked_sdk_runner_missing_tool_result_maps_to_parse_failed_candidate(self):
        runner = MockedRealAgentSdkRunner(lambda request: None)
        result = runner.run_reservation_call(call_request())
        candidate = build_spring_event_dispatch_candidate(
            result.ai_result,
            context(result),
            SIGNING_KEY,
            TIMESTAMP,
        )

        self.assertEqual(coerce_tool_result_payload(None), {})
        self.assertEqual(candidate.payload["eventType"], "AI_PARSE_FAILED")
        self.assertEqual(candidate.payload["failureReason"], "AI_RESULT_SCHEMA_FIELDS_INVALID")

    def test_mocked_sdk_runner_conflicting_confirmed_result_needs_confirmation(self):
        def conflicting_result(request):
            payload = load_sample_result("confirmed.json")
            payload["confirmedDateTime"] = "2026-06-01T20:00:00"
            return payload

        runner = MockedRealAgentSdkRunner(conflicting_result)
        result = runner.run_reservation_call(call_request())
        candidate = build_spring_event_dispatch_candidate(
            result.ai_result,
            context(result),
            SIGNING_KEY,
            TIMESTAMP,
        )

        self.assertEqual(candidate.payload["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
        self.assertEqual(candidate.payload["providerStatus"], "AI_RESULT_CONFLICT")
        self.assertEqual(candidate.payload["failureReason"], "AI_RESULT_CONFIRMED_DATETIME_CONFLICT")


def fail_if_loaded():
    raise AssertionError("SDK loader must not run in the skeleton")


def fail_if_checked():
    raise AssertionError("surface checker must not run when gate is already blocked")


def fail_if_built(request):
    raise AssertionError("execution skeleton must not build when SDK surface is not ready")


def fake_execution_skeleton():
    return build_real_agent_execution_skeleton(call_request(), prompt_loader=lambda: "system prompt")


def ready_surface():
    return RealAgentSdkSurfaceStatus(
        clawops_agent_importable=True,
        openai_realtime_importable=True,
        websockets_installed=True,
    )


def missing_websockets_surface():
    return RealAgentSdkSurfaceStatus(
        clawops_agent_importable=True,
        openai_realtime_importable=True,
        websockets_installed=False,
        missing=("websockets",),
    )


def fake_surface_importer(module_name):
    if module_name == "clawops.agent":
        return types.SimpleNamespace(
            ClawOpsAgent=object,
            OpenAIRealtime=object,
            BuiltinTool=object,
        )
    if module_name == "openai.resources.realtime.realtime":
        return types.SimpleNamespace(AsyncRealtimeConnection=object)
    raise ImportError(module_name)


def call_request():
    return RealAgentCallRequest(
        reservation_id=100,
        sidecar_call_id="fake-sidecar-call-100",
        target_phone_number="placeholder-target-token",
        target_phone_mask="****0000",
        restaurant_name="예약식당",
        reservation_date_time="2026-06-01T19:00:00",
        party_size=4,
    )


class FakeCallSession:
    def __init__(self, end_after_seconds=None):
        self.end_after_seconds = end_after_seconds

    async def wait(self):
        if self.end_after_seconds is None:
            await asyncio.Event().wait()
        else:
            await asyncio.sleep(self.end_after_seconds)


class FakeSdkCallSession:
    def __init__(self, end_after_seconds=None):
        self.call_id = "fake-provider-call-sdk"
        self.end_after_seconds = end_after_seconds
        self.hungup = False
        self.handlers = {}

    def on(self, event_name, handler):
        self.handlers[event_name] = handler

    async def wait(self):
        if self.end_after_seconds is None:
            await asyncio.Event().wait()
        else:
            await asyncio.sleep(self.end_after_seconds)

    async def hangup(self):
        self.hungup = True


class FakeOpenAIRealtime:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


class FakeBuiltinTool:
    NONE = "none"
    SEND_DTMF = "send_dtmf"


def build_fake_agent_class(submit_tool_result):
    class FakeClawOpsAgent:
        instances = []

        def __init__(self, **kwargs):
            self.kwargs = kwargs
            self.builtin_tools = kwargs.get("builtin_tools")
            self.tools = {}
            self.event_handlers = {}
            self.disconnected = False
            self.call_to = None
            self.call_session = None
            FakeClawOpsAgent.instances.append(self)

        def tool(self, fn):
            self.tools[fn.__name__] = fn
            return fn

        def on(self, event_name):
            def decorator(fn):
                self.event_handlers.setdefault(event_name, []).append(fn)
                return fn
            return decorator

        async def call(self, to, *, timeout=60):
            self.call_to = to
            if submit_tool_result:
                self.call_session = FakeSdkCallSession()
                asyncio.create_task(self.submit_result_after_tool_registration())
            else:
                self.call_session = FakeSdkCallSession(end_after_seconds=0.01)
            for event_name, handlers in self.event_handlers.items():
                for handler in handlers:
                    self.call_session.on(event_name, handler)
            return self.call_session

        async def submit_result_after_tool_registration(self):
            await asyncio.sleep(0.01)
            await self.tools["submit_reservation_call_result"](
                resultStatus="CONFIRMED",
                summary="6월 1일 19시 4명 예약이 가능하다고 확인했다.",
                confirmedDateTime="2026-06-01T19:00:00",
                partySize="4",
                reservationNameProvided="true",
                phoneNumberProvided="true",
                restaurantRequestedNameOrPhone="true",
                alternativeTimeSuggested="false",
                transcriptSummary="직원이 6월 1일 19시 4명 예약 가능하다고 답변했다.",
            )

        async def disconnect(self):
            self.disconnected = True

    return FakeClawOpsAgent


def build_fast_failure_agent_class(reason):
    class FakeClawOpsAgent:
        instances = []

        def __init__(self, **kwargs):
            self.kwargs = kwargs
            self.event_handlers = {}
            self.tools = {}
            self.disconnected = False
            self.call_session = None
            FakeClawOpsAgent.instances.append(self)

        def tool(self, fn):
            self.tools[fn.__name__] = fn
            return fn

        def on(self, event_name):
            def decorator(fn):
                self.event_handlers.setdefault(event_name, []).append(fn)
                return fn
            return decorator

        async def call(self, to, *, timeout=60):
            self.call_session = FakeSdkCallSession(end_after_seconds=0.01)
            for handler in self.event_handlers.get("call_failed", []):
                self.call_session.on("call_failed", handler)
                await handler(self.call_session, reason)
            return self.call_session

        async def disconnect(self):
            self.disconnected = True

    return FakeClawOpsAgent


def fake_clawops_agent_modules(fake_agent_class):
    clawops_module = types.ModuleType("clawops")
    agent_module = types.ModuleType("clawops.agent")
    agent_module.BuiltinTool = FakeBuiltinTool
    agent_module.ClawOpsAgent = fake_agent_class
    agent_module.OpenAIRealtime = FakeOpenAIRealtime
    return {
        "clawops": clawops_module,
        "clawops.agent": agent_module,
    }


def context(result):
    return ReservationResultMappingContext(
        reservation_id=100,
        requested_date_time="2026-06-01T19:00:00",
        requested_party_size=4,
        sidecar_call_id=result.sidecar_call_id,
        provider_call_id=result.provider_call_id,
        occurred_at="2026-06-01T10:00:00",
    )


if __name__ == "__main__":
    unittest.main()
