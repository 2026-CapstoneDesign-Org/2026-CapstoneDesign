import pathlib
import sys
import unittest


PRE_IMPORT_MODULES = set(sys.modules)
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from real_agent_adapter import (  # noqa: E402
    APPROVAL_REQUIRED,
    REAL_AGENT_DISABLED,
    REAL_AGENT_NOT_IMPLEMENTED,
    SDK_NOT_INSTALLED,
    SECRET_MISSING,
    SPRING_PREFLIGHT_REQUIRED,
    TARGET_NOT_ALLOWLISTED,
    RealAgentAdapter,
    RealAgentBlockedError,
    RealAgentDependencyContext,
    evaluate_real_agent_gate,
)
from real_agent_interface import RealAgentCallRequest  # noqa: E402


class RealAgentAdapterTest(unittest.TestCase):
    def test_module_import_does_not_import_sdk_modules(self):
        for module_name in ("clawops", "openai"):
            if module_name not in PRE_IMPORT_MODULES:
                self.assertNotIn(module_name, sys.modules)

    def test_real_agent_disabled_blocks(self):
        result = evaluate_real_agent_gate(context(real_agent_enabled=False), sdk_probe=lambda: True)

        self.assertFalse(result.allowed)
        self.assertIn(REAL_AGENT_DISABLED, result.block_reasons)

    def test_approval_required_blocks(self):
        result = evaluate_real_agent_gate(context(approval_granted=False), sdk_probe=lambda: True)

        self.assertFalse(result.allowed)
        self.assertIn(APPROVAL_REQUIRED, result.block_reasons)

    def test_spring_preflight_required_blocks(self):
        result = evaluate_real_agent_gate(context(spring_preflight_passed=False), sdk_probe=lambda: True)

        self.assertFalse(result.allowed)
        self.assertIn(SPRING_PREFLIGHT_REQUIRED, result.block_reasons)

    def test_secret_missing_blocks_without_secret_values(self):
        result = evaluate_real_agent_gate(context(present_secret_names=()), sdk_probe=lambda: True)

        self.assertFalse(result.allowed)
        self.assertIn(SECRET_MISSING, result.block_reasons)
        self.assertEqual(
            result.missing_secret_names,
            ("CLAWOPS_API_KEY", "CLAWOPS_ACCOUNT_ID", "CLAWOPS_FROM_NUMBER", "OPENAI_API_KEY"),
        )

    def test_allowlist_blocks(self):
        result = evaluate_real_agent_gate(
            context(target_phone_number="test-target-0002"),
            sdk_probe=lambda: True,
        )

        self.assertFalse(result.allowed)
        self.assertIn(TARGET_NOT_ALLOWLISTED, result.block_reasons)

    def test_sdk_not_installed_blocks_without_importing_sdk(self):
        result = evaluate_real_agent_gate(context(), sdk_probe=lambda: False)

        self.assertFalse(result.allowed)
        self.assertFalse(result.sdk_installed)
        self.assertIn(SDK_NOT_INSTALLED, result.block_reasons)

    def test_all_dependencies_still_block_as_not_implemented(self):
        result = evaluate_real_agent_gate(context(), sdk_probe=lambda: True)

        self.assertFalse(result.allowed)
        self.assertTrue(result.sdk_installed)
        self.assertEqual(result.block_reasons, (REAL_AGENT_NOT_IMPLEMENTED,))

    def test_adapter_run_raises_blocked_error(self):
        adapter = RealAgentAdapter(context(), sdk_probe=lambda: True)

        with self.assertRaises(RealAgentBlockedError) as captured:
            adapter.run_reservation_call(call_request())

        self.assertEqual(captured.exception.gate_result.block_reasons, (REAL_AGENT_NOT_IMPLEMENTED,))


def context(
    runtime_mode="real-agent",
    real_agent_enabled=True,
    approval_required=True,
    approval_granted=True,
    spring_preflight_required=True,
    spring_preflight_passed=True,
    target_phone_number="test-target-0001",
    allowed_target_numbers=("test-target-0001",),
    present_secret_names=("CLAWOPS_API_KEY", "CLAWOPS_ACCOUNT_ID", "CLAWOPS_FROM_NUMBER", "OPENAI_API_KEY"),
):
    return RealAgentDependencyContext(
        runtime_mode=runtime_mode,
        real_agent_enabled=real_agent_enabled,
        approval_required=approval_required,
        approval_granted=approval_granted,
        spring_preflight_required=spring_preflight_required,
        spring_preflight_passed=spring_preflight_passed,
        target_phone_number=target_phone_number,
        allowed_target_numbers=allowed_target_numbers,
        present_secret_names=present_secret_names,
    )


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


if __name__ == "__main__":
    unittest.main()
