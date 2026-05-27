import pathlib
import sys
import types
import unittest


PRE_IMPORT_MODULES = set(sys.modules)
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from real_agent_adapter import APPROVAL_REQUIRED, REAL_AGENT_NOT_IMPLEMENTED, RealAgentBlockedError, RealAgentGateResult  # noqa: E402
from real_agent_interface import RealAgentCallRequest  # noqa: E402
from reservation_result_mapper import ReservationResultMappingContext  # noqa: E402
from real_agent_sdk_runner import (  # noqa: E402
    MockedRealAgentSdkRunner,
    SDK_NOT_READY,
    RealAgentSdkRunner,
    RealAgentSdkSurfaceStatus,
    build_real_agent_execution_skeleton,
    check_real_agent_sdk_surface,
    coerce_tool_result_payload,
    load_real_agent_sdk_modules,
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
        self.assertEqual(skeleton.result_tool["name"], "submit_reservation_call_result")
        self.assertEqual(skeleton.call_config["targetPhoneMask"], "****0000")
        self.assertEqual(skeleton.result_wait_config["missingResultPolicy"], "AI_PARSE_FAILED")
        self.assertTrue(skeleton.disconnect_config["finally"])
        self.assertNotIn("target_phone_number", repr(skeleton))

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
